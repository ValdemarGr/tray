package tray.params

case class WatchAll(
                     kind: String,
                     id: String,
                     resourceId: String,
                     resourceUri: String,
                     token: String,
                     expiration: Long,
                     `type`: String,
                     address: String,
                     params: Map[String, String],
                     payload: Boolean,
                   )

object WatchAll {
  implicit lazy val enc: io.circe.Encoder.AsObject[WatchAll] = io.circe.generic.semiauto.deriveEncoder[WatchAll]
}