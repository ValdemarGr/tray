package tray.serde

case class ObjectACL(
                      kind: String,
                      id: String,
                      selfLink: String,
                      bucket: String,
                      `object`: String,
                      generation: Long,
                      entity: String,
                      role: String,
                      email: String,
                      entityId: String,
                      domain: String,
                      etag: String,
                      projectTeam: ObjectACL.ProjectTeam
                    )

object ObjectACL {
  implicit lazy val dec: io.circe.Decoder[ObjectACL] = io.circe.generic.semiauto.deriveDecoder[ObjectACL]

  case class ProjectTeam(
                        projectNumber: String,
                        team: String
                        )

  object ProjectTeam {
    implicit lazy val dec: io.circe.Decoder[ProjectTeam] = io.circe.generic.semiauto.deriveDecoder[ProjectTeam]
  }

}
