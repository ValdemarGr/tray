package tray

import java.nio.charset.StandardCharsets

import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Assertion, GivenWhenThen, Succeeded}
import tray.api.Objects
import tray.core.GCSItem
import tray.serde._
import tray.serde.Compose.{ComposeDestination, ComposeItem}
import cats.effect._

import scala.concurrent.{ExecutionContext, Future}

class ComposeTest extends AsyncFunSuite with GivenWhenThen {
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private def succ(stream: fs2.Stream[IO, _]): Future[Assertion] = stream.compile.drain.as(Succeeded).unsafeToFuture()
  private def succ(io: IO[_]): Future[Assertion] = io.as(Succeeded).unsafeToFuture()

  import SharedStorage._

  val name1 = "compose-object-1"
  val name2 = "compose-object-2"
  val name3 = "compose-destinationobject"
  val asItem1 = GCSItem(bucket, name1)
  val asItem2 = GCSItem(bucket, name2)
  val composeDestination = GCSItem(bucket, name3)
  val putItems = fs2.Stream(asItem1, asItem2)
  val items = putItems ++ fs2.Stream(composeDestination)

  val testData = "Helooooo"

  test("delete objects if already exists") {
    val eff = items.evalFilter(item => Objects.exists[IO](item)).evalMap(item => Objects.delete[IO](item))
    succ(eff)
  }

  test("put objects to GCS") {
    val data: fs2.Stream[IO, Byte] = fs2
      .Stream(testData.getBytes(StandardCharsets.UTF_8): _*)
      .lift[IO]
    succ(putItems.evalMap(item => Objects.putObject[IO](item, data)))
  }

  test("get compose the GCS objects") {
    val c = ComposeDestination("application/text")
    val items = putItems.compile.toList.map(_.path).map(ComposeItem)
    Given(s"the list ${items}")
    val _ = (items should have).length(2)
    succ(Objects.compose[IO](composeDestination, Compose(items, c)))
  }

  test("get composed object from GCS") {
    val eff = for {
      d <- Objects.getObject[IO](composeDestination)
    } yield {
      val e = putItems.map(_ => testData).compile.fold("") { case (x, y) => x + y }
      val s = new String(d, StandardCharsets.UTF_8)
      s should be(e)
    }
    succ(eff)
  }

  test("should clean up") {
    val eff = items.evalMap(item => Objects.delete[IO](item))
    succ(eff)
  }
}
