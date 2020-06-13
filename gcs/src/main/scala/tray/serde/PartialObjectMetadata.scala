package tray.serde

case class PartialObjectMetadata(
                    kind: Option[String]= None,
                    id: Option[String]= None,
                    selfLink: Option[String]= None,
                    name: Option[String]= None,
                    bucket: Option[String]= None,
                    generation: Option[Long]= None,
                    metageneration: Option[Long]= None,
                    contentType: Option[String]= None,
                    timeCreated: Option[String]= None,
                    updated: Option[String]= None,
                    timeDeleted: Option[String]= None,
                    temporaryHold: Option[Boolean]= None,
                    eventBasedHold: Option[Boolean]= None,
                    retentionExpirationTime: Option[String]= None,
                    storageClass: Option[String]= None,
                    timeStorageClassUpdated: Option[String]= None,
                    size: Option[Long]= None,
                    md5Hash: Option[String]= None,
                    mediaLink: Option[String]= None,
                    contentEncoding: Option[String]= None,
                    contentDisposition: Option[String]= None,
                    contentLanguage: Option[String]= None,
                    cacheControl: Option[String]= None,
                    metadata: Option[Map[String, String]]= None,
                    acl: Option[List[ObjectACL]]= None,
                    owner: Option[ObjectMetadata.Owner]= None,
                    crc32c: Option[String]= None,
                    componentCount: Option[Int]= None,
                    etag: Option[String]= None,
                    customerEncryption: Option[ObjectMetadata.CustomerEncryption]= None,
                    kmsKeyName: Option[String]= None,
                         )

object PartialObjectMetadata {
  implicit lazy val enc: io.circe.Encoder.AsObject[PartialObjectMetadata] = io.circe.generic.semiauto.deriveEncoder[PartialObjectMetadata]
  implicit lazy val dec: io.circe.Decoder[PartialObjectMetadata] = io.circe.generic.semiauto.deriveDecoder[PartialObjectMetadata]
}