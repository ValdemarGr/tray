package tray

import fs2._
import org.http4s.headers._
import org.http4s._
import cats.effect._
import cats.implicits._
import tray.endpoints.Endpoints

trait ObjectsAPI[F[_]] {
  def putBlob(path: StoragePath, bytes: Array[Byte]): F[Unit]

  def putPipe(path: StoragePath, length: Long): Pipe[F, Byte, Unit]

  def putResumableFrom(loc: Location, offset: Long, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Long)]

  def putResumable(path: StoragePath, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Location, Long)]

  def getBlob(path: StoragePath): Stream[F, Byte]

  def getRange(path: StoragePath, start: Long, end: Long, chunkFactor: Int = 1): Stream[F, Byte]

  def getResumableFrom(path: StoragePath, start: Long, chunkFactor: Int = 1): Stream[F, Byte]

  def getResumable(path: StoragePath, chunkFactor: Int = 1): Stream[F, Byte]

  val byteStreamToResumable: Pipe[F, Byte, Either[(Throwable, Long), Chunk[Byte]]]

  def getMetadata(path: StoragePath): F[BlobMetadata]
}

object ObjectsAPI {
  // magic constant from docs (256kb)
  // https://cloud.google.com/storage/docs/performing-resumable-uploads#chunked-upload
  // + 1 since google cloud storage api has an off by one error
  val RECOMMENDED_SIZE: Int = 1024 * 256

  //indicators for resumable upload
  sealed trait ApiOffset
  final case class IncompleteOffset(next: Long) extends ApiOffset
  case object DoneOffset extends ApiOffset

  def apply[F[_]: Concurrent](bs: BlobStore[F]): ObjectsAPI[F] = new ObjectsAPI[F] {
    import bs._

    def putBlob(path: StoragePath, bytes: Array[Byte]): F[Unit] =
      Stream
        .chunk[F, Byte](Chunk.array(bytes))
        .through(putPipe(path, bytes.length.toLong))
        .compile
        .drain

    def putPipe(path: StoragePath, length: Long): Pipe[F, Byte, Unit] = { stream =>
      val eff =
        for {
          headers <- auth.getHeader
          req = Request[F](
            method = Method.POST,
            uri = Endpoints.upload / "b" / path.bucket / "o" +? (("name", path.path)) +? (("uploadType", "media")),
            body = stream,
            headers = Headers(`Content-Type`(MediaType.application.`octet-stream`), `Content-Length`(length)) ++ headers
          )
          _ <- client
            .run(req)
            .use(res => ErrorHandling.failOnNonSuccess(req, res))
            .void
        } yield ()

      Stream.eval(eff)
    }

    // These next blocks of code regard resumable uploads
    //Resume Incomplete https://cloud.google.com/storage/docs/performing-resumable-uploads#resume-upload
    def handleHeader(resp: Response[F]): F[ApiOffset] =
      if (resp.status.code == 308) {
        for {
          range <- resp.headers.get[Range] match {
            case None =>
              ErrorHandling
                .errBody(s"missing range header in response: $resp", resp.body)
                .flatMap(em => Concurrent[F].raiseError[Range](new Exception(em)))
            case Some(range) => Concurrent[F].pure(range)
          }

          l <- range.ranges.toList
            .flatMap(_.second.toList)
            .maxByOption(identity) match {
            case None =>
              ErrorHandling
                .errBody(s"range header had no end ranges in response: $resp", resp.body)
                .flatMap(em => Concurrent[F].raiseError[Long](new Exception(em)))
            case Some(value) => Concurrent[F].pure(value)
          }
        } yield IncompleteOffset(l)
      } else if (resp.status == Status.Ok || resp.status == Status.Created) Concurrent[F].pure(DoneOffset)
      else {
        ErrorHandling
          .errBody(s"got unexpected result from api with response: $resp", resp.body)
          .flatMap(em => Concurrent[F].raiseError(new Exception(em)))
      }

    def putByteRange(uri: Uri, offset: Long, chunk: Chunk[Byte], isLast: Boolean): F[Either[Throwable, ApiOffset]] = {
      val endingO = if (isLast) {
        Some(offset + chunk.size)
      } else None

      val req = Request[F](
        method = Method.PUT,
        uri = uri,
        headers = Headers(
          `Content-Length`(chunk.size.toLong),
          `Content-Range`(range = Range.SubRange(offset, offset + chunk.size - 1), endingO)
        ) // - 1 since this range is _inclusive_ of the first byte; size(0, 1, .., 99) = 100
      ).withEntity(chunk)

      val effect: F[ApiOffset] = client.run(req).use { resp =>
        handleHeader(resp)
      }

      effect.attempt
    }

    // we eat one extra, to check if this chunk is the last one
    def putResumableFrom(loc: Location, offset: Long, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Long)] =
      stream =>
        stream.pull
          .unconsN((RECOMMENDED_SIZE * chunkFactor) + 1, allowFewer = true)
          .flatMap {
            case None => Pull.done
            case Some((x: Chunk[Byte], tmp: Stream[F, Byte])) =>
              val (isLast, bytes, xs) =
                if (x.size <= RECOMMENDED_SIZE * chunkFactor) {
                  (true, x, tmp)
                } else {
                  (false, x.dropRight(1), Stream.chunk(x.takeRight(1)) ++ tmp)
                }

              Stream
                .eval(putByteRange(loc.uri, offset, bytes, isLast = isLast))
                .flatMap {
                  case Left(err)                        => Stream((err, offset))
                  case Right(DoneOffset)                => Stream.empty
                  case Right(IncompleteOffset(thisEnd)) =>
                    // if we didn't progress, fail
                    if (thisEnd == offset)
                      Stream((new Exception(s"server didn't accept any bytes at $offset"), offset))
                    // else we continue
                    else {
                      // begin after the offset we just completed at
                      val nextOffset = thisEnd + 1
                      /* off-by-one correctness sketch
                       * offset is 200
                       * server ate 5 out of 10
                       * thisEnd = 204 (server ate bytes [200, 201 ..., 204])
                       * nextOffset = 205
                       * remaining must be [205, 206, 207, 208, 209]
                       * below must be drop nextOffset - offset (5) bytes
                       *
                       * in the case that all bytes are eaten then nextOffset = offset + size([200, ..., 209]) = 210
                       * [200, ..., 209].drop(210 - 200) = []
                       */
                      val bytesThatServerDidntAccept: Chunk[Byte] = bytes.drop((nextOffset - offset).toInt)
                      val rest = Stream.chunk(bytesThatServerDidntAccept) ++ xs
                      rest.through(putResumableFrom(loc, nextOffset, chunkFactor))
                    }
                }
                .pull
                .echo
          }
          .stream

    def initiateResumable(path: StoragePath): F[Location] =
      for {
        headers <- auth.getHeader
        req = Request[F](
          method = Method.POST,
          uri = Endpoints.upload / "b" / path.bucket / "o" +? (("name", path.path)) +? (("uploadType", "resumable")),
          headers = Headers(`Content-Type`(MediaType.application.`octet-stream`)) ++ headers
        )
        resp <- client
          .run(req)
          .use(res => ErrorHandling.failOnNonSuccess(req, res))
        loc <- Concurrent[F].fromOption(
          resp.headers.get[`Location`],
          new Exception(s"failed to find location header in resumable upload")
        )
      } yield loc

    def putResumable(path: StoragePath, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Location, Long)] =
      stream => {
        val putLoc: F[Location] = initiateResumable(path)

        val putInto: Stream[F, (Throwable, Location, Long)] =
          Stream
            .eval(putLoc)
            .flatMap { loc =>
              stream.through(putResumableFrom(loc, 0, chunkFactor)).map { case (t, o) => (t, loc, o) }
            }

        putInto
      }

    //https://cloud.google.com/storage/docs/json_api/v1/objects/get
    def getBlob(path: StoragePath): Stream[F, Byte] =
      Stream.eval(auth.getHeader).flatMap { headers =>
        val req = Request[F](
          method = Method.GET,
          uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? (("alt", "media")),
          headers = headers
        )

        client
          .stream(req)
          .evalMap(res => ErrorHandling.failOnNonSuccess(req, res))
          .flatMap(_.body)

      }

    def getRange(path: StoragePath, offset: Long, end: Long, chunkFactor: Int = 1): Stream[F, Byte] =
      Stream.eval(auth.getHeader).flatMap { headers =>
        val thisChunk = RECOMMENDED_SIZE * chunkFactor
        val thisEnd = Math.min(offset + thisChunk, end) - 1 // since this is inclusive

        val req = Request[F](
          method = Method.GET,
          uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? (("alt", "media")),
          headers = headers ++ Headers(Range(offset, thisEnd))
        )

        val c = client
          .run(req)
          .evalMap(res => ErrorHandling.failOnNonSuccess(req, res))

        /*
         * We must use the Content-Length header to determine how much the server actually gave us, to derive the next range.
         *
         * We need to do some unsafe work with the response
         * Say we were to use a stream's lifecycle management for a resource
         * when we open the resource via a flatMap we would need to say resp.body ++ getRange(path, range')
         * Doing so would only let the resource close once the entire callstack of streams had completed.
         *
         * To combat this, realize that the responses headers can exceed the lifetime of the response
         * such that it becomes a matter of closing the resource _after_ the current body has been consumed
         * and passing the header to the next batch
         */
        Stream.eval(c.allocated).flatMap { case (resp, release) =>
          val cl = resp.headers.get[`Content-Length`].map(_.length)

          cl match {
            case None =>
              val failF = ErrorHandling
                .errBody(s"failed to find content-length header in response $resp", resp.body)
                .flatMap { em =>
                  Concurrent[F].raiseError[Byte](new Exception(em))
                }
              Stream.eval(release) >> Stream.eval(failF)
            case Some(length) =>
              val theseBytes = resp.body.onFinalize[F](release)
              val nextOffset = offset + length
              /*
               * if we are done let the last byte be n, then nextOffset = n + 1 and end is the index + 1
               * we can either check nextOffset - 1 == end - 1 or nextOffset - 1 == thisEnd or nextOffset == end
               * all of which are equivalent
               */
              val nextBytes =
                if (nextOffset == end) Stream.empty
                else getRange(path, offset + length, end, chunkFactor)

              theseBytes ++ nextBytes
          }
        }
      }

    def getResumable(path: StoragePath, chunkFactor: Int = 1): Stream[F, Byte] =
      getResumableFrom(path, 0, chunkFactor)

    def getResumableFrom(path: StoragePath, start: Long, chunkFactor: Int = 1): Stream[F, Byte] =
      Stream.eval(getMetadata(path)).flatMap { metadata =>
        getRange(path, start, metadata.size, chunkFactor)
      }

    val byteStreamToResumable: Pipe[F, Byte, Either[(Throwable, Long), Chunk[Byte]]] = stream =>
      stream.chunks
        .zipWithScan(0L) { case (idx, bytes) =>
          idx + bytes.size
        }
        .attempt
        .zipWithPrevious
        .flatMap {
          case (Some(Right((_, l))), Left(err)) => Stream(Left((err, l)))
          case (_, Left(err))                   => Stream(Left((err, 0)))
          case (_, Right((bytes, _)))           => Stream(Right(bytes))
        }

    def getMetadata(path: StoragePath): F[BlobMetadata] =
      auth.getHeader.flatMap { headers =>
        val req = Request[F](
          method = Method.GET,
          uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? (("alt", "json")),
          headers = headers
        )

        client
          .expect[BlobMetadata](req)
      }
  }
}
