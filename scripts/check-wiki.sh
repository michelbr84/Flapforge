#!/usr/bin/env bash
# Validates wiki/ before it is published to the GitHub wiki: [[wiki links]] and
# relative links resolve, images exist and are used, every page carries its
# language-switch line, file names are wiki-safe and the sidebar lists every
# page. Run locally (bash scripts/check-wiki.sh) and by .github/workflows/wiki.yml.
set -euo pipefail
cd "$(dirname "$0")/.."
fail=0
err() { echo "check-wiki: $*" >&2; fail=1; }
shopt -s nullglob
pages=(wiki/*.md)
[ ${#pages[@]} -gt 0 ] || { err "no pages under wiki/"; exit 1; }
for req in Home _Sidebar _Footer; do
  [ -f "wiki/$req.md" ] || err "wiki/$req.md is missing"
done

for f in wiki/* wiki/images/*; do
  n=$(basename "$f")
  [ "$n" = README.md ] && err "$f would publish as a page called README"
  if LC_ALL=C grep -q '[^ -~]' <<<"$n" || [[ "$n" == *' '* ]] || [[ "$n" == *,* ]]; then
    err "$f: file names must be ASCII, without spaces or commas"
  fi
done

for f in "${pages[@]}"; do
  # [[Page Name]] and [[Label|Page-Name]]: the target is the part after the pipe,
  # spaces standing for dashes as GitHub resolves them.
  while read -r t; do
    [ -n "$t" ] || continue
    [ -f "wiki/$t.md" ] || err "$f: [[...]] link to missing page '$t'"
  done < <(grep -oE '\[\[[^]]+\]\]' "$f" | sed -E 's/^\[\[//; s/\]\]$//; s/^.*\|//; s/ /-/g' | sort -u)
  # ](target) links: external ones are left alone, images and pages must exist.
  while read -r t; do
    [ -n "$t" ] || continue
    case "$t" in
      http://*|https://*|mailto:*|'#'*) ;;
      *.png|*.jpg|*.gif|*.svg) [ -f "wiki/$t" ] || err "$f: missing image '$t'" ;;
      *) [ -f "wiki/${t%%#*}.md" ] || err "$f: relative link to missing page '${t%%#*}'" ;;
    esac
  done < <(grep -oE '\]\([^) ]*(\([^)]*\))?[^) ]*\)' "$f" | sed -E 's/^\]\(//; s/\)$//' | sort -u)

  n=$(basename "$f" .md)
  case "$n" in _Sidebar|_Footer) continue ;; esac
  if [[ "$n" == *'-(pt-BR)' ]]; then
    head -n 1 "$f" | grep -qE '^_Idioma:_ \[\[English\|[^]]+\]\] · \*\*Português \(Brasil\)\*\*$' \
      || err "$f: first line is not the pt-BR language-switch line"
  else
    head -n 1 "$f" | grep -qE '^_Language:_ \*\*English\*\* · \[\[Português \(Brasil\)\|[^]]+-\(pt-BR\)\]\]$' \
      || err "$f: first line is not the English language-switch line"
  fi
  grep -qF "$n]]" wiki/_Sidebar.md || grep -qF "[[${n//-/ }]]" wiki/_Sidebar.md \
    || err "_Sidebar.md does not link to '$n'"
done

for img in wiki/images/*; do
  grep -qF "(images/$(basename "$img"))" wiki/*.md || err "$img is not referenced by any page"
done

[ "$fail" -eq 0 ] && echo "check-wiki: OK (${#pages[@]} pages)"
exit "$fail"
