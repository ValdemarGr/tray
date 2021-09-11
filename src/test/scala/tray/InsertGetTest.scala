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

  val elementF = memoedStoragePathF
  val element2F = memoedStoragePathF

  def getTest(spF: IO[StoragePath], dataF: IO[String]) =
    test(s"should get the inserted object") {
      for {
        bucket <- memoBucket
        sp <- spF
        data <- dataF
        string <- bs
          .getBlob(sp)
          .through(fs2.text.utf8Decode)
          .compile
          .lastOrError
      } yield assertEquals(string, data)
    }

  val data = infiniteDataStream.take(16).compile.to(Array)
  test(s"should insert an object") {
    for {
      bucket <- memoBucket
      element <- elementF
      _ <- bs.putBlob(element, data)
    } yield ()
  }

  getTest(elementF, IO.pure(new String(data, StandardCharsets.UTF_8)))

  val dataStream = infiniteDataStream.take(16)
  test(s"should upload a small object resumable") {
    val runEff: IO[Option[(Throwable, Location, Long)]] =
      for {
        bucket <- memoBucket
        element2 <- element2F
        l <- dataStream
          .through(bs.putResumable(element2, chunkFactor = 1))
          .compile
          .last
      } yield l

    runEff.flatMap {
      case None            => IO.unit
      case Some((t, _, _)) => IO.raiseError(t)
    }
  }

  getTest(element2F, IO.pure(dataStream.through(fs2.text.utf8Decode).compile.string))

  val spF = memoedStoragePathF
  val numBytes = 1024L * 300L
  val bigData = infiniteDataStream.take(numBytes)

  test("should resumably upload an object that requires two iterations") {
    for {
      bucket <- memoBucket
      sp <- spF
      lo <- bigData.through(bs.putResumable(sp)).compile.last
    } yield assert(clue(lo).isEmpty)
  }

  test("there should be an equivalent number of bytes when getting the resource") {
    for {
      bucket <- memoBucket
      sp <- spF
      gotten <- bs.getBlob(sp).compile.foldChunks(0L) { case (accum, next) => accum + next.size }
    } yield assertEquals(gotten, numBytes)
  }
}
