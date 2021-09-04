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
  def apply[F[_]: Concurrent](auth: GCSAuth[F], client: Client[F]): BlobStore[F] =
    new BlobStore[F] {
      // error helpers
      def errBody(defaultErr: String, resp: Stream[F, Byte]): F[String] =
        resp.through(fs2.text.utf8Decode).compile.foldMonoid.attempt.map {
          case Left(err)   => defaultErr + s", cloud not get body with error $err"
          case Right(body) => defaultErr + s"\n$body"
        }

      def failOnNonSuccess(req: Request[F], res: Response[F]): F[Response[F]] =
        if (res.status.isSuccess) Concurrent[F].pure(res)
        else
          errBody(s"tray response had bad status code: \n$res\n$req", res.body).flatMap { em =>
            Concurrent[F].raiseError(new TrayError.HttpStatusError(em))
          }

      def putBlob(path: StoragePath, bytes: Array[Byte]): F[Unit] =
        Stream
          .chunk[F, Byte](Chunk.array(bytes))
          .through(putPipe(path, bytes.length.toLong))
          .compile
          .drain

      // this should work, but it doesn't
      def putPipeChunked(path: StoragePath): Pipe[F, Byte, Unit] = { stream =>
        val eff =
          for {
            headers <- auth.getHeader
            req = Request[F](
              method = Method.POST,
              uri = Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path) +? ("uploadType", "media"),
              headers = Headers(`Content-Type`(MediaType.application.`octet-stream`)) ++ headers
            ).withEntity(stream)
            _ <- client
              .run(req)
              .use(res => failOnNonSuccess(req, res))
              .void
          } yield ()

        Stream.eval(eff)
      }

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
              .use(res => failOnNonSuccess(req, res))
              .void
          } yield ()

        Stream.eval(eff)
      }

      //https://cloud.google.com/storage/docs/json_api/v1/objects/get
      def getBlob(path: StoragePath): Resource[F, Stream[F, Byte]] =
        for {
          headers <- Resource.eval(auth.getHeader)
          req = Request[F](
            method = Method.GET,
            uri = Endpoints.basic / "b" / path.bucket / "o" / path.path +? ("alt", "media"),
            headers = headers
          )
          out <- client
            .run(req)
            .evalMap(res => failOnNonSuccess(req, res))
            .map(_.body)
        } yield out

      // These next blocks of code regard resumable uploads
      //Resume Incomplete https://cloud.google.com/storage/docs/performing-resumable-uploads#resume-upload
      def handleHeader(resp: Response[F], doneSize: Long): F[Long] =
        if (resp.status.code == 308) {
          for {
            range <- resp.headers.get[Range] match {
              case None =>
                errBody(s"missing range header in response: $resp", resp.body).flatMap(em =>
                  Concurrent[F].raiseError[Range](new Exception(em))
                )
              case Some(range) => Concurrent[F].pure(range)
            }

            l <- range.ranges.toList
              .flatMap(_.second.toList)
              .maxByOption(identity) match {
              case None =>
                errBody(s"range header had no end ranges in response: $resp", resp.body).flatMap(em =>
                  Concurrent[F].raiseError[Long](new Exception(em))
                )
              case Some(value) => Concurrent[F].pure(value)
            }
          } yield l
        } else if (resp.status == Status.Ok || resp.status == Status.Created) {
          Concurrent[F].pure(doneSize)
        } else {
          errBody(s"got unexpected result from api with response: $resp", resp.body).flatMap(em =>
            Concurrent[F].raiseError(new Exception(em))
          )
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

      def resumeFrom(uri: Uri, offset: Long, chunkSize: Int, stream: Stream[F, Byte]): Stream[F, (Throwable, Long)] =
        stream.pull
          .unconsN(chunkSize + 1, allowFewer = true)
          .flatMap {
            case None                                        => Pull.done
            case Some((x: Chunk[Byte], xs: Stream[F, Byte])) =>
              // if the next chunk is non-empty then we are not done
              val (firsts, nextFirst) = x.splitAt(chunkSize)

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

                  resumeFrom(
                    uri,
                    offset = offset + chunkSize - chunksThatServerDidntEat.size,
                    chunkSize = chunkSize,
                    stream = xs.cons(chunksThatServerDidntEat ++ nextFirst)
                  )
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
            .use(res => failOnNonSuccess(req, res))
          loc <- Concurrent[F].fromOption(
            resp.headers.get[`Location`],
            new Exception(s"failed to find location header in resumable upload")
          )
        } yield loc

      def putResumable(path: StoragePath, chunkFactor: Int = 1): Pipe[F, Byte, (Throwable, Location, Long)] =
        stream => {
          // magic constant from docs (256kb)
          // https://cloud.google.com/storage/docs/performing-resumable-uploads#chunked-upload
          val reccomendedBy = 262144
          val putLoc: F[Location] = initiateResumable(path)

          val putInto: Stream[F, (Throwable, Location, Long)] =
            Stream
              .eval(putLoc)
              .flatMap { loc =>
                resumeFrom(loc.uri, 0, reccomendedBy * chunkFactor, stream).map { case (t, o) => (t, loc, o) }
              }

          putInto
        }
    }
}
