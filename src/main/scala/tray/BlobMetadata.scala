package tray

import org.http4s.EntityDecoder
import cats.effect._

final case class BlobMetadata(
  size: Long
)

object BlobMetadata {
  import io.circe._
  implicit lazy val dec: Decoder[BlobMetadata] = io.circe.generic.semiauto.deriveDecoder[BlobMetadata]
  implicit def edec[F[_]: Concurrent]: EntityDecoder[F, BlobMetadata] = org.http4s.circe.jsonOf[F, BlobMetadata]
}
