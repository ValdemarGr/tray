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

trait BlobStore[F[_]] {
  def putBlob(path: StoragePath, contentType: MediaType, bytes: Array[Byte]): F[Unit]

  def putPipe(path: StoragePath, contentType: MediaType, length: Long): Pipe[F, Byte, Unit]

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

      def putBlob(path: StoragePath, contentType: MediaType, bytes: Array[Byte]): F[Unit] =
        Stream.chunk[F, Byte](Chunk.array(bytes))
          .through(putPipe(path, contentType, bytes.length.toLong))
          .compile
          .drain

      def putPipe(path: StoragePath, contentType: MediaType, length: Long): Pipe[F, Byte, Unit] = { stream =>
        val eff =
          for {
            headers <- auth.getHeader
            _ <- client
              .run(
                Request[F](
                  method = Method.POST,
                  uri = Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path) +? ("uploadType", "media"),
                  body = stream,
                  headers = Headers(`Content-Type`(contentType), `Content-Length`(length)) ++ headers
                )
              )
              .use(failOnNonSuccess)
              .void
          } yield ()

        Stream.eval(eff)
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
