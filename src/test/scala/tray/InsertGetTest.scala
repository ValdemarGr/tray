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

class InsertGetTest extends CatsEffectSuite {
  import cats.implicits._

  val elementF = TestUtil.randomStoragePath.memoize.unsafeRunSync()
  val element2F = TestUtil.randomStoragePath.memoize.unsafeRunSync()
  val clientFixture = ResourceSuiteLocalFixture(
    "storage_client",
    JdkHttpClient.simple[IO].evalMap { client =>
      GCSAuth[IO].map(auth => BlobStore[IO](auth, client))
    }
  )

  override def munitFixtures = List(clientFixture)

  def getTest(nameF: IO[String]) =
    test(s"should get the inserted object") {
      val bs = clientFixture()

      for {
        bucket <- TestUtil.testBucket
        name <- nameF
        string <- bs
          .getBlob(StoragePath(name, bucket))
          .use(_.compile.to(Array))
          .map(bytes => new String(bytes, StandardCharsets.UTF_8))
      } yield assertEquals(string, name)
    }

  test(s"should insert an object") {
    for {
      bucket <- TestUtil.testBucket
      element <- elementF
      _ <- clientFixture().putBlob(element, element.path.getBytes(StandardCharsets.UTF_8))
    } yield ()
  }

  getTest(elementF.map(_.path))

  val elemChunkedF: IO[StoragePath] = TestUtil.randomStoragePath.memoize.unsafeRunSync()
  test(s"should insert an object chunked encoding") {
    for {
      bucket <- TestUtil.testBucket
      elementChunked <- elemChunkedF
      data = fs2.Stream.chunk(fs2.Chunk.array(elementChunked.path.getBytes(StandardCharsets.UTF_8))).lift[IO]
      _ <- data.through(clientFixture().putPipeChunked(elementChunked)).compile.drain
    } yield ()
  }

  getTest(elemChunkedF.map(_.path))

  test(s"should upload resumable") {
    val bytes = fs2.Stream
      .eval(element2F)
      .flatMap(element2 => fs2.Stream(element2.path.getBytes().toList: _*).lift[IO])
    val runEff: IO[Option[(Throwable, Location, Long)]] =
      for {
        bucket <- TestUtil.testBucket
        element2 <- element2F
        l <- bytes
          .through(clientFixture().putResumable(element2, chunkFactor = 1))
          .compile
          .last
      } yield l

    runEff.flatMap {
      case None            => IO.unit
      case Some((t, _, _)) => IO.raiseError(t)
    }
  }

  getTest(element2F.map(_.path))
}
