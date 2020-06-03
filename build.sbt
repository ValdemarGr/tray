import Dependencies._
import Settings._

lazy val gcs = (project in file("gcs"))
  .settings(commonSettings: _*)
  .settings(
    fork in run := true,
    scalaVersion in ThisBuild := "2.13.0",
    version      in ThisBuild := "0.0.1",
    name := "via",
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      fs2Core,
      fs2IO,
      scalaTest,
      scalaCheck,
      scalaTestScalaCheckIntegration,
      http4sCore,
      http4sDsl,
      http4sClient,
      googleCreds
    )
  )


lazy val root = (project in file("root"))
  .settings(commonSettings: _*)
  .settings(
    fork in run := true,
    scalaVersion in ThisBuild := "2.13.0",
    version      in ThisBuild := "0.0.1",
    name := "via",
    libraryDependencies ++= Seq(
      catsCore,
      catsEffect,
      fs2Core,
      fs2IO,
      http4sCore,
      http4sDsl,
      http4sClient,
      scalaTest,
      googleCreds
    )
  )
  .dependsOn(gcs)
