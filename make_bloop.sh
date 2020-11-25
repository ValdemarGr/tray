#!/bin/bash
DEPS=$(bazel query "deps(//$1:all)" --output location | grep -E '.\.jar$' | grep maven | sed 's/BUILD:[0-9]*:[0-9]*: source file @maven\/\/://')

mkdir -p .bloop

echo $DEPS | python write_bloop_config.py --name $1 --path $(pwd) --home $HOME
