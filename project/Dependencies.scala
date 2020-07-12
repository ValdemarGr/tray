import sbt._

object Dependencies {
  lazy val catsCore = "org.typelevel" %% "cats-core" % "2.1.1"
  lazy val catsEffect = "org.typelevel" %% "cats-effect" % "2.1.1"
  lazy val catsFree = "org.typelevel" %% "cats-free" % "2.1.1"

  lazy val fs2Core = "co.fs2" %% "fs2-core" % "2.3.0" // For cats 2 and cats-effect 2
  lazy val fs2IO = "co.fs2" %% "fs2-io" % "2.3.0"

  lazy val http4sCore = "org.http4s" %% "http4s-core" % "0.21.4"
  lazy val http4sDsl = "org.http4s" %% "http4s-dsl" % "0.21.4"
  lazy val http4sServer = "org.http4s" %% "http4s-blaze-server" % "0.21.4"
  lazy val http4sClient = "org.http4s" %% "http4s-async-http-client" % "0.21.4"

  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.2" % Test
  lazy val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.14.3" % Test
  lazy val scalaTestScalaCheckIntegration = "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test

  lazy val googleCreds = "com.google.auth" % "google-auth-library-oauth2-http" % "0.20.0"

  lazy val gcCore = "com.google.cloud" % "google-cloud-core" % "1.93.1"
  lazy val gcsStorage = "com.google.cloud" % "google-cloud-storage" % "1.102.0"

  lazy val circeCore = "io.circe" %% "circe-core" % "0.13.0"
  lazy val circeGeneric = "io.circe" %% "circe-generic" % "0.13.0"
  lazy val circeParser = "io.circe" %% "circe-parser" % "0.13.0"
}
