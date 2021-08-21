package tray

import org.http4s.Uri

final case class StoragePath(
  path: String,
  bucket: String
)
