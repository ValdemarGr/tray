#!/bin/bash

COMP=$(curl -s "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/$1/scala-compiler-$1.jar" | sha256sum | awk ' { print $1 } ' | tr -d '\n')
LIB=$(curl -s "https://repo1.maven.org/maven2/org/scala-lang/scala-library/$1/scala-library-$1.jar" | sha256sum | awk ' { print $1 } ' | tr -d '\n')
REF=$(curl -s "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/$1/scala-reflect-$1.jar" | sha256sum | awk ' { print $1 } ' | tr -d '\n')

echo "scala_repositories((
    \"$1\",
    {
        \"scala_compiler\": \"${COMP}\",
        \"scala_library\": \"${LIB}\",
        \"scala_reflect\": \"${REF}\",
    }
))"