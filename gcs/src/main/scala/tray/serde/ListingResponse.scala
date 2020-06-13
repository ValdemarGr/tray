package tray.serde

import io.circe.Decoder

case class ListingResponse[T: Decoder](
                          kind: String,
                          nextPageToken: Option[String],
                          prefixes: List[String],
                          items: List[T]
                          )

object ListingResponse {
  implicit def dec[T: Decoder]: io.circe.Decoder[ListingResponse[T]] = io.circe.generic.semiauto.deriveDecoder[ListingResponse[T]]
}