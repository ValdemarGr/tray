load("@rules_jvm_external//:defs.bzl", "maven_install")
load("@rules_jvm_external//:specs.bzl", "maven", "parse")
load("//:scala_version.bzl", "scala_major", "scala_minor")

def _make_dep(scalaMinor, group, module, ver):
    return {
        "scalaMinor": scalaMinor,
        "group": group,
        "module": module,
        "ver": ver,
    }

def _stringify_mvn(dep):
    versionExtra = ("_2.{}".format(dep["scalaMinor"])) if (dep["scalaMinor"] != None) else ""
    return dep["group"] + ":" + dep["module"]["module"] + versionExtra

def _to_maven(dep):
    artifact = parse.parse_maven_coordinate(_stringify_mvn(dep) + ":" + dep["ver"])
    return maven.artifact(
        group = artifact["group"],
        artifact = artifact["artifact"],
        packaging = artifact.get("packaging"),
        classifier = artifact.get("classifier"),
        version = artifact["version"],
        exclusions = None,
    )

def _deps(scalaMinor, group, ver, *modules):
    deps = {}
    for module in modules:
        deps[module["module"]] = _make_dep(scalaMinor, group, module, ver)
    return deps

def _modt(module, *transitive):
    return {
        "module": module,
        "transitive": [(t + "_" + scala_major + ".{}".format(scala_minor) if scalaArtifact else t) for t, scalaArtifact in transitive],
    }

def _get_deps(scalaMinor):
    rawDeps = {}
    catsDeps = _deps(
        scalaMinor,
        "org.typelevel",
        "2.1.1",
        _modt("cats-core", ("org.typelevel:cats-kernel", True)),
        _modt("cats-effect", ("org.typelevel:cats-kernel", True), ("org.typelevel:cats-core", True)),
        _modt("cats-free", ("org.typelevel:cats-kernel", True), ("org.typelevel:cats-core", True)),
    )
    http4sDeps = _deps(
        scalaMinor,
        "org.http4s",
        "0.21.4",
        _modt("http4s-core", ("org.http4s:parboiled", True), ("io.chrisdavenport:vault", True)),
        _modt("http4s-dsl"),
        _modt("http4s-async-http-client", ("org.asynchttpclient:async-http-client", False), ("org.http4s:http4s-client", True)),
    )
    fs2 = _deps(
        scalaMinor,
        "co.fs2",
        "2.3.0",
        _modt("fs2-core", ("org.scodec:scodec-bits", True)),
        _modt("fs2-io"),
    )
    circe = _deps(
        scalaMinor,
        "io.circe",
        "0.13.0",
        _modt("circe-core"),
        _modt("circe-generic", ("com.chuusai:shapeless", True), ("org.typelevel:cats-core", True)),
        _modt("circe-parser"),
    )
    kindProjector = _deps(
        scalaMinor,
        "org.typelevel",
        "0.10.3",
        _modt("kind-projector"),
    )
    googleAuth = _deps(
        None,
        "com.google.auth",
        "0.20.0",
        _modt("google-auth-library-oauth2-http", ("com.google.auth:google.auth.library.credentials", False)),
    )

    return dict(rawDeps.items() + catsDeps.items() + http4sDeps.items() + fs2.items() + circe.items() + googleAuth.items() + kindProjector.items())

def _mvn_flat(string):
    return "@maven//:" + string.replace(":", "_").replace("-", "_").replace(".", "_")

def make_importable(scalaMinor):
    mod = {}
    for k, v in _get_deps(scalaMinor).items():
        transitive = v["module"]["transitive"]
        flatItems = [_mvn_flat(t) for t in transitive]
        actual = _mvn_flat(_stringify_mvn(v))
        mod[k] = [actual] + flatItems
    return mod

importable = make_importable(scala_minor)

def dependencies():
    deps = _get_deps(scala_minor)
    asMvn = [_to_maven(dep) for _, dep in deps.items()]
    maven_install(
        artifacts = asMvn,
        repositories = [
            "https://repo.maven.apache.org/maven2/",
            "https://mvnrepository.com/artifact",
            "https://maven-central.storage.googleapis.com",
        ],
        fetch_sources = True,
        generate_compat_repositories = True,
        #        maven_install_json = "//:maven_install.json",
    )
