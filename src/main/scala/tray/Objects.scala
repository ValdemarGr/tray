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

sealed abstract class Objects[F[_]: Concurrent] private (auth: GCSAuth[F], client: Client[F]) {
  def failOnNonSuccess(res: Response[F]): F[Response[F]] =
    if (res.status.isSuccess) Concurrent[F].pure(res)
    else
      res.body.through(fs2.text.utf8Decode).compile.toList.map(_.mkString).flatMap { body =>
        Concurrent[F].raiseError(new TrayError.HttpStatusError(s"tray response had bad status code: $res\n$body"))
      }

  def insert(path: StoragePath, bytes: Array[Byte]): F[Unit] =
    for {
      headers <- auth.getHeader
      _ <-
        client
          .run(
            Request[F](
              method = Method.POST,
              uri =
                Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path.renderString) +? ("uploadType", "media"),
              body = Stream.chunk(Chunk.array(bytes)),
              headers = Headers(`Content-Type`(MediaType.application.json), `Content-Length`(bytes.size)) ++ headers
            )
          )
          .use(failOnNonSuccess)
          .void
    } yield ()

  def get(path: StoragePath): Resource[F, Stream[F, Byte]] =
    for {
      headers <- Resource.eval(auth.getHeader)
      out <- client
        .run(
          Request[F](
            method = Method.GET,
            uri = Endpoints.basic / "b" / path.bucket / "o" / path.path.renderString +? ("alt", "media"),
            headers = headers
          )
        )
        .evalMap(failOnNonSuccess)
        .map(_.body)
    } yield out

}

object Objects {
  def apply[F[_]: Concurrent](auth: GCSAuth[F], client: Client[F]) =
    new Objects(auth, client) {}
}
