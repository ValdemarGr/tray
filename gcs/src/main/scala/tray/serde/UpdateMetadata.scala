package tray.serde

case class UpdateMetadata(
                           acl: Option[List[ObjectACL]],
                           cacheControl: Option[String],
                           contentDisposition: Option[String],
                           contentEncoding: Option[String],
                           contentLanguage: Option[String],
                           contentType: Option[String],
                           eventBasedHold: Option[Boolean],
                           metadata: Option[Map[String, String]],
                           temporaryHold: Option[Boolean],
                         )

object UpdateMetadata {
  implicit lazy val enc: io.circe.Encoder.AsObject[UpdateMetadata] = io.circe.generic.semiauto.deriveEncoder[UpdateMetadata]
}