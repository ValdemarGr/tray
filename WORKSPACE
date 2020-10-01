workspace(name = "tray")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

skylib_version = "0.8.0"

http_archive(
    name = "bazel_skylib",
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib.{}.tar.gz".format(skylib_version, skylib_version),
)

rules_scala_version = "a2f5852902f5b9f0302c727eead52ca2c7b6c3e2"  # update this as needed

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "8c48283aeb70e7165af48191b0e39b7434b0368718709d1bced5c3781787d8e7",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")

scala_register_toolchains()

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")

scala_repositories()

protobuf_version = "3.11.3"

protobuf_version_sha256 = "cf754718b0aa945b00550ed7962ddc167167bd922b842199eeb6505e6f344852"

http_archive(
    name = "com_google_protobuf",
    sha256 = protobuf_version_sha256,
    strip_prefix = "protobuf-%s" % protobuf_version,
    url = "https://github.com/protocolbuffers/protobuf/archive/v%s.tar.gz" % protobuf_version,
)

# Dependencies needed for google_protobuf.
# You may need to modify this if your project uses google_protobuf for other purposes.
load("@com_google_protobuf//:protobuf_deps.bzl", "protobuf_deps")

protobuf_deps()

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_binary", "scala_library", "scala_test")
