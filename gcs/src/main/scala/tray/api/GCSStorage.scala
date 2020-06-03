package tray.api

import cats.Monad
import cats.effect.{ConcurrentEffect, Resource, Sync}
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.headers.{Accept, Authorization}
import tray.GCSItem
import tray.auth.TokenDispenser
import tray.underlying.StorageEndpoints

// fs2 stream compiler is defined for cats.effect.Sync
class GCSStorage[F[_]]
  (client: Client[F], tokenDispenser: TokenDispenser[F])(implicit F: Monad[F], S: Sync[F]) {
  import StorageEndpoints._

  private def authedRequest[A, R](m: Method, uri: Uri)(handler: Response[F] => F[R]): F[R] = client.fetch{
    F.map(tokenDispenser.getToken){ token =>
        val creds: Credentials.Token = Credentials.Token(AuthScheme.Bearer, token.getTokenValue)

        Request[F](
          method = m,
          uri = uri,
          httpVersion = HttpVersion.`HTTP/1.1`,
          headers = Headers.of(
            Authorization(creds),
            Accept(MediaType.application.json)
          )
        )
      }
    }(handler)

  def getObject(item: GCSItem): F[Array[Byte]] =
    Objects.get(item) match { case (uri, m) => authedRequest(m, uri) { response =>
      response
        .body
        .compile
        .to(Array)
    } }
}

object GCSStorage {
  def apply[F[_]: ConcurrentEffect](td: TokenDispenser[F]): Resource[F, GCSStorage[F]] =
    AsyncHttpClient.resource[F](AsyncHttpClient.defaultConfig).map(c => new GCSStorage[F](c, td))

  def apply[F[_]: ConcurrentEffect](client: Client[F], td: TokenDispenser[F]): GCSStorage[F] =
    new GCSStorage(client, td)
}
