package tray.api

import cats.Monad
import cats.effect.{ConcurrentEffect, Resource, Timer}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.headers._
import org.http4s.util.threads.threadFactory
import tray.auth.TokenDispenser

/**
 * The Google Storage interface, note that `Sync[F]` is needed as side-effect suspension is used and the fs2 compiler needs this implicit.
 */
class GCStorage[F[_]: Timer: ConcurrentEffect]
  (client: Client[F], tokenDispenser: TokenDispenser[F])(implicit F: Monad[F]) {

  protected[api] val baseChunkSize = 256 * 1024

  protected[api] def contentRangeHeader(start: Long, end: Long, max: Option[Long]) =
    `Content-Range`(org.http4s.headers.Range.SubRange(start, end), max)

  protected[api] def rangeHeader(start: Long, end: Long) =
    Range(org.http4s.headers.Range.SubRange(start, end))

  protected[api] def authedRequest[R](m: Method, uri: Uri, b: EntityBody[F], extraHeaders: Header*)
                                 (handler: Response[F] => F[R]): F[R] = client.fetch{
    F.map(tokenDispenser.getToken){ token =>
      val creds: Credentials.Token = Credentials.Token(AuthScheme.Bearer, token.getTokenValue)

      val hs: Seq[Header] = Seq(
        Authorization(creds): Header,
        Accept(MediaType.application.json): Header
      ) ++ extraHeaders

      Request[F](
        method = m,
        uri = uri,
        httpVersion = HttpVersion.`HTTP/1.1`,
        headers = Headers.of(hs: _*),
      ).withEntity(b)
    }
  } { r =>
    handler(r)
  }

  protected[api] def unwrapToAB(r: Response[F]): F[Array[Byte]] = {
    r
      .body
      .compile
      .to(Array)
  }
}

object GCStorage {
  def apply[F[_]: ConcurrentEffect: Timer](td: TokenDispenser[F]): Resource[F, GCStorage[F]] =
    AsyncHttpClient.resource[F](new DefaultAsyncHttpClientConfig.Builder()
      .setMaxConnectionsPerHost(200)
      .setMaxConnections(400)
      .setThreadFactory(threadFactory(name = { i =>
        s"http4s-async-http-client-worker-${i.toString}"
      }))
      .setRequestTimeout(50000000)
      .setReadTimeout(50000000)
      .build()).map(c => new GCStorage[F](c, td))

  def apply[F[_]: ConcurrentEffect: Timer](client: Client[F], td: TokenDispenser[F]): GCStorage[F] =
    new GCStorage(client, td)
}
