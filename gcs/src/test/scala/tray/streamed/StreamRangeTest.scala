package tray.streamed

import java.nio.charset.StandardCharsets

import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Assertion, Succeeded}
import tray.api.Objects
import tray.core.GCSItem
import cats.effect._
import tray.api.RequestUtil._
import org.scalacheck._
import org.scalatestplus.scalacheck.Checkers

import scala.concurrent.{ExecutionContext, Future}

class StreamRangeTest extends AsyncFunSuite with Checkers {
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private def succ(io: IO[_]): Future[Assertion] = io.as(Succeeded).unsafeToFuture()

  val maxRangeSize = 1000
  val genRangeSet = for {
    start <- Gen.chooseNum[Long](0, maxRangeSize - 1)
    end <- Gen.chooseNum[Long](start + 1, maxRangeSize)
    stepsize <- Gen.chooseNum[Long](1, maxRangeSize)
  } yield { (start, end, stepsize) }

  test("should make an offset of expected ranges") {
    val p = Prop.forAll(genRangeSet) {
      case (start, end, stepsize) =>
        val stream: List[(Long, Long)] = beginOffsetRange(start, end, stepsize).compile.toList
        println(stream)
        stream.zipWithIndex.foldLeft(true){ case (accum, ((s, e), i)) =>
          val offset = (stepsize + 1) * i + start
          accum && s == offset && e == math.min(offset + stepsize, end - 1)
        }
    }
    check(p)
  }
}
