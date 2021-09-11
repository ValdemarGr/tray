package tray

import munit.CatsEffectSuite
import cats.effect._
import tray.objects._

class ResumableGetTest extends CatsEffectSuite with TestUtil {

  val spF = memoedStoragePathF
  val dataSize = 1024L * 256L * 4L
  val dataStream = infiniteDataStream.take(dataSize)

  test(s"should put 1 mb of data into the storage path") {
    for {
      bucket <- memoBucket
      sp <- spF
      l <- dataStream
        .through(bs.putPipe(sp, dataSize))
        .compile
        .last
    } yield l
  }

  test(s"should get the data streamed (resumable) and verify the checksum") {
    for {
      bucket <- memoBucket
      sp <- spF
      _ <- compareBytestreams(bs.getResumable(sp), dataStream)
    } yield ()
  }
}
