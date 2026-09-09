# Goals event wiring — what backs each displayed value

Status: survivability doc for the Goals redesign. Every claim is file:line where
verified; anything not confirmed on the final read pass is marked `UNVERIFIED`.

## 1. Pipeline: run -> Statistics -> evaluators -> Goals reads

Simulation builds the run's stat layers at construction
(`gameplay/Simulation.java:106,167-172`): `stack.setLayer(BIRD/BIRD_SYNERGY/
UPGRADES/PRESTIGE/CHALLENGE, ...)`. Scores are awarded in-tick against resolved
stats (`Simulation.java:332-336`, `SCORE_MULT`), emitted as `TickFact.Scored`.

End-of-run accounting: `Statistics` records run counters; the profile keeps
lifetime bests such as `bestGates`, `bestPoints` (`progression/AchievementEvaluator.java:46-48`
mirrors run counters to lifetime bests via `progression/Statistics.java`,
`StatisticKey.java`). Evaluators are pure functions over profile state:
`UnlockEvaluator` and `AchievementEvaluator` (`AchievementEvaluator.java:51`
"Pure: nothing here reads a clock, a random stream or anything but its
arguments").

Goals reads: `ui/screens/GoalsScreen.java` renders from profile statistics +
evaluator results + collection progress. Exact read calls `UNVERIFIED`.

## 2. Displayed value -> source -> updating event -> persisted?

| Goals value | Authoritative source | Updating event | Persisted |
| --- | --- | --- | --- |
| challenge unlock | `UnlockEvaluator` over profile statistics | run-end evaluation / roll | yes (profile flags) |
| challenge progress / best | `Statistics` lifetime bests (`AchievementEvaluator.java:46-48`, :262-266) | each run end | yes |
| challenge completion | profile progress flags (shape `UNVERIFIED`) | run-end evaluator | yes |
| challenge reward | single `RewardDef` via wallet (`AchievementEvaluator.java:178-180` `rewardOf`) | on completion | yes (coins/spendables) |
| achievement unlock | `AchievementEvaluator`, `AchievementRecord` created with injected timestamp (`AchievementEvaluator.java:38-39`) | run-end evaluation, once | yes — record in profile; see section 3 |
| achievement progress | lifetime counter mirror (`AchievementEvaluator.java:46-48,262-266`) | run end | yes |
| achievement reward | `rewardOf` single path (`AchievementEvaluator.java:178-180`) | on unlock | yes |
| achievement unlock date | the timestamp stored on the `AchievementRecord` at creation (`AchievementEvaluator.java:38`) | at unlock | **see section 3** |
| milestone progress | `progression/CollectionProgress.java:313` gates on `content.has(GameContent.UPGRADES)`; per-item progress tallied there | purchase / collection events | yes |
| milestone reward | reward path `UNVERIFIED` (assume single `RewardDef` like achievements until proven otherwise) | on milestone | yes |
| collection counts | `CollectionProgress` (`CollectionProgress.java:313`) based on owned upgrades/birds | purchases | yes |

## 3. Display-only risk findings

- **Achievement unlock DATE — persisted, not rendered from a clock.** The
  evaluator receives the timestamp as an injected argument and stores it on the
  `AchievementRecord` (`AchievementEvaluator.java:38-39`, "with the injected
  timestamp"). The evaluator itself never reads a clock (`AchievementEvaluator.java:51`).
  Whether the save schema round-trips that field is the one open point:
  `UNVERIFIED` (profile JSON shape). The Goals screen must render the date from
  that record field, never from `System.currentTimeMillis`.
- The only remaining display-only risk: a counter with no milestone/achievement
  backing would show zero forever — `AchievementEvaluator.java:46-48` already
  forces `0 / target` when no lifetime best exists, so bars cannot invent value.
- `UNVERIFIED`: challenge completion flags and milestone reward wiring read
  paths; confirm in the save-schema pass.

## 4. Duplicate reward paths

Achievements pay through **one** path: `AchievementEvaluator.rewardOf`
(`AchievementEvaluator.java:178-180`) returns a single `RewardDef` per id and
`AchievementEvaluator.java:38-39` pays it through the wallet exactly once (counted
as `coinsEarned`, E32.a). No second grant path found in the evaluator.
Challenges/milestones: `UNVERIFIED` on this pass — flag for the economy audit.

## 5. Purity check (Goals read path)

Grep of `src/main/java/io/github/michelbr84/flapforge/gameplay/` and
`progression/` for `currentTimeMillis|nanoTime|Math.random|java.awt|new Random|
Thread.|Executors.` found **no violations**: the only RNG constructions are
seeded named streams — `Simulation.java:162`, `harness/BotPilot.java:151`,
`DailyChallenge.java:184` — all `new RandomProvider(seed).stream(...)`. No clock
reads, no `java.awt` in gameplay or progression. Evaluator purity is stated in
`AchievementEvaluator.java:51`.