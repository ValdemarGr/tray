package tray

import java.nio.charset.StandardCharsets

import org.scalatest.funsuite.{AnyFunSuite, AsyncFunSuite}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Assertion, GivenWhenThen, Succeeded}
import tray.api.Objects
import tray.core.GCSItem
import tray.serde._
import tray.serde.Compose.{ComposeDestination, ComposeItem}
import cats.effect._
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.implicits._

import scala.concurrent.{ExecutionContext, Future}

class Blabla extends AsyncFunSuite {
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private def succ(stream: fs2.Stream[IO, _]): Future[Assertion] = stream.compile.drain.as(Succeeded).unsafeToFuture()
  private def succ(io: IO[_]): Future[Assertion] = io.as(Succeeded).unsafeToFuture()

  import SharedStorage._
  import fs2.text._

  test("helloasd") {
    //val eff = Objects.exists[IO](GCSItem(bucket, "test")).map(x => x should be(false))

    val o = AsyncHttpClient.resource[IO]().use{ c =>
      c.get(uri"https://google.com")(r => r.body.through(utf8Decode).compile.toList.map(_.mkString))
    }
    succ(o.flatMap(x => IO(println(x))).as(4 should be(6)))
  }

}