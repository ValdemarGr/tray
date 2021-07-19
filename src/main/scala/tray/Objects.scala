package tray

import tray.auth.GCSAuth
import org.http4s.client.Client
import org.http4s.Request
import org.http4s.Method
import tray.endpoints.Endpoints
import org.http4s.Response
import cats.effect.kernel.Sync
import tray.errors.TrayError
import fs2._

import cats.implicits._
import org.http4s._
import org.http4s.headers._
import cats.effect.kernel.Resource

sealed abstract class Objects[F[_]: Sync] private (auth: GCSAuth[F], client: Client[F]) {
  def failOnNonSuccess(res: Response[F]): F[Response[F]] =
    if (res.status.isSuccess) Sync[F].pure(res)
    else Sync[F].raiseError(new TrayError.HttpStatusError(s"tray response had bad status code: $res"))

  def insert(path: StoragePath, bytes: Array[Byte]): F[Unit] =
    client
      .run(
        Request[F](
          method = Method.POST,
          uri =
            Endpoints.upload / "b" / path.bucket / "o" +? ("name", path.path.renderString) +? ("uploadType", "media"),
          body = Stream.chunk(Chunk.array(bytes)),
          headers = Headers(`Content-Type`(MediaType.application.json), `Content-Length`(bytes.size))
        )
      )
      .use(failOnNonSuccess)
      .void

  def get(path: StoragePath): Resource[F, Stream[F, Byte]] =
    client
      .run(
        Request[F](
          method = Method.GET,
          uri = Endpoints.basic / "b" / path.bucket / "o" / path.path.renderString +? ("alt", "media")
        )
      )
      .evalMap(failOnNonSuccess)
      .map(_.body)
}
