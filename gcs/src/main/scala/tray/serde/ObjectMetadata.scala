package tray.serde

case class ObjectMetadata(
                    kind: String,
                    id: String,
                    selfLink: String,
                    name: String,
                    bucket: String,
                    generation: Long,
                    metageneration: Long,
                    contentType: String,
                    timeCreated: String,
                    updated: String,
                    timeDeleted: String,
                    temporaryHold: Boolean,
                    eventBasedHold: Boolean,
                    retentionExpirationTime: String,
                    storageClass: String,
                    timeStorageClassUpdated: String,
                    size: Long,
                    md5Hash: String,
                    mediaLink: String,
                    contentEncoding: String,
                    contentDisposition: String,
                    contentLanguage: String,
                    cacheControl: String,
                   metadata: Map[String, String],
                    acl: List[ObjectACL],
                    owner: ObjectMetadata.Owner,
                    crc32c: String,
                    componentCount: Int,
                    etag: String,
                    customerEncryption: ObjectMetadata.CustomerEncryption,
                    kmsKeyName: String
                         )

object ObjectMetadata {
  implicit lazy val dec: io.circe.Decoder[ObjectMetadata] = io.circe.generic.semiauto.deriveDecoder[ObjectMetadata]
  implicit lazy val enc: io.circe.Encoder.AsObject[ObjectMetadata] = io.circe.generic.semiauto.deriveEncoder[ObjectMetadata]

  case class Owner(
                  entity: String,
                  entityId: String
                  )

  object Owner {
    implicit lazy val dec: io.circe.Decoder[Owner] = io.circe.generic.semiauto.deriveDecoder[Owner]
    implicit lazy val enc: io.circe.Encoder.AsObject[Owner] = io.circe.generic.semiauto.deriveEncoder[Owner]
  }

  case class CustomerEncryption(
                                 encryptionAlgorithm: String,
                                 keySha256: String
                               )

  object CustomerEncryption {
    implicit lazy val dec: io.circe.Decoder[CustomerEncryption] = io.circe.generic.semiauto.deriveDecoder[CustomerEncryption]
    implicit lazy val enc: io.circe.Encoder.AsObject[CustomerEncryption] = io.circe.generic.semiauto.deriveEncoder[CustomerEncryption]
  }

}