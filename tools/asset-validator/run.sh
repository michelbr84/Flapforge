#!/usr/bin/env bash
# Validates assets/manifest.json. Every argument is forwarded to AssetValidator.
#   tools/asset-validator/run.sh --quiet
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
./gradlew assetValidator -PtoolArgs="$*"
