package tray.serde

case class ObjectMetadata(
                           kind: String,
                           id: String,
                           selfLink: String,
                           mediaLink: String,
                           name: String,
                           bucket: String,
                           generation: String,
                           metageneration: String,
                           contentType: String,
                           storageClass: String,
                           size: String,
                           md5Hash: String,
                           crc32c: String,
                           etag: String,
                           timeCreated: String,
                           updated: String,
                           timeStorageClassUpdated: String
                         )
