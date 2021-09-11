package tray

import cats.effect.IO
import java.util.UUID
import munit.CatsEffectSuite
import org.http4s.client.jdkhttpclient.JdkHttpClient
import tray.auth.GCSAuth
import fs2._
import cats.Eval
import cats.implicits._
import cats.effect.ResourceApp

trait TestUtil extends CatsEffectSuite {
  val TEST_BUCKET_ENV = "TEST_BUCKET"

  val testBucket: IO[String] = IO {
    Option(System.getenv(TEST_BUCKET_ENV))
  }.flatMap(IO.fromOption(_)(new Exception(s"failed to find environment var for $TEST_BUCKET_ENV")))

  lazy val memoBucket = testBucket.memoize.unsafeRunSync()

  val randomStoragePath: IO[StoragePath] =
    for {
      bucket <- testBucket
      path <- IO.blocking(UUID.randomUUID().toString())
    } yield StoragePath(path = path, bucket = bucket)

  def memoedStoragePathF: IO[StoragePath] = randomStoragePath.memoize.unsafeRunSync()

  val blobStore = ResourceSuiteLocalFixture(
    "storage-client",
    JdkHttpClient.simple[IO].evalMap { client =>
      GCSAuth[IO].map(auth => BlobStore[IO](auth, client))
    }
  )

  override def munitFixtures: List[Fixture[_]] = blobStore :: super.munitFixtures.toList

  lazy val bs = blobStore()

  val baseString = "i am a base string!!"
  def makeBytes(remaining: Int): Eval[Chunk[Byte]] = Eval.defer {
    if (remaining <= baseString.length()) Eval.now(Chunk.array(baseString.take(remaining).getBytes()))
    else {
      val left = remaining / 2
      val right = remaining - left
      for {
        l <- makeBytes(left)
        r <- makeBytes(right)
      } yield l ++ r
    }
  }

  val infiniteDataStream = Stream.chunk(makeBytes(1024 * 128).value).repeat
}
