package tray

import java.nio.charset.StandardCharsets

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers._
import org.scalatest.{Assertion, Succeeded}
import tray.api.Objects

import scala.concurrent.{ExecutionContext, Future}

class SimpleIO extends AsyncFunSuite {
  implicit override def executionContext: ExecutionContext = ExecutionContext.Implicits.global
  implicit val timer: Timer[IO] = IO.timer(executionContext)
  implicit val cs: ContextShift[IO] = IO.contextShift(executionContext)

  private def succ(io: IO[_]): Future[Assertion] = io.as(Succeeded).unsafeToFuture()

  import SharedStorage._

  val name = "simple-io-object"
  val asItem = GCSItem(bucket, name)

  val testData = "Helooooo"

  test("delete object if already exists") {
    val eff = for {
      e <- Objects.exists(asItem)
      _ <- if (e) Objects.delete[IO](asItem) else IO.unit
    } yield {}
    succ(eff)
  }

  test("put object to GCS") {
    val data: fs2.Stream[IO, Byte] = fs2
      .Stream(testData.getBytes(StandardCharsets.UTF_8): _*)
      .lift[IO]
    succ(Objects.putObject[IO](asItem, data))
  }

  test("get object from GCS") {
    val eff = for {
      data <- Objects.getObject[IO](asItem)
    } yield {
      new String(data, StandardCharsets.UTF_8) should be (testData)
    }
    eff.unsafeToFuture()
  }

  test("should clean up") {
    val eff: IO[Unit] = Objects.delete[IO](asItem)
    succ(eff)
  }
}
