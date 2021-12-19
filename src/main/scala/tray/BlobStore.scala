package tray

import tray.auth.GCSAuth
import org.http4s.client.Client

final case class BlobStore[F[_]](
  auth: GCSAuth[F],
  client: Client[F]
)
