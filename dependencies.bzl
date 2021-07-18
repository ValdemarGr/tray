load("@scala_things//:dependencies/dependencies.bzl", "java_dependency", "scala_dependency", "scala_fullver_dependency", "make_scala_versions", "apply_scala_version", "apply_scala_fullver_version")

scala_versions = make_scala_versions(
    "2",
    "13",
    "6",
)

project_deps = [
]

def add_scala_ver(s):
    return apply_scala_version(scala_versions, s)

def add_scala_fullver(s):
    return apply_scala_fullver_version(scala_versions, s)
