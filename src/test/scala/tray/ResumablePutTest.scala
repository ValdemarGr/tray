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

  test("chuck the length of the data for exactly one request") {
    for {
      bucket <- memoBucket
      sp <- exactlyOneSp
      gotten <- bs.getBlob(sp).compile.foldChunks(0L) { case (accum, next) => accum + next.size }
    } yield assertEquals(gotten, exactlyOneItBytes)
  }

  test("chuck the length of the data for two requests") {
    for {
      bucket <- memoBucket
      sp <- twoItsSp
      gotten <- bs.getBlob(sp).compile.foldChunks(0L) { case (accum, next) => accum + next.size }
    } yield assertEquals(gotten, twoItsBytes)
  }
}
