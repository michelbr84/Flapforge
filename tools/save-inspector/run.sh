#!/usr/bin/env bash
# Prints and validates a profile directory. Every argument is forwarded to SaveInspector.
#   tools/save-inspector/run.sh --home ~/.flapforge
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
./gradlew saveInspector -PtoolArgs="$*"
