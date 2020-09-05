package tray

import java.nio.charset.StandardCharsets

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Assertion, GivenWhenThen, Succeeded}
import tray.api.Objects
import tray.serde.Compose.{ComposeDestination, ComposeItem}
import tray.serde._

import scala.concurrent.{ExecutionContext, Future}

class ParallelPutAndGet extends AsyncFunSuite with GivenWhenThen {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private def succ(stream: fs2.Stream[IO, _]): Future[Assertion] = stream.compile.drain.as(Succeeded).unsafeToFuture()
  private def succ(io: IO[_]): Future[Assertion] = io.as(Succeeded).unsafeToFuture()

  import SharedStorage._

  val testData = "I am data!"
  val n = 6
  val chunkSizes = storage.baseChunkSize
  val prefix = "put-get-par"
  val outName = prefix + "-out"
  val tempNames = fs2.Stream(0 until n).map(i => prefix + s"-${i}").map(name => GCSItem(bucket, name))
  val allNames = fs2.Stream(GCSItem(bucket, outName)) ++ tempNames
  val data = fs2.Stream(testData).repeat.take(chunkSizes * n)

  test("delete objects if already exists") {
    val eff = allNames.evalFilter(item => Objects.exists[IO](item)).evalMap(item => Objects.delete[IO](item))
    succ(eff)
  }

  test("puts the data chunks in parallel") {
    val eff = Objects.putParallel(outName, data, n, 1, prefix)
    succ(eff)
  }

  test("gets the data chunks in parallel") {
    val o = Objects.getObject()
  }

/*
  test("should clean up") {
    val eff = items.evalMap(item => Objects.delete[IO](item))
    succ(eff)
  }
*/
}
