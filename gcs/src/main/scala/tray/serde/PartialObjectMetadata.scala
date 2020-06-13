package tray.serde

case class PartialObjectMetadata(
                    kind: Option[String],
                    id: Option[String],
                    selfLink: Option[String],
                    name: Option[String],
                    bucket: Option[String],
                    generation: Option[Long],
                    metageneration: Option[Long],
                    contentType: Option[String],
                    timeCreated: Option[String],
                    updated: Option[String],
                    timeDeleted: Option[String],
                    temporaryHold: Option[Boolean],
                    eventBasedHold: Option[Boolean],
                    retentionExpirationTime: Option[String],
                    storageClass: Option[String],
                    timeStorageClassUpdated: Option[String],
                    size: Option[Long],
                    md5Hash: Option[String],
                    mediaLink: Option[String],
                    contentEncoding: Option[String],
                    contentDisposition: Option[String],
                    contentLanguage: Option[String],
                    cacheControl: Option[String],
                    metadata: Option[Map[String, String]],
                    acl: Option[List[ObjectACL]],
                    owner: Option[ObjectMetadata.Owner],
                    crc32c: Option[String],
                    componentCount: Option[Int],
                    etag: Option[String],
                    customerEncryption: Option[ObjectMetadata.CustomerEncryption],
                    kmsKeyName: Option[String],
                         )

object PartialObjectMetadata {
  implicit val enc: io.circe.Encoder.AsObject[PartialObjectMetadata] = io.circe.generic.semiauto.deriveEncoder[PartialObjectMetadata]
}