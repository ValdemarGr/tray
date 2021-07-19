package tray.endpoints

import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

object Endpoints {
  val baseUri = uri"https://storage.googleapis.com"

  val basic = baseUri / "storage" / "v1"

  val upload = baseUri / "upload" / "storage" / "v1"
}
