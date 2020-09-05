package tray.serde

case class Compose(
  sourceObjects: List[Compose.ComposeItem],
  destination: Compose.ComposeDestination,
  kind: String = "storage#composeRequest"
)

object Compose {
  case class ComposeItem(
    name: String
  )

  case class ComposeDestination(
    contentType: String
  )
}
