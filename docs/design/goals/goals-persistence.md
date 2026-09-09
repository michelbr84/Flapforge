# Goals — Persistence Design

Status: design investigation, for the Goals redesign (achievements card shows "Unlocked <date>").
Save schema v1 is frozen at tag `v0.1.0`.

## 1. What Goals state lives in the save — field map

Today "Goals" is two feature sets on the same `PlayerProfile`:

- **Achievements** — a flat map keyed by achievement id.
- **Challenges** — a second flat map keyed by challenge id (daily/weekly/seasonal variants).

All ids are bare ids (never `upgrade:` unlock ids). Values are plain JSON records; the save
loader uses Gson with `TypeToken.getParameterized(Map.class, String.class, AchievementRecord.class)`
style, not record reflection.

| Save field (`PlayerProfile`) | Key / type | Meaning | Written where |
|---|---|---|---|
| `achievements` | `Map<String, AchievementRecord>` | One entry per unlocked achievement, keyed by bare id | `ProgressionManager` on unlock |
| `achievements.<id>.unlockedAtEpochMs` | `long` | **The unlock date, epoch millis** — this is the value the new Achievements card renders as "Unlocked <date>" | `ProgressionManager` writes `new AchievementRecord(time.epochMillis())` on first unlock |
| `achievements.<id>.progress` | `double` | Current progress toward unlock (0..1) for progress-based achievements | updated on progress ticks |
| `achievements.<id>.unlocked` | `boolean` | Convenience flag; derived from presence of the entry + `unlockedAtEpochMs` | kept in sync by `ProgressionManager` |
| `challenges` | `Map<String, ChallengeRecord>` | One entry per challenge, keyed by bare id | `ProgressionManager` on challenge completion |
| `challenges.<id>.completedAtEpochMs` | `long` | Challenge completion date, epoch millis | on completion |
| `challenges.<id>.progress` | `double` | Progress toward completion (0..1) | on progress ticks |
| `challenges.<id>.claimedRewards` | `boolean` / `List<String>` | Whether / which rewards have been claimed | on first claim |
| `challenges.<id>.best` | `int` | Best value achieved for this challenge window | on run end |

Related, same profile, for context:

| Save field | Key / type | Meaning |
|---|---|---|
| `upgrades` | `Map<String, Integer>` | Owned level per upgrade node, keyed by **bare id** |
| `abilityLevelCap` | `int` | Raised by node grants; ceiling is `UpgradeManager.abilityLevelCeiling(content)` — a save carrying a higher cap than the build supports must still load |
| `passiveSlotBonus` | `int` | Raised by node grants |
| `coins` / currency fields | `long` | Wallet used by `UpgradeManager.reconcile` for refunds |
| `reconciled` | `boolean` | Set by `UpgradeManager.reconcile`; refunds credited **once** |

Unlock-date trace (verified in code):

- `ProgressionManager.java:490` — `achievements.put(id, new AchievementRecord(time.epochMillis()))`
- `PlayerProfile.java:526` — field `unlockedAtEpochMs`
- `GoalsScreen.java:289` — `isoDate(record.unlockedAtEpochMs)` renders the "Unlocked <date>" line

**Verdict: the unlock date IS persisted today.** No schema change is needed to show it.

## 2. Migration recommendation

No migration is required for the Goals redesign as scoped:

- The unlock/completion **date** is already persisted (`unlockedAtEpochMs`, `completedAtEpochMs`).
- Rendering it is a pure UI change in `GoalsScreen`.

Recommended migration path, in priority order:

1. **Add only new optional fields** (e.g., a `displayNameVersion` or a `meta` object) as
   Gson `@SerializedName` with defaults. Gson fills missing fields with Java defaults on load,
   so old saves load unmodified — no migration function, no `settings.v<N>.json` churn.
2. If the redesign adds a brand-new **category** (e.g., "Seasons" distinct from challenges),
   add a new top-level `Map<String, SeasonRecord>` keyed by season id. New top-level fields
   default to empty; old saves load cleanly, and the wall-clock date of the season is derived,
   not stored — so no backfill and no bump to save `version`.
3. Only if a field's **meaning changes in place** (e.g., `progress` meaning flips from
   "points" to "percentage of a new maximum") do we bump save `version` and ship a real
   migration in `persistence`. Never swap a field's meaning in place and call it done.

Current save `version` is v1 (frozen at `v0.1.0`). Adding optional fields or a new
top-level map keeps v1 and avoids both the migration function and the
"old file kept as `settings.v<N>.json`" path.

## 3. Reward idempotence — the answer

**Rewards are idempotent per claim; a redesign must keep "claimed once" monotonic.**

- `challenges.<id>.claimedRewards` is a one-way flag. The same challenge id is never claimed
  twice in the same window.
- `achievements` entries are created once (`put` on first unlock); a duplicate unlock is a
  no-op because the entry already exists — the earliest `unlockedAtEpochMs` is retained.
- Currency grants flow through the same "credit once" discipline as
  `UpgradeManager.reconcile`: refunds/grants are credited exactly once per id, gated by
  `reconciled`/`claimedRewards`. Do not credit on every load.

Redesign rule: any reward path must be keyed by a stable id and gated by a persisted
"already granted" marker. Never derive "should grant" from wall-clock alone on load (e.g.,
"today's daily was completed, grant again") — that would double-pay when a save is loaded
twice in the same window. Keep the marker on the save, not in memory.

## 4. How to write a Goals test safely

**No test may write to the real profile directory.** The invariant: `ls ~/.flapforge` after
a full build must still say the directory does not exist on a machine that has never run the
game.

Pattern for any Goals/Progression test:

```java
class GoalsPersistenceTest {
    @TempDir Path tempDir;                       // JUnit guarantee: per-test, deleted after

    @BeforeEach void setUp() {
        SavePaths.override(tempDir.resolve("profile"));   // redirect ALL profile I/O
        // build a PlayerProfile in memory; do NOT touch SavePaths.defaultPath()
    }

    @Test void unlockDatePersists() {
        PlayerProfile p = PlayerProfile.createEmpty();
        ProgressionManager.grant(p, "achievement.first-flight",
                /* mode */ testClock);          // inject a fixed clock, never currentTimeMillis
        assertThat(p.achievements().get("first-flight").unlockedAtEpochMs())
            .isEqualTo(TEST_EPOCH_MS);
        // round-trip: serialize to tempDir, load back, compare identical values
    }
}
```

Rules that make it safe:

- Always redirect with `SavePaths.override(...)` pointing under a `@TempDir` (or
  `build/smoke/` for smoke tests). Never let a test fall through to the OS default.
- Use a **fixed/injected clock** (`long epoch` parameter), never
  `System.currentTimeMillis`/`nanoTime` in `progression`/`persistence` code — those packages
  are pure and forbid it.
- Test exactly one persisted side effect per test: the field you wrote, the idempotence
  (grant twice → one entry, earliest date), and the load (serialize → reload → identical
  values).
- Smoke tests that start the real app must pass `--home build/smoke/app-home` so the
  app's own profile dir lands under `build/`, never `~/.flapforge`.
- After the test phase, `ls ~/.flapforge` must still report "No such file or directory"
  on a machine that has never run the game. If it exists, a test broke the rule.

## 5. Compatibility verdict

- **Backward compatible, no migration needed** for the as-scoped Goals redesign.
  `achievements.<id>.unlockedAtEpochMs` already carries the date the new Achievements card
  needs; `GoalsScreen` already renders it.
- Old v1 saves load unchanged under the "add optional fields only" rule; new saves with the
  optional fields load fine on older builds that ignore unknown fields.
- The one hard compatibility line: never change the meaning of an existing persisted field
  in place. If the redesign insists on reinterpreting `progress` or adding a
  required-with-backfill field, that is a save `version` bump with a real migration — and a
  `settings.v<N>.json` preservation path.

## Verification commands (run before reporting done)

```bash
./gradlew --offline build
./gradlew --offline test
./gradlew saveInspector -PtoolArgs="..."      # inspect/validate a save directory
ls ~/.flapforge                                # must still say it does not exist
```
