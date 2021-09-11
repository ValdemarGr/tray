load("@scala_things//:dependencies/dependencies.bzl", "apply_scala_fullver_version", "java_dependency", "make_scala_versions", "scala_dependency", "scala_fullver_dependency")

scala_versions = make_scala_versions(
    "2",
    "13",
    "6",
)

project_deps = [
    #plugins
    scala_dependency("com.olegpy", "better-monadic-for", "0.3.1"),

    #testing
    scala_dependency("org.scalameta", "munit", "0.7.27"),
    scala_dependency("org.typelevel", "munit-cats-effect-3", "1.0.5"),

    #http4s
    scala_dependency("org.http4s", "http4s-core", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-client", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-dsl", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-client", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-jdk-http-client", "0.5.0-M4"),
    scala_dependency("org.http4s", "http4s-circe", "1.0.0-M20"),

    #circe
    scala_dependency("io.circe", "circe-core", "0.14.1"),
    scala_dependency("io.circe", "circe-parser", "0.14.1"),
    scala_dependency("io.circe", "circe-generic", "0.14.1"),
    scala_dependency("io.circe", "circe-generic-extras", "0.14.1"),

    #CE
    scala_dependency("org.typelevel", "cats-effect", "3.2.5"),

    #google
    java_dependency("com.google.auth", "google-auth-library-oauth2-http", "0.27.0"),
    java_dependency("com.google.auth", "google-auth-library-credentials", "0.27.0"),
]

def add_scala_fullver(s):
    return apply_scala_fullver_version(scala_versions, s)
