# AssetValidator

Checks `src/main/resources/assets/manifest.json` — the one door original art and sound enter
through (plan D18, milestone M7) — the way a player's install sees it. For every entry:

- the `path` resolves on the classpath under `/assets/`;
- a `license` is declared (the value is recorded, never parsed);
- the file is what its `kind` claims, by magic number rather than by extension:
  `SPRITE` / `SHEET` must start with the PNG signature, `AUDIO` must be a RIFF/WAVE container
  (the only format the game decodes, D19), `FONT` must be a TrueType (`00 01 00 00` or `true`)
  or OpenType (`OTTO`) file.

Parse errors of the manifest itself (duplicate ids, unknown kinds, missing paths) are reported
the same way. The shipped manifest carries an empty list and passes.

## Running

```bash
./gradlew assetValidator
./gradlew assetValidator -PtoolArgs="--manifest build/some/manifest.json --quiet"
```

or through the wrappers in this directory (they forward every argument):

```bash
tools/asset-validator/run.sh
tools\asset-validator\run.ps1 --quiet
```

## Options

| Option | Meaning |
| --- | --- |
| `--manifest PATH` | check this file instead of the classpath manifest (paths still resolve on the classpath) |
| `--quiet` | print only the problems |
| `--help` | print the options |

## Exit status

`0` when every entry is sound; `1` on any problem (the Gradle task fails). `System.exit` is not
called — the status travels as an exception, as in `contentCheck` (D4).
