workspace(name = "cats-tray")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

skylib_version = "1.0.3"

http_archive(
    name = "bazel_skylib",
    sha256 = "1c531376ac7e5a180e0237938a2536de0c54d93f5c278634818e0efc952dd56c",
    type = "tar.gz",
    url = "https://mirror.bazel.build/github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib-{}.tar.gz".format(skylib_version, skylib_version),
)

http_archive(
    name = "rules_proto",
    sha256 = "8e7d59a5b12b233be5652e3d29f42fba01c7cbab09f6b3a8d0a57ed6d1e9a0da",
    strip_prefix = "rules_proto-7e4afce6fe62dbff0a4a03450143146f9f2d7488",
    urls = [
        "https://mirror.bazel.build/github.com/bazelbuild/rules_proto/archive/7e4afce6fe62dbff0a4a03450143146f9f2d7488.tar.gz",
        "https://github.com/bazelbuild/rules_proto/archive/7e4afce6fe62dbff0a4a03450143146f9f2d7488.tar.gz",
    ],
)

load("@rules_proto//proto:repositories.bzl", "rules_proto_dependencies", "rules_proto_toolchains")

rules_proto_dependencies()

rules_proto_toolchains()

# dependencies
commitSha = "e48fe88ef733f71ab780b196bb65f6c803662fdf"
http_archive(
    name = "scala_things",
    # sha256 = "5ecc4779aa907f050f01ec42daad87164e5b1ed3858b7bf18cce2e1afc0afd87",
    strip_prefix = "bazel-things-%s" % commitSha,
    url = "https://github.com/valdemargr/bazel-things/archive/%s.zip" % commitSha,
)

load("@scala_things//:dependencies/init.bzl", "bazel_things_dependencies")

bazel_things_dependencies()

load("//:dependencies.bzl", "project_deps", "scala_versions")
load("@scala_things//:dependencies/dependencies.bzl", "install_dependencies", "to_string_version")

install_dependencies(project_deps, scala_versions)

# scala
rules_scala_version = "b85d1225d0ddc9c376963eb0be86d9d546f25a4a"  # update this as needed
http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "f6fa4897545e8a93781ad8936d5a59e90e2102918e8997a9dab3dc5c5ce2e09e",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

load("@io_bazel_rules_scala//:scala_config.bzl", "scala_config")
load("//:dependencies.bzl", "scala_versions")

scala_config(to_string_version(scala_versions))

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

register_toolchains("@scala_things//toolchain/development:development-scala_toolchain")

scala_repositories()

load("@io_bazel_rules_scala//testing:junit.bzl", "junit_repositories", "junit_toolchain")

junit_repositories()

junit_toolchain()
