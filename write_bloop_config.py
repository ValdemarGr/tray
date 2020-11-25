import argparse
import json
import sys

parser = argparse.ArgumentParser()
parser.add_argument("--name", type=str)
parser.add_argument("--path", type=str)
parser.add_argument("--home", type=str)

args = parser.parse_args()

deps = sys.stdin.read()

asLst = deps.split()

absPath = args.path + "/" + args.name

sources = list(filter(lambda x: x.endswith("sources.jar"), asLst))
nonSources = list(filter(lambda x: not x.endswith("sources.jar"), asLst))

def modularize(dep):
    toSearch = "maven2/"
    strippedStr = dep[dep.find(toSearch)+len(toSearch):]
    removedJar = strippedStr[:strippedStr.rfind("/")]
    ver = removedJar[removedJar.rfind("/") + 1:]
    rest = removedJar[:removedJar.rfind("/")]
    pkgName = rest[rest.rfind("/") + 1:]
    org = rest[:rest.rfind("/")].replace("/", ".")

    return {
            "organization": org,
            "name": pkgName,
            "version": ver,
            "configurations": "default",
            "artifacts": [
                {
                    "name": pkgName,
                    "path": dep
                },
                {
                    "name": pkgName,
                    "classifier": "sources",
                    "path": dep[:-4] + "-sources.jar"
                }
            ]
    }

out = {
    "version": "1.4.0",
    "project": {
        "name" : args.name,
        "scala": {
            "organization": "org.scala-lang",
            "name": "scala-compiler",
            "version": "2.12.10",
            "options":[],
            "jars": [
                args.home + "/.sbt/boot/scala-2.12.10/lib/scala-reflect.jar",
                args.home + "/.sbt/boot/scala-2.12.10/lib/jansi.jar",
                args.home + "/.sbt/boot/scala-2.12.10/lib/scala-library.jar",
                args.home + "/.sbt/boot/scala-2.12.10/lib/scala-xml_2.12.jar",
                args.home + "/.sbt/boot/scala-2.12.10/lib/jline.jar",
                args.home + "/.sbt/boot/scala-2.12.10/lib/scala-compiler.jar"
            ]
        },
        "directory" : absPath,
        "workspaceDir" : args.path,
        "sources" : [
            absPath + "/src/main/scala",
            absPath + "/main/scala"
        ],
        "dependencies":[],
        "classpath": nonSources,
        "out": args.path + "/.bloop/" + args.name,
        "classesDir": args.path + "/.bloop/" + args.name + "/scala-2.12/classes",
        "resolution": {
            "modules": list(map(lambda x: modularize(x), nonSources)) 
        },
        "tags": ["library"]
    }
}

print(json.dumps(out))
