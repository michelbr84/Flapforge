#!/usr/bin/env bash
# Runs the balancing simulation. Every argument is forwarded to BalancingSim.
#   tools/balancing/run.sh --seeds 200 --skill average --csv build/balancing.csv
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
./gradlew balancing -PtoolArgs="$*"
