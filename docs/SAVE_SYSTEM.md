# Save system

How Flapforge stores a player's progress, what it guarantees, and what you have to do when the
shape of a save changes. The decisions behind it are D15 (save system), D14 (progression write
path), D23 (injected time and executors) and errata E13, E15, E21 and E22 of the
implementation plan.

The one rule everything else follows from: **a player's progress is never destroyed by this
program.** Not by a crash mid-write, not by a corrupt file, not by a downgrade, not by a bug in a
migration. Every failure path moves a file aside; none deletes one.

---

## 1. Where the files live

`persistence.SavePaths` resolves the profile directory, in this order of precedence:

1. `SavePaths.override(Path)` — tests and tools (`saveInspector --home`);
2. the system property `flapforge.home`;
3. the environment variable `FLAPFORGE_HOME`;
4. the per-OS default.

| Platform | Default directory |
| --- | --- |
| Linux and anything unrecognised | `~/.flapforge` |
| Windows | `%APPDATA%/Flapforge` (falls back to `~/AppData/Roaming/Flapforge`) |
| macOS | `~/Library/Application Support/Flapforge` |

Nothing creates the directory until something is written into it.

| File | Written by | Purpose |
| --- | --- | --- |
| `save.json` | every save | the profile |
| `save.json.bak` | once per session, right after a successful load | yesterday's profile |
| `backups/save.v<N>.pre-migration.json` | once, before the first migration from version `N` | the file as it was before the schema moved |
| `save.corrupt-<epochMs>.json` | a failed load | a save that could not be read |
| `save.reset-<epochMs>.json` | `--reset-save` | the save the player asked to abandon |
| `settings.json` | `SettingsStore` | options, not progress (same write path, different policy) |

Timestamps in those names come from the injected `core.TimeSource`, never from a wall clock read
inside the pure packages (D23). Two failures inside the same millisecond get a `-2`, `-3` … suffix
instead of overwriting one another.

---

## 2. The envelope

```json
{ "version": 1, "appVersion": "0.1.0", "contentVersion": 1, "savedAtEpochMs": 1756684800000,
  "profile": { … } }
```

- `version` — the **schema** version, and the only field read before the loader decides whether it
  can read the rest. `persistence.SaveFile.VERSION` is the version this build reads and writes.
- `appVersion`, `contentVersion` — diagnostics. They say which build and which content last wrote
  the file so a bug report can be matched to a release; nothing branches on them.
- `savedAtEpochMs` — from the injected time source.
- `profile` — one `progression.PlayerProfile`, the single persisted POJO. Its full field list is
  §4 of the plan; `src/test/resources/fixtures/save_v1.json` is the frozen v1 sample.

A missing `version` is read as the current one (a hand-edited file that lost the line is far more
likely than a build that never wrote one). A `version` that is present but is not a number cannot
have been written by any build, so the file is treated as unreadable.

`PlayerProfile` is deliberately dull: public fields, a no-argument constructor, initialisers as
defaults, and only numbers, booleans, strings, `List<String>`, string-keyed maps and small nested
POJOs. No enum keys, no `java.time`, no interfaces, no polymorphism. A save must bind with plain
Gson on every JDK the game ships on and stay readable by a human with a text editor.

---

## 3. Loading

```
read → parse → migrate → aliases → bind → normalize
```

E21 fixes the order of the middle steps: **aliases run before normalisation**, because a renamed id
has to be renamed before the consistency rules decide what it implies. Binding sits between them
for the obvious reason that `normalize` is a method on the bound object — everything before it works
on the parsed tree, which is the only representation that still holds fields this build has never
heard of.

- **migrate** — `SaveMigrator` walks the `Migration` chain from the file's version to this build's
  (§5). A missing step is reported, never guessed at.
- **aliases** — `SaveManager.aliasStep`, injected. `aliases.json` arrives with the upgrade trees in
  M4; it renames ids in `unlocked`, `upgrades`, `abilityLevels` and `selected`, drops removed nodes
  and credits their refund once, with `profile.reconciled` recording what has already been paid.
  `persistence` never imports `content`, which is why the step is injected rather than imported.
- **normalize** — `PlayerProfile.normalize(ProfileSchema)` repairs and reports (§4).

The profile the session runs on is `SaveManager.profile()`. The tree that was read is kept for the
write path (§6).

---

## 4. Normalisation: what "repair" means

`PlayerProfile.normalizeAndReport(schema)` returns one English line per repair — the loader carries
them in `LoadResult.repairs()` and `saveInspector` prints them. Two kinds of repair exist.

**Nulls and impossible numbers.** A missing map or list becomes an empty one, a negative balance
becomes zero, a level below 1 becomes 1, an ability cap below the base cap of 2 is raised, an
upgrade or ability level of zero is dropped, and duplicate or blank ids are removed.

**The E15 consistency rules.** An id the profile *uses* must be an id the profile *owns*:

| Because the profile has | `unlocked` gains |
| --- | --- |
| `abilityLevels.<id>` | `ability:<id>` |
| `challenges.<id>` | `challenge:<id>` |
| `upgrades.<node>` | `tree:<the node's tree>` |
| `selected.birdId` / `paletteId` | `bird:<id>` / `cosmetic:<bird>:<palette>` |
| `selected.worldId` / `tierId` | `world:<id>` / `tier:<id>` |
| `selected.activeAbilityId`, `passiveAbilityIds` | `ability:<id>` |

Upgrade nodes are **not** unlockables (E21). `unlocked` never contains an `upgrade:` id — one found
in an old file is dropped — and node ownership lives only in `profile.upgrades`, keyed by the bare
node id. What an owned node implies is the unlock of its *tree*.

Which ids exist is content, and `progression` does not depend on `content`, so the id tables arrive
as a `progression.ProfileSchema`: the birds and their palettes, the worlds, the tiers, the
abilities, the node → tree map and the fallback selection. A `selected.*` id no table knows falls
back to its default. A table left empty means "accept every id", which is what a milestone that has
not shipped that registry yet uses — a build with no world registry must not "repair" a world
selection away.

---

## 5. Migrations

A migration is a pure function on the parsed tree:

```java
new Migration(1, 2, tree -> {
    JsonObject profile = tree.getAsJsonObject("profile");
    long coins = profile.get("coins").getAsLong();     // a field the current POJO does not have
    profile.remove("coins");
    profile.add("wallet", walletWith(coins));
    return tree;
});
```

It works on the tree rather than on `PlayerProfile` on purpose: the POJO is the *current* shape, so
binding an old file to it would drop exactly the fields the migration needs to read.

**Rules.** A step never mutates its argument (`Migration.apply` hands it a deep copy); it touches
only what changed, so unknown keys survive; and it is total — a field the old file did not have is a
default, not a failure. `SaveMigrator` sets `version` after each step, so a step does not have to.
The chain is ordered and gapless: at most one step may start at a given version, and the migrator
walks `n → n+1 → …` until it reaches the target. Migrating is idempotent, so a double load can never
run a step twice.

**Adding one**, when the shape of a save changes:

1. raise `SaveFile.VERSION`;
2. write the `Migration(old, new, …)` and add it to `SaveMigrator.standard()`;
3. add the fixture pair `src/test/resources/fixtures/save_v<old>_to_v<new>/{in,expected}.json` and a
   `SaveMigrationTest` case over it;
4. keep the older fixtures: they are what proves that the whole chain still runs.

Before any step runs, the file is copied to `backups/save.v<N>.pre-migration.json`. An existing
copy is never overwritten — the first pre-migration state is the one worth keeping.

Version 1 is the first version there has ever been, so the shipped chain is empty. The contract is
kept alive by a synthetic `1 → 2` step in `SaveMigrationTest` (the one above), driven through the
real `SaveManager` by `SaveManager.schemaVersion(int)`. That seam exists only so the migration path,
the pre-migration backup and the "too new" refusal are exercised before the first real schema change
arrives; production leaves it at `SaveFile.VERSION`.

---

## 6. Writing, and what "unknown fields survive" means

The write path is `serialise → overlay → executor → tmp + fsync + atomic rename → fsync dir`.

**Overlay (E22).** `SaveManager` keeps the tree it read and lays the freshly serialised state *over*
it, so a key written by a newer build survives a load and a save by this one, at any depth. Merging
recurses into the nodes that are POJOs on both sides — the root, `profile`, `profile.statistics`,
`profile.selected`, `profile.daily` and `profile.prestigeBaseline` — and every map-typed and
list-typed node is replaced **wholesale** (`SaveManager.REPLACE_WHOLESALE`). JSON arrays are always
replaced, whether or not they are listed, which is why `prestigeBaseline` (three scalars and one
list) does not need to be: listing it only cost forward compatibility for the keys a newer build
might put inside it.

That distinction is the whole design:

- *merged nodes* are how an unknown field survives. A future `profile.statistics.perfectRuns` is
  still in the file after this build saves over it.
- *replaced nodes* are how a removal sticks. If `profile.upgrades` were merged, a prestige reset
  would resurrect every node, an alias removal would undo itself, a daily reset would keep
  yesterday's modifiers and the run-history cap would never actually drop a run.

So: an unknown **field** at a merged node survives; an unknown **entry inside a map or a list**
does not, because the map is written as a whole. `SaveManagerTest.unknownFieldsRoundTrip` pins the
first, `mapAndListNodesAreReplacedWholesaleSoAPrestigeResetPersists` and
`anIdRemovedByTheAliasStepDoesNotReappear` pin the second.

The same is true one level down: an unknown field inside a *typed value* of a wholesale map or list
— `achievements.<id>.futureField`, a `runHistory[]` entry — is dropped too, because its container is
written whole. That is deliberate (a stale entry must not come back), but it means a newer build may
not put per-entry state in those maps and expect an older build to keep it.

**Atomicity (D15, E13).** `AtomicFiles` writes `save.json.tmp`, forces it to the device, moves it
onto `save.json` with `ATOMIC_MOVE` and forces the directory. **Every** write in the profile
directory goes through it, the once-per-session `save.json.bak` copy included: a plain
`Files.copy(REPLACE_EXISTING)` truncates its destination first, so a crash in the middle of one
would destroy the previous good backup. A failing move is retried
*immediately* three times (a lock held by an indexer is usually gone by the next call) — there is no
sleep and no back-off, because the pure packages may not touch the thread API — then falls back to a
non-atomic `REPLACE_EXISTING`, and only then reports `IO_FAILED`. A power cut or a `kill -9` leaves
either the old file or the new one, never a truncated one.
`SaveManagerTest.crashLeavesOldFileIntact` proves it with `AtomicFiles.FailurePoint`, which injects
a simulated crash at a chosen step.

**Threading (D23).** The JSON is rendered on the calling thread — the loop thread in the game — and
the file write runs on the injected `Executor`. `app.Threads.saveExecutor()` is a single daemon
thread that runs everything it is given, in order; the coalescing lives in `SaveManager`, which
keeps at most one task in flight and lets a `save()` that arrives while it runs *replace* the text
the next write will put on the disk. So a burst still collapses into the newest state, and nothing
is lost track of: `pendingWrites()` returns to 0 and `flush(timeoutMs)` returns `true` once the
newest write has landed. (The executor used to do the collapsing itself with `DiscardOldestPolicy`.
A discarded task never runs and never reports, so every discard leaked one pending slot and
`flush` could never succeed again.) Nothing in the save path throws: a finished write is pushed onto
a queue that the loop drains once per tick with `pollCompletedWrite()` and turns into the
`SaveFailed` event and its toast. Tests inject a direct executor and a `FixedTimeSource`, which is
what makes save timestamps reproducible in fixtures.

**The dirty flag.** `ProgressionManager.isDirty()` is cleared by a write that *landed*, never by one
that was queued: `GameContext.saveProfile()` calls `markSaveQueued()`, and `drainSaveResults()`
calls `confirmSave()` only when a write reported OK and nothing newer is still in flight. A failure
therefore leaves the profile dirty, so the autosave and the exit save both try again — a read-only
profile directory or a full disk used to lose the whole session even though the very next write
would have succeeded.

**When it writes** (D15): at the end of a run, on a settings change, on an autosave every 60 s while
the top screen is not running a live run (`Screen.blocksAutosave()`), and at exit (bounded join).
After a purchase and on a selection change arrive with the M4 screens that can fire them.

---

## 7. Failure, backup and refusal policy

| Situation | What happens |
| --- | --- |
| No file, no backup | fresh profile, `NEW_PROFILE`, nothing written until something changes |
| No file, but `save.json.bak` loads | `RESTORED_FROM_BACKUP`; the backup is adopted and written straight back to `save.json` |
| File reads, parses, binds | `LOADED`; `save.json` is copied to `save.json.bak` once per session |
| File exists and cannot be **opened** | `UNREADABLE`: nothing is quarantined and nothing is written; the backup carries the session if there is one, otherwise a fresh profile does, and `readOnly()` is true |
| File is unparseable, unbindable, or unmigratable | quarantined as `save.corrupt-<epochMs>.json`, then `save.json.bak` is tried |
| … and the backup loads | `RESTORED_FROM_BACKUP`; the session runs on it **and it is written back to `save.json` before the session starts** |
| … and the backup fails too, or there is none | `RESET_AFTER_CORRUPT`; fresh profile; both files are left where they are |
| `version` is newer than this build | `REFUSED_NEWER_VERSION`: the session runs on a fresh profile it will **never** write, so the newer save survives byte for byte |

Writing the recovery back is not an optimisation. Nothing else in the game writes a profile that has
not changed, so a player who recovers, looks at the menu and quits would find `save.json` still
missing next time — and that session's first write would replace the good `.bak` with a fresh
profile. The whole profile would be gone two sessions after the corruption.

The distinction between "cannot be opened" and "does not parse" matters for the same reason. An
antivirus lock, a cloud-sync placeholder or a transient `EIO` says nothing about the bytes on the
disk; quarantining such a file turns a passing inconvenience into a permanent loss, so it is left
alone and the session refuses to write.

The backup is copied **once per session, right after a successful load**, which is what makes it
useful: it is the profile as this session found it, not as this session left it. A session that was
itself restored from the backup does not copy over it. The copy goes through `AtomicFiles`, and it
counts as done only when the write reported OK, so a transient failure is retried on the next load
rather than skipped for the session.

"Play without saving" is not an error state to hide. `LoadResult.readOnly()` is true, every `save()`
returns `false`, and the UI is expected to say so — a player who downgraded should be told their
progress is safe and simply not being touched, not left guessing.

`--reset-save` (`SaveManager.resetToFresh()`) moves the current save to `save.reset-<epochMs>.json`
**and its backup to `save.bak.reset-<epochMs>.json`** and starts fresh. It does not delete anything
either. The backup has to move with the save: otherwise the next launch would find no `save.json`,
restore the abandoned profile from `save.json.bak` (the row at the top of the table) and silently
undo the flag.

---

## 8. The pre-1.0 reset policy and the frozen v1 (D15)

Until the `v0.1.0` tag, **the save format was not stable**. A build before that tag could raise
`SaveFile.VERSION` without shipping a migration, in which case the older file was refused (§7) and
the player started over with `--reset-save` and a banner explaining it. That freedom was the price
of getting the schema right while the game was still being designed.

The `v0.1.0` tag **froze save v1** (`SaveFile.VERSION == 1`, sample:
`src/test/resources/fixtures/save_v1.json`), and from that build on the promise flips: every
change to the persisted shape ships a `Migration`, every
migration ships its fixture pair, and a save written by any released build loads in every later one.
The synthetic `1 → 2` step in `SaveMigrationTest` exists precisely so that the machinery which makes
that promise keepable is already tested on the day the first real change lands.

---

## 9. Inspecting a save

```bash
./gradlew saveInspector -PtoolArgs="--home ~/.flapforge"
./gradlew saveInspector -PtoolArgs="--home build/test-home --json"
```

`tools/SaveInspector` loads a profile directory through the same `SaveManager` the game uses and
prints the files and their sizes, the load status, the envelope, the wallet, level and experience,
the unlock counts by namespace, the owned nodes and ability levels, the daily pick, the statistics
highlights, every repair normalisation had to make and every field the file carries that this build
does not know. It fails when the save was unusable, refused or repaired, so a release script can
gate on it. See `tools/save-inspector/README.md`.

---

## 10. Tests that pin this document

| Test | What it holds down |
| --- | --- |
| `SaveManagerTest.roundTripKeepsEveryField` | every persisted field survives a save and a load |
| `SaveManagerTest.unknownFieldsRoundTrip` | a hand-added field nested two levels deep survives a save |
| `SaveManagerTest.mapAndListNodesAreReplacedWholesale…` | a reset is not undone by the overlay |
| `SaveManagerTest.anIdRemovedByTheAliasStepDoesNotReappear` | an alias removal sticks |
| `SaveManagerTest.crashLeavesOldFileIntact` | a crash mid-write leaves a loadable file |
| `SaveManagerTest.theBackupIsCopiedOncePerSession…` | `.bak` is the pre-session file, copied once |
| `SaveManagerTest.aFailedBackupCopyIsNotRecordedAsDone` | a backup that failed is retried, not skipped |
| `SaveManagerTest.aBurstOfSavesCoalescesAndStillSettles…` | one task per burst, `pendingWrites()` back to 0 |
| `SaveManagerTest.flushWaitsForARunningWriteAndReturnsTrue` | `flush` really is the bounded join D15 asks for |
| `SaveManagerTest.resetToFreshMovesTheBackupAsideTooSo…` | `--reset-save` sticks across a relaunch |
| `SaveCorruptRecoveryTest.aMissingSaveIsRecoveredFromTheBackup` | a vanished `save.json` costs nothing |
| `SaveCorruptRecoveryTest.anUnreadableSaveIsLeftWhereItIs…` | an unopenable file is never quarantined |
| `SaveManagerTest.theFixtureIsInternallyConsistent…` | `save_v1.json` satisfies E15 with no repairs |
| `SaveCorruptRecoveryTest` | quarantine → backup → fresh, and nothing is ever deleted |
| `SaveMigrationTest` | ordering, idempotence, the pre-migration backup, the refusal |
| `ProgressionManagerTest` | D14's write order, applied exactly once per run |
| `ProgressionEconomyTest` | `economy.json` → `ProgressionRules` → the real reward formula |
| `ProgressionWiringTest` | the game-over path: apply once, publish, save, retry keeps it |

---

## 11. How the game uses it

`GameApplication.runWindowed` is the only place a save manager is built:

```java
SaveManager save = new SaveManager(threads.saveExecutor(), timeSource)
        .stamp(Flapforge.version(), SaveFile.CONTENT_VERSION);
SaveManager.LoadResult loaded = options.resetSave() ? save.resetToFresh() : save.load();
```

The executor is the single daemon `flapforge-save` thread (queue depth 1, latest wins) and the
clock is `app.SystemTimeSource`, so nothing in `persistence` or `progression` ever reads a wall
clock of its own (D23). The load happens **before** the window exists, so the menu is never drawn
against a profile that is still loading. A `LoadResult` with `hasNotice()` becomes a warning toast:
`toast.save_restored`, `toast.save_reset` or `toast.save_read_only`. `--reset-save` prints its
confirmation on stdout — it is a command-line flag, so its answer belongs on the command line — and
quarantines the old file rather than deleting it.

`GameContext` carries `save`, `progression` and `progressionRules` (built once with
`ProgressionRules.fromEconomy(content.economy())`) and exposes `profile()` and `saveProfile()`.
`GameContext.drainSaveResults()`, which `ScreenManager` runs once per tick, drains both the
settings store and the save manager, so a failed write of either becomes one `SaveFailed` event and
one toast on the loop thread.

Write triggers wired today: **run end** (`GameScreen`, immediately after `ProgressionManager.apply`
and before the game-over overlay is pushed, so an instant retry can never lose a reward — D29), the
**60-second autosave** (`GameApplication.loopTick`: every `AUTOSAVE_TICKS` = 3600 ticks, when the
profile is dirty and the top screen is not running a live run — `Screen.blocksAutosave()`, which
`GameScreen` overrides while its run has not finished) and **exit**
(`GameApplication.saveOnExit`, only when the profile is still dirty, before `Threads.shutdown`
drains the executor with its own bounded wait; a drain that times out now says so on stderr rather
than losing the write in silence). The purchase and selection-change triggers of D15 arrive with
the screens that need them (M4).

The autosave is also the retry path. A write that fails leaves the profile dirty, so the next
autosave tick tries again — which matters because the condition that broke the write (a full disk, a
profile directory that went read-only) is usually gone minutes later.

Nothing locks the profile directory, so two instances of the game on one `save.json` still overwrite
each other. D15 does not ask for a lock and the recovery policy does not depend on one; it is
recorded here so the next person does not have to rediscover it.

A headless run (`--headless-run N`) builds no save manager at all: its output must depend on the
seed and the shipped data alone (D12), so `GameContext.canProgress()` is false and nothing is
written.
