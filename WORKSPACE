workspace(name = "tray")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

RULES_JVM_EXTERNAL_TAG = "3.3"

RULES_JVM_EXTERNAL_SHA = "d85951a92c0908c80bd8551002d66cb23c3434409c814179c0ff026b53544dab"

http_archive(
    name = "rules_jvm_external",
    sha256 = RULES_JVM_EXTERNAL_SHA,
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

# bazel-skylib 0.8.0 released 2019.03.20 (https://github.com/bazelbuild/bazel-skylib/releases/tag/0.8.0)
skylib_version = "0.8.0"

http_archive(
    name = "bazel_skylib",
    sha256 = "2ef429f5d7ce7111263289644d233707dba35e39696377ebab8b0bc701f7818e",
    type = "tar.gz",
    url = "https://github.com/bazelbuild/bazel-skylib/releases/download/{}/bazel-skylib.{}.tar.gz".format(skylib_version, skylib_version),
)

rules_scala_version = "725b00465b83b776b5ad22eada026c3546df635c"  # update this as needed

http_archive(
    name = "io_bazel_rules_scala",
    sha256 = "0a1189d4701e35d962a07baf4016f899588133ce39dd1f2f7fe9a8d8e515156e",
    strip_prefix = "rules_scala-%s" % rules_scala_version,
    type = "zip",
    url = "https://github.com/bazelbuild/rules_scala/archive/%s.zip" % rules_scala_version,
)

load("@io_bazel_rules_scala//:version.bzl", "bazel_version")

bazel_version(name = "bazel_version")

#load("@io_bazel_rules_scala//scala:toolchains.bzl", "scala_register_toolchains")
#
#scala_register_toolchains()

register_toolchains("//toolchains:main-scala_toolchain")

load("@io_bazel_rules_scala//scala:scala.bzl", "scala_repositories")
load("//:scala_version.bzl", "scala_version")

scala_repositories(
    (
        scala_version,
        {
            "scala_compiler": "cedc3b9c39d215a9a3ffc0cc75a1d784b51e9edc7f13051a1b4ad5ae22cfbc0c",
            "scala_library": "0a57044d10895f8d3dd66ad4286891f607169d948845ac51e17b4c1cf0ab569d",
            "scala_reflect": "56b609e1bab9144fb51525bfa01ccd72028154fc40a58685a1e9adcbe7835730",
        },
    ),
    fetch_sources = True,
)

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

load("//:third_party.bzl", "dependencies")

dependencies()

#load("//3rdparty:workspace.bzl", "maven_dependencies")
#
#maven_dependencies()
#
#load("//3rdparty:target_file.bzl", "build_external_workspace")
#
#build_external_workspace(name = "third_party")
