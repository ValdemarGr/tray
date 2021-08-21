package tray

import munit.CatsEffectSuite
import tray.BlobStore
import org.http4s.client.jdkhttpclient.JdkHttpClient
import cats.effect.IO
import tray.auth.GCSAuth
import org.http4s.Uri
import java.nio.charset.StandardCharsets
import org.http4s.MediaType

class InsertGetTest extends CatsEffectSuite {
  import cats.implicits._

  val element = "InsertGetTest_element"
  val clientFixture = ResourceSuiteLocalFixture(
    "storage_client",
    JdkHttpClient.simple[IO].map { client =>
      GCSAuth[IO].map(auth => BlobStore[IO](auth, client))
    }
  )

  override def munitFixtures = List(clientFixture)
  val sp = StoragePath(element, "os-valdemar")

  test(s"should insert an object by name $element") {
    clientFixture().map(_.putBlob(sp, element.getBytes(StandardCharsets.UTF_8)))
  }

  test(s"should get the inserted object by name $element") {
    val stringF: IO[String] = clientFixture()
      .flatMap(_.getBlob(sp).use(_.compile.to(Array)))
      .map(bytes => new String(bytes, StandardCharsets.UTF_8))
    assertIO(stringF, element)
  }

  test(s"should upload resumable") {
    clientFixture().flatMap{ bs =>
      val bytes = fs2.Stream(element.getBytes().toList : _*).lift[IO]
      bytes.through(bs.putResumable(StoragePath("der er hest i den", "os-valdemar"))).compile.drain
    }
  }
}
