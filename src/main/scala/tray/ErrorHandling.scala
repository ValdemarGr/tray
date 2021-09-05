package tray

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s._
import tray.errors._

object ErrorHandling {
  // error helpers
  def errBody[F[_]: Concurrent](defaultErr: String, resp: Stream[F, Byte]): F[String] =
    resp.through(fs2.text.utf8Decode).compile.foldMonoid.attempt.map {
      case Left(err)   => defaultErr + s", cloud not get body with error $err"
      case Right(body) => defaultErr + s"\n$body"
    }

  def failOnNonSuccess[F[_]: Concurrent](req: Request[F], res: Response[F]): F[Response[F]] =
    if (res.status.isSuccess) Concurrent[F].pure(res)
    else
      errBody(s"tray response had bad status code: \n$res\n$req", res.body).flatMap { em =>
        Concurrent[F].raiseError(new TrayError.HttpStatusError(em))
      }
}
