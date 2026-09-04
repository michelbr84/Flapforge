# Flapforge — Game Design

> Skill-based arcade roguelite with persistent meta-progression. Every number in this
> document is read from the shipped data files (`src/main/resources/data/*.json`) or
> measured in `docs/BALANCING.md` — nothing here is aspirational.

## Pitch

One-button arcade flying with the exact feel of the classic (60 Hz, flap 405 px/s, gravity
1800 px/s², scroll 120 px/s — pinned by `ClassicFeelTest` to 0 px of drift), wrapped in a
roguelite loop: mid-run drafts reshape each attempt, five worlds escalate the hazards, and
everything you earn — coins, XP, birds, abilities, upgrade trees, achievements — persists in
a crash-safe local save. Skill decides the run; meta-progression decides what skill gets to
work with. No online services: seeds, dailies and records are all local and deterministic.

## The core loop

1. **Pick a loadout** — bird, palette, one active ability, passive abilities, world, tier,
   mode (Standard, Seeded, Daily; Challenges have their own screen).
2. **Fly** — one input (Space / ↑ / left-click) flaps; X / Shift / right-click fires the
   active ability. Pass gates, collect coins, keep a clean-gate streak.
3. **Draft** — at gates 10/25/45/70/100/140 the run pauses in a breather and offers 1-of-3
   modifiers (rarity-weighted). Forced modifiers (daily/challenge) are pre-taken.
4. **Boss** — each world has a boss at a fixed gate: warning banner, streamed patterns,
   survive the timer, earn the reward.
5. **Die or clear** — the game-over strip pays coins/XP immediately (retry never loses a
   reward), the summary shows the build; the meta-progression applies once per run.
6. **Spend** — shop, upgrade trees, ability levels, unlocks; repeat stronger.

## Run structure

`READY → FLYING ↔ {BREATHER → CHOOSING_MODIFIER → RESUME_HOLD} ↔ {BOSS_WARNING → BOSS} →
DYING → FINISHED`. Offers never open during a boss (E7); a resume hold counts down 3-2-1 with
30 i-frames. Retry is instant (Space) with a new seed; the daily keeps its seed and counts the
attempt (D29). Every run is fully deterministic for its seed — obstacles, coins, offers,
patterns all draw from named RNG streams.

## Difficulty

Two curves (`data/difficulty.json`), applied per gate passed:

* `classic` (Green Fields) — only upstream's moving-gate ramp: `MOVING_CHANCE 0.05`,
  `+0.05/gate`, capped at 1.
* `standard` (all other worlds) — the moving ramp plus `SCROLL_SPEED ×(1 + 0.004/gate)` up to
  ×1.5 and `GAP_SIZE ×(1 − 0.002/gate)` down to ×0.8.

Three tiers select on top of the curve:

| tier | scroll | gap | flags | reward mult |
| --- | --- | --- | --- | --- |
| `normal` | — | — | — | ×1.0 |
| `hard` | ×1.10 | ×0.92 | — | ×1.5 |
| `nightmare` | ×1.20 | ×0.85 | `ALL_OBSTACLES_MOVE`, `LETHAL_CEILING` | ×2.5 |

`hard` unlocks via `best_gates 40` or `total_gates 400` (or the `hard_tier_1` node);
`nightmare` via the `boss_corridor_1` challenge or level 20. `SPEED_RAMP` (the
`speed_run_1` challenge) adds `+0.0005 × ticks` to the scroll multiplier. The classic curve
and cell are frozen by the content validator and the published determinism hash.

## Worlds and hazards

Five worlds, each with a palette, a hazard mix and a boss (`worlds.json`):

| # | world | style | hazards (spawn weights) | boss (gate / survive ticks / reward) |
| --- | --- | --- | --- | --- |
| 1 | Green Fields | hills | pipe gates only (100) | 30 / 1200 / 200 coins + Wind Valley |
| 2 | Wind Valley | canyon | gates 60, wind zones 40 (ambient headwind −20) | 30 / 1500 / 300 + Iron Forge + forge tree |
| 3 | Iron Forge | factory | gates 40, gears 30, pistons 30 | 30 / 1800 / 400 + Storm Sky |
| 4 | Storm Sky | storm | gates 55, lightning 25, wind 20; ambient darkness 0.5 | 35 / 1800 / 500 + Void |
| 5 | Void | void | all four families + rule cycles | 40 / 2100 / 800 + voidglass palette |

Obstacles come in five families — the **pipe gates** (standard/floating, optional oscillator)
every world spawns, plus four hazard families: **gears** (rotating
circles, optional vertical rail), **pistons** (telegraph → extend → hold → retract), **wind
zones** (constant force; changes the trajectory, never lethal), **lightning strikes**
(partial-height bolt with a guaranteed safe band, warning before strike). Every hazard is
data in `patterns.json` under per-kind `ParamSpec` feasibility rules; the expert bot clears
every pattern in ≥ 30 % of seeds (`ContentFeasibilityTest`).

The **Void cycles its rules** every 5 gates with a 90-tick telegraph banner: `ALL_OBSTACLES_MOVE`,
`GAP_SIZE ×0.85`, `GRAVITY ×1.3`, or `LETHAL_CEILING`. Storm Sky's ambient
`lightningEveryGates 3` is cosmetic (sky flash + thunder only — lethal bolts come from spawns).

## Birds

Seven birds (`birds.json`), identical hitbox 33×31, all unlocked through play or purchase:

| bird | role | signature |
| --- | --- | --- |
| Forgewing (`classic`) | balanced default | no modifiers — the reference |
| Zephyr (`swift`) | fast | higher gravity + flap |
| Anvil (`heavy`) | heavy | fall capped at 450 px/s |
| Ironbeak (`guardian`) | defensive | innate shield; −20 % coin mult |
| Jackdaw (`gambler`) | risk/reward | +30 % score & coins, ×0.9 gap, +0.10 hitbox scale |
| Oracle (`mystic`) | ability-focused | ×1.3 ability duration, ×1.4 cooldown, 3 passive slots |
| Cinder (`forge`) | upgrade synergy | +2 % score/gate, capped at +50 %; +0.5 % flap and +1 % coins per owned upgrade level |

## Abilities

Eight abilities (`abilities.json`), 3 levels each, purchasable to the cap (base 2; the
`master_forge_1` node grants +1). Loadout: 1 active + 2 passives (Oracle: 3) + innate bird
passives. `shield`/`revive` are stat-driven (`SHIELD_CHARGES`/`REVIVES`), so upgrade nodes
grant them without the ability.

| ability | kind | tags | what it does |
| --- | --- | --- | --- |
| double_flap | ACTIVE | MOVEMENT | 2 charges, recharge every 5 gates, second flap mid-air |
| shield | PASSIVE | DEFENSIVE | +1 shield charge; absorbs a hit (45 i-frames); L2+ regenerates |
| dash | ACTIVE | MOVEMENT | 20 ticks of ×2.5 scroll, gravity off, i-frames |
| coin_magnet | PASSIVE | ECONOMY | +90 magnet radius |
| slow_time | ACTIVE | TEMPO | 90 ticks at ×0.5 world speed (apex unchanged) |
| emergency_recovery | PASSIVE | DEFENSIVE, REVIVE | +1 revive: auto-flap kick + 90 i-frames |
| score_multiplier | ACTIVE | ECONOMY | 300 ticks of ×2 score |
| invulnerability | ACTIVE | DEFENSIVE | 120 ticks immune |

Rule flags strip what they forbid: `NO_DEFENSIVE_ABILITIES` zeroes shields and removes
`DEFENSIVE` abilities (including innate ones); `NO_REVIVE` removes revives. Modifier
eligibility is derived from the same flags (E12).

## Modifiers and synergies

Seventeen modifiers (`modifiers.json`), drafted 1-of-3 at gates 10/25/45/70/100/140, rarity
weights COMMON 60 / RARE 28 / EPIC 10 / LEGENDARY 2, stacks and exclusions per id. Tags:
`ECONOMY, SPEED, DEFENSE, PRECISION, TEMPO, RISK, GREED`. Four set-bonus synergies activate
when two *distinct* taken entries cover the required tag multiset (E16):

| synergy | needs | effect |
| --- | --- | --- |
| coin_engine | ECONOMY + ECONOMY | +25 % coin mult |
| bulwark | DEFENSE + DEFENSE | +1 shield charge |
| needle_threader | PRECISION + PRECISION | −0.10 hitbox scale |
| daredevil | SPEED + RISK | +35 % score mult |

The average bot that reaches its third offer activates a synergy in 69.9 % of runs
(BALANCING.md §13.1).

## Challenges

Seven challenges (`challenges.json`), self-contained runs (world unlock not required, E6):

| challenge | world | rules | objective | first-clear reward |
| --- | --- | --- | --- | --- |
| no_shield_1 | Green Fields | no defensive abilities | survive 30 gates | 200 + ember palette |
| speed_run_1 | Wind Valley | SPEED_RAMP | survive 30 gates | 250 + comet palette |
| tiny_wings_1 | Green Fields | flap ×0.7 | survive 20 gates | 150 |
| moving_world_1 | Green Fields | all obstacles move | survive 25 gates | 250 + bronze palette |
| one_life_1 | Iron Forge | no defensive, no revive | survive 30 gates | 400 + invulnerability |
| coin_rush_1 | Wind Valley | coin spawn ×3 + forced coin_drops | collect 60 coins | 300 + gilded palette |
| boss_corridor_1 | Green Fields | forced corridor + its own boss | clear the boss | 500 + `tier:nightmare` |

`speed_run_1` ships at 30 gates (measured: expert met 40 in 28 % of seeds vs the ≥ 30 % bar;
62 % at 30 — BALANCING.md §11.1).

## Bosses

Every world streams 2–3 authored patterns at its boss gate; survive `surviveTicks` to clear.
The corridor boss (`boss_corridor_1`) loops its own pattern. Only world bosses pay the boss
reward and count toward clears (E26). Boss clear rates ≥ 30 % for the expert bot
(`ContentFeasibilityTest`, @sim).

## Economy

Two resources: **coins** (spent) and **XP** (never spent). Every coin grant — level rewards,
achievements, boss/challenge rewards — goes through the wallet and counts once. Per run
(E32.a, `economy.json.rewards`):

```
participation 20 · first-run bonus 25 · 2/gate · 1/point · 5 coins per 5-gate clean streak
+ 150 per world boss + challenge first-clears + 100 challenge bonus
× COIN_MULT × tier rewardMult × daily ×1.25   (+ coins physically collected)
```

Run 1 pays ≈ 50 coins even at 0 gates (45 base + collected pickups). XP: 15 + 10/gate (+200
per boss), level curve base 100 × 1.10 growth, max level 50, milestone rewards at levels
2/5/10/15/20/25.

**Prestige** (level 25, max 5): resets coins/XP/level/upgrades/ability levels/challenges,
keeps birds, cosmetics, achievements and lifetime statistics; each stack grants +5 % coin
mult (`PRESTIGE` layer) and a badge on the menu; cumulative unlock conditions read
"since prestige" against a baseline snapshot (E23). The two-step confirm lives in the
Statistics screen.

**Daily** (`economy.json.daily`): one deterministic pick per UTC date — seed
`fnv1a("daily:" + yyyy-MM-dd)`, world, tier from `{normal, hard}` and 2 forced compatible
modifiers, all drawn from *unlocked* content; the pick is persisted on first view (E27) and
reused for the date; ×1.25 rewards; requires `feature:seeded_runs`.

## Progression arc

Defaults (E18): Forgewing, double_flap, Green Fields, flight tree, normal tier. The measured
journey (`MetaSimTest`, BALANCING.md §13): the spender bot buys `feather_1` by run 3 (mean
1.15), Ironbeak by run 3, the shield by run 5, modifier drafts by run 7 (mean 5.6), world 2 by
run ≈ 3 on average, and owns every non-cosmetic unlockable by run 25 on average (bound: 200).
The saver reaches world 2 in a mean of 2.2 runs, worst line 4 (bound: 10). Synergy rate
69.9 % ≥ 20 %.

## Accessibility

`reduceFlashing` (honoured by lightning, telegraphs and particles), `highContrast`,
colour-blind palettes, `textScale`, `holdToFlap` (auto-flap every 24 ticks), rebinding for
every action except the mouse buttons, volumes and mute — all persisted in `settings.json`.
The bundled font is OFL-licensed; both languages (EN, pt-BR) render it.

## Deferred (with reasons)

Leaderboards (no online infra in 1.0 — `runHistory` keeps local data); challenge sharing and
community packs (`ChallengeDef` is already JSON, an export is a small follow-up); mod packs
(`~/.flapforge/mods` overlay follows the data-driven loader); New Game+ (superseded by tiers +
prestige); "upgrade materials" as a second currency (coins cover it; the wallet is map-keyed);
licensed music (the procedural sequencer ships, a streaming voice exists); endless tiers (the
`tierGenerator` key is reserved); original art/SFX packs (drop in via `assets/manifest.json`
without code changes). See `docs/ROADMAP.md` for the next-step anchors.
