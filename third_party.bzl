load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven", "parse")

def dependency(coordinates, exclusions = None):
    artifact = parse.parse_maven_coordinate(coordinates)
    return maven.artifact(
        group = artifact["group"],
        artifact = artifact["artifact"],
        packaging = artifact.get("packaging"),
        classifier = artifact.get("classifier"),
        version = artifact["version"],
        exclusions = exclusions,
    )

scala_version = "2.12.10"

def getDeps(scalaMajor):
    return [
        dependency("org.typelevel:cats-core_2.{}:2.1.1".format(scalaMajor)),
        dependency("org.typelevel:cats-effect_2.{}:2.1.1".format(scalaMajor)),
        dependency("org.typelevel:cats-free_2.{}:2.1.1".format(scalaMajor)),
        #
        dependency("co.fs2:fs2-core_2.{}:2.3.0".format(scalaMajor)),
        dependency("co.fs2:fs2-io_2.{}:2.3.0".format(scalaMajor)),
        #
        dependency("org.http4s:http4s-core_2.{}:0.21.4".format(scalaMajor)),
        dependency("org.http4s:http4s-dsl_2.{}:0.21.4".format(scalaMajor)),
        dependency("org.http4s:http4s-blaze-server_2.{}:0.21.4".format(scalaMajor)),
        dependency("org.http4s:http4s-async-http-client_2.{}:0.21.4".format(scalaMajor)),
        #
        dependency("org.scalatest:scalatest_2.{}:3.1.2".format(scalaMajor)),
        dependency("org.scalacheck:scalacheck_2.{}:1.14.3".format(scalaMajor)),
        dependency("org.scalatestplus:scalatestplus-scalacheck_2.{}:3.1.0.0-RC2".format(scalaMajor)),
        #
        dependency("com.google.auth:google-auth-library-oauth2-http:0.20.0"),
        dependency("com.google.cloud:google-cloud-core:1.93.1"),
        dependency("com.google.cloud:google-cloud-storage:1.102.0"),
        #
        dependency("io.circe:circe-core_2.{}:0.13.0".format(scalaMajor)),
        dependency("io.circe:circe-generic_2.{}:0.13.0".format(scalaMajor)),
        dependency("io.circe:circe-parser_2.{}:0.13.0".format(scalaMajor)),
        #
        dependency("org.typelevel:kind-projector_2.{}:0.10.3".format(scalaMajor)),
    ]

def dependencies(scalaMajor):
    maven_install(
        artifacts = getDeps(scalaMajor),
        repositories = [
            "https://repo.maven.apache.org/maven2/",
            "https://mvnrepository.com/artifact",
            "https://maven-central.storage.googleapis.com",
        ],
        fetch_sources = True,
        generate_compat_repositories = True,
        #        maven_install_json = "//:maven_install.json",
    )
