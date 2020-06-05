package tray.api

import cats.Monad
import cats.effect.{ConcurrentEffect, Resource, Sync, Timer}
import fs2.{Chunk, Pure}
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import org.http4s._
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.Client
import org.http4s.client.middleware.GZip
import org.http4s.headers._
import org.http4s.util.threads.threadFactory
import tray.GCSItem
import tray.auth.TokenDispenser
import tray.underlying.StorageEndpoints

// fs2 stream compiler is defined for cats.effect.Sync
class GCSStorage[F[_]: Timer: ConcurrentEffect]
  (client: Client[F], tokenDispenser: TokenDispenser[F])(implicit F: Monad[F], S: Sync[F]) {
  import StorageEndpoints._

  private def rangeHeader(start: Long, end: Long, max: Option[Long]) =
    `Content-Range`(org.http4s.headers.Range.SubRange(start, end), max)

  private def authedRequest[R](m: Method, uri: Uri, b: EntityBody[F], extraHeaders: Header*)
                                 (handler: Response[F] => F[R]): F[R] = GZip()(client).fetch{
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
  }(handler)

  private def unwrapToAB(r: Response[F]): F[Array[Byte]] = {
    r
      .body
      .compile
      .to(Array)
  }

  def getObject(item: GCSItem): F[Array[Byte]] =
    Objects.get(item) match { case (uri, m) => authedRequest(m, uri, EmptyBody)(unwrapToAB) }

  def putObject(item: GCSItem, data: fs2.Stream[F, Byte]): F[Unit] =
    Objects.put(item) match { case (uri, m) => authedRequest(m, uri, data)(_ => S.unit) }

  def putParallel(item: GCSItem, data: fs2.Stream[F, Byte], concurrent: Int, chunkFactor: Int, prefix: String): F[Unit] = {
    val rechunked: fs2.Stream[F, (Chunk[Byte], Long)] = data.chunkN(baseChunkSize * chunkFactor).zipWithIndex
    import cats.implicits._

    val uploaded: fs2.Stream[F, String] = rechunked.mapAsyncUnordered(concurrent) { case (c, i) =>
      val itemName = prefix + "-" + i.toString
      println(itemName)
      putObject(GCSItem(item.bucket, itemName), fs2.Stream.chunk(c)).as(itemName)
    }

    val formattedSources: F[String] = uploaded
      .compile
      .toList
      .map(_.map(name => s"""{"name":"${name}"}""").mkString(","))

    formattedSources.flatMap{ items =>
      import fs2.text._
      val s = s"""{"sourceObjects":[${items}],"destination":{"contentType":"application/json"}}"""

      val (uri, m) = Objects.compose(item)

      authedRequest(m, uri, fs2.Stream(s).through(utf8Encode), `Content-Type`(MediaType.application.json))(_ => S.unit)
    }
  }

  private val baseChunkSize = 256 * 1024

  private trait Offset
  private case object Done extends Offset
  private case class NotDone(offset: Long) extends Offset

  private def doExpBackoffChunkedRequest(m: Method, uri: Uri, bytes: fs2.Chunk[Byte], h: Header): F[Offset] = {
    val b: fs2.Stream[Pure, Byte] = fs2.Stream.chunk(bytes)

    import scala.concurrent.duration._

    // Do backoff
    fs2.Stream.retry(
      fo = authedRequest(m, uri, b, h) { r =>
        (r.status, r.headers.get(org.http4s.headers.Range).flatMap(x => x.ranges.head.second)) match {
          case (status, _) if status.code < 300 =>
            F.pure[Offset](Done)
          case (status, _) if status.code != 308 =>
            S.raiseError[Offset](new Exception(s"failed with status ${r.status.toString()}"))
          case (_, Some(endRange)) =>
            F.pure[Offset](NotDone(endRange + 1))
          case (_, None) =>
            S.raiseError[Offset](new Exception(s"failed to get header in ${r.toString()}"))
        }
      },
      delay = 2.seconds,
      nextDelay = last => (last.toSeconds^2).seconds,
      maxAttempts = 4
    )
      .compile
      .lastOrError
  }

  def putObjectChunked(item: GCSItem, data: fs2.Stream[F, Byte], chunkFactor: Int): F[Unit] = {
    val rechunked: fs2.Stream[F, Chunk[Byte]] = data.chunkN(baseChunkSize * chunkFactor)

    val (initialUri, initialM) = Objects.initiateResumableUpload(item)

    authedRequest(initialM, initialUri, EmptyBody) { resp =>
      // Location header has the new uri
      val h: Option[Location] = resp.headers.get(Location)

      h match {
        case None => S.raiseError(new Exception(s"failed to find location header, got ${resp.status.toString()}"))
        case Some(loc) => {
          val uri = loc.uri
          val m: Method = Objects.resumableUploadChunk

          val firsts: fs2.Stream[F, Chunk[Byte]] = rechunked
            .dropLast

          val completedFirsts: fs2.Stream[F, Offset] = firsts
            .fold(F.pure[Offset](NotDone(0.toLong))){ case (prevOffsetF, bytes) =>
              import cats.implicits._
              prevOffsetF.flatMap{
                case Done => S.raiseError[Offset](new Exception("got done when there was still work"))
                case NotDone(offset) =>
                  val start = offset
                  val end = bytes.size.toLong + offset - 1

                  val h = rangeHeader(start, end, None)
                  doExpBackoffChunkedRequest(m, uri, bytes, h)
              }
            }.evalMap(x => x)

          val combined: fs2.Stream[F, Offset] = completedFirsts
            .lastOr(NotDone(0)) // If there is only one chunk
            .flatMap{
              case Done => fs2.Stream.empty
              case NotDone(offset) =>
                rechunked
                  .last
                  .collect{ case Some(x) => x }
                  .evalMap { bytes =>
                    val start = offset
                    val end = bytes.size.toLong + offset - 1
                    val length = Some(end + 1)

                    val h = rangeHeader(start, end, length)
                    doExpBackoffChunkedRequest(m, uri, bytes, h)
                  }
            }

          combined.compile.drain
        }
      }
    }
  }
}

object GCSStorage {
  def apply[F[_]: ConcurrentEffect: Timer](td: TokenDispenser[F]): Resource[F, GCSStorage[F]] =
    AsyncHttpClient.resource[F](new DefaultAsyncHttpClientConfig.Builder()
      .setMaxConnectionsPerHost(200)
      .setMaxConnections(400)
      .setThreadFactory(threadFactory(name = { i =>
        s"http4s-async-http-client-worker-${i.toString}"
      }))
      .setRequestTimeout(50000000)
      .setReadTimeout(50000000)
      .build()).map(c => new GCSStorage[F](c, td))

  def apply[F[_]: ConcurrentEffect: Timer](client: Client[F], td: TokenDispenser[F]): GCSStorage[F] =
    new GCSStorage(client, td)
}
