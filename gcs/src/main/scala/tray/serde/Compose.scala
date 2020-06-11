package tray.serde

case class Compose(
                    sourceObjects: List[Compose.ComposeItem],
                    destination: Compose.ComposeDestination
                  )

object Compose {
  case class ComposeItem(
                           name: String
                           )

  case class ComposeDestination(
                                 contentType: String
                               )
}