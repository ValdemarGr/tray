package tray

import java.nio.charset.StandardCharsets

import cats.effect.{ContextShift, IO, Timer}
import fs2.Chunk
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Assertion, GivenWhenThen, Succeeded}
import tray.api.Objects

import scala.concurrent.{ExecutionContext, Future}

class ParallelPutAndGet extends AsyncFunSuite with GivenWhenThen {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private def succ(stream: fs2.Stream[IO, _]): Future[Assertion] = stream.compile.drain.as(Succeeded).unsafeToFuture()
  private def succ(io: IO[_]): Future[Assertion] = io.as(Succeeded).unsafeToFuture()

  import SharedStorage._
  import fs2.text._

  val n = 4
  val chunkSizes = storage.baseChunkSize
  val prefix = "put-get-par"
  val outName = prefix + "-out"
  val tempNames = fs2.Stream(0 until n).map(i => prefix + s"-${i}").map(name => GCSItem(bucket, name))
  val allNames = fs2.Stream(GCSItem(bucket, outName)) ++ tempNames
  val pureD = "abcdefg hellooooo i am data :).." // 32
  val mulOneKb = (pureD * 2 * 16 /*=1024*/)
  val mulOneMb = (mulOneKb * 1024)
  val asBytes = mulOneMb.getBytes(StandardCharsets.UTF_8)
  val data = fs2.Stream(asBytes: _*)

  test("delete objects if already exists") {
    val eff = allNames.evalFilter(item => Objects.exists[IO](item)).evalMap(item => Objects.delete[IO](item))
    succ(eff)
  }

  test("puts the data chunks in parallel") {
    val eff = Objects.putParallel[IO](GCSItem(bucket, outName), data, n, 1, prefix)
    succ(eff)
  }

  test("gets the data chunks in parallel") {
    val outData: fs2.Stream[IO, Chunk[Byte]] = Objects.getObjectParallel[IO](GCSItem(bucket, outName), 1, n, _ => IO.raiseError(new Exception("failed to upload")))
    val sIO = outData.through(utf8DecodeC).compile.fold(""){ case (a, b) => a ++ b }

    val eff = sIO.map(s => s should be(mulOneMb))
    succ(eff)
  }

  test("should clean up") {
    val eff = allNames.evalMap(item => Objects.delete[IO](item))
    succ(eff)
  }
}
