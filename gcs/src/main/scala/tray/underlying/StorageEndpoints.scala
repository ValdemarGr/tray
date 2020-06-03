package tray.underlying

import org.http4s.Method.SafeMethodWithBody
import org.http4s.{Method, Uri}
import tray.GCSItem

object StorageEndpoints {
  import org.http4s._
  import org.http4s.implicits._

  private val base: Uri = uri"https://storage.googleapis.com"
  private val storageVersion = "v1"
  private val baseUrl: Uri = base / "storage" / storageVersion

  // private def uploadUrl(bucketName: String): String = base + "/upload" + storageVersion + "/b" + bucketName + "/o"
  // private def batchUrl(path: String): String = base + "/batch" + storageVersion + "/" + path

  object Objects {
    val suffix = "o"
    val prefix = "b"
    private def objectBaseUrl(bucket: String): Uri = baseUrl / prefix / bucket / suffix

    def get(gcsItem: GCSItem): (Uri, SafeMethodWithBody) = objectBaseUrl(gcsItem.bucket) / gcsItem.path -> Method.GET
  }
}
