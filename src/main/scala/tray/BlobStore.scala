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

trait BlobStore[F[_]] {
  def putBlob(path: StoragePath, bytes: Array[Byte]): F[Unit]

  //triggers chunked transfer encoding
  def putPipe(path: StoragePath, length: Long): Pipe[F, Byte, Unit]

  //enables resumed uploads
  def putResumable(path: StoragePath, chunkSize: Int = 512 * 1024): Pipe[F, Byte, (Throwable, Location, Long)]

  def getBlob(path: StoragePath): Resource[F, Stream[F, Byte]]
}

object BlobStore {
  def apply[F[_]: Concurrent](auth: GCSAuth[F], client: Client[F]): BlobStore[F] =
    new BlobStore[F] {
      def failOnNonSuccess(res: Response[F]): F[Response[F]] =
        if (res.status.isSuccess) Concurrent[F].pure(res)
        else
          res.body.through(fs2.text.utf8Decode).compile.toList.map(_.mkString).flatMap { body =>
            Concurrent[F].raiseError(new TrayError.HttpStatusError(s"tray response had bad status code: $res\n$body"))
          }

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
            _ <- client
              .run(
                Request[F](
                  method = Method.POST,
                  uri = Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path) +? ("uploadType", "media"),
                  body = stream,
                  headers =
                    Headers(`Content-Type`(MediaType.application.`octet-stream`), `Content-Length`(length)) ++ headers
                )
              )
              .use(failOnNonSuccess)
              .void
          } yield ()

        Stream.eval(eff)
      }

      def putByteRange(uri: Uri, offset: Long, chunk: Chunk[Byte]): F[Either[Throwable, Long]] = {
        val req = Request[F](
          method = Method.PUT,
          uri = uri,
          headers = Headers(`Content-Length`(chunk.size), `Content-Range`(offset, offset + chunk.size))
        ).withEntity(chunk)

        val effect: F[Either[Throwable, Long]] = client.run(req).use[Either[Throwable, Long]] { resp =>
          //Resume Incomplete https://cloud.google.com/storage/docs/performing-resumable-uploads#resume-upload
          val parsed: Either[Throwable, Long] = if (resp.status.code == 308) {
            val rh: Option[Range] = resp.headers.get[Range]
            val outE: Either[Throwable, Long] = rh match {
              case None => Left(new Exception(s"missing range header in response: $resp"))
              case Some(rng) =>
                val maxed = rng.ranges.toList
                  .flatMap(_.second.toList)
                  .maxByOption(identity)

                maxed match {
                  case None        => Left(new Exception(s"range header had no end ranges in response: $resp"))
                  case Some(value) => Right(value)
                }
            }
            outE
          } else if (resp.status == Status.Ok || resp.status == Status.Created) {
            Right(chunk.size + offset)
          } else {
            Left(new Exception(s"got unexpected result from api with response: $resp"))
          }

          Concurrent[F].pure(parsed)
        }

        effect.attempt
          .map(_.flatten)
      }

      def resumeFrom(uri: Uri, offset: Long, chunkSize: Int, stream: Stream[F, Byte]): Stream[F, (Throwable, Long)] =
        stream.pull
          .unconsN(chunkSize)
          .flatMap {
            case None => Pull.done
            case Some((x: Chunk[Byte], xs: Stream[F, Byte])) =>
              val placed: F[Either[Throwable, Long]] = putByteRange(uri, offset, x)

              val outStream: Stream[F, (Throwable, Long)] = Stream.eval(placed).flatMap {
                case Left(err) => Stream((err, offset))
                case Right(newOffest) =>
                  val toDrop = newOffest - offset

                  val chunksThatServerDidntEat: Chunk[Byte] =
                    if (toDrop == x.size) {
                      Chunk.empty
                    } else {
                      x.drop(toDrop.toInt)
                    }

                  resumeFrom(
                    uri,
                    offset = offset + chunkSize - chunksThatServerDidntEat.size,
                    chunkSize = chunkSize,
                    stream = xs.cons(chunksThatServerDidntEat)
                  )
              }

              outStream.pull.echo
          }
          .stream

      def putResumable(path: StoragePath, chunkSize: Int = 512 * 1024): Pipe[F, Byte, (Throwable, Location, Long)] =
        stream => {
          val putLoc: F[Location] =
            for {
              headers <- auth.getHeader
              resp <- client
                .run(
                  Request[F](
                    method = Method.POST,
                    uri =
                      Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path) +? ("uploadType", "resumable"),
                    headers = Headers(`Content-Type`(MediaType.application.`octet-stream`)) ++ headers
                  )
                )
                .use(x => failOnNonSuccess(x))
              loc <- Concurrent[F].fromOption(
                resp.headers.get[`Location`],
                new Exception(s"failed to find location header in resumable upload")
              )
            } yield loc

          val putInto: Stream[F, (Throwable, Location, Long)] =
            Stream
              .eval(putLoc)
              .flatMap(loc => resumeFrom(loc.uri, 0, chunkSize, stream).map { case (t, o) => (t, loc, o) })

          putInto
        }

      //https://cloud.google.com/storage/docs/json_api/v1/objects/get
      def getBlob(path: StoragePath): Resource[F, Stream[F, Byte]] =
        for {
          headers <- Resource.eval(auth.getHeader)
          out <- client
            .run(
              Request[F](
                method = Method.GET,
                uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? ("alt", "media"),
                headers = headers
              )
            )
            .evalMap(failOnNonSuccess)
            .map(_.body)
        } yield out
    }
}
