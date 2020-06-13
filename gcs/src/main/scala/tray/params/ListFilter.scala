package tray.params

case class ListFilter(
                       delimiter: Option[String] = None,
                       endOffset: Option[String] = None,
                       includeTrailingDelimiter: Option[Boolean] = None,
                       maxResults: Option[Int] = None,
                       prefix: Option[String] = None,
                       projection: Option[ACLFilter] = None,
                       startOffset: Option[String] = None,
                       versions: Option[Boolean] = None
                     ) {

  import io.circe.syntax._

  def toQP: Map[String, String] = Map(
    "delimiter" -> delimiter.asJson,
    "endOffset" -> endOffset.asJson,
    "includeTrailingDelimiter" -> includeTrailingDelimiter.asJson,
    "maxResults" -> maxResults.asJson,
    "prefix" -> prefix.asJson,
    "projection" -> projection.asJson,
    "startOffset" -> startOffset.asJson,
    "versions" -> versions.asJson
  )
    .filterNot { case (_, v) => v.isNull }
    .mapValues(_.noSpaces)
}