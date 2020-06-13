package tray.serde

case class Rewrite(
                  kind: String,
                  totalBytesRewritten: Long,
                  objectSize: Long,
                  done: Boolean,
                  rewriteToken: Option[String]
                  )

object Rewrite {
  implicit lazy val dec: io.circe.Decoder[Rewrite] = io.circe.generic.semiauto.deriveDecoder[Rewrite]
}
