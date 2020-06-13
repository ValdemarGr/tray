package tray.params

trait ACLFilter

object ACLFilter {
  case object FullACL extends ACLFilter
  case object NoACL extends ACLFilter

  import io.circe.syntax._

  implicit lazy val enc: io.circe.Encoder[ACLFilter] = {
    case FullACL => "full".asJson
    case NoACL => "noAcl".asJson
  }
}