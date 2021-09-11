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

class SimplePutGetTest extends CatsEffectSuite with TestUtil {
  import cats.implicits._

  val spF = memoedStoragePathF
  val data = infiniteDataStream.take(16).compile.to(Array)

  test(s"should insert an object") {
    for {
      bucket <- memoBucket
      sp <- spF
      _ <- bs.putBlob(sp, data)
    } yield ()
  }

  test(s"should get the inserted object") {
    for {
      bucket <- memoBucket
      sp <- spF
      string <- bs
        .getBlob(sp)
        .through(fs2.text.utf8Decode)
        .compile
        .lastOrError
    } yield assertEquals(string, new String(data, StandardCharsets.UTF_8))
  }
}
