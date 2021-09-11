package tray

import munit.CatsEffectSuite
import tray.BlobStore
import org.http4s.client.jdkhttpclient.JdkHttpClient
import cats.effect.IO
import tray.auth.GCSAuth
import org.http4s.Uri
import java.nio.charset.StandardCharsets
import org.http4s.MediaType
import org.http4s.headers.Location
import tray.objects._
import fs2._
import cats.Eval
import org.http4s.client.Client
import org.http4s.Response
import org.http4s.Status
import cats.effect.kernel.Resource

class InsertGetTest extends CatsEffectSuite with TestUtil {
  import cats.implicits._

  val smallSp = memoedStoragePathF
  val dataStream = infiniteDataStream.take(16)

  test(s"should upload a small object resumable") {
    val runEff: IO[Option[(Throwable, Location, Long)]] =
      for {
        bucket <- memoBucket
        sp <- smallSp
        l <- dataStream
          .through(bs.putResumable(sp, chunkFactor = 1))
          .compile
          .last
      } yield l

    runEff.flatMap {
      case None            => IO.unit
      case Some((t, _, _)) => IO.raiseError(t)
    }
  }

  test(s"should get the inserted object") {
    for {
      bucket <- memoBucket
      sp <- smallSp
      string <- bs
        .getBlob(sp)
        .through(fs2.text.utf8Decode)
        .compile
        .lastOrError
    } yield assertEquals(string, dataStream.through(fs2.text.utf8Decode).compile.string)
  }

  val exactlyOneItBytes = 1024L * 256L
  val exactlyOneItData = infiniteDataStream.take(exactlyOneItBytes)
  val exactlyOneSp = memoedStoragePathF

  val twoItsBytes = exactlyOneItBytes + 1L
  val twoItsData = infiniteDataStream.take(twoItsBytes)
  val twoItsSp = memoedStoragePathF

  val fourItsBytes = exactlyOneItBytes * 4
  val fourItsData = infiniteDataStream.take(fourItsBytes)
  val fourItsSp = memoedStoragePathF

  test("should resumably upload an object that requires exactly one request") {
    for {
      bucket <- memoBucket
      sp <- exactlyOneSp
      lo <- exactlyOneItData.through(bs.putResumable(sp, chunkFactor = 1)).compile.last
    } yield assert(clue(lo).isEmpty)
  }

  test("should resumably upload an object that two requests") {
    for {
      bucket <- memoBucket
      sp <- twoItsSp
      lo <- twoItsData.through(bs.putResumable(sp, chunkFactor = 1)).compile.last
    } yield assert(clue(lo).isEmpty)
  }

  test("should resumably upload an object that four requests") {
    for {
      bucket <- memoBucket
      sp <- fourItsSp
      lo <- fourItsData.through(bs.putResumable(sp, chunkFactor = 1)).compile.last
    } yield assert(clue(lo).isEmpty)
  }

  test("check the length of the data for exactly one request") {
    for {
      bucket <- memoBucket
      sp <- exactlyOneSp
      gotten <- bs.getBlob(sp).compile.foldChunks(0L) { case (accum, next) => accum + next.size }
    } yield assertEquals(gotten, exactlyOneItBytes)
  }

  test("check the length of the data for two requests") {
    for {
      bucket <- memoBucket
      sp <- twoItsSp
      gotten <- bs.getBlob(sp).compile.foldChunks(0L) { case (accum, next) => accum + next.size }
    } yield assertEquals(gotten, twoItsBytes)
  }

  test("check the length of the data for four requests") {
    for {
      bucket <- memoBucket
      sp <- fourItsSp
      gotten <- bs.getBlob(sp).compile.foldChunks(0L) { case (accum, next) => accum + next.size }
    } yield assertEquals(gotten, fourItsBytes)
  }

  val failBytes = 1024L * 256L * 3
  val failData = infiniteDataStream.take(failBytes)
  val failSp = memoedStoragePathF
  test("should upload the first of three chunks, fail on the second and try again and succeed") {
    val failingClient = Client[IO] { req =>
      import org.http4s.headers._
      val crh = req.headers.get[`Content-Range`]
      crh match {
        case Some(cr) if cr.range.first > 0 =>
          Resource.eval(IO.println(s"failing at range $cr").as(Response[IO](status = Status.InternalServerError)))
        case _ => bs.client.run(req)
      }
    }
    val failTwoBs = BlobStore[IO](bs.auth, failingClient)
    for {
      bucket <- memoBucket
      sp <- failSp
      failures <- failData.through(failTwoBs.putResumable(sp)).compile.toList
      (_, loc, offset) <- IO(assert(clue(failures).size == 1), "there should be one failure").as(failures.head)
      _ <- IO.println(s"failed (on purpose) at $offset")
      los <- failData.drop(offset).through(bs.putResumableFrom(loc, offset)).compile.to(List)
    } yield assert(clue(los).isEmpty, "there should be no failures")
  }
}
