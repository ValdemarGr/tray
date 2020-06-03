package tray.api

import cats.Monad
import cats.effect.{ConcurrentEffect, Resource, Sync}
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.headers.{Accept, Authorization, `Content-Range`}
import tray.GCSItem
import tray.auth.TokenDispenser
import tray.underlying.StorageEndpoints

// fs2 stream compiler is defined for cats.effect.Sync
class GCSStorage[F[_]]
  (client: Client[F], tokenDispenser: TokenDispenser[F])(implicit F: Monad[F], S: Sync[F]) {
  import StorageEndpoints._

  private def rangeHeader(start: Int, end: Int, max: Option[Int]) =
    `Content-Range`(org.http4s.headers.Range.SubRange(start, end), max.map(_.toLong))

  private def authedRequest[A, R](m: Method, uri: Uri, extraHeaders: Header*)
                                 (handler: Response[F] => F[R]): F[R] = client.fetch{
    F.map(tokenDispenser.getToken){ token =>
      val creds: Credentials.Token = Credentials.Token(AuthScheme.Bearer, token.getTokenValue)

      val hs: Seq[Header] = Seq(
        Authorization(creds),
        Accept(MediaType.application.json)
      ) ++ extraHeaders

      Request[F](
        method = m,
        uri = uri,
        httpVersion = HttpVersion.`HTTP/1.1`,
        headers = Headers.of(hs: _*)
      )
    }
  }(handler)

  private def unwrapToAB(r: Response[F]): F[Array[Byte]] = {
    r
      .body
      .compile
      .to(Array)
  }

  def getObject(item: GCSItem): F[Array[Byte]] =
    Objects.get(item) match { case (uri, m) => authedRequest(m, uri)(unwrapToAB) }

  private val baseChunkSize = 256 * 1024

  def uploadChunked(item: GCSItem, data: fs2.Stream[F, Byte], chunkFactor: Int) = {
    val rechunked = data.chunkN(baseChunkSize * chunkFactor)

    rechunked
      .zipWithIndex
      .map{ case (bytes, i) =>
        val start = chunkFactor * i
        val end = chunkFactor * (i + 1) - 1

        val h = rangeHeader(start.toInt, end.toInt, None)

        
      }
  }
}

object GCSStorage {
  def apply[F[_]: ConcurrentEffect](td: TokenDispenser[F]): Resource[F, GCSStorage[F]] =
    AsyncHttpClient.resource[F](AsyncHttpClient.defaultConfig).map(c => new GCSStorage[F](c, td))

  def apply[F[_]: ConcurrentEffect](client: Client[F], td: TokenDispenser[F]): GCSStorage[F] =
    new GCSStorage(client, td)
}
