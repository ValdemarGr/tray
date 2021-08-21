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
  val element2 = "InsertGetTest_element2"
  val clientFixture = ResourceSuiteLocalFixture(
    "storage_client",
    JdkHttpClient.simple[IO].evalMap { client =>
      GCSAuth[IO].map(auth => BlobStore[IO](auth, client))
    }
  )

  override def munitFixtures = List(clientFixture)

  test(s"should insert an object by name $element") {
    clientFixture().putBlob(StoragePath(element, "os-valdemar"), element.getBytes(StandardCharsets.UTF_8))
  }

  def getTest(name: String) =
    test(s"should get the inserted object by name $name") {
      val bs = clientFixture()

      val stringF: IO[String] = bs
        .getBlob(StoragePath(name, "os-valdemar"))
        .use(_.compile.to(Array))
        .map(bytes => new String(bytes, StandardCharsets.UTF_8))

      assertIO(stringF, name)
    }

  getTest(element)

  test(s"should upload resumable") {
    val bytes = fs2.Stream(element2.getBytes().toList: _*).lift[IO]
    bytes
      .through(clientFixture().putResumable(StoragePath(element2, "os-valdemar"), chunkSize = 4))
      .compile
      .drain
  }

  getTest(element2)
}
