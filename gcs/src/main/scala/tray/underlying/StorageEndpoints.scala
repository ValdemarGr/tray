package tray.underlying

import org.http4s.Uri.Path
import tray.GCSItem
import tray.params.ListFilter

object StorageEndpoints {
  import org.http4s._
  import org.http4s.implicits._

  protected val base: Uri = uri"https://storage.googleapis.com"
  protected val storageVersion = "v1"

  object ObjectsEndpoints {
    private val suffix = "o"
    private val prefix = "b"

    private def objectBucketPath(item: GCSItem): Path = (Uri() / prefix / item.bucket / suffix / item.path).path
    private def objectBucketPath(bucket: String): Path = (Uri() / prefix / bucket / suffix).path

    private def objectBaseUrl(item: GCSItem): Uri = (base / "storage" / storageVersion) addPath objectBucketPath(item)
    private def objectBaseUrl(bucket: String): Uri = (base / "storage" / storageVersion) addPath objectBucketPath(bucket)

    private def objectUploadBaseUrl(bucket: String) = base / "upload" / "storage" / storageVersion / prefix / bucket / suffix

    def list(bucket: String, filter: ListFilter, page: Option[String], fieldFilters: String*): (Uri, Method) = {
      val qps: Map[String, String] = filter.toQP

      val withPage: Map[String, String] = page
        .map(pageToken => "pageToken" -> pageToken)
        .toMap ++ qps

      val uri = objectBaseUrl(bucket).withQueryParams(withPage)

      val withOptionalFilter = if (fieldFilters.nonEmpty) {
        uri +? ("fields", fieldFilters)
      } else {
        uri
      }

      withOptionalFilter  -> Method.GET
    }
    def watchAll(item: GCSItem): (Uri, Method) = objectBaseUrl(item.copy(path = "watch")) -> Method.PUT
    def update(item: GCSItem): (Uri, Method) = objectBaseUrl(item) -> Method.PUT
    def copy(from: GCSItem, to: GCSItem): (Uri, Method) = ((objectBaseUrl(from) / "copyTo") addPath objectBucketPath(to)) -> Method.POST
    def rewrite(from: GCSItem, to: GCSItem, nextToken: Option[String]): (Uri, Method) = {
      val base = objectBaseUrl(from) / "rewriteTo" addPath objectBucketPath(to)

      val withToken = nextToken
        .map(t => base +? ("rewriteToken", t))
        .getOrElse(base)

      withToken -> Method.POST
    }
    def delete(gcsItem: GCSItem): (Uri, Method) = objectBaseUrl(gcsItem) -> Method.DELETE
    def patch(gcsItem: GCSItem): (Uri, Method) = objectBaseUrl(gcsItem) -> Method.PATCH
    def get(gcsItem: GCSItem): (Uri, Method) = objectBaseUrl(gcsItem) +? ("alt", "media") -> Method.GET
    def getMetadata(gcsItem: GCSItem, filters: String*): (Uri, Method) = {
      val defaultUri = objectBaseUrl(gcsItem) +? ("alt", "json")
      val withOptionalFilter = if (filters.nonEmpty) {
        defaultUri +? ("fields", filters)
      } else {
        defaultUri
      }

      withOptionalFilter -> Method.GET
    }
    def initiateResumableUpload(gcsItem: GCSItem): (Uri, Method) = objectUploadBaseUrl(gcsItem.bucket) +? ("uploadType", "resumable") +? ("name", gcsItem.path) -> Method.POST
    def put(gcsItem: GCSItem): (Uri, Method) = objectUploadBaseUrl(gcsItem.bucket) +? ("uploadType", "media") +? ("name", gcsItem.path) -> Method.POST
    def compose(gcsItem: GCSItem): (Uri, Method) =objectBaseUrl(gcsItem) / "compose" -> Method.POST
    val resumableUploadChunk: Method = Method.PUT
  }
}
