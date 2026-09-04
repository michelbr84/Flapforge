#!/usr/bin/env bash
# Flapforge — packaging script (E9, M9).
#
# Builds the fat jar, exports the procedural icons and runs jpackage to write a
# self-contained app image to build/dist/:
#   Linux   → build/dist/Flapforge      (icon: build/icon/flapforge.png)
#   macOS   → build/dist/Flapforge.app  (icon: build/icon/flapforge.icns)
#   Windows → build/dist/Flapforge      (icon: build/icon/flapforge.ico, run from
#                                          Git Bash/MSYS where uname reports MINGW*)
#
# jpackage ships with JDK 14+. The script prefers $JAVA_HOME/bin/jpackage (the
# JDK the caller selected — a distro "JRE headless" plus a PATH jpackage cannot
# jlink and fails with "Module ... not found") and falls back to the PATH one.
# When neither exists it prints a clear message and exits 1 instead of failing
# later inside a half-written app image.
#
# Usage: scripts/package.sh [gradle options]
#   scripts/package.sh --offline
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."

echo "== Building the fat jar and exporting the icons"
./gradlew fatJar iconExport "$@"

jar="$(ls build/libs/flapforge-*-all.jar | head -n 1)"
version="$(sed -n 's/^version=//p' src/main/resources/version.properties)"

case "$(uname -s)" in
    Darwin)
        icon=build/icon/flapforge.icns ;;
    Linux*)
        icon=build/icon/flapforge.png ;;
    MINGW*|MSYS*|CYGWIN*|Windows*)
        icon=build/icon/flapforge.ico ;;
    *)
        echo "package.sh: unsupported platform '$(uname -s)' — expected Linux, macOS or Windows." >&2
        exit 1 ;;
esac
if [ ! -f "$icon" ]; then
    echo "package.sh: missing $icon — run './gradlew iconExport' first." >&2
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jpackage" ]; then
    jpackage_cmd="$JAVA_HOME/bin/jpackage"
elif command -v jpackage >/dev/null 2>&1; then
    jpackage_cmd=jpackage
else
    echo "package.sh: jpackage not found. It ships with JDK 14+; install a JDK 17+ (Temurin," >&2
    echo "           Zulu, ...) or point JAVA_HOME at one, then re-run scripts/package.sh." >&2
    exit 1
fi

echo "== Packaging $jar (app-version $version, icon $icon)"
rm -rf build/dist
# macOS: --app-version feeds CFBundleVersion, whose first component cannot be
# zero ("The first number in an app-version cannot be zero or negative"), so a
# 0.y.z version is repacked there as its significant form (0.1.0 -> 1.0). The
# jar keeps the real version in its Implementation-Version.
bundle_version="$version"
if [ "$(uname -s)" = Darwin ]; then
    bundle_version="$(printf '%s' "$version" | awk -F. '
        { i = 1; while (i <= NF && $i + 0 == 0) i++;
          out = ""; for (; i <= NF; i++) out = out (out == "" ? "" : ".") $i;
          print (out == "" ? "1" : out) }')"
    if [ "$bundle_version" != "$version" ]; then
        echo "   macOS: app-version $version repacked as CFBundleVersion $bundle_version (first component cannot be zero)"
    fi
fi
"$jpackage_cmd" \
    --type app-image \
    --input build/libs \
    --main-jar "$(basename "$jar")" \
    --name Flapforge \
    --app-version "$bundle_version" \
    --icon "$icon" \
    --dest build/dist

echo "App image written to build/dist/"
