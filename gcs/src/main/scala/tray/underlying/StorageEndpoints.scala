package tray.underlying

import org.http4s.Method.SafeMethodWithBody
import tray.GCSItem

object StorageEndpoints {
  import org.http4s._
  import org.http4s.implicits._

  protected val base: Uri = uri"https://storage.googleapis.com"
  protected val storageVersion = "v1"
  val baseUrl: Uri = base / "storage" / storageVersion

  object Objects {
    val suffix = "o"
    val prefix = "b"
    private def objectBaseUrl(bucket: String): Uri = baseUrl / prefix / bucket / suffix

    def get(gcsItem: GCSItem): (Uri, SafeMethodWithBody) = (objectBaseUrl(gcsItem.bucket) / gcsItem.path) +? ("alt", "media")  -> Method.GET
  }
}
