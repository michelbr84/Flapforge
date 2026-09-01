#!/usr/bin/env bash
# Flapforge — build script (Linux/macOS).
#
# Compiles the project with -Xlint:all -Werror, runs the default test suite and
# produces the self-contained jar at build/libs/flapforge-<version>-all.jar.
# Any extra arguments are forwarded to Gradle (e.g. scripts/build.sh --offline).
#
# Usage: scripts/build.sh [gradle options]
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
exec ./gradlew build fatJar "$@"
