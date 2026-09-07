#!/usr/bin/env bash
# Prints the GitHub release notes for one version: that version's section of
# CHANGELOG.md (the text between "## <version> — <date>" and the next "## "
# heading), a download table for the bundles release.yml attaches, and the
# changelog / compare links. Used by .github/workflows/release.yml when a
# release is cut and by .github/workflows/release-notes.yml to (re)apply the
# notes to an existing release; run locally as
#   bash scripts/release-notes.sh 0.2.0 > build/release-notes.md
set -euo pipefail
cd "$(dirname "$0")/.."

version=${1:-}
if [ -z "$version" ]; then
  echo "usage: $0 <version>   (e.g. 0.2.0, or v0.2.0)" >&2
  exit 2
fi
version=${version#v}
tag="v$version"
repo=${GITHUB_REPOSITORY:-michelbr84/Flapforge}

# The changelog section, without the heading and its surrounding blank lines.
section=$(awk -v heading="## $version — " '
  index($0, heading) == 1 { found = 1; next }
  found && /^## / { exit }
  found { print }
' CHANGELOG.md | sed -e '/./,$!d' | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}')
if [ -z "$section" ]; then
  echo "release-notes: CHANGELOG.md has no '## $version — <date>' section" >&2
  exit 1
fi

# The newest tag below this version, for the compare link (empty for the first
# release, or when the checkout carries no tags).
previous=$(git tag --list 'v*' --sort=-v:refname 2>/dev/null | grep -vx "$tag" | while read -r t; do
  if [ "$(printf '%s\n%s\n' "$t" "$tag" | sort -V | tail -n 1)" = "$tag" ]; then
    echo "$t"
    break
  fi
done)

printf '%s\n' "$section"
cat <<NOTES

## Downloads

| Platform | File | How to run |
| --- | --- | --- |
| Windows 10+ | \`Flapforge-$version-windows.zip\` | Unzip and run \`Flapforge\\Flapforge.exe\` (bundled runtime, nothing to install) |
| macOS 12+ | \`Flapforge-$version-macos.zip\` | Unzip and open \`Flapforge.app\` (unsigned: right-click › Open the first time) |
| Linux | \`Flapforge-$version-linux.zip\` | Unzip and run \`Flapforge/bin/Flapforge\` |
| Any OS with Java 17+ | \`flapforge-$version-all.jar\` | \`java -jar flapforge-$version-all.jar\` (\`--lang pt_BR\`, \`--fullscreen\`, \`--no-audio\`, \`--seed N\`, …) |
| Android 13+ | \`Flapforge-$version-android.apk\` | Sideload (debug-signed; allow installs from unknown sources) |

Saves live in \`~/.flapforge\` (Linux), \`%APPDATA%\\Flapforge\` (Windows) or
\`~/Library/Application Support/Flapforge\` (macOS) and load unchanged across
releases. Player guide: https://github.com/$repo/wiki (English and Português).
NOTES

if [ -n "$previous" ]; then
  echo
  echo "**Full changelog:** https://github.com/$repo/blob/$tag/CHANGELOG.md · **Compare:** https://github.com/$repo/compare/$previous...$tag"
else
  echo
  echo "**Full changelog:** https://github.com/$repo/blob/$tag/CHANGELOG.md"
fi
