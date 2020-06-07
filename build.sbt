import Dependencies._
import Settings._

lazy val gcs = (project in file("gcs"))
  .settings(commonSettings: _*)
  .settings(
    fork in run := true,
    scalaVersion in ThisBuild := "2.12.10",
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
      googleCreds,
      gcCore,
      gcsStorage
    )
  )


lazy val root = (project in file("root"))
  .settings(commonSettings: _*)
  .settings(
    fork in run := true,
    scalaVersion in ThisBuild := "2.12.10",
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
      googleCreds,
      gcCore,
      gcsStorage
    )
  )
  .dependsOn(gcs)


lazy val microsite = (project in file("site"))
  .enablePlugins(MicrositesPlugin)
  .settings(
    scalaVersion in ThisBuild := "2.12.10",
    micrositeName := "Tray",
    micrositeDescription := "A fully asynchronous cats-effect and fs2 cloud object storage layer.",
    micrositeBaseUrl := "",
    micrositeCompilingDocsTool := WithMdoc,
    mdocIn := sourceDirectory.value / "main" / "mdoc",
    micrositeDocumentationUrl := "docs/intro",
    micrositeHighlightTheme := "atom-one-light"
  )
