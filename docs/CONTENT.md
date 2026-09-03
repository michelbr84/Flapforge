# Content

Everything Flapforge can do is described by JSON in `src/main/resources/data/`. The code owns
behaviour; the data owns which birds exist, what an upgrade costs, which world a challenge runs
in and what unlocks what. This document is the reference for the files, the id namespaces, the
rules the validator enforces and what to do when it says no.

The decisions behind it are D10 (data-driven content), D13 (meta-progression and unlock graph),
D25 (strings) and errata E3, E5, E19, E20, E21 and E31.f of the implementation plan.

The one rule everything else follows from: **content that binds is content that has been
proved consistent.** There is no lenient path — a typo is an error with a JSON pointer, never a
silently ineffective effect.

---

## 1. The files

| File | Holds | Complete since |
| --- | --- | --- |
| `birds.json` | the seven birds: base stats, effects, ramp and synergy effects, palettes, passive slots, unlocks | M4 |
| `difficulty.json` | difficulty curves, the speed ramp rate and the three tiers | M1 |
| `economy.json` | currencies, run rewards, XP curve and level rewards, features, the daily block, the prestige block | M3 |
| `upgrades.json` | the three upgrade trees and their eighteen nodes | M4 |
| `aliases.json` | the id reconciliation table applied to old saves (E21) | M4 (empty) |
| `abilities.json` | the eight abilities: kind, tags, level costs, unlocks | stub in M4, completed in M5 |
| `modifiers.json` | the draft: offer schedule, offer width, rarity weights, the seventeen modifiers and the four synergies | M6 |
| `worlds.json` | the five worlds: order, curve, palette, ambience, spawn weights, patterns, rule cycles, boss and boss reward | stub in M4, completed in M7 (music in M8) |
| `patterns.json` | the twenty-one obstacle set pieces: world patterns, boss phases and the corridors of `boss_corridor_1` | M7 |
| `challenges.json` | the seven challenges: world, rules, objective, rewards, unlocks | stub in M4, completed in M8 |
| `achievements.json` | the forty-one achievements: condition, reward | stub in M4, completed in M8 |
| `strings/en.json`, `strings/pt_BR.json` | every display string, including `<kind>.<id>.name` and `.desc` | M2 |

A "stub" file carries its **final unlock and reward blocks** and leaves the behaviour-dependent
fields empty (E19). That is deliberate: those blocks are edges of the unlock graph, so the graph
— and therefore the promise that progression never dead-ends — can be proved a milestone before
the systems that read the rest of the file exist. Every file states in its own `_comment` key
which fields land in which milestone.

Shipped JSON carries no comments. Any key whose name starts with `_comment` is ignored by the
binder, which is where those notes live.

`ContentLoader.FILES` is the set the game loads. `GameContent.has(name)` reports whether a file
was supplied, and cross-reference rules that point into a file that was not supplied are skipped
— that is how M1's `birds.json` + `difficulty.json` fixture still validates on its own while the
shipped set is checked in full.

`GameContent.playable(kind)` is the other half: content can be authored, validated and visible
before its system exists. Abilities became playable in M5, worlds in M7, challenges in M8 (with
`BossEncounter` and `ObjectiveEvaluator`); achievements do with the M8 evaluator. One kind
answers per id: `playable(FEATURE, id)` is false while
`GameContent.featureMilestone(id)` names a milestone — `modifiers` named M6 until the draft overlay shipped and is live now, `seeded_runs`
names M9. A feature is *buyable* before that, which is what the plan asks for, so the shop labels
it with the milestone rather than presenting a switch that does nothing. The UI asks these to show
content as locked by milestone rather than offering something that cannot happen yet.

---

## 2. Id namespaces

Ids are lower snake case: `^[a-z][a-z0-9_]*$`. An **unlockable id** is namespaced, and that is
what `profile.unlocked` holds:

| Namespace | Example | Comes from |
| --- | --- | --- |
| `bird:` | `bird:guardian` | `birds.json` |
| `cosmetic:<bird>:<palette>` | `cosmetic:classic:ember` | a palette inside a bird |
| `ability:` | `ability:shield` | `abilities.json` |
| `tree:` | `tree:forge` | `upgrades.json` `trees[]` |
| `tier:` | `tier:hard` | `difficulty.json` `tiers[]` |
| `world:` | `world:void` | `worlds.json` |
| `challenge:` | `challenge:no_shield_1` | `challenges.json` |
| `feature:` | `feature:modifiers` | `economy.json` `features[]` |
| `modifier:` | `modifier:gold_rush` | `modifiers.json` (M6) |

Two things that look like unlockables and are not:

* **upgrade nodes** (E21). Ownership lives in `profile.upgrades` as `<node id> → level`, and
  `tree:<id>` is what has to be unlocked to buy into a tree. `profile.unlocked` never contains an
  `upgrade:` id.
* **achievements**. They are recorded in `profile.achievements`. `achievement:<id>` exists as a
  node of the unlock graph so that conditions can require one and rewards can hang off one, but
  nothing has to be able to "reach" an achievement.

`ContentKind` maps a kind to both its namespace and the prefix of its display strings.

---

## 3. How to add things

### A bird

1. Append an entry to `birds.json`. `hitbox` is `{w: 33, h: 31, ox: -17, oy: -12}` for every
   shipped bird; `baseStats` only needs the stats that differ from `StatId`'s defaults.
2. Give it at least a `default` palette and — per E20 — a `prestige` palette with
   `{"type": "prestige", "value": 1}`.
3. Give it an `unlock`. If it is not a cosmetic it needs a **cumulative** way in (see §5), so
   `any_of[<something skilful>, {"type": "purchase", "amount": N}]` is the shape to copy.
4. Add `bird.<id>.name` / `.desc` and `cosmetic.<id>.<palette>.name` / `.desc` to
   `strings/en.json` **and** `strings/pt_BR.json`.
5. `./gradlew contentCheck`.

### An upgrade node

1. Append to `upgrades.json` `nodes[]`. `costs` must have exactly `maxLevel` entries.
2. `prereqs` are node ids **in the same tree and in a lower tier**.
3. `effectsPerLevel` scales per D8: `FLAT_ADD` and `PERCENT_ADD` scale linearly with the level,
   `MULTIPLY` compounds (`value^level`). `levelOverrides` replaces the computed effects of one
   level when a node needs a hand-authored step.
4. `grants` are `{type: UNLOCK|ABILITY_CAP|PASSIVE_SLOT, id?, amount?}` (E31.f). Only `UNLOCK` is
   an unlock-graph edge. The E3 budget is fixed: the ability level cap is `2 + Σ ability_cap`, and
   it may not exceed the number of levels every ability ships (3), so exactly one `ability_cap: 1`
   grant exists, on `forge/master_forge_1`. Passive slots are `bird.passiveSlots + Σ passive_slot`
   and may not exceed 4, so exactly one `passive_slot: 1` grant exists, on
   `economy/ability_scholar_1`.
5. Add `upgrade.<id>.name` / `.desc` to both string files.
6. A node with **no** `effectsPerLevel` earns its price entirely from its grants, so it stops
   being buyable once those grants are owned: `UpgradeManager.isRedundant` refuses it and the
   screen badges it *Already unlocked*. `hard_tier_1` is the shipped case and is a deliberate
   early-access shortcut, not an oversight — `tier:hard` is also free at 40 gates in one run or
   400 across the profile (`difficulty.json`), so the node is worth its 400 coins only to a player
   who wants Hard before reaching either, and the unlock graph asserts the free path is the
   cheaper one. Anything a node grants that no system reads yet must carry its milestone in the UI
   (E19); see `docs/PROGRESSION.md` §6.

### An ability

An ability is two halves: **data** in `abilities.json` and, only when the stat pipeline cannot
express what it does, a **behaviour** in `ability/behaviors`. The data half needs no code at all —
`coin_magnet` is nothing but `MAGNET_RADIUS +90` — and the behaviour half exists for what a stat
cannot say: setting the bird's velocity, holding its line, configuring the shield or revive system,
or cancelling a lethal hit (D9, E24).

1. **Append the entry** to `abilities.json`:

   ```json
   { "id": "tailwind", "kind": "ACTIVE", "behavior": "tailwind", "tags": ["MOVEMENT"],
     "levels": [
       { "cooldownTicks": 600, "durationTicks": 40, "params": { "lift": 0.5 }, "cost": 0 },
       { "cooldownTicks": 500, "durationTicks": 50, "params": { "lift": 0.7 }, "cost": 400 },
       { "cooldownTicks": 400, "durationTicks": 60, "params": { "lift": 0.9 }, "cost": 800 } ],
     "effects": [ { "stat": "GRAVITY", "op": "MULTIPLY", "value": 0.5 } ],
     "unlock": { "type": "any_of", "conditions": [
       { "type": "best_gates", "value": 15 },
       { "type": "purchase", "amount": 200 } ] } }
   ```

   `kind` is `ACTIVE` (one slot, activated with the `ABILITY` action) or `PASSIVE` (a slot each,
   always on). `tags` drive the rule flags: `DEFENSIVE` is stripped by `NO_DEFENSIVE_ABILITIES`
   and `REVIVE` by `NO_REVIVE`, innate ones included.

2. **`effects` is the stat half.** A passive contributes them for the whole run; an active only
   while its duration runs — which is what makes the dash a burst instead of a permanent stat
   change. An active that declares `effects` therefore needs a non-zero `durationTicks` at every
   level, and the validator says so.

3. **`levels` is exactly three** (E3: the cap is `2 + Σ ability_cap grants` and may not exceed the
   levels the thinnest ability ships). Level 1 comes with the unlock and **must cost 0**; levels 2
   and 3 are bought in the shop, each costing more than the level below it. Cooldowns may never
   grow with the level and durations may never shrink. An `ACTIVE` needs at least one gate — a
   cooldown, a duration or `charges` — or it could be pressed every tick for free; a `PASSIVE`
   must declare `cooldownTicks: 0` and `durationTicks: 0`.

4. **`behavior` must be a registered id.** If the ability is pure data, reuse an existing
   behaviour; otherwise implement `AbilityBehavior`, register it in `BehaviorRegistry.shipped()`
   and declare what it reads:

   ```java
   public final class TailwindBehavior implements AbilityBehavior {
       public static final String LIFT = "lift";
       public static final List<ParamSpec> PARAMS = List.of(ParamSpec.up(LIFT, 0, 1));

       @Override public void onActivate(AbilityContext ctx) { … }
   }
   ```

   `ParamSpec.up` / `down` / `free` declare a key's range and how it may move between levels (up
   for a magnitude that improves, down for one that shrinks, free for a cadence or a switch); the
   canonical constructor takes a `required` flag for a key a level may leave out. The validator
   checks `params` against the specs in both directions: a key the behaviour does not read is an
   error
   (a typo cannot become a silently ignored default), a required key missing from a level is an
   error, a value outside the range is an error, and a column moving against its trend is an
   error.

5. **Know which hooks you may write the bird from.** `onActivate`, `onTick` and `onLethalHit` may
   set the bird's velocity and position directly; `onEquip`, `onFlap` and `onCoinNear` may not.
   `canActivate` is a question the manager asks *before* spending a charge — return `false` and
   the press costs nothing and reaches the HUD's refusal beat. Declare `holdsBird()` when the
   behaviour pins the bird's y while it runs (the simulation then refuses flaps it would undo)
   and `routesCoins()` when you implement `onCoinNear` (nothing walks the coins otherwise). A
   behaviour is created per equipped ability per run, so per-run state may live in its fields — but
   it must never keep static state, read a clock or draw from an unseeded source (D12).

6. **Add the strings** `ability.<id>.name` / `.desc` to `strings/en.json` **and**
   `strings/pt_BR.json`. A per-level parameter that the ability panel should spell out also needs
   an `ability.param.<key>` line; `ProgressionText` maps the key to it.

7. **Give it an unlock with a cumulative path** (§5), like any other non-cosmetic unlockable.

8. `./gradlew contentCheck`, then measure it:
   `./gradlew balancing -PtoolArgs="--seeds 200 --skill all --ability <id>"`.
   `AbilityBotRunTest` compares every ability's mean gates against the ability-free baseline, so an
   ability that costs the bot survival fails the build rather than shipping as a trap
   (`docs/BALANCING.md` §7.2).

### A modifier

A modifier is a card a mid-run draft can offer (D11, D27). It is **pure data**: `ModifierDirector`
pushes its `effects` into the `MODIFIERS` stat layer and its `flags` into the run's rules, so a new
card needs no code at all unless it wants a stat that does not exist yet.

1. **Append the entry** to `modifiers.json`:

   ```json
   { "id": "updraft", "rarity": "RARE", "tags": ["TEMPO", "PRECISION"], "maxStacks": 2,
     "excludes": ["stormrider"], "requiresFlagsAbsent": ["NO_COINS"],
     "effects": [ { "stat": "GRAVITY", "op": "MULTIPLY", "value": 0.94 } ],
     "flags": [], "streakBonus": { "coins": 10 },
     "unlock": { "type": "default" } }
   ```

2. **`rarity` is how often it is seen, not how strong it is** — but the two have to agree, because
   `rarityWeights` (60/28/10/2) makes a COMMON thirty times as likely as a LEGENDARY. Measure the
   card before choosing (step 8); a RARE that measures like an EPIC is a draft with one correct
   answer in it.

3. **`tags` are the only input of the synergies block.** Each taken entry contributes its tags
   once, however many stacks it holds, and a set bonus needs two *distinct* entries (E16) — so a
   card carrying both tags of a synergy still cannot complete it alone. A card with no tag is an
   error: it could never feed one.

4. **`maxStacks` caps the whole run**, not the draft. The pool stops offering a card at its cap,
   and a challenge that forces the same id more often than that is a validator error.

5. **`excludes` is symmetric and enforced in both directions**: holding either half drops the
   other from the pool, and one draft never shows both halves of an exclusion.

6. **Eligibility has two halves (E12).** `requiresFlagsAbsent` is the authored half — list the
   rule flags that would make the card a lie. The derived half is computed from the effects and
   needs no data: a card whose whole effect list is a no-op in this run is dropped anyway
   (`SHIELD_CHARGES` under `NO_DEFENSIVE_ABILITIES`, `REVIVES` under `NO_REVIVE`, the coin stats
   under `NO_COINS`, and the two ability-timing stats when nothing equipped declares a cooldown or
   a duration). Author the flag only when the derivation cannot see it — `phoenix` pays coins as
   well as reviving, so `NO_REVIVE` has to be written down.

7. **What a card may not do.** It may not grant `SPEED_RAMP` or `ALL_OBSTACLES_MOVE` (the
   difficulty layer reads those at run start and never again), and it may not touch
   `MOVING_CHANCE`, which the spawn decision reads — a drafted change to it would make the
   obstacle sequence depend on what the player picked, and E32.d requires that sequence to be a
   function of the seed alone. A card that pays a `streakBonus` must list `NO_COINS`. A card with
   no effect, no flag and no streak bonus is an error.

8. **Add the strings** `modifier.<id>.name` / `.desc` to `strings/en.json` **and**
   `strings/pt_BR.json`; the overlay, the HUD chips and the run summary all read them through
   `ProgressionText`. Then `./gradlew contentCheck`, and measure it:

   ```
   ./gradlew balancing -PtoolArgs="--seeds 200 --skill all --modifier <id>"
   ./gradlew balancing -PtoolArgs="--seeds 200 --skill all --modifier <id> --modifier-stacks 2"
   ```

   The sweep forces the card on every run of a cell and prints the payout delta against the same
   seeds without it, per skill preset. `docs/BALANCING.md` §8.2 is that table for the shipped
   cards; add the row rather than guessing, because three of the seventeen shipped cards measured
   at exactly 0.0 % before they were fixed.

9. **Give it an unlock.** `{"type": "default"}` puts it in every profile's pool; anything else is
   a `modifier:<id>` unlockable and needs a cumulative path (§5) like any other non-cosmetic id.

### A synergy

A synergy is a set bonus over the tags of the cards a run holds (D27).

```json
{ "id": "slipstream", "requiresTags": ["TEMPO", "PRECISION"],
  "effects": [ { "stat": "HITBOX_SCALE", "op": "FLAT_ADD", "value": -0.05 } ], "flags": [] }
```

- `requiresTags` is a **multiset**: `["ECONOMY", "ECONOMY"]` means two ECONOMY contributions, and
  they must come from at least two distinct taken entries (E16). Fewer than two tags is an error,
  because one tag can never be split across two entries.
- The resolver recomputes the active set every time the build changes — a draft pick, the forced
  cards of a challenge — and pushes the effects into the `MOD_SYNERGY` layer. Deactivation works
  the same way, so a bonus is not permanent if the build stops covering it.
- The same rules as a card apply to what it may grant: no `SPEED_RAMP` / `ALL_OBSTACLES_MOVE`, no
  `MOVING_CHANCE`, and no synergy id may collide with a modifier id (they share the string
  tables).
- The validator **warns** when no legal build of the shipped cards can satisfy the tags — it
  searches subsets honouring `excludes` — rather than failing, because a card added later can make
  it reachable again.
- Add `synergy.<id>.name` / `.desc` to both string tables: the HUD lists the bonus, the draft
  overlay promises it on the card that would complete it, the toast announces it and the run
  summary lists it.

### A palette

Append to the bird's `palettes[]` with four `#RRGGBB` colours and an unlock. Cosmetics are the
only place `{"type": "prestige"}` and `{"type": "counter", "counter": …}` are allowed (E20), and
the only unlockables exempt from the cumulative-path rule: a trophy palette is allowed to require
the boss that pays it.

### A world (M7)

One entry of `worlds.json`. Every field:

| Field | Meaning |
| --- | --- |
| `id`, `order` | the id (`world:<id>` in the unlock graph) and the position in the picker |
| `curve` | a curve of `difficulty.json` (`classic` for Green Fields, `standard` elsewhere; D20) |
| `style` | the parallax style the renderer draws: `hills`, `canyon`, `factory`, `storm`, `void` (`WorldStyle`) |
| `unlock` | the unlock condition; every world but the first is `any_of[world_cleared <previous>, purchase N]` |
| `palette` | seven `#RRGGBB` colours: `skyTop`, `skyBottom`, `ground`, `pipe`, `accent`, `fog`, `letterbox` |
| `effects`, `flags` | the `WORLD` layer and the world's rule flags for every run played there |
| `spawnWeights` | the spawn table: weight per obstacle kind (`pipe_gate`, `gear`, `piston`, `wind_zone`, `lightning`); at least one positive weight |
| `patterns` | the ids of the set pieces the world draws (each must belong to the world and have a positive weight) |
| `ambient` | `darkness` in `[0, 1]` (what the renderer hides around the bird — Storm Sky alone is dark), `windX` in `[-60, 60]` px/s (a permanent change of the relative scroll: Wind Valley's −20 pushes the bird back), `windY` in `[-900, 900]` px/s² (a permanent vertical acceleration), `lightningEveryGates` (a cosmetic sky flash every N gates, E8: no hitbox) |
| `ruleCycles` | `null`, or `{everyGates, telegraphTicks, options[]}` with each option `{flags[], effects[]}`; every `everyGates` gates the next option is drawn (never the same twice in a row) and lands `telegraphTicks` flying ticks later, replacing the previous option's flags and effects; at least two non-empty options, none touching `MOVING_CHANCE` |
| `boss` | `atGate`, `warningTicks`, `patterns[]` (weight-0 phases of `patterns.json`, ≥ 480 px each), `surviveTicks`, `reward {coins, unlocks[]}` — the reward is an edge of the unlock graph (M8 plays it) |
| `music`, `sfxSet` | `music` is M8's; `sfxSet` is one of `FIELDS, CANYON, FACTORY, STORM, VOID` and flavours every sound cue (E31.g) |

**How the spawn table plays.** The first column of a run is always a static standard gate
(upstream's rule). Afterwards the kind is drawn from `spawnWeights` (the `spawn` stream), the
moving flag or a gear's rail against the resolved `MOVING_CHANCE` (`spawn`), and the geometry from
the `obstacle` stream — gates as upstream, gears with a radius in `[24, 56]` and a sweep centre
clear of the edges, pistons `[120, 300]` px long from either edge with a random phase offset, wind
zones `[100, 200]` × `[200, 320]` px in the middle band pushing ∓500 px/s² or ∓40 px/s, bolts
from either edge lighting 35–65 % of the height. A spawned lightning column is always reachable
from the column before it: its side is the one whose unlit band is nearer that column's band and
its lit fraction is shortened until the travel fits 80 px (`SpawnTable.LIGHTNING_MAX_TRAVEL_PX`),
and it warns from 75 ticks of scroll out. The cursor measures the 160 px interval from where a
pipe body's right edge would be, so a 112 px gear or a 200 px zone pushes the next column out by
its extra width. `ALL_OBSTACLES_MOVE` is applied when a decision becomes an obstacle, per kind: a
gate oscillates, a gear rides the default rail, a piston's telegraph drops to 25 ticks, bolts and
zones are unchanged.

**Scoring rule.** Every lethal column — gate, gear, piston, lightning — scores one gate when the
bird's hitbox left edge passes its right edge (`Obstacle.scoreLineX`), so the gate-keyed curve
advances in a gear-heavy world; a wind zone never scores. The coin trail (E2) is laid along each
scoring column's safe band (`Obstacle.safeBandY`: gap centre, a gear's larger free side, a
piston's free side, a bolt's unlit side).

### A pattern (M7)

One entry of `patterns.json`: `{id, world, weight, minGate, scoringSteps, steps[]}`. A world
pattern has `weight > 0` and is listed by its world; between chunks the streamer starts one at a
free spawn with probability `Σ eligible weights / (Σ weights + 100)` among the patterns whose
`minGate ≤ gatesPassed`, then picks one weighted; one plain spawn separates two chunks. A boss
phase or a challenge's `forcedPattern` has `weight 0` and is streamed on demand (a forced pattern
loops from the first spawn). `scoringSteps: false`, or `"scoring": false` on a step, spawns a
column that awards no gate and gets no coins (E14: a challenge with a boss needs a scoring forced
pattern).

Each step is `{dx, kind, params, scoring?}`: the column lands `dx` px after the previous column's
left edge (`dx ≥ 100`), and `params` follow the kind's `ParamSpec` (`ObstacleParams`; fractions
are of the 598 px playable height):

| Kind | Params | Ranges |
| --- | --- | --- |
| `pipe_gate` | `layout` (`STANDARD`/`FLOATING`), `gapCenter` (`0..1` or `"random"`), `gapSize`, `oscillate`, `amplitude`, `speed` | `gapSize` is a base value the run scales by its gap multiplier (tier ×0.9/×0.8, the curve's ramp, a cycle option, a card) and keeps centred; `"random"` draws upstream's geometry from the `obstacle` stream; `speed` 0 uses the `OSCILLATION_SPEED` stat |
| `gear` | `cy`, `radius`, `rail {amplitude, speed}` | `radius` 24–56; the rail is a triangle wave of `amplitude` px at `speed` px/s of world time |
| `piston` | `side` (`TOP`/`BOTTOM`), `length`, `telegraphTicks`, `extendTicks`, `holdTicks`, `retractTicks`, `phaseOffset` | `length` 80–360, `telegraphTicks ≥ 15`; defaults 40/12/30/20 |
| `wind_zone` | `width`, `cy`, `height`, `accelY`, `scrollDelta` | `width` 60–240, `accelY` −900..900 px/s² (negative lifts), `scrollDelta` −60..60 px/s (positive scrolls faster) |
| `lightning` | `side`, `lengthFrac`, `warningTicks`, `strikeTicks` | `lengthFrac` 0.3–0.7 (a safe band always exists), `warningTicks ≥ 30`, `strikeTicks` 6–16 |

**Feasibility rules** (the validator, §4): `dx ≥ 100`; a gate's `gapSize × (tightest tier
multiplier) × 0.9 ≥ 54.5`, i.e. `gapSize ≥ 76` with the nightmare tier's ×0.8; `telegraphTicks ≥
15`; `lengthFrac ≤ 0.7`; a gate right after a bolt has an authored `gapCenter` on the bolt's unlit
side (`≤ 0.5` after a `BOTTOM` bolt, `≥ 0.5` after a `TOP` one, never `"random"`); a bolt's safe
band is no further from the previous lethal column's band than the scroll between them
(`dx − that column's width − 5` px: the bird climbs about one px per px of scroll at the fastest
tier scroll); a boss phase spans `≥ 480` px.

**Authoring and testing.** Write the steps, run `./gradlew contentCheck`, then measure:

```bash
./gradlew balancing -PtoolArgs="--pattern forge_gear_corridor --skill expert --seeds 50 --ticks 2400"
./gradlew balancing -PtoolArgs="--pattern all --skill expert --seeds 50 --ticks 2400"
./gradlew balancing -PtoolArgs="--world iron_forge --tier all --skill expert --seeds 50"
```

`--pattern` loops the pattern from the first spawn in the pattern's own world (its curve, effects
and ambience) unless `--world` pins another; `ContentFeasibilityTest` (`simTest`) asserts the
expert survives 2 400 ticks of every pattern in ≥ 30 % of 50 seeds and reaches `boss.atGate` in
every world × tier at the same rate, and `docs/BALANCING.md` §10 records the table.

### A challenge (M8)

One entry of `challenges.json`. Every field:

| Field | Meaning |
| --- | --- |
| `id` | the id (`challenge:<id>` in the unlock graph, recorded in `profile.challenges`) |
| `world`, `tier`, `curve` | where and how hard the run plays; the tier and curve override the world's own. **E6: the world is a place, never a requirement** — three shipped challenges play in worlds a fresh profile does not own, and the screen labels the world |
| `allowOffers` | whether modifier drafts are offered during the run (the E7 boss guard sits on top of this) |
| `flags`, `effects` | the rule flags and the `CHALLENGE` layer stat modifiers the run starts under |
| `forcedModifiers` | modifier ids the run starts with, taken one at a time under the authored rules; the validator rejects an id that resolves to nothing, more copies than a card's `maxStacks`, two cards that exclude each other, and a card whose `requiresFlagsAbsent` names a flag the challenge itself turns on (a broken list would silently lose a card at run start) |
| `forcedPattern` | the only pattern the run streams, looped from the first spawn, or absent (M7) |
| `boss` | the challenge's *own* boss block (same shape as a world's), or absent — a challenge never fights its world's boss (E26), so a boss here only sets `objectiveMet` and pays nothing on the boss terms |
| `objective` | `{type, value}`: `SURVIVE_GATES` (pass N gates), `SURVIVE_TICKS` (fly N ticks), `COLLECT_COINS` (take N coins), `REACH_POINTS` (score N points) or `BOSS_CLEARED` (`value` is `1`); judged every tick, latched once, and the run continues after it is met |
| `rewards` | `{coins, unlocks[]}` — what the **first** completion pays (E11); every completion after that pays only the economy's `challengeBonus` |
| `unlock` | the condition that opens `challenge:<id>` |

**Validator rules a new challenge must satisfy:** known `world`/`tier`/`curve` ids; a
`BOSS_CLEARED` objective needs a `boss` block on the challenge (E26); a boss whose `atGate` is
beyond a `SURVIVE_GATES` objective is rejected (E14 — the run would be won before reaching it);
a challenge with both a boss and a `forcedPattern` needs a scoring pattern (`scoringSteps` true
and at least one scoring step, E14, otherwise `atGate` is unreachable); the rule-contradiction
checks of §4 apply to the challenge's flags and effects.

**Authoring and testing.** Write the entry, run `./gradlew contentCheck`, then measure:

```bash
./gradlew balancing -PtoolArgs="--challenge my_challenge_1 --skill expert --seeds 50"
./gradlew balancing -PtoolArgs="--challenge all --skill all --seeds 50"
```

`--challenge <id|all>` plays the challenge as the player would — its world, tier, curve, flags,
effects, forced cards, forced pattern and boss — and prints the objective and boss clear rates,
the phases reached and the deaths by kind. `ContentFeasibilityTest` (`simTest`) asserts every
shipped challenge's objective and every world boss at ≥ 30 % expert success, and
`docs/BALANCING.md` §11 records the table. The strings (`challenge.<id>.name`/`.desc`) go into
both `strings/en.json` and `strings/pt_BR.json`.

### An achievement (M8)

One entry of `achievements.json`: `{id, hidden, condition, reward}`. The `condition` is
`{counter, scope, op, value}`:

| Scope | The counter names | Read through |
| --- | --- | --- |
| `LIFETIME` | a `StatisticKey` field (`totalRuns`), one entry of a map counter (`bossClears.void`, `bestGatesByTier.hard`), the size of a list (`bossesCleared`), or a profile-root scalar (`level`, `xp`, `prestigeCount`; E5) | `Statistics.resolve(profile, counter)` |
| `RUN` | exactly one of `run.gatesPassed`, `run.points`, `run.streakBest`, `run.coinsCollected` | the run that just finished, only ever judged inside `ProgressionManager.apply` — the purchase pass has no run and never grants a `RUN` achievement |
| `COLLECTION` | `collection.<category>.percent` with category one of `birds, abilities, worlds, challenges, cosmetics, achievements, upgrades, all` | `CollectionProgress` |

`op` is one of `GTE`, `LTE`, `EQ` (compare ops). An achievement fires once — an already-held id
is skipped — records `achievements.<id>` with the timestamp, pays its `reward` coins through the
wallet, and hands `reward.unlocks` to the unlock step. `hidden: true` changes nothing in the
evaluation; only the display is withheld (a `???` row and no milestone bar) until it fires. The
validator checks the counter name against its scope (including every `LIFETIME` map key and
every collection category) and every reward unlock id.

**How to add one.** Append the entry, add the two `achievement.<id>.name`/`.desc` strings to
both string files, run `./gradlew contentCheck`; `AchievementEvaluatorTest` is the place for a
new shape of condition, and a threshold achievable in one run belongs in
`ProgressionManagerTest`'s purchase-pass assertions (E17: a purchase can fire achievements).

### A music block (M8)

The `music` object of a `worlds.json` world (a world without one is silent, and the menu always
plays Green Fields' loop): `{tempo, scale, seed, layers}`.

| Field | Meaning |
| --- | --- |
| `tempo` | beats per minute, `60..200` (`MusicDef.MIN_TEMPO`/`MAX_TEMPO` — the render cost is bounded by it) |
| `scale` | one of `major_pent`, `minor_pent`, `dorian`, `phrygian`, `whole_tone` — the semitone vocabulary both the validator and `MusicSequencer` read from `MusicDef.SCALES`, so the two can never disagree |
| `seed` | the sequencer seed; each layer draws from its own stream derived from it, so a world always sounds like itself and two renders are byte-identical |
| `layers` | any subset of `bass`, `lead`, `arp`, `pad`, `drums`, in `MusicDef.LAYERS` order |

The plan's §4 example pins only the *shape* of the block; the per-world values are a free
choice, and the shipped Green Fields block (`seed` 11, `layers` `bass`/`lead`/`drums`)
deliberately differs from that example's illustration (`seed` 1, `layers` `bass`/`lead` — same
`tempo` 112 and `scale` `major_pent`). The other four worlds are not pinned by the plan at all.

The sequencer renders a deterministic 8-bar loop (10–30 ms per world, budget 150 ms,
`MusicSequencerTest` prints and checks it); during a boss the same block is re-rendered at
×1.15 tempo (capped at 170 BPM) and the mixer crossfades on `BossStarted`/`BossCleared`. How to
add or retune one: edit the block, run `./gradlew contentCheck` (tempo bounds, scale and layer
names, seed presence), then `./gradlew test --tests '*MusicSequencerTest*'` to hear it through
`CaptureAudioBackend` and re-check the render budget.

### A font asset (M8)

`assets/manifest.json` is the drop-in path of D18: ids resolve only through the manifest, never
by scanning directories. The one shipped entry is the UI font:

```json
{
  "id": "font/ui",
  "path": "assets/fonts/Nunito-VariableFont_wght.ttf",
  "kind": "font",
  "license": "OFL-1.1",
  "source": "https://github.com/google/fonts/tree/main/ofl/nunito",
  "licenseFile": "assets/fonts/OFL.txt"
}
```

`AssetManager.font("font/ui")` decodes it with `Font.createFont(TRUETYPE_FONT, …)` and
`BootSequence`'s first step installs it through `Fonts.install` — lazily, never a static
initialiser (E10). A missing entry, a missing file or a bad font leaves the logical `SansSerif`
in place, so the game still runs. To swap the font: drop the file under `assets/fonts/`, point
the entry's `path` at it, keep `kind: "font"`, fill `license`/`source`/`licenseFile`, record the
licence in `THIRD_PARTY_NOTICES.md`, and run `./gradlew assetValidator` — it re-checks every
entry's path, kind and licence file. Bold is a derived style on the single face, so a
variable font's default instance is what ships.

The plan's §4 example wrote `Nunito-Regular.ttf` with `licenseFile` `assets/fonts/LICENSE`; the
shipped entry is the same family and licence as the variable face `Nunito-VariableFont_wght.ttf`
with `OFL.txt` — the plan's M8 file list says `assets/fonts/*` without pinning the face, so the
choice of file names is free as long as the entry's shape (`id`, `path`, `kind`, `license`,
`source`, `licenseFile`) stays.

---

## 4. What the validator checks

`ContentValidator.errorsOf(content)` collects every problem, each with a `file#/json/pointer`
location, and `GameContent.fromJson` raises them together as one `ContentException`.

**Shape (StrictBinder).** A key that matches no record component is an error. An enum string that
is not a constant is an error listing the valid ones. A fractional value in an integer component
is an error. Missing lists and maps become empty; missing objects become `null`, which is how an
optional block is expressed.

**Ids.** Unique per kind, matching the id regex, in every file. Palettes are unique per bird.

**Silhouettes.** `BirdDef.shape` must be one of `BirdDef.SHAPES` — `balanced`, `swift`, `heavy`,
`guardian`, `gambler`, `mystic`, `forge`. `ProceduralArt.drawBirdPortrait` falls through to the
balanced silhouette for anything else, so an unchecked typo would ship the wrong bird art in
silence, which is the class of failure D10 exists to prevent.

**Cross-references.** Bird passive abilities and palette unlock ids; upgrade `tree`, `prereqs`
and `UNLOCK` grants; challenge `world`, `tier`, `curve`, `forcedModifiers` and rewards; world
`curve` and `boss.reward`; modifier `excludes`; achievement counters; level-reward unlocks; the
daily tier pool; every namespaced id in every reward. A reference into a file that was not
supplied is skipped (§1).

**Patterns (M7).** Every pattern id a world lists, a boss names or a challenge forces resolves. A
pattern a world lists belongs to that world and has a positive weight (else the world could never
draw it); a boss phase or a forced pattern has weight 0 (else the world would also draw it at
random) and a boss phase spans at least 480 px. A weighted pattern its world does not list is an
error; a weight-0 pattern nothing names is a warning. Every step's `params` are checked against
the kind's `ObstacleParams` contract — unknown keys, wrong shapes and out-of-range values are
reported with the step's pointer — and the feasibility rules of the plan hold: `dx ≥ 100` between
columns, a gate's `gapSize × (the tightest tier's GAP_SIZE multiplier) × 0.9 ≥ 54.5` (the
nightmare tier's 0.8 makes that `gapSize ≥ 76`; the run scales an authored gap by the same
multiplier, so the rule describes what the tier plays), a piston's `telegraphTicks ≥ 15`, a bolt's
`lengthFrac ≤ 0.7`, a gate right after a bolt authored on the bolt's unlit side (never `"random"`),
and a bolt's safe band within reach of the previous lethal column's band — no further than the
scroll between them (`dx − width − 5` px). E14: a challenge with a boss and a forced pattern needs
that pattern to score (`scoringSteps` true and a scoring step), or `boss.atGate` is unreachable.

**Bosses (M8).** Every boss block — a world's or a challenge's — has `warningTicks ≥ 60` (one
second of banner and empty sky) and `surviveTicks ≥ 300` (about one loop of a 480 px phase at
the classic scroll); shorter values are errors. A `BOSS_CLEARED` objective needs a `boss` block on
the challenge itself, because a challenge never fights its world's boss (E26). A boss whose
`atGate` lies beyond a `SURVIVE_GATES` objective is rejected (E14): the run would be won before the
fight; `atGate` at or below the objective value is fine, and `COLLECT_COINS` / `REACH_POINTS`
objectives put no bound on it. No rule anywhere asks for a challenge's `world` to be unlocked
(E6): three shipped challenges play in worlds a fresh profile does not own.

**Worlds (M7).** The ambient wind takes a wind zone's ranges (`windX` in `[-60, 60]` px/s,
`windY` in `[-900, 900]` px/s²), the spawn table has a positive weight, and a `ruleCycles` block
has at least two options, each turning on a flag or applying an effect, none touching
`MOVING_CHANCE` (E32.d) and none carrying the contradictions a challenge is refused.

**Counters (E5).** A `LIFETIME` counter is a `StatisticKey` field, a `<mapField>.<key>` entry of
one (`bossClears.void`, `bestGatesByTier.hard`) or a profile-root scalar (`level`, `xp`,
`prestigeCount`). A `RUN` counter is one of `run.gatesPassed`, `run.points`, `run.streakBest`,
`run.coinsCollected`. A `COLLECTION` counter is `collection.<category>.percent`.

**Costs and levels.** `costs.length == maxLevel`. Ability level 1 costs 0 (it comes with the
unlock), every level above it costs more than 0 and more than the level below it.

**Abilities (M5).** `behavior` must be an id `BehaviorRegistry` implements — an unknown one used to
be a run that silently did nothing. Every `params` key must be one the behaviour declares as a
`ParamSpec`, every required key must be present at every level, every value must sit inside its
range, and every column must follow its trend (`up`, `down` or free). A `PASSIVE` declares
`cooldownTicks: 0` and `durationTicks: 0`; an `ACTIVE` declares a cooldown, a duration or
`charges`, and — when it declares `effects` — a non-zero duration at every level, because an
active contributes its effects only while its duration runs. A level-up may not lengthen a cooldown
or shorten a duration.

**Modifiers and synergies (M6).** The offer schedule is strictly ascending and positive, a draft
shows at least one card, no rarity weight is negative and every rarity used has one — a card in a
rarity with no weight could never be offered. A card is takeable at least once, carries at least
one tag, names only modifiers that exist in `excludes` and never itself, and does something
(an effect, a flag or a streak bonus). A `streakBonus` must declare `NO_COINS` in
`requiresFlagsAbsent`. Neither a card nor a synergy may grant `SPEED_RAMP` or `ALL_OBSTACLES_MOVE`
mid-run (the difficulty layer resolves both at run start) or touch `MOVING_CHANCE` (the spawn
decision reads it, so a drafted change would make the obstacle sequence depend on the player's
choice, E32.d). A synergy needs at least two `requiresTags`, must not share an id with a modifier,
and is *warned* about — not rejected — when no legal build can satisfy it. A challenge's
`forcedModifiers` must resolve, must not list a card more often than its `maxStacks`, must not
hold two cards that exclude each other and must not force a card the challenge's own `flags`
forbid: `ModifierDirector` applies the list under those same rules, so anything unchecked here is
a card the challenge would silently lose at run start.

**Prerequisites.** The node graph is acyclic and tier-consistent (a prerequisite sits in the same
tree, in a lower tier).

**Caps (E3).** `2 + Σ ability_cap grants ≤ min(levels over abilities)`;
`max(bird.passiveSlots) + Σ passive_slot grants ≤ 4`.

**Cosmetic-only conditions (E20).** `prestige` and `counter` are errors anywhere but on a palette.

**Where a `purchase` may sit (D13).** At the root of a condition tree, or directly under an
`any_of` — nowhere else. The shop reads a `purchase` branch as "this is what it costs" and sells
the unlockable for it; under an `all_of` the coins are one requirement among several, so the sale
would hand over something its siblings still gate. `UnlockEvaluator.priceOf` refuses to price such
a branch as well, so the two halves agree.

**Contradictions.** `NO_COINS` with a `COLLECT_COINS` objective, or with a `COIN_MULT` /
`COIN_SPAWN_RATE` effect; `NO_DEFENSIVE_ABILITIES` with a `SHIELD_CHARGES` effect; `NO_REVIVE`
with a `REVIVES` effect.

**The classic table.** Bird `classic` + curve `classic` + tier `normal` must resolve, through the
real `StatSheet` and `DifficultyCurve`, to `1800 / 405 / 1500 / 120 / 128 / 160` with
`MOVING_CHANCE` 0.05 at gate 0 and 1.0 at gates 19 and 25 — at gate 0 *and* at gate 25, so a
stray entry added to the classic curve cannot hide.

**Strings (D25, E31.h).** Every `StringKey` and every `<kind>.<id>.name` / `.desc` of every kind
that ships must exist in `strings/en.json` (an error). A key missing from a translation is a
warning: it falls back to English at runtime.

**Warnings** do not fail anything: an effect that does nothing (`MULTIPLY ×1.0`, a zero
`FLAT_ADD`), and `coinsPerPoint: 0` with no other consumer of points (E1).

---

## 5. The unlock graph

`UnlockGraph.of(content)` turns every condition, reward and grant into one directed graph and
proves four things (D13):

1. **No cycle.** Nothing may require, however indirectly, something it unlocks.
2. **Reachability.** Every unlockable is reachable from the default set, which is derived from
   the data itself: the ids whose unlock is `{"type": "default"}` (E18).
3. **A cumulative path for every non-cosmetic unlockable.** The cumulative condition types are
   `total_gates`, `runs`, `level`, `coins_earned_total` and `purchase` — the ones a player
   reaches by playing *more*, never by playing *better*. Every bird, ability, world, tree, tier,
   challenge and feature must be reachable using only those, so a player who cannot clear a boss
   can still get everything. Level rewards and `UNLOCK` upgrade grants carry a cumulative path;
   boss, challenge and achievement rewards do not.
4. **Every currency has a source.** The paying currency is derived from the data, not assumed:
   every reward block — the run terms, `xp.levelRewards`, and the boss, challenge and achievement
   rewards — pays `economy.primaryCurrency()`, because `RewardDef` and `LevelRewardDef` carry an
   amount and no currency. A second declared currency therefore has no source until that changes,
   and the check says so.

Edges point *from* what has to happen first *to* what it opens:

* a condition of type `challenge`, `achievement` or `world_cleared`;
* an `economy.xp.levelRewards` entry (cumulative);
* a world `boss.reward` — this is the chain `green_fields → wind_valley → iron_forge →
  storm_sky → void`, and `wind_valley` also pays `tree:forge`;
* a challenge `rewards.unlocks` and an achievement `reward.unlocks`;
* an `UNLOCK` upgrade grant, from the node's `tree:` (cumulative, priced at the node's first
  level **plus one level of every prerequisite, transitively**: `hard_tier_1` costs 400 and cannot
  be bought without `coin_purse_1` at 80, so the edge costs 480).

Conditions that only read the player's own numbers (`runs`, `purchase`, `best_gates`,
`prestige`, `counter`, …) are leaves: they need nothing else unlocked first.

---

## 6. `contentCheck`

```
./gradlew contentCheck                      # summary + problems + the whole graph
./gradlew contentCheck -PtoolArgs="--quiet" # summary + problems, no graph
./gradlew contentCheck -PtoolArgs="--graph-only"
```

It loads the shipped files exactly as a player's install does, runs the full validator and the
string check, prints the unlock graph as an indented tree and then the cheapest cumulative path
of every unlockable, and fails the Gradle task on any error. Warnings are printed and do not
fail.

### Reading a failure

```
  ERROR  upgrades.json#/nodes/11/prereqs/0: unknown upgrade node 'scholar_2'
```

* `upgrades.json` — the file;
* `#/nodes/11/prereqs/0` — a JSON pointer: the twelfth entry of `nodes`, its `prereqs`, first
  entry. Array indices are zero-based and follow file order;
* the message names the rule that was broken.

Some errors are reported against a rule rather than a single value, and then the pointer is the
owner: `upgrades.json#/nodes: the ability level cap reaches 4 …` (the E3 budget is a property of
the whole node list), or `worlds.json#/worlds/4: 'world:void' cannot be reached from the default
set […]` (reachability is a property of the graph, reported where the node is authored).

When the content does not even bind, the tool prints the binder's errors and stops — there is no
graph to draw for content whose shape is unknown.

---

## 7. Tests

| Test | What it holds down |
| --- | --- |
| `ContentIntegrityTest` | the shipped files load, bind, validate; the roster, the eighteen nodes with their full price table, the stub blocks and the classic table are what §4 of the plan says |
| `ContentValidatorTest` | one deliberately broken fixture per rule, under `src/test/resources/fixtures/content_bad/`, each pinned to its message *and* its pointer |
| `UnlockGraphTest` | the four graph invariants on the shipped content, plus a bird reachable only through a challenge, which must be refused |
| `StrictBinderTest` | the binding rules themselves |

A `content_bad` fixture is a copy of **one** shipped file with **one** defect; the test swaps it
into the shipped set. Add a fixture rather than a second full content set: the rest of the
content stays real, so the fixture cannot drift into passing for the wrong reason.

---

## 8. Adding a content file

1. Write the `*Def` records in `content.defs`. Records are the schema: component names are JSON
   keys, and the compact constructor is where range checks live (they are reported at the
   record's own pointer).
2. Add the base name to `ContentLoader.FILES`, a registry to `GameContent`, and bind it in
   `GameContent.fromJson`. A file that is not in `REQUIRED_FILES` may be absent, which turns off
   the rules that need it.
3. Add its kind to `ContentKind` if it is a new kind, so its display strings are required.
4. Extend `ContentValidator` and, if it carries unlocks or rewards, `UnlockGraph`.
5. Extend `ContentCheck`'s summary and `ContentIntegrityTest`.
