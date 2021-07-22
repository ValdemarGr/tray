package tray

import munit.CatsEffectSuite
import tray.Objects
import org.http4s.client.jdkhttpclient.JdkHttpClient
import cats.effect.IO
import tray.auth.GCSAuth
import org.http4s.Uri
import java.nio.charset.StandardCharsets

class InsertGetTest extends CatsEffectSuite {
  import cats.implicits._

  val element = "InsertGetTest_element"
  val clientFixture = ResourceSuiteLocalFixture(
    "storage_client",
    JdkHttpClient.simple[IO].map { client =>
      GCSAuth[IO].map(auth => Objects[IO](auth, client))
    }
  )
  override def munitFixtures = List(clientFixture)
  val sp = StoragePath(Uri.Path.unsafeFromString(element), "os-valdemar")

  test(s"should insert an object by name $element") {
    clientFixture().map(_.insert(sp, element.getBytes(StandardCharsets.UTF_8)))
  }

  test(s"should get the inserted object by name $element") {
    val stringF: IO[String] = clientFixture().flatMap(_.get(sp).use(_.compile.to(Array))).map(bytes => new String(bytes, StandardCharsets.UTF_8))
    assertIO(stringF, element)
  }
}
