scala_major = "2"

scala_minor = "12"

scala_patch = "10"

scala_version = scala_major + "." + scala_minor + "." + scala_patch
artifact_version = scala_major + "." + scala_minor
def add_scala_ver(s):
    return s + "_" + scala_major + "_" + scala_minor
