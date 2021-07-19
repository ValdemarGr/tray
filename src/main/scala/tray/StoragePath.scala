package tray

import org.http4s.Uri

final case class StoragePath(
  path: Uri.Path,
  bucket: String
)
