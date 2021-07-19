load("@scala_things//:dependencies/dependencies.bzl", "java_dependency", "scala_dependency", "scala_fullver_dependency", "make_scala_versions", "apply_scala_version", "apply_scala_fullver_version")

scala_versions = make_scala_versions(
    "2",
    "13",
    "6",
)

project_deps = [
    #http4s
    scala_dependency("org.http4s", "http4s-core", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-client", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-dsl", "1.0.0-M20"),
    scala_dependency("org.http4s", "http4s-client", "1.0.0-M20"),

    #CE
    scala_dependency("org.typelevel", "cats-effect", "3.1.1"),

    #google
    java_dependency("com.google.auth", "google-auth-library-oauth2-http", "0.27.0"),
    java_dependency("com.google.auth", "google-auth-library-credentials", "0.27.0")
]

def add_scala_ver(s):
    return apply_scala_version(scala_versions, s)

def add_scala_fullver(s):
    return apply_scala_fullver_version(scala_versions, s)
