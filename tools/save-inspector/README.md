# SaveInspector

Prints and validates a Flapforge profile directory (plan D15, milestone M3). It loads
`save.json` through the very same `persistence.SaveManager` the game uses — same migration chain,
same repairs, same failure policy — so what it prints is what the game sees, not a second opinion.

It never writes `save.json`. It does run the load policy in full, so a run refreshes
`save.json.bak` exactly as starting the game would, and an unusable save is quarantined as
`save.corrupt-<epochMs>.json` rather than left to be found again.

## Running

```bash
./gradlew saveInspector -PtoolArgs="--home ~/.flapforge"
./gradlew saveInspector -PtoolArgs="--home build/test-home --json"
```

or through the wrappers in this directory (they forward every argument):

```bash
tools/save-inspector/run.sh --home ~/.flapforge
tools\save-inspector\run.ps1 --home $env:APPDATA\Flapforge
```

## Options

| Option | Default | Meaning |
| --- | --- | --- |
| `--home DIR` | the platform location | profile directory to inspect |
| `--json` | — | print a machine-readable report instead of the text one |
| `--help` | — | print the options |

The platform location is Linux `~/.flapforge`, Windows `%APPDATA%/Flapforge`, macOS
`~/Library/Application Support/Flapforge` (`persistence.SavePaths`).

## Output

- **Location** — the profile directory and every file in it that belongs to the save system:
  `save.json`, `save.json.bak`, quarantined saves (`save.corrupt-<epochMs>.json`), reset backups
  (`save.reset-<epochMs>.json`) and pre-migration copies (`backups/save.v<N>.pre-migration.json`),
  each with its size.
- **Status** — how the load ended (`LOADED`, `RESTORED_FROM_BACKUP`, `RESET_AFTER_CORRUPT`,
  `REFUSED_NEWER_VERSION`, `NEW_PROFILE`), whether the session would be read-only, which schema
  version was migrated from, and the envelope (`version`, `appVersion`, `contentVersion`,
  `savedAtEpochMs`).
- **Profile** — level and experience, prestige count, wallet, ability cap, passive slot bonus,
  unlock count by namespace, owned upgrade nodes and ability levels, achievements, challenge
  records and the current selection.
- **Daily** — the persisted daily pick (E27): date, seed, world, tier, forced modifiers, attempts.
- **Statistics** — runs, gates, points, coins earned/spent/collected, experience, best streak,
  ability uses, shield absorbs and revives, bosses cleared, challenges completed, dailies played,
  playtime and the run-history fill against its cap of 100.
- **Validation** — every repair `PlayerProfile.normalize` had to make (E15/E21 consistency rules)
  and every field the file carries that this build does not know. Unknown fields are *not* errors:
  the write path preserves them on purpose (E22).

## Exit status

The tool succeeds when the save is sound (or there is no save yet) and fails when it was unusable,
refused as too new, or had to be repaired — the failure message carries the report status, and
`--json` carries `"valid": true|false` plus the `status` and `repairs` fields. A usage error fails
the same way with the usage text on stderr. A failure fails the Gradle task, so a release script
can gate on it.

## See also

`docs/SAVE_SYSTEM.md` — the file layout, the write path, the overlay rule, the backup, quarantine
and refusal policy, and how to add a migration.
