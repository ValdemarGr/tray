package tray.underlying

import tray.GCSItem

object StorageEndpoints {
  import org.http4s._
  import org.http4s.implicits._

  protected val base: Uri = uri"https://storage.googleapis.com"
  protected val storageVersion = "v1"

  object Objects {
    val suffix = "o"
    val prefix = "b"
    private def objectBaseUrl(bucket: String): Uri = base / "storage" / storageVersion / prefix / bucket / suffix
    private def objectUploadBaseUrl(bucket: String) = base / uri"upload".path / "storage" / storageVersion / prefix / bucket / suffix

    def get(gcsItem: GCSItem): (Uri, Method) = (objectBaseUrl(gcsItem.bucket) / gcsItem.path) +? ("alt", "media")  -> Method.GET
    def initiateResumableUpload(gcsItem: GCSItem): (Uri, Method) = objectUploadBaseUrl(gcsItem.bucket) +? ("uploadType", "resumable") +? ("name", gcsItem.path) -> Method.POST
    def put(gcsItem: GCSItem): (Uri, Method) = objectUploadBaseUrl(gcsItem.bucket) +? ("uploadType", "media") +? ("name", gcsItem.path) -> Method.POST
    def compose(gcsItem: GCSItem): (Uri, Method) = objectBaseUrl(gcsItem.bucket) / gcsItem.path / "compose" -> Method.POST
    val resumableUploadChunk: Method = Method.PUT
  }
}
