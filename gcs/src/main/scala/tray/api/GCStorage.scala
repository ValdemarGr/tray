package tray.api

import cats.{Applicative, Monad}
import cats.effect.{ConcurrentEffect, Resource, Timer}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.headers._
import org.http4s.util.threads.threadFactory
import tray.auth.TokenDispenser
import tray.underlying.Batch

/**
 * The Google Storage interface, note that `Sync[F]` is needed as side-effect suspension is used and the fs2 compiler needs this implicit.
 */
class GCStorage[F[_]: Timer: ConcurrentEffect]
  (client: Client[F], tokenDispenser: TokenDispenser[F])(implicit F: Monad[F]) {

  protected[tray] val baseChunkSize = 256 * 1024

  protected[tray] def contentRangeHeader(start: Long, end: Long, max: Option[Long]) =
    `Content-Range`(org.http4s.headers.Range.SubRange(start, end), max)

  protected[tray] def rangeHeader(start: Long, end: Long) =
    Range(org.http4s.headers.Range.SubRange(start, end))

  protected[tray] def makeRequest[R](m: Method, uri: Uri, b: EntityBody[F], extraHeaders: Header*): F[Request[F]] =
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

  protected[tray] def authedRequest[R](m: Method, uri: Uri, b: EntityBody[F], extraHeaders: Header*)
                                     (handler: Response[F] => F[R]): F[R] = client.fetch(makeRequest(m, uri, b, extraHeaders: _*))(handler)
  protected[tray] def authedRequest[R](r: Request[F])
                                     (handler: Response[F] => F[R]): F[R] = client.fetch(r)(handler)

  protected[tray] def unwrapToAB(r: Response[F]): F[Array[Byte]] = {
    r
      .body
      .compile
      .to(Array)
  }

  val batchAp: Applicative[Batch[*, F]] = Batch.batchInstance[F]
    /*
      def batchedRequest(m: Method, uri: Uri, bodies: fs2.Stream[F, Request[F]], extraHeaders: Header*) = {
        val boundaryId = UUID.randomUUID().toString
        val contentIdBase = UUID.randomUUID().toString

        val mixedPartCT: `Content-Type` = `Content-Type`(MediaType.multipartType(MediaType.multipart.mixed.subType, Some(boundaryId)))


        Content-Type: application/http
    Content-Transfer-Encoding: binary
    Content-ID: <b29c5de2-0db4-490b-b421-6a51b598bd22+1>

      // Bodies must be made to multiparts
      val formattedBodies = bodies.zipWithIndex.map{ case (b, i) =>
        val headers: Seq[Header] = Seq(
          `Content-Type`(MediaType.application.http) : Header,
          Header("Content-ID", contentIdBase + "-" + i.toString): Header
        ) ++ `Content-Transfer-Encoding`.parse("binary").toOption.toSeq

        val headersToString: Seq[String] = b.headers.toList.map(_.value)
        val requestToString = b.method.renderString

        Part(
          headers = Headers.of(headers: _*),
          body = fs2.Stream()
        )
      }

      val t: Part[F] = ??? //org.http4s.multipart.Part()

      val mp: Multipart[F] = Multipart(Vector(t))

      Request(

      )

    }*/
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
