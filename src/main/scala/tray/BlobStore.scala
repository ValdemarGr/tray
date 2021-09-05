package tray

import tray.auth.GCSAuth
import org.http4s.client.Client
import org.http4s.Request
import org.http4s.Method
import tray.endpoints.Endpoints
import org.http4s.Response
import cats.effect.kernel.Concurrent
import tray.errors.TrayError
import fs2._

import cats.implicits._
import org.http4s._
import org.http4s.headers._
import cats.effect.kernel.Resource
import cats.data.NonEmptyList
import cats.data.EitherT

trait BlobStore[F[_]] extends ObjectsAPI[F]

object BlobStore {
  // magic constant from docs (256kb)
  // https://cloud.google.com/storage/docs/performing-resumable-uploads#chunked-upload
  val RECOMMENDED_SIZE: Int = 1024 * 256

  def apply[F[_]: Concurrent](auth: GCSAuth[F], client: Client[F]): BlobStore[F] =
    new BlobStore[F] {
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
              uri = Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path) +? ("uploadType", "media"),
              body = stream,
              headers =
                Headers(`Content-Type`(MediaType.application.`octet-stream`), `Content-Length`(length)) ++ headers
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
      def handleHeader(resp: Response[F], doneSize: Long): F[Long] =
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
          } yield l
        } else if (resp.status == Status.Ok || resp.status == Status.Created) {
          Concurrent[F].pure(doneSize)
        } else {
          ErrorHandling
            .errBody(s"got unexpected result from api with response: $resp", resp.body)
            .flatMap(em => Concurrent[F].raiseError(new Exception(em)))
        }

      def putByteRange(uri: Uri, offset: Long, chunk: Chunk[Byte], isLast: Boolean): F[Either[Throwable, Long]] = {
        val endingO = if (isLast) {
          Some(offset + chunk.size)
        } else None

        val req = Request[F](
          method = Method.PUT,
          uri = uri,
          headers = Headers(
            `Content-Length`(chunk.size),
            `Content-Range`(range = Range.SubRange(offset, offset + chunk.size - 1), endingO)
          ) // - 1 since 0 indexed
        ).withEntity(chunk)

        val effect: F[Long] = client.run(req).use { resp =>
          handleHeader(resp, chunk.size + offset)
        }

        effect.attempt
      }

      def putResumableFrom(uri: Uri, offset: Long, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Long)] = stream =>
        stream.pull
          .unconsN((RECOMMENDED_SIZE * chunkFactor) + 1, allowFewer = true)
          .flatMap {
            case None                                        => Pull.done
            case Some((x: Chunk[Byte], xs: Stream[F, Byte])) =>
              // if the next chunk is non-empty then we are not done
              val (firsts, nextFirst) = x.splitAt(RECOMMENDED_SIZE * chunkFactor)

              def returnOrGo(
                fa: F[Either[Throwable, Long]]
              )(f: Long => Stream[F, (Throwable, Long)]): Stream[F, (Throwable, Long)] =
                Stream.eval(fa).flatMap {
                  case Left(err)     => Stream((err, offset))
                  case Right(offset) => f(offset)
                }

              val restStream: Stream[F, (Throwable, Long)] = if (nextFirst.isEmpty) {
                returnOrGo(putByteRange(uri, offset, x, isLast = true))(_ => Stream.empty)
              } else {
                returnOrGo(putByteRange(uri, offset, x, isLast = false)) { newOffset =>
                  val toDrop = newOffset - offset

                  val chunksThatServerDidntEat: Chunk[Byte] =
                    if (toDrop == x.size) {
                      Chunk.empty
                    } else {
                      x.drop(toDrop.toInt)
                    }

                  xs.cons(chunksThatServerDidntEat ++ nextFirst).through {
                    putResumableFrom(
                      uri,
                      offset = newOffset,
                      chunkFactor = chunkFactor
                    )
                  }
                }
              }

              restStream.pull.echo
          }
          .stream

      def initiateResumable(path: StoragePath): F[Location] =
        for {
          headers <- auth.getHeader
          req = Request[F](
            method = Method.POST,
            uri = Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path) +? ("uploadType", "resumable"),
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
                stream.through(putResumableFrom(loc.uri, 0, chunkFactor)).map { case (t, o) => (t, loc, o) }
              }

          putInto
        }

      //https://cloud.google.com/storage/docs/json_api/v1/objects/get
      def getBlob(path: StoragePath): Stream[F, Byte] =
        Stream.eval(auth.getHeader).flatMap { headers =>
          val req = Request[F](
            method = Method.GET,
            uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? ("alt", "media"),
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
          val thisEnd = Math.min(offset + thisChunk, end)

          val req = Request[F](
            method = Method.GET,
            uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? ("alt", "media"),
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
                val nextBytes = getRange(path, offset + length, end, chunkFactor)
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
            uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? ("alt", "json"),
            headers = headers
          )

          client
            .expect[BlobMetadata](req)
        }
    }
}
