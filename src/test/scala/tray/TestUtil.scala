package tray

import cats.effect.IO
import java.util.UUID

object TestUtil {
  val TEST_BUCKET_ENV = "TEST_BUCKET"
  val testBucket: IO[String] = IO {
    Option(System.getenv(TEST_BUCKET_ENV))
  }.flatMap(IO.fromOption(_)(new Exception(s"failed to find environment var for $TEST_BUCKET_ENV")))

  val randomStoragePath: IO[StoragePath] =
    for {
      bucket <- testBucket
      path <- IO.blocking(UUID.randomUUID().toString())
    } yield StoragePath(path = path, bucket = bucket)
}
