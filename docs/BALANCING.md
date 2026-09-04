# Balancing — the conversion table (M1), the run economy (M3), the meta-progression (M4), the abilities (M5), the modifiers (M6), the worlds (M7) and the bosses and challenges (M8)

Flapforge is a rewrite of a 30 Hz Flappy Bird clone
([kingyuluk/FlappyBird](https://github.com/kingyuluk/FlappyBird), the tree at commit `b811782` of
this repository) as a 60 Hz fixed-timestep game with `double` physics. This document is the record
of that conversion: every upstream constant, the value it became, and the number a test actually
measures.

The rule was **no re-tuning by feel**. Every air-motion row was converted analytically and then
pinned by `ClassicFeelTest`, which runs a literal transliteration of the old code
(`ClassicReference`: `int` fields, `ACC_FLAP 14`, `ACC_Y 2`, the dead `MAX_VEL_Y`,
`BOTTOM_BOUNDARY 598`, the `y = min(y − v, 598)` clamp and the unclamped rectangle) beside the new
simulation and requires the two trajectories to agree to **0.0 px**. Exactly one deviation is
intentional — the ground rule — and it has its own section and its own test.

Sections 1–4 are the M1 conversion; section 5 is the M3 economy, section 6 the M4
meta-progression and section 7 the M5 abilities, and the same rule holds there — their numbers are
measured output over hundreds of seeds per cell, not estimates.

Every "Measured" entry in sections 1–4 is produced by the M1 test sources, not by hand:

| Source | What it measures |
| --- | --- |
| `src/test/java/io/github/michelbr84/flapforge/gameplay/ClassicFeelTest.java` | air trajectory parity, apex, first-frame travel, death fall |
| `src/test/java/io/github/michelbr84/flapforge/gameplay/GroundRuleTest.java` | the ground-rule deviation bounds |
| `src/test/java/io/github/michelbr84/flapforge/gameplay/ClassicReference.java` | the upstream transliteration both are compared against |
| `src/test/java/io/github/michelbr84/flapforge/GameScreenTest.java` | ground scroll rate, freeze on death, blink period, the sky under the game-over overlay |
| `src/test/java/io/github/michelbr84/flapforge/RenderLayerTest.java` | cloud spawn rule, cloud speed, wing cadence and pose |

## 1. Air motion and geometry

| Upstream (30 Hz, per frame; from source) | Flapforge (60 Hz tick, px/s) | Measured | Note |
| --- | --- | --- | --- |
| flap sets `v = 14` only if `rect.y > 20` ⇒ centre `y > 32`; ignored while the key is held (`keyFlag`) | `FLAP_VELOCITY = 405` (sets, never adds); refused when `y ≤ 32`; `justPressed` only | trajectory delta **0.0 px** over 120 frames / 10 flaps (`tenFlapsOver120FramesMatchToZeroPixels`); identical accept/refuse sets under ceiling spam | `405 = 14×30 − 15` half-step correction |
| gravity `2` px/frame² (velocity-first) | `GRAVITY = 1800`, velocity-first (semi-implicit) Euler | first frame after a flap moves **12.0 px** (`6.25 + 5.75`), apex **42.25 px at tick 13**, **42.0 px at tick 14** vs upstream's 42 at frames 6–7 | `2 × 30²` |
| `MAX_VEL_Y = 15` is dead code (`velocity < MAX_VEL_Y` never blocks a negative fall; dives reach 48 px/frame = 1440 px/s) | classic bird `MAX_FALL_SPEED = 1500` (never reached on screen); `450` is Anvil's data | a 450 clamp diverges by exactly **256 px** in a 1 s dive (`fallClamp450DocumentsTheDivergence`) | clamping at 450 would change the feel; 1500 does not engage |
| pipe speed `4` px/frame | `SCROLL_SPEED = 120` (2.0 px/tick) | `groundScrollsWhileFlyingAndStopsOnDeath`: **2.0 px/tick** exactly | doubles cleanly |
| moving pipe `dealtY` 0→51→0 at 1 px/frame; the top column lags 2 frames | `Oscillator(amplitude 51, speed 30 px/s, triangle)` moving the pair rigidly | period **3.4 s** | the 2-frame lag is dropped (a rigid pair; the lag was an artefact of two independent sprites) |
| gap `H/5 = 128`, interval `H/4 = 160`, top ∈ `[80, 400]` | `GAP_SIZE 128`, `GATE_INTERVAL 160`, same range | `PipeGateLayoutTest` | |
| floating pipes `y ∈ [53, 106)`, `h ∈ [106, 160)`, passable above and below | `PipeGate.FLOATING`, same ranges | `PipeGateLayoutTest` | |
| spawn when `last.x + 40 < 420`, next `x = last.x + 160`; `P(moving) = (score + 1) / 20`, ¼ hover ¾ normal, static ½/½ | same cursor rule; `MOVING_CHANCE = 0.05 + 0.05 × gates` (curve `classic`) | `ObstacleSpawnerTest`, `DifficultyCurveTest` | identical distribution |
| the **first** pair has its own branch (`pipes.size() == 0`): always a static normal pair at `x = 420`, `top ∈ [80, 400]`, no probability rolled | `SpawnTable.rollFirst`, drawing from the `obstacle` stream only, so the `spawn` stream stays aligned | `theFirstGateOfARunIsAlwaysStaticAndStandardLikeUpstream` over 300 seeds | the opening obstacle of every run is the same shape as upstream's; `ALL_OBSTACLES_MOVE` (nightmare) still moves it |
| bird hitbox `(x − 17, y − 12, 33, 31)` inside the 39×33 sprite (the height is derived from the *width*) | `HitboxSpec {33, 31, −17, −12}` | `HitboxTest` | the quirk is preserved as data, not reproduced as a bug |
| pipe hitbox 40 wide (the 44 cap is decorative), top pipe drawn from `−100` | same | `CollisionSystemTest` | |
| score counted on a spawn heuristic (`pipes ≥ 6 && lastPipe.x − 86 ≤ 300`) | `gatesPassed++` when `gate.x + 40 ≤ hitboxLeft` (fully cleared) | ~2 upstream frames later | intentional: the old counter was an artefact of the spawn loop |
| death: `velocity = 0`, pipes stop, the bird falls to the ground | `DYING` phase; the run seeds `vy = +15` px/s (E28) | death fall matches upstream **0.0 px** (`deathFallMatchesUpstreamDeadBirdFall`) | `+15` px/s is the 60 Hz image of upstream's `velocity = 0` |
| frame loop is `Thread.sleep(33)` (≈ 29.5 Hz actual) | `TICK_NS = 16 666 667`, accumulator, ≤ 6 ticks/frame, 100 ms clamp, render capped at 60 by default | `GameLoopTest` | the tick rate is pinned by `tickRateIsPinnedAtSixty` |

### The `vy` mapping (E28)

Upstream stores an **up-positive integer velocity per frame**; Flapforge stores a
**down-positive `double` in px/s**. The two representations are related by

```
vy_ff = 15 − 30 · v_up
```

Both halves matter. The `−30 · v_up` converts px/frame at 30 Hz to px/s. The `+15` is the
half-step correction: upstream applies gravity *before* it moves (`v -= 2; y -= v`), so one 30 Hz
frame is not two 60 Hz ticks of the same velocity but two ticks straddling it. Substituting
`v_up = 14` gives `vy_ff = 15 − 420 = −405`, which is where `FLAP_VELOCITY = 405` comes from;
substituting `v_up = 0` (upstream's death) gives `vy_ff = +15`, which is why `Run.beginDying()`
seeds `+15` rather than `0`. With those two values the trajectory parity is exact rather than
approximate.

## 2. The ground rule — the one intentional deviation

Upstream's ground constant is an accident. `BOTTOM_BOUNDARY` is an *instance-field initialiser*
that reads the static `BIRD_HEIGHT` before the constructor has set it, so it evaluates to
`640 − 42 − 0 = 598`. The bird's centre is then clamped at 598 (the sprite is half buried, its
bottom at 614) and death only fires when the **unclamped** rectangle top passes 598 — centre above
610. In between there is a ~28 px window in which the bird is visibly buried in the ground and a
flap is still accepted.

Flapforge dies when the sprite bottom touches the ground line: `y ≥ GROUND_DEATH_Y = 598 − 16.5 =
581.5`. There is no clamp and no buried window. `ClassicReference` keeps the original quirk so the
difference stays measurable, and `GroundRuleTest` asserts its bounds:

| Case | Upstream | Flapforge | Measured deviation | Asserted bound |
| --- | --- | --- | --- | --- |
| plain fall from `y = 320` at terminal speed | 17 frames (= 34 ticks) | 32 ticks | **2 ticks earlier** (1 upstream frame) | ≥ 1 and ≤ 4 ticks |
| slow approach: a flap at `y = 560`, then no input | 17 frames (= 34 ticks) | 29 ticks | **5 ticks earlier** (2.5 upstream frames) | ≥ 1 and ≤ 14 ticks |
| plain falls from `y = 100 … 560` in steps of 40 | 8–23 frames | 12–43 ticks | **1–4 ticks earlier**, worst case at `y = 500` and `y = 540` | Flapforge never dies later |
| a flap at `y = 590` | accepted (`rect.y = 578 > 20`), the bird is alive and buried | impossible: the bird is already dead at 581.5 | — | `flapAt590IsImpossibleInFlapforgeButAcceptedUpstream` |

In play the deviation is invisible: the bird dies when it *looks* like it hit the ground instead of
a fifth of a second after sinking into it.

## 3. Cosmetic rows

These do not affect the simulation, but they are what makes the game *read* as the original, so
they were converted with the same discipline.

| Upstream | Flapforge | Measured | Note |
| --- | --- | --- | --- |
| a 253×84 background strip tiled across the window, scrolled at `GAME_SPEED` 4 px/frame, wrapping at the strip width; ground band = half the image = 42 px; sky `0x4bc4cf` | `BackgroundRenderer`: procedural sky gradient, two parallax hill bands and a 42 px ground band whose tuft/dirt pattern wraps at **253 px**, all scrolled at `SCROLL_SPEED` (120 px/s); sky from `WorldPalette.GREEN_FIELDS` (`#4BC4CF`) | 2.0 px/tick, wrap period 253 px | hills scroll at 0.25× and 0.5× of the ground for depth — new, upstream had a flat strip |
| clouds: speed `GAME_SPEED × 2`, spawn check every 100 ms of wall time, 6 % chance, max 7, `x = 420`, `y ∈ [20, 213)`, scale 1–2×, two images (48×33 and 40×32) | `CloudLayer`: `CLOUD_SPEED_FACTOR × ` the run's scroll (240 px/s at the classic 120), 6 % per **6 ticks** (= 100 ms at 60 Hz), max 7, same spawn band, same scale range, two procedural silhouettes | `Playfield.CLOUD_MAX 7`, `CLOUD_SPAWN_PCT 6`, `CLOUD_SPEED_FACTOR 2`, `theCloudStepFollowsTheWorldScrollAndTheDeadDriftIsAbsolute` | the period is driven by ticks instead of `System.currentTimeMillis()` so no wall clock enters the loop; upstream's `GAME_SPEED` was constant, so the ×2 is derived from the resolved `SCROLL_SPEED × TIME_SCALE` instead of hard-coded (the hard tier and Slow Time would desynchronise the sky otherwise); the layer owns an **unseeded** RNG and can never influence the simulation (D12) |
| on death: the background returns before `movement()`, the clouds drop to `speed = 1` (30 px/s), the pipes stop — on the game-over screen too | ground and hills stop, obstacles frozen in `DYING`/`FINISHED`, clouds drift at 30 px/s including under the game-over overlay (which ticks `GameRenderer.tickFrozen()`, since the screen manager only ticks the top screen) | `groundScrollsWhileFlyingAndStopsOnDeath`: the scroll distance is **unchanged** for 20 ticks after the run ends; `theSkyKeepsDriftingUnderTheGameOverOverlayAndSurvivesTheRetry` | upstream's `resetGame()` reset bird and pipes but not `GameForeground`, so an instant retry keeps the sky it had |
| wing frame `wingState / 10 % 8`, `wingState = 0` on an accepted flap; the "up" image while `velocity > 0` (upstream's sign: rising) | `BirdRenderer`: 8 frames × **20 ticks**, restarted on a `Flapped` fact; `BirdPose.UP` while `vy < 0` (Flapforge's sign: rising), `BirdPose.DEAD` from `DYING` on | cycle 160 ticks ≈ 2.67 s, as upstream | |
| start / game-over prompt hidden while `flashCount ≤ 30`, shown until 60, then reset (1 s off, 1 s on) | `HudRenderer` READY hint and `GameOverOverlay` prompt: **60 ticks hidden, 60 ticks shown** | `theGameOverPromptBlinksOnTheUpstreamPeriod` | |
| score text centred at `H/10`, bold 32 | `HudRenderer`: centred at **y 64**, bold 32, outlined | | the outline is new: the score has to stay readable over a pipe |
| `Frame.setSize(420, 640)` counted the window **insets**, so roughly 28 px of the playfield were hidden under the title bar — which is why upstream needed `TOP_BAR_HEIGHT 20` to keep clouds out of it (E30.i) | the window sizes its **canvas** to 420×640 and the viewport letterboxes it, so all 640 rows are visible; fullscreen saves and restores the windowed bounds and decoration state | `WindowScaleTest`, `SmokeWindowTest` | `CloudLayer.SPAWN_TOP_Y` keeps the 20 px band anyway, for the same look |

## 4. Difficulty (M1 scope)

Green Fields uses the `classic` curve, which is pure upstream and nothing else:

| Curve | Entry | Meaning |
| --- | --- | --- |
| `classic` | `MOVING_CHANCE FLAT_ADD base 0.05 perGate 0.05 max 1.0` | upstream's `P(moving) = (score + 1) / 20`, capped at certainty |

The `standard` curve (`SCROLL_SPEED MULTIPLY perGate 0.004 max 1.5`,
`GAP_SIZE MULTIPLY perGate −0.002 min 0.8`) and the `hard` / `nightmare` tiers exist in the data
model from M1 but are only *balanced* in M9, against the `MetaSim` thresholds. Nothing in this
document was tuned to make a bot survive longer; when a number has to change, the change belongs in
`difficulty.json`, never in the conversion table above.

## 5. The run economy (M3)

Everything below was **measured**, not chosen: the tables come from `BalancingSim`, which drives the
shipped content with `BotPilot` and runs the real `RunRewardCalculator` against the shipped
`economy.json`. Reproduce every row with

```bash
./gradlew balancing -PtoolArgs="--seeds 500 --skill all --ticks 20000 --csv build/balancing-m3.csv"
```

500 seeds per skill, bird `classic`, world `green_fields`, tier `normal`, a 20 000-tick (5.5 min)
budget per run, every multiplier at 1 and no first-run bonus (so these are *later* runs).

### 5.1 Coins per run, by skill

| Skill | gates mean | coins p10 | p50 | p90 | mean | runs paying 0 | xp mean |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `novice` (reaction 12 ticks, error 24 px) | 4.7 | 0 | 31 | 64 | **33.2** | 20.6 % | 58.2 |
| `average` (8 ticks, 12 px) | 84.1 | 34 | 269 | 950 | **357.4** | 1.6 % | 855.6 |
| `expert` (2 ticks, 2 px) | 240.2 | 1014 | 1046 | 1067 | **1015.1** | 0 % | 2417.2 |
| `perfect` (0 ticks, 0 px) | 241.6 | 1014 | 1043 | 1066 | **1017.4** | 0 % | 2430.5 |

Run 1 adds the flat 25-coin first-run bonus before the (unit) multipliers, so a beginner's first run
pays a mean of **58.2** coins — the plan's worked example, 20 + 25 + 20 + 10 = 75 for a 10-gate first
run, sits inside that distribution.

**The 20.6 % of novice runs that pay nothing** are the ones that die before the first pipe. With
`BIRD_X = 105` and the first gate spawning at `x = 420`, the score line needs 372 px of scroll —
tick ~186 at 2 px/tick — so the 180-tick participation gate lands almost exactly on "reached the
first pipe". That is defensible and deliberate (it is what kills the instant-retry dive, §5.4), but
it is worth knowing that one beginner run in five is worth zero before M4 prices anything against
the mean. If it ever has to change, the lever is `PARTICIPATION_TICKS`, not the coin terms.

### 5.2 The coin trail: how much of it is actually taken

`COIN_SPAWN_RATE` is 0.5, so a gate gets one coin half the time (E2). What reaches the wallet:

| Skill | coins spawned per run | collected | collection rate |
| --- | --- | --- | --- |
| `novice` | 3.9 | 2.5 | **65.6 %** |
| `average` | 43.6 | 42.4 | **97.2 %** |
| `expert` | 121.7 | 120.6 | **99.1 %** |
| `perfect` | 122.3 | 121.2 | **99.1 %** |

The trail sits on the gap centre, which is the optimal flight line, so for anyone who can fly a
pickup is not a decision: it is a flat +0.5 coins per gate on top of `coinsPerGate`, ~11 % of a
competent run's payout. That matches E2 as written and is not a defect, but M6's coin modifiers
(`coin_rush_1` is `MUL 3`) will be multiplying a term that is already automatic. If pickups are ever
meant to be a *choice*, E2 allows any layout through the gap and the trail can be offset off the
flight line; the table above is the baseline that change would be measured against.

**Geometry.** A coin rides its gate's safe band every tick, so the worst-case clearance is a
constant: `gap / 2 − amplitude − RADIUS = gap / 2 − 51 − 8`. At the shipped `normal` gap of 128 that
is **5.0 px**; at `hard` (`GAP_SIZE × 0.9`) it would be −1.4 px and at `nightmare` (`× 0.8`) −7.8 px
if the coin stayed where it was spawned, which is why it does not. `PickupTest` ticks a trail
through a whole 3.4 s oscillation at `GAP × 0.8` and asserts every coin stays inside the gap.

### 5.3 Streaks

A streak step is five consecutive clean gates (`economy.rewards.streak.step`) and pays 5 coins. A
gate is clean when its column was never grazed — resolved once the column has left the inflated
hitbox, so a graze *after* the score line still costs the gate it happened on (D26).

| Skill | best streak mean | best streak max | steps per run | gates per step |
| --- | --- | --- | --- | --- |
| `novice` | 2.7 | 13 | 0.26 | 17.9 |
| `average` | 13.3 | 44 | 8.63 | 9.7 |
| `expert` | 28.1 | 56 | 30.77 | 7.8 |
| `perfect` | 27.2 | 78 | 30.31 | 8.0 |

"Gates per step" is `gates ÷ steps`: five is the floor, a run with no graze at all. An expert needs
7.8, so grazes cost it about a third of the steps a flawless run would earn — which is what a bot
flying 2 px from the optimal line should look like. The steps are **15 %** of that run's coins
(30.8 × 5 = 154 of 1015).
Before the streak was resolved after the graze window, the same expert kept a near-perfect streak:
98 % of its near misses landed in the three ticks between the score line and the closing of the
inflated hitbox and were silently forgiven.

### 5.4 The instant-retry dive

The dive is the loop the participation gate exists to kill: flap once, hit the ground, retry. 400 of
them, applied through the real `ProgressionManager` against the shipped `economy.json`:

| | coins | XP | level | over |
| --- | --- | --- | --- | --- |
| XP participation ungated (E32.a as written) | 2 725 | 6 000 | **21** | 5.3 min = **511 coins/min** |
| XP participation gated (shipped) | **25** | 0 | 1 | 5.3 min = 4.7 coins/min, → 0 |
| `average` bot actually playing | 18 562 | 32 950 | 37 | 73.8 min = **251 coins/min** |

The 2 725 is the 25-coin first-run bonus plus the level rewards at 2, 5, 10, 15 and 20
(50 + 150 + 500 + 800 + 1 200): the coin gate stopped the run rewards, and the XP walked around it
through the level curve. Ungated, mashing space was twice as profitable as playing well and cleared
`feature:seeded_runs` (level 5) and `tier:nightmare` (level 20) in about five minutes. Gating the XP
participation is a deliberate amendment to E32.a's literal formula, recorded in the CHANGELOG and in
`RunRewardCalculator`'s Javadoc; the 25 coins the shipped column shows are the first run's
unconditional bonus, which no amount of retrying pays twice.

## 6. Meta-progression (M4)

M4 turns coins into a build, so this section is what a coin buys: how long the opening hour takes,
what each of the eighteen nodes is worth, and what the seven birds pay. Every number below was
produced by driving the shipped content through the real managers and the real
`RunRewardCalculator` — the same code the game runs — and is reproducible with
`./gradlew balancing -PtoolArgs="--bird all"`, `./gradlew simTest` and the harness described under
each table.

**Read the survival numbers with the caveat in §6.4.** `BotPilot` is not a monotone function of the
stats the tree sells, so a row that says a node costs the bot gates says something about the bot,
not about the node.

### 6.1 The journey: how long the openings take

Greedy shopper (buy the cheapest thing the wallet can pay for, node or shop offer, until nothing is
affordable), fifteen runs, ten seed families per skill, on the shipped `economy.json` and
`upgrades.json`. The number is the run **after which** the milestone was reached, so "1" means the
first run paid for it.

| Skill | First upgrade | Which node | Ironbeak (`bird:guardian`) | Second bird owned |
| --- | --- | --- | --- | --- |
| `novice` | 1 (median; 1–3 across seeds) | `feather_1`, 10/10 seeds | 3 | 3 |
| `average` | 1 (all seeds) | `feather_1`, 10/10 seeds | 3 (1–3) | 2 (1–3) |
| `expert` | 1 (all seeds) | `feather_1`, 10/10 seeds | 1 | 1 |

M4's target (§6 of the plan, E17) is an upgrade by run 2–3 and Ironbeak by run 3; the shipped prices
land the upgrade one run earlier than the window and Ironbeak exactly on it. `feather_1` at 50 coins
is the cheapest thing in the game and is what every skill buys first — `NewPlayerJourneyTest` pins
both the node and the run, so doubling any price fails it rather than quietly promoting the next
cheapest node.

Ironbeak arrives on run 3 by the free path (`runs 3`), not by the 150-coin one: a novice's first two
runs do not pay for it. That is E18 working as intended — the reward bird is a *reward*.

### 6.2 What each node is worth

300 seeds per row, `average` bot, bird `classic`, tier `normal`, world Green Fields. Each row owns
**only** that node, at its maximum level, against a baseline profile that owns nothing (84.50 gates,
359.51 coins). `cost` is the whole ladder, `dPayout` is the E32.a payout with the run's own
`COIN_MULT` applied.

| Node | Max | Cost | Gates | ΔGates | ΔPayout | Reads it |
| --- | --- | --- | --- | --- | --- | --- |
| `feather_1` | 3 | 420 | 70.41 | −14.09 | −56.55 | the run |
| `glide_1` | 2 | 290 | 84.50 | **+0.00** | **+0.00** | nothing — see §6.3 |
| `slim_frame_1` | 3 | 1 050 | 73.30 | −11.21 | −45.42 | the run |
| `quick_recharge_1` | 3 | 1 050 | 84.50 | +0.00 | +0.00 | M5 |
| `updraft_1` | 2 | 1 200 | 90.03 | **+5.53** | **+23.15** | the run |
| `featherfall_2` | 2 | 1 500 | 72.30 | −12.21 | −48.50 | the run |
| `coin_purse_1` | 4 | 1 200 | 84.50 | +0.00 | **+63.44** | the run |
| `scholar_1` | 4 | 1 200 | 84.50 | +0.00 | +0.00 | the run (XP, not coins) |
| `lodestone_1` | 3 | 1 050 | 84.50 | +0.00 | +0.15 | the run |
| `coin_rain_1` | 3 | 840 | 84.50 | +0.00 | **+25.32** | the run |
| `hard_tier_1` | 1 | 400 | 84.50 | +0.00 | +0.00 | grants `tier:hard` |
| `ability_scholar_1` | 1 | 900 | 84.50 | +0.00 | +0.00 | M5 |
| `tempered_shield_1` | 2 | 1 700 | 84.50 | +0.00 | +0.00 | M5 |
| `ability_forge_1` | 3 | 1 900 | 84.50 | +0.00 | +0.00 | M5 |
| `cooldown_forge_1` | 3 | 1 900 | 84.50 | +0.00 | +0.00 | M5 |
| `hitbox_forge_1` | 2 | 2 100 | 79.86 | −4.65 | −19.37 | the run |
| `master_forge_1` | 1 | 1 200 | 84.50 | +0.00 | +0.00 | M5 |
| `second_chance_1` | 1 | 1 500 | 84.50 | +0.00 | +0.00 | M5 |

The whole tree costs **21 400** coins (`ContentIntegrityTest` pins the row-by-row table).

Three groups come out of this:

* **Economy nodes do exactly what they say.** `coin_purse_1` maxed is +17.6 % payout for 1 200
  coins and `coin_rain_1` is +7.0 % for 840; both pay for themselves and neither touches survival.
  `lodestone_1` is worth 0.15 coins a run to the bot, which flies a fixed line and picks up almost
  the whole trail without a magnet — it is a comfort node for a human, and M9's MetaSim (§13)
  confirmed it is harmless to the thresholds rather than re-measuring it against a coin-missing
  policy; that measurement stays open (docs/ROADMAP.md).
* **Seven nodes were M5 content** (`quick_recharge_1`, `ability_scholar_1`, `tempered_shield_1`,
  `ability_forge_1`, `cooldown_forge_1`, `master_forge_1`, `second_chance_1`) — 10 150 of the
  21 400. Until M5 their effects resolved in the stat sheet and nothing consumed them, so the
  upgrade screen marked each of them *Arrives in M5* (E19); M5 removed the note. `SHIELD_CHARGES`
  and `REVIVES` are now read by `ShieldSystem` / `ReviveSystem`, the two `ABILITY_*_MULT` nodes
  scale every activation, and the `ABILITY_CAP` / `PASSIVE_SLOT` grants are spent in the shop and
  on the bird screen. The table above predates them and is not re-measured here — §7 measures the
  abilities themselves.
* **The flight nodes measure worse than nothing**, which is a property of the bot, not of the nodes
  (§6.4).

### 6.3 `glide_1` cannot bind

`glide_1` is `MAX_FALL_SPEED PERCENT_ADD −0.10` at 90/200 coins, and it is a prerequisite of
`updraft_1`, the strongest measured node.

The base cap is 1 500 px/s. With gravity 1 800 and a playfield 581.5 px tall, the fastest a bird can
possibly be falling before it hits something is `sqrt(2 × 1800 × 581.5) = 1447 px/s`, and the
measured maximum over 300 seeds is far below even that. So for five of the seven birds the cap is
never reached and the node changes nothing: **+0.00 gates and +0.00 coins at both levels**, exactly
as the table shows. It binds only for `heavy` (cap 450), where it is actively harmful.

The node is what §4 of the plan specifies, and the plan's node table is binding, so M4 ships it as
written and records the measurement here instead of re-authoring it. **For M9:** either give
`glide_1` an effect that binds inside the reachable range (a `GRAVITY` reduction while falling, or a
`MULTIPLY` whose floor is under 1 447), or drop it from `updraft_1.prereqs` so nobody is required to
buy a dead node on the way to a live one. Note also that nothing refunds an upgrade —
`UpgradeManager` has no respec path; only an `aliases.json` removal pays coins back — so a player
who buys it on `heavy` cannot undo it.

M9 took the first fork: §12.2 retunes the node in data so its cap cuts into the measured dive
range, and §6.2's `+0.00` row is the M4-era measurement it supersedes.

### 6.4 The bot is not a monotone oracle

`feather_1` maxed is `GRAVITY −9 %` and costs the `average` bot 14 gates. `slim_frame_1` and
`hitbox_forge_1` shrink the hitbox and also cost it gates. That is not a balance result: `BotPilot`
aims at a fixed offset (`AIM_OFFSET_PX = 20`) over a fixed arc window (`ARC_TICKS = 27`), so any
change to gravity or flap velocity re-phases its flap timing against constants that did not move,
and the survival number swings ±25 % with no trend. A sweep of `feather_1` L0..L3 over 300 seeds
gives 83.11 / 62.32 / 86.42 / 67.85 mean gates on the reviewers' seed family.

**Nothing was done about it in M4 on purpose.** `BotPilot` drives the golden run and the CI
`--headless-run` hash (`hash=eaaa01685261a433`), which M4 must not change, so re-phasing the pilot is
an M9 change, not a fix to slip into a content milestone. **For M9 (E25):** scale the pilot's aim
offset and arc window from the run's own resolved `GRAVITY` / `FLAP_VELOCITY` (`arc =
2 × FLAP_VELOCITY / GRAVITY` in ticks) so the pilot is invariant to the physics it is measuring, and
average many more seeds per data point in `MetaSim`. Until then, read the ΔPayout column — coins are
linear in the multipliers and do not depend on the pilot's phase — and treat ΔGates as noise for any
node that touches the physics.

### 6.5 What the seven birds pay

300 seeds, `average` bot, tier `normal`. `payout` is the E32.a formula with the bird's own
`COIN_MULT`; `maxed` is the same bird owning every node at its maximum level.

| Bird | Gates | Points | Payout | Payout, tree maxed |
| --- | --- | --- | --- | --- |
| `classic` | 84.50 | 84.50 | 359.51 | 515.10 |
| `swift` | 78.58 | 78.58 | 334.04 | 314.95 |
| `heavy` | 67.79 | 67.79 | 290.47 | 257.54 |
| `guardian` | 84.50 | 84.50 | **296.07** | 442.34 |
| `gambler` | 38.12 | 49.56 | 222.26 | 171.79 |
| `mystic` | 84.50 | 84.50 | 359.51 | 515.10 |
| `forge` | 58.11 | 81.98 | 277.61 | **566.84** |

Two of these are honest and five need M9's attention:

* **Cinder (`forge`) is the design working.** 277.61 unupgraded, 566.84 with the tree maxed — the
  only bird that beats `classic` maxed, because `BIRD_SYNERGY` scales with `Σ upgrades`. It is meant
  to be the late bird and it measures like one.
* **Ironbeak (`guardian`) paid 17.6 % less than the free default and gained nothing back in M4.**
  Its gates were identical to `classic` (the base stats are the same) and its only live difference
  was `COIN_MULT −0.20`, because the innate `shield` that pays for that was M5 content. It is live
  now: §7.1 measures a shield at +96 % gates / +92 % payout for the `average` bot, which is what the
  −20 % buys. The bird screen still names a bird's innate passives on the ability line, without the
  milestone note.
* **Oracle (`mystic`) is byte-identical to `classic`** on every column, and costs 600 coins or an M8
  achievement. Its distinguishing content is the third passive slot, which is live from M5 (§7.4:
  two defensive passives measure 205.4 mean gates against 158.8 for one) and is not visible in this
  M4 table.
* **Jackdaw (`gambler`) does not recover its −54 % gates** with +30 % score and coins, at any tree
  level — 222.26 plain and 171.79 maxed. It was the clearest retune candidate for M9; M9's pass
  (§12, §13) retuned only what a threshold demanded, and the meta cells passed on the shipped
  bird data, so the retune stays an open, recorded question (docs/ROADMAP.md).
* `swift` and `heavy` both lose payout when the tree is maxed, for the §6.4 reason.

### 6.6 The participation gate catches beginners

E32.a pays nothing for a run with no gate that lasted under 180 ticks (3 s). Over 500 seeds on the
shipped economy:

| Skill | Zero-payout runs |
| --- | --- |
| `novice` | 103 / 500 = **20.6 %** |
| `average` | 8 / 500 = 1.6 % |
| `expert` | 0 / 500 = 0 % |

The rule exists to kill the instant-retry dive (§5.4) and it does. But one novice run in five is a
genuine attempt that ends before the first obstacle column and pays nothing, which is what pushes
the first upgrade from run 1 to run 2–3 on some seed families. **For M9:** either lower the tick
threshold to ~90, or pay a small consolation for a run that reached at least one obstacle column.
The number is recorded here so that decision is made against data.

### 6.7 The harness

`BalancingSim` gained a `payout` column in M4: the same run through `RunRewardCalculator` with the
run's own resolved `COIN_MULT` / `XP_MULT` and the tier's `rewardMult`, next to the multiplier-free
`coins` column §5 uses. Without it the economy birds are invisible — `classic`, `guardian` and
`mystic` print identical numbers, and `guardian`'s whole design is a coin multiplier.

```
./gradlew balancing -PtoolArgs="--bird all --seeds 300"
./gradlew balancing -PtoolArgs="--skill all --csv build/balancing.csv"
```

The tables in §6.1–6.6 were produced by driving the same public APIs
(`RunLoadout.configFor` → `RunFactory` → `HeadlessRunner` → `RunRewardCalculator`) over the seed
ranges each table names; §6.1 additionally runs `UnlockManager` and `UpgradeManager`, which is what
`NewPlayerJourneyTest` does with one seed family and hard assertions.

## 7. Abilities (M5)

Every number below is `BalancingSim` output on the shipped content, `classic` bird, `green_fields`,
tier `normal`, 20 000-tick budget, one ability equipped in the slot its kind belongs to:

```
./gradlew balancing -PtoolArgs="--seeds 200 --skill average --ability all"
./gradlew balancing -PtoolArgs="--seeds 60 --skill all --ability all"
```

### 7.1 What each ability is worth, per skill

Deltas are against the same seeds with **nothing equipped**, 60 seeds per cell. The baseline row is
absolute (mean gates / mean payout); every other row is `Δgates / Δpayout`. "Uses" is the mean number
of activations per run, novice / average / expert / perfect — a passive is never activated.

| Ability | Uses | novice | average | expert | perfect |
| --- | --- | --- | --- | --- | --- |
| *(none)* | — | 5.47 / 37 | 83.73 / 357 | 246.62 / 1043 | 242.60 / 1023 |
| `double_flap` | 0.5 / 9.3 / 29.1 / 29.6 | −3 % / −2 % | −5 % / −5 % | −1 % / −1 % | +1 % / +2 % |
| `shield` | passive | **+102 % / +66 %** | **+96 % / +92 %** | +1 % / +1 % | +2 % / +2 % |
| `dash` | 0.2 / 4.7 / 14.2 / 14.1 | −7 % / −6 % | +5 % / +1 % | +3 % / −1 % | +4 % / +1 % |
| `coin_magnet` | passive | +0 % / +1 % | +0 % / +0 % | +0 % / +0 % | +0 % / +0 % |
| `slow_time` | 0.2 / 3.6 / 11.5 / 11.6 | −5 % / −3 % | −5 % / −5 % | −3 % / −3 % | −0 % / −0 % |
| `emergency_recovery` | passive | **+111 % / +70 %** | **+108 % / +102 %** | +1 % / +1 % | +2 % / +2 % |
| `score_multiplier` | 0.2 / 3.3 / 10.0 / 9.7 | +0 % / +2 % | +0 % / +3 % | +0 % / +3 % | +0 % / +3 % |
| `invulnerability` | 0.2 / 3.0 / 8.8 / 8.6 | +3 % / +2 % | +1 % / +1 % | +1 % / +1 % | +1 % / +1 % |

How to read it:

* **The two defensive passives roughly double a fallible run and do nothing for a good one.** At
  `expert` and `perfect` the bot almost never takes the hit they absorb, so they collapse to +1–2 %;
  at `novice` and `average` they are the strongest thing in the shop. That is the intended shape of
  a defensive item and it is why they must beat the baseline in `AbilityBotRunTest`.
* **`slow_time` measures negative and is not broken.** Halving `TIME_SCALE` scrolls the world more
  slowly, so a fixed tick budget contains fewer gates; the run is not shorter in seconds, it is
  shorter in columns. It is the one ability the regression test allows below the baseline (the
  tolerance is 0.75 of it).
* **`score_multiplier` is exactly its own effect**: gates unchanged, payout +3 %, which is
  `2 × SCORE_MULT` over the 300 ticks a level-1 window covers, three times a run.
* **The `dash` at `novice` is noise**: 0.2 activations per run over 60 seeds means a handful of runs
  differ at all. Over 200 `average` seeds the dash is +9 % gates (87.14 against 79.57).

### 7.2 The level-1 dash was a trap, and how it is measured now

Before the M5 review pass, `DashBehavior` granted exactly `durationTicks + invulnExtraTicks`
i-frames, and level 1 ships `invulnExtraTicks: 0`. The invulnerability therefore expired on the very
tick the held line released — with the world 100 px further along (20 ticks at `SCROLL_SPEED × 2.5`)
and the bird still inside the column it had dashed into, whose overlap span is 73 px.

| 200 seeds, `average` | mean gates | mean payout | reached the 20 000-tick budget |
| --- | --- | --- | --- |
| nothing equipped | 79.57 | 339.00 | 9.0 % |
| `dash`, before | 13.86 | 73.48 | 0.0 % |
| `dash`, after | 87.14 | 359.43 | 11.5 % |

The fix is behavioural, not numeric: the burst asks for **ghost until clear** when it releases — the
same rule D9 gives a shield absorb — so it always ends clear of what it flew into. The alternative
(raising level 1's `invulnExtraTicks` to ≥ 15) would have bought the same ticks in data and left the
same trap one tier of scroll speed away. `AbilityBotRunTest.noAbilityMakesTheBotWorseThanFlyingWithNone`
now sweeps 24 seeds per ability and fails the build if any ability drops below 0.75 of the
ability-free baseline, which is what would have caught this.

### 7.3 `coin_magnet` is worth +0.13 coins a run in M5 content

200 seeds, `average` bot: mean payout **339.13** with the magnet against **339.00** without;
collection 97.4 % of the spawned trail against 97.1 %. Raising `COIN_SPAWN_RATE` to 4.0 moves it
from 96.0 % to 97.5 % — +532 coins over 33 896 coins spawned across 100 runs.

The cause is structural, not a weak radius: E2 lays the coin trail along `Obstacle.safeBandY`, which
is the line the bird has to fly anyway, so there is nothing off-path left to attract. Widening the
trail vertically would fix that and was **not** done in M5: coin positions are folded into
`Simulation.stateHash` through `PickupLayer`, so any change to the trail moves the published
`--headless-run` hash (`eaaa01685261a433`), which M5 must not.

What M5 did instead is price it from the measurement: **120 / 240 / 480** instead of 250 / 500 /
1000, which makes it the cheapest ability in the shop rather than the price of a dash. It becomes a
real purchase when M6's coin modifiers and M7's obstacle families put coins somewhere other than the
flight line, and M9's MetaSim should re-measure it then — together with `lodestone_1` (§6.2), which
has the same problem for the same reason.

### 7.4 `shield` and `emergency_recovery` measure the same, and stack

200 seeds, `average` bot, 20 000 ticks:

| Loadout | mean gates | absorbs | revives |
| --- | --- | --- | --- |
| nothing | 79.57 | 0.00 | 0.00 |
| `shield` | 158.84 | 0.91 | 0.00 |
| `emergency_recovery` | 158.47 | 0.00 | 0.91 |
| both | **205.38** | 0.91 | 0.71 |

That the two are indistinguishable in isolation is what D9 specifies: each absorbs exactly one lethal
hit. The recovery's kick (`−FLAP_VELOCITY × 1.0`) and its longer window (90 i-frames against 45) do
not show up because the bot flies the same line either way. They are therefore sold as a **pair**,
not as a ladder — two passive slots, two charges, 205.4 gates — and the difference between them
arrives with the shield's level-2 regeneration (one charge back every 15 gates, every 10 at level 3),
which is the only thing in the pair that scales. `emergency_recovery` costing more (400 against 200)
and unlocking later (150 total gates against 5 runs) is recorded here as an open question for M9's
retune rather than as settled balance.

### 7.5 A save on the ground lifts the bird 80 px

Every save that cancels a `GROUND` hit — invulnerability ticks, the ghost state, a shield charge or a
revive — puts the bird back at `GROUND_DEATH_Y − 80` with `vy = 0`. The lift is not decoration: the
M1 ground rule kills anything at or below the ground line at the *start* of the next tick, so without
it a charge would buy exactly one tick. 80 px is about 18 ticks of free fall, which is the window the
player gets to fly out of it.

Two consequences are deliberate:

* **A shield charge does save a dive into the ground**, the one hazard the classic feel treats as
  final. D9 says a charge absorbs "one lethal hit" and a ground death is one. Measured with three
  charges (`tempered_shield_1` at level 2 plus the ability) and no input at all: absorbs at ticks
  48, 102 and 156, the run ending at tick 210 — about 54 ticks per charge, because the absorb also
  grants 45 invulnerability ticks and those now cover the ground too, lifting the bird each time it
  sinks back into it. It costs a charge, emits `ShieldAbsorbed` and plays the shield cue — but it
  has no distinct animation, which is an M7 presentation item.
* **Nothing is lifted in mid-air.** A revive there gets its velocity kick and stays in the column it
  was in; the shield's identical lift was already guarded that way, and the revive's was not until
  the M5 review pass.

### 7.6 The harness

`BalancingSim` gained `--ability <id|all|none>` and `--ability-level <n>` in M5, and prints an
`ability uses / shield absorbs / revives` line per cell. `--ability all` sweeps the ability-free
baseline plus all eight, which is what produced §7.1–7.4.

The `--headless-run` hash is **unchanged through M5**, verified on JDK 17 and JDK 21:
`--headless-run 3000 --seed 42` prints `hash=eaaa01685261a433 ticks=3000 gates=36 points=36` and the
600-frame line CI compares is `hash=b014de5e0ccf63dc ticks=600 gates=6 points=6`. It cannot move
with a loadout: `GameApplication.simulationHashLine` builds `RunConfig.classic(seed)` — classic
bird, Green Fields, tier `normal`, nothing equipped — and reads no profile at all, and
`Simulation.stateHash` folds ability, shield and revive state only when the run has some
(`hasRunSystems()`). That is what keeps the number comparable across milestones that add systems
around it.

## 8. Modifiers and synergies (M6)

Every number below is `BalancingSim` output on the shipped content, `classic` bird,
`green_fields`, tier `normal`, nothing equipped — with one exception, flagged where it appears:

```
./gradlew balancing -PtoolArgs="--seeds 300 --skill average --drafts --ticks 20000"
./gradlew balancing -PtoolArgs="--seeds 200 --skill all --ticks 20000 --modifier all"
./gradlew balancing -PtoolArgs="--seeds 200 --skill all --ticks 20000 --modifier stormrider,tailwind"
```

`--drafts` makes every modifier available, turns offers on and lets the bot answer them by taking
the first card (D21). `--modifier` does the opposite: it *forces* a named card (or a comma-separated
build) on every run of a cell and prints what it was worth against the same seeds without it, which
is the only way to price one card at a time.

### 8.1 The draft as a bot plays it

300 seeds, average preset, 20 000-tick budget, one card taken per offer:

| measurement | value |
|---|---|
| runs reaching offer 1 / 2 / 3 | 78.7 % / 68.7 % / 56.7 % |
| runs reaching offer 4 / 5 / 6 | 46.0 % / 34.3 % / 24.7 % |
| cards taken | 927 over 300 runs (3.09 per run) |
| rarity of the cards taken | COMMON 62.0 %, RARE 26.9 %, EPIC 10.2 %, LEGENDARY 0.9 % |
| rarity on the table (5000 sampled drafts, 3 cards each) | COMMON 57.8 %, RARE 29.9 %, EPIC 10.8 %, LEGENDARY 1.5 % |
| runs activating at least one synergy | 95/300 (31.7 %) |
| **of the runs that reached offer 3** | **92/170 (54.1 %)** |
| time spent in a breather waiting for clear air | 962 breathers, p50 241 ticks, mean 4.03 s, 9.9 % of all live ticks |

The synergy row is §6's M6 criterion — an average bot that reaches the third offer activates at
least one set bonus in at least 20 % of those runs — and it clears it by a factor of two and a
half. It is reported here in M6 and asserted by `MetaSimTest` in M9.

The breather row is the one number here the tool does not print: it comes from driving the same
300 runs through `Run.tick` and counting the phase of every tick. It matters because a breather is
a *live* phase — the world ticks while the draft waits for clear air — so a tenth of a drafting run
is time the player must still be able to pause (D2); `GameScreenTest` holds that down.

Two things the table says that are worth keeping:

- **The mix on the table follows `rarityWeights` (60/28/10/2) to within about two points.** The
  "taken" column is the first draw of each draft, so it measures the same distribution from the
  other side; the "pool" column includes the second and third cards, where drawing without
  replacement pulls the mix slightly towards the rarer classes, and where E12's derived eligibility
  removes the two ability-timing cards for a loadout that has no ability timers at all (§8.2).
- **The bot takes 3.09 cards per run and a third of its runs build a synergy.** That is with the
  worst possible drafting policy: the first card, every time. A player who reads the tags is the
  reason the number is a floor and not a target.

### 8.2 What one card is worth

200 seeds per cell, 20 000-tick budget, four skill presets, each card forced on every run of its
cell. `Δpayout` is against the same seeds with nothing forced; the baseline is
34.3 / 339.0 / 1015.1 / 999.9 mean payout (novice / average / expert / perfect) at
4.8 / 79.6 / 239.8 / 237.2 mean gates.

| Card | rarity | novice | average | expert | perfect |
| --- | --- | --- | --- | --- | --- |
| `score_plus` | C | +1.4 % | +2.4 % | +2.4 % | +2.4 % |
| `coin_drops` | C | +2.2 % | +3.5 % | +3.5 % | +3.5 % |
| `tailwind` | C | −0.1 % | +10.4 % | −1.1 % | −1.0 % |
| `slower_obstacles` | C | −0.5 % | +3.1 % | −6.5 % | −5.9 % |
| `quick_hands` | C | +0.0 % | +0.0 % | +0.0 % | +0.0 % |
| `light_frame` | R | +4.9 % | −14.1 % | +2.3 % | +3.3 % |
| `streak_bounty` | R | +8.8 % | +23.8 % | +30.5 % | +29.8 % |
| `magnet_burst` | R | +14.5 % | +13.2 % | +13.2 % | +13.2 % |
| `wide_gaps` | R | −0.2 % | +1.0 % | +4.0 % | +3.7 % |
| `temp_shield` | E | +83.9 % | +94.7 % | +3.3 % | +4.5 % |
| `second_wind` | E | +80.7 % | +91.3 % | +3.3 % | +4.5 % |
| `heavy_wallet` | E | +23.4 % | +12.1 % | +27.1 % | +27.9 % |
| `glass_wings` | E | −5.7 % | −6.5 % | −16.9 % | −13.4 % |
| `long_fuse` | E | +0.0 % | +0.0 % | +0.0 % | +0.0 % |
| `gold_rush` | L | +97.7 % | +119.2 % | +79.8 % | +83.5 % |
| `phoenix` | L | +88.4 % | +85.0 % | −24.0 % | −23.1 % |
| `stormrider` | L | +12.1 % | +32.8 % | +8.9 % | +11.2 % |

What the sweep changed in the shipped file, and why:

- **`temp_shield` was a RARE worth more than every EPIC and LEGENDARY.** Over 120 average-preset
  seeds on a 120 000-tick budget it measured +111.6 % against `second_wind`'s +108.1 % — one stack
  of a RARE for one EPIC — and its `maxStacks` was 2. It is now EPIC with `maxStacks` 1, so the
  draft no longer has one obviously correct answer whenever a DEFENSE card is on the table.
- **The SPEED axis was a trap.** `SCROLL_SPEED` is a far steeper difficulty knob than any score
  payoff can pay for: at the authored ×1.25, `stormrider` cost a perfect pilot 93 % of its payout
  and 95 % of its gates over a 120 000-tick budget, and `gold_rush` at ×1.15 cost it 67 %. The
  scroll terms are now ×1.02 (`tailwind`), ×1.05 (`gold_rush`) and ×1.05 (`stormrider`), which is
  what the table above measures. The axis is positive at every preset at the 20 000-tick budget and
  still costs a marathon runner something at 120 000, which is the risk it is supposed to be.
- **`magnet_burst` did nothing.** `MAGNET_RADIUS +60` on a coin trail laid along the gap centre
  (E2) measured +0.0 % at every skill preset — the same ticks, the same gates and the same coins as
  a run without it — because the bird already flies through the coins; §5.2 measures the bot
  collecting 97.5 % of them without any magnet. It keeps the radius, which a human player and M7's
  off-path trails will feel, and gains `COIN_MULT +15 %` so a RARE slot buys something measurable.
- **`phoenix` was `second_wind` with a 30 % coin tax.** Both granted `REVIVES +1` with
  `maxStacks` 1; the LEGENDARY was strictly worse than the EPIC. It now grants two revives, which
  is the `REVIVES` clamp, so the tax buys the longest run in the game: +85 % payout and +160 % gates
  at the average preset, against `second_wind`'s +91 % payout and +96 % gates.
- **`quick_hands` and `long_fuse` are exactly 0.0 % at every preset** for the E18 default loadout,
  because `ABILITY_COOLDOWN_MULT` and `ABILITY_DURATION_MULT` are read by `AbilityInstance` alone
  and `double_flap` declares neither at any level. They are not broken — with `slow_time` equipped
  (`--ability slow_time --modifier quick_hands`, 200 average seeds) `quick_hands` is +2.6 % payout
  and 74.5 → 76.6 gates — so E12's derived eligibility now keeps them off the table for a loadout
  that has no use for them rather than showing a new drafter a blank card roughly one draw in
  eight.

### 8.3 What a set bonus is worth

The four synergies, forced as their cheapest two-card build, same seeds and budget:

| Synergy | build | novice | average | expert | perfect |
| --- | --- | --- | --- | --- | --- |
| `coin_engine` | `coin_drops` + `magnet_burst` | +40.1 % | +38.8 % | +38.8 % | +38.8 % |
| `bulwark` | `temp_shield` + `second_wind` | +242.9 % | +175.9 % | +3.3 % | +4.5 % |
| `needle_threader` | `light_frame` + `wide_gaps` | +9.8 % | +11.7 % | +4.9 % | +5.8 % |
| `daredevil` | `stormrider` + `tailwind` | +20.8 % | +75.4 % | +26.9 % | +31.2 % |

`bulwark`'s expert and perfect columns are the 20 000-tick budget, not the bonus: both builds reach
it, so there is nothing left to buy. `daredevil` measured −48.9 % (average) and −81.3 % (perfect)
on the same seeds with the authored scroll terms — the synergy the SPEED/RISK axis exists for cost
four fifths of an expert's run — which is what made the axis worth measuring one card at a time in
the first place.

### 8.4 Two behaviours worth recording

- **The resume grants 30 i-frames, and they cancel a ground hit like any other lethal hit.** A
  player who dives the moment the countdown ends can buy an altitude reset roughly half a second
  long, six times a run rather than once per spent shield charge. The rule is M5's (§7.5) applied to
  the phase M6 added, and it is deliberate: the resume must never be the tick that kills. If it ever
  reads as an exploit, the fix is a resume-specific counter that cancels obstacle hits only.
- **`glass_wings` is net-negative at every preset** (−5.7 / −6.5 / −16.9 / −13.4 %): `HITBOX +0.15`
  costs more than `SCORE ×1.5` pays, the same shape of problem the SPEED axis had. It ships as the
  plan authored it and is recorded here for M9's balance pass. `light_frame` and `wide_gaps` are
  worth more to a perfect pilot than to an average one, which is §6.4's non-monotone bot rather
  than a property of the cards.

### 8.5 The harness and the hash

`BalancingSim` gained `--drafts` (the aggregate table of §8.1), `--modifier <id|all|build>` and
`--modifier-stacks N` (the per-card table of §8.2) in M6. The forced path applies the same authored
rules a draft would — `maxStacks`, `excludes`, `requiresFlagsAbsent` — so a cell can never measure a
build the game would not let a player hold.

The `--headless-run` hash is **unchanged through M6**, verified on JDK 17 and JDK 21:
`--headless-run 3000 --seed 42` prints `hash=eaaa01685261a433 ticks=3000 gates=36 points=36`. It
cannot move with a draft, for the same reason it could not move with a loadout: the published
configuration carries `ModifierCatalog.EMPTY` and `allowOffers=false`, and `Simulation.stateHash`
folds the draft state only when `ModifierDirector.isActive()` — a run that can draft, or that
already took something.

## 9. What is not yet measured here

The runs-to-unlock table that `BalancingSim --meta` prints (M9, E25) landed as §13; the worlds
are §10. Four M4 measurements, three M5 ones and two M6 ones are
recorded above as open questions rather than as settled balance:
`glide_1` cannot bind (§6.3 — closed by the M9 retune, §12.2), the bot is not monotone in the stats the tree sells (§6.4), Jackdaw and
Oracle do not pay for themselves (§6.5), the participation gate zeroes one novice run in five (§6.6),
`coin_magnet` needs off-path coins to be worth anything (§7.3), `emergency_recovery` and `shield` are
priced apart but measure the same (§7.4), the ground save has no animation (§7.5), `glass_wings` is
net-negative at every preset (§8.4) and a draft resume can be spent as a free altitude reset (§8.4).
The rule stays
the same: a row is added only once a test or a tool measures it — §5's, §7's and §8's numbers are
`BalancingSim` output, not estimates, and the shape of the distributions matters as much as the means
(an expert's coins are almost a constant because the bot reaches the tick budget; a novice's are not).

## 10. Worlds, patterns and tiers (M7)

Every number below is `BalancingSim` output on the shipped `worlds.json` / `patterns.json` after
the M7 review pass (2026-09-02), the classic bird, no ability, no drafts, seeds `1..N`, the
20 000-tick budget unless stated. `ContentFeasibilityTest` (`simTest`) asserts §10.1 and §10.3
on seeds 1–50 with the same bot, budgets and seeds.

### 10.1 The expert reaches the boss gate — world × tier

`./gradlew balancing -PtoolArgs="--seeds 50 --world all --tier all --skill expert"`; success =
`gatesPassed ≥ boss.atGate` (30 / 30 / 30 / 35 / 40). The bar is 30 % on every cell (§6, risk
11); gates are the median and the mean of the same 50 runs, deaths the killer by kind.

| world | tier | reach boss gate | gates p50 / mean | deaths |
| --- | --- | --- | --- | --- |
| green_fields (30) | normal | **100 %** | 248 / 246.3 | ALIVE 49, PIPE_GATE 1 |
| | hard | 84 % | 73 / 106.3 | PIPE_GATE 44, ALIVE 6 |
| | nightmare | **32 %** | 21 / 24.7 | PIPE_GATE 50 |
| wind_valley (30) | normal | 98 % | 137 / 124.9 | ALIVE 29, PIPE_GATE 21 |
| | hard | 98 % | 108 / 107.9 | PIPE_GATE 48, ALIVE 2 |
| | nightmare | 92 % | 57 / 59.7 | PIPE_GATE 50 |
| iron_forge (30) | normal | 98 % | 89 / 119.2 | PIPE_GATE 37, PISTON 12, ALIVE 1 |
| | hard | 80 % | 52 / 66.5 | PIPE_GATE 33, PISTON 17 |
| | nightmare | 64 % | 44 / 44.5 | PIPE_GATE 48, PISTON 2 |
| storm_sky (35) | normal | 90 % | 65 / 70.3 | PIPE_GATE 49, LIGHTNING 1 |
| | hard | 38 % | 28 / 39.2 | PIPE_GATE 50 |
| | nightmare | 38 % | 25 / 29.0 | PIPE_GATE 47, LIGHTNING 3 |
| void (40) | normal | 98 % | 124 / 138.7 | PIPE_GATE 47, LIGHTNING 1, ALIVE 2 |
| | hard | 92 % | 96 / 102.4 | PIPE_GATE 50 |
| | nightmare | 58 % | 55 / 52.6 | PIPE_GATE 49, PISTON 1 |

Every cell is above the bar and the feasibility test holds every cell to it — the 15 % floor the
first M7 pass gave Green Fields nightmare is gone. That cell went from 22 % to 32 % without a
change to Green Fields or to the bot's Green Fields decisions: the nightmare tier's
`ALL_OBSTACLES_MOVE` used to be folded into the gate decision *before* the layout roll, so every
gate took the moving layout mix (¼ floating); the rule is now applied when the decision becomes
an obstacle (E32.d, §10.5) and a gate rolled static keeps the static mix (½ floating), which the
bot survives more often. The tightest cells are the two nightmare gate worlds and Storm Sky's two
upper tiers, all killed by the moving gate at 1.5× scroll (§10.4); M9's tier balance reads them
from here.

### 10.2 Survival by world and preset

`./gradlew balancing -PtoolArgs="--seeds 100 --world all --skill all"` (normal tier). Gates are
nearest-rank percentiles; deaths are counted by the obstacle kind that killed the bird (M7:
`RunStats.deathKind`), `GROUND`/`CEILING` when nothing did, `ALIVE` at the budget.

| world | preset | gates p50 | p90 | mean | budget | deaths |
| --- | --- | --- | --- | --- | --- | --- |
| green_fields | novice | 4 | 13 | 5.5 | 0 % | PIPE_GATE 100 |
| green_fields | average | 63 | 200 | 81.7 | 7 % | PIPE_GATE 93 |
| green_fields | expert | 248 | 248 | 243.0 | 95 % | PIPE_GATE 5 |
| green_fields | perfect | 248 | 248 | 244.5 | 95 % | PIPE_GATE 5 |
| wind_valley | novice | 3 | 13 | 5.5 | 0 % | PIPE_GATE 100 |
| wind_valley | average | 35 | 84 | 40.5 | 0 % | PIPE_GATE 100 |
| wind_valley | expert | 136 | 150 | 123.1 | 60 % | PIPE_GATE 40 |
| wind_valley | perfect | 137 | 150 | 132.0 | 66 % | PIPE_GATE 34 |
| iron_forge | novice | 6 | 20 | 7.8 | 0 % | PIPE_GATE 91, PISTON 5, GEAR 4 |
| iron_forge | average | 40 | 101 | 52.1 | 0 % | PIPE_GATE 73, PISTON 21, GEAR 6 |
| iron_forge | expert | 92 | 173 | 106.9 | 1 % | PIPE_GATE 69, PISTON 30 |
| iron_forge | perfect | 94 | 147 | 101.6 | 1 % | PIPE_GATE 59, PISTON 40 |
| storm_sky | novice | 4 | 17 | 7.0 | 0 % | PIPE_GATE 96, LIGHTNING 4 |
| storm_sky | average | 39 | 92 | 43.2 | 0 % | PIPE_GATE 99, LIGHTNING 1 |
| storm_sky | expert | 66 | 123 | 74.1 | 0 % | PIPE_GATE 95, LIGHTNING 5 |
| storm_sky | perfect | 64 | 118 | 70.7 | 0 % | PIPE_GATE 97, LIGHTNING 3 |
| void | novice | 5 | 17 | 7.7 | 0 % | PIPE_GATE 90, GEAR 5, LIGHTNING 4, PISTON 1 |
| void | average | 54 | 101 | 56.3 | 0 % | PIPE_GATE 90, LIGHTNING 5, PISTON 3, GEAR 2 |
| void | expert | 129 | 243 | 139.9 | 2 % | PIPE_GATE 96, LIGHTNING 1, GEAR 1 |
| void | perfect | 136 | 264 | 151.6 | 6 % | PIPE_GATE 94 |

What changed against the first M7 pass, and why. Iron Forge's perfect run went from 82 to 94
median gates and its gear deaths from 10 % to 0 %: the pilot now clears a gear on the side that
leads on (§10.4) and the cursor keeps 120 px of clear air after a 112 px gear (§10.5). Pistons
took the deaths gears used to cause (33 % → 40 %): a piston 160 px after another column is now the
tightest thing in the world, which is the tier balance's next item. Wind Valley's perfect run
went from 59 % to 66 % of runs reaching the budget once a zone stopped covering the approach to
the gate after it. Storm Sky's perfect bolt deaths are 3 % (4 % before); the Void's are 0 % (5 %
before, all from bolts drawn on the far side of a gear, §10.5), and its perfect median went from
108 to 136 gates. Gates kill everywhere: even in Iron Forge and the Void, where half the spawns
are gears, pistons and bolts, the gate is the killer in 59–97 % of runs — the new families are
read by the oracles and telegraph themselves, a moving gate at 1.5× scroll does not. No world but
Green Fields lets an expert reach the budget: the standard curve's scroll ramp (×1.5 at gate 125)
ends every run around gate 100–250.

### 10.3 Every pattern in isolation

`./gradlew balancing -PtoolArgs="--seeds 50 --pattern all --skill expert --ticks 2400"` — each
pattern in its own world (its curve, effects and ambience), looped from the first spawn; success =
still flying after 2 400 ticks, which is longer than every shipped `surviveTicks`, so surviving it
is surviving a whole boss fight on that phase.

| pattern | world | survive |
| --- | --- | --- |
| wv_updraft_run | wind_valley | 100 % |
| wv_crosswind | wind_valley | 100 % |
| forge_gear_corridor | iron_forge | 100 % |
| forge_piston_row | iron_forge | 98 % |
| storm_bolt_lane | storm_sky | 100 % |
| storm_squall | storm_sky | 100 % |
| void_mixer | void | 100 % |
| void_gauntlet | void | 100 % |
| gf_boss_p1 / gf_boss_p2 | green_fields | 100 % / 100 % |
| wv_boss_p1 / wv_boss_p2 | wind_valley | 86 % / 100 % |
| forge_boss_p1 / forge_boss_p2 | iron_forge | 100 % / 100 % |
| storm_boss_p1 / storm_boss_p2 | storm_sky | 100 % / 100 % |
| void_boss_p1 / p2 / p3 | void | 100 % / 100 % / 100 % |
| corridor_1 / corridor_boss_p1 | green_fields | 100 % / 100 % |

Two data edits from the review pass. `storm_squall` step 3 and `wv_crosswind` step 3 were
`"random"` gates 160 px after a `BOTTOM` bolt and a floating gate: a centre rolled at the bottom
was a 130 px dive in the 23 ticks the nightmare scroll leaves, so they are authored (0.4 and
0.45) and the validator now refuses a random gate right after a bolt. `storm_bolt_lane`'s
`BOTTOM` bolt after the 0.7 gate lit from y 269 — 174 px of climb in 160 px of `dx`, the one
column that killed the expert in Storm Sky nightmare (14 of 50 runs, every one at that step) —
and lights 45 % of the height now (safe above y 329, 114 px of climb); `storm_boss_p1` got the
same treatment (`BOTTOM` 0.6 → 0.4). Six authorings of that lane were measured on Storm Sky ×
tiers before choosing: moving the *gate* down (0.55–0.65) cost 10–20 points of reach rate at
every tier, lowering the *bolt* cost none and removed the deaths. The validator now bounds a
bolt's travel by the scroll to it (§10.5), which is what caught both.

### 10.4 What the bot models, and what it does not

Every change to the pilot in M7 is to its *model of the world*, never to its reaction or its
error, and none of them changes a decision in Green Fields — the published `--headless-run` hash
and the golden run pin the bot there, and every M1–M6 table stands.

1. **The corridor is bounded by the nearest column not yet cleared** (the M6 pilot took the
   farthest one inside its flap window, which with 40 px gates 160 px apart was the same
   column; a pattern step 130 px behind a gate is not).
2. **The flap arc is computed under the wind the bird is in** (D21: a −600 px/s² updraft turns
   the 42 px rise into 68).
3. **The gear oracle keeps its clearance from the chord** the circle cuts through the bird's x
   range on each crossing tick, not from the whole diameter.
4. **A gear is cleared on the side that leads on** (review pass). `Oracles.gearCorridors`
   returns both sides; the pilot aims at the gear band nearer the band of the column after it
   and, with the gear as its current column, takes the side that holds a flap arc, is reachable
   before the crossing and is nearest the aim — the larger side only as a tie-break. A band the
   box fits in but a flap does not (under a gear near the ground) is never chosen: three of the
   six remaining gear-only deaths were exactly that trap. Gear-only table, perfect bot, 20 seeds,
   3 000 ticks: M6 pilot 7/20, first M7 pass 14/20, now 20/20 (`BotOracleTest` requires 20/20
   for every kind). A band consistent with two gears — above both, below both or between —
   always exists for spawn-table gears, so the "pairs of big gears on opposite sides" the first
   pass recorded as unwinnable content were a limit of the bot, not of the data.

What it still does not model, recorded so M9's tier balance reads the tables for what they are:
it picks the first card; it never anticipates a lane change before the current column is
cleared; and its gate oracle uses a moving gate where it is (shrunk by the travel during one
flap rise) rather than where it will be at the crossing tick — D21 asks for the prediction, and
it is the reason a moving gate at 1.5× scroll is the killer in every world above. That change
would move the published hash, so it waits for the M9 baseline re-record.

### 10.5 The fairness rules, in numbers

The review pass added four rules to the spawner and two to the validator; each is a
consequence of the physics, so the numbers are worth writing down.

- **A spawn-table bolt is reachable from the column before it.** The scroll between a 40 px
  column clearing the bird's box and a bolt 160 px on striking is 115 px: 57 ticks at the
  classic 2 px/tick, 33 at the nightmare late-run 3.45, 19 at the 360 px/s cap. Flapping every
  tick the bird climbs 6.25 px/tick; from rest it falls `0.25·n·(n+1)` px in `n` ticks (95 px
  in 19). `SpawnTable.roll` therefore takes the previous decision's reference band
  (`SpawnDecision.referenceBandY`: the gap centre at the default 128 px gap, a gear's larger
  side, a piston's free side, a bolt's unlit side — never the resolved gap or the oscillator,
  so the decision stays seed-only) and swaps the bolt to the side whose unlit band is nearer it,
  then shortens the lit fraction (in hundredths, never below 0.30) until the travel from that
  band to 24 px clear of the lit span fits `LIGHTNING_MAX_TRAVEL_PX` = 80. Over 2 000 gate→bolt
  pairs the rule swaps the side in about a third and shortens about a sixth of the bolts; the
  table also warns from 75 ticks out (patterns keep their authored 45) and the renderer shows an
  idle bolt column from spawn. A gate+bolt table at ×1.5 scroll over the standard ramp is flown
  20/20 seeds with no bolt death; Storm Sky expert bolt deaths per tier: 2 % / 0 % / 6 %.
- **Wide columns keep their clear air.** `x = last.x + max(0, last.width − 40) + 160`: a 112 px
  gear or a 200 px zone pushes the next column out by its extra width, so any two columns have
  at least 120 px between the right edge of one and the left edge of the next; a 24 px bolt is
  never pulled closer. Two big railed gears used to leave 48 px of scroll (~22 ticks) to cross a
  172 px sweep.
- **The breather always finds its window.** `isDraftPathClear` needs no lethal column between
  the bird's hitbox left edge (x 88) and the right edge of the playfield: 332 px, plus a 20 px
  margin so the window is a few ticks wide. D11's 1.5 intervals give a 40 px gate 360 px of
  clear air (fine), a 112 px gear 288 (never clear) and a pattern step `dx + 200` (never clear
  under 132 px). `Simulation.deferSpawn` now passes the 352 px as an absolute floor behind the
  last column's right edge; Green Fields and the 128 px corridor of `ModifierDirectorTest` get
  the same x they always got. Longest breather over every world × 12 seeds × take/skip: under
  300 ticks (`BreatherClearanceTest`; Iron Forge and the Void had 600–2 565 tick breathers
  looping on the 600-tick retry).
- **An authored gap is a base value.** A pattern gate's `gapSize` is scaled by `GAP_SIZE / 128`
  — the tier's ×0.9/×0.8, the standard curve's ramp down to ×0.8, the Void's ×0.85 option, a
  card — and kept centred. The validator's `gapSize × 0.8 × 0.9 ≥ 54.5` now describes what the
  nightmare tier plays; the tightest shipped pattern gate (118 px, `gf_boss_p2`) is 75.5 px on
  nightmare at gate 0 and 60 px at the curve's floor, above the 54.5 a scaled hitbox lands
  through.
- **Validator, patterns.** A gate right after a bolt is authored (never `"random"`) on the
  bolt's unlit side; a bolt's safe band is no further from the previous lethal column's band
  than the scroll between them — `dx − width − 5` px, one px of climb per px of scroll, which is
  the 6.25 px/tick climb at the 6 px/tick cap with the bird free to start moving inside the
  previous gap. The shipped file passes both; `content_bad/bolt_then_gate.json` pins three
  pointers.

---

## 11. Bosses and challenges (M8)

Every number here comes from `./gradlew balancing -PtoolArgs="--seeds 50 --challenge all --skill
all"` and `--seeds 50 --boss all --skill all` (seeds 1–50, 20 000-tick budget, classic bird,
no ability, no drafts), and the two `≥ 30 %` rows are asserted by `ContentFeasibilityTest`
(`@Tag("sim")`) on the same seeds. A challenge is played as the player would
(`RunFactory.challengeConfig`: its world, tier, curve, flags, effects, forced cards, forced
pattern and boss); a boss cell starts the run *at* the boss (`RunSetup.startingAtBoss`: the
warning fires at the first gate and the curve is shifted so that gate plays under the
difficulty of the authored `atGate`), so the table measures the fight and not the road to it —
§10.1 already measures the road.

### 11.1 Challenge objectives, by skill

Objective completion rate over 50 seeds; the expert column is the bar (≥ 30 %).

| challenge | world / objective | novice | average | expert | perfect | expert gates p50 | deaths (expert) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `no_shield_1` | green_fields / SURVIVE_GATES 30 | 0 % | 74 % | **92 %** | 98 % | 69 | gate 50 |
| `speed_run_1` | wind_valley / SURVIVE_GATES 30 (SPEED_RAMP) | 0 % | 34 % | **62 %** | 68 % | 33 | gate 50 |
| `tiny_wings_1` | green_fields / SURVIVE_GATES 20 (FLAP_VELOCITY ×0.7, classic curve) | 0 % | 50 % | **80 %** | 82 % | 33 | gate 49, alive 1 |
| `moving_world_1` | green_fields / SURVIVE_GATES 25 (ALL_OBSTACLES_MOVE) | 6 % | 88 % | **96 %** | 98 % | 66 | gate 50 |
| `one_life_1` | iron_forge / SURVIVE_GATES 30 (NO_DEFENSIVE_ABILITIES, NO_REVIVE) | 0 % | 66 % | **98 %** | 98 % | 89 | gate 37, piston 12, alive 1 |
| `coin_rush_1` | wind_valley / COLLECT_COINS 60 (COIN_SPAWN_RATE ×3, `coin_drops`) | 0 % | 56 % | **98 %** | 100 % | 137 | alive 29, gate 21 |
| `boss_corridor_1` | green_fields / BOSS_CLEARED (corridor_1 looped, own boss at gate 20) | 0 % | 58 % | **100 %** | 100 % | 343 | alive 50 |

Two things the table says:

- **`speed_run_1` was retuned from 40 to 30 gates.** The plan's table (§4) ships it at
  `SURVIVE_GATES 40`; shipped is `30`, a plan deviation recorded here pending sign-off as a new
  errata item against §4 (the plan file itself is not edited). The expert met 40 gates in
  14/50 seeds (28 %) and in 49/200 (24.5 %) — the
  ramp is `SCROLL_SPEED × (1 + 0.0005 × ticksAlive)`, ×2.5 by gate 30, on top of Wind Valley's
  wind zones, and the expert's gate count is p50 29–33 (200-seed CDF: ≥ 30 in 49.5 %, ≥ 32 in
  43.5 %, ≥ 35 in 33 %, ≥ 40 in 24.5 %). The bot is not tuned (§10.4); the objective is. Thirty
  gates puts the expert at 62 % (31/50) and the average bot at 34 %, which is the shape of every
  other challenge; the strings say "thirty" in both languages. The alternative — a compensating
  `GAP_SIZE` effect — would have changed the challenge's feel rather than its length.
- **`boss_corridor_1` is a survival cap, not a fight.** The looped `corridor_1` never opens a gap
  the expert cannot hold, so every expert seed reaches the budget at 343 gates and clears the
  corridor boss (`corridor_boss_p1`, 900 ticks) on the way; the average bot clears it in 58 %
  of seeds. Its reward is `tier:nightmare`, which is what makes that reasonable: the fight is
  the entry ticket, the tier is the challenge.

### 11.2 Boss encounters, by skill

Clear rate over 50 seeds, started at the boss; "phases" is the mean of the furthest phase
reached (2 phases everywhere, 3 in the Void).

| world | fight | novice | average | expert | perfect | phases (expert) | deaths (expert) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| green_fields | 1 200 ticks at gate 30 | 4 % | 72 % | **100 %** | 100 % | 2.00 | alive 47, gate 3 |
| wind_valley | 1 500 ticks at gate 30 | 8 % | 66 % | **96 %** | 100 % | 2.00 | gate 30, alive 20 |
| iron_forge | 1 800 ticks at gate 30 | 0 % | 46 % | **100 %** | 100 % | 2.00 | gate 31, piston 19 |
| storm_sky | 1 800 ticks at gate 35 | 4 % | 70 % | **100 %** | 100 % | 2.00 | gate 50 |
| void | 2 100 ticks at gate 40 | 0 % | 66 % | **100 %** | 98 % | 3.00 | gate 50 |

The bosses are, for the expert, easier than the road to them (§10.1: 32 % on Green Fields
nightmare, 38 % on Storm Sky hard) — the phases are authored patterns the bot's per-kind
oracles read exactly, and §10.3 already showed every phase survivable in isolation. The novice
column is the honest one: a boss is a wall for a beginner and a coin flip for the average bot,
which is the difficulty the world unlock chain (`world_cleared` or a purchase, E18) is designed
around. No boss block was changed (M9 later softened one phase pattern — the note below).

### 11.2.1 Boss clear by tier, and the `forge_boss_p2` fix (M9 review)

The table above measured every boss on the default tier only, and the M9 review swept the
encounters per tier (`--boss all --tier all`, 2026-09-04): every world × tier cell clears 30 %
with the expert — except **iron_forge on hard: 0/30 at every skill**, all thirty expert deaths a
`PISTON`, while the same fight on the same build cleared 30/30 on normal and 30/30 on nightmare.
A 100-seed perfect sweep on hard scored 0/100, and whole-run clears on hard (`--world
iron_forge --tier hard --seeds 40`) were 0/40 expert, 0/40 perfect and 1/40 average — so on hard
the boss reward (400 coins) and the `world:storm_sky` unlock were unreachable, and the daily
(`economy.daily.tierPool [normal, hard]`) could draw a day whose boss payout could not be earned.

The isolated pattern was *not* the problem (§10.3: survivable for 2 400 ticks) and the tier
shape alone was not either: reverting only the speed (`×1.15`) or only the gap (`×0.90`) left
the perfect pilot at 0/20 — the fight hung on which phase the presses had reached when their
columns crossed the bird at the hard combination (world `×1.10` on top of tier `×1.10`). The
root cause is geometric: `forge_boss_p2`'s presses were `length 300`, and `2 × 300 > 598` leaves
the top and bottom corridors with **no overlap** — a crossing window in which both heads reach
their authored extension has no legal y, so survival depended on the arrival phase, and hard's
speed landed the columns in the trapped phases while normal and nightmare landed them in open
ones. The fix is the shape `void_boss_p2` already plays: `length 300 → 260` on all four presses,
so the corridors overlap at every arrival phase and the fight is won by flying, not by phase
luck. `dx`, `telegraphTicks` and `phaseOffset` are untouched; no tier block moved for this.

After the fix (`--boss iron_forge`, expert, 30 seeds per tier): normal 30/30, hard 30/30,
nightmare 30/30; perfect, 100 seeds per tier: 99/97/95. In-run clears on hard are back to the
shape of the other worlds: average 35 %, expert 85 %, perfect 90 % (40 seeds). The per-tier
sweep is now a gate — `ContentFeasibilityTest.theExpertSurvivesEveryWorldBossEncounter` loops
every world over `normal`, `hard` and `nightmare` at the same 30 % bar, so an unwinnable tier
boss cannot ship again. The shipped per-tier table (expert, 50 seeds, started at the boss):

| world | normal | hard | nightmare |
| --- | --- | --- | --- |
| green_fields | 100 % | 94 % | 94 % |
| wind_valley | 96 % | 98 % | 92 % |
| iron_forge | 100 % | 96 % | 98 % |
| storm_sky | 100 % | 96 % | 90 % |
| void | 100 % | 94 % | 98 % |

### 11.3 What the encounter does to a run, in numbers

- **Warning.** 120 ticks (150 in the Void) with spawning suppressed: 240 px of scroll at the
  classic speed, so the last table column is gone from the screen before the first phase
  column appears; that first column is floored at the right edge (`ObstacleSpawner` resume
  floor) because the cursor's `last.x + 160` would otherwise land inside the playfield.
- **Fight.** The phases loop by ticks, not by columns: 1 200 ticks of Green Fields at
  2 px/tick is 2 400 px, five loops of the two 640/600 px phases, and a scroll card makes it
  more columns in the same time. The spawn decisions are therefore seed-only *up to the
  warning* (`DeterminismTest`), and the number of phase steps — and where the streams resume
  after the clear — depends on the build; E32.d's invariance is asserted on the prefix.
- **Clear.** The next ordinary column is 1.5 intervals further out (400 px behind the last
  phase column instead of 160), and an offer whose schedule gate fell inside the encounter opens
  in that air, after the last phase column passes the bird (`BossOfferInterplayTest`).
- **Pay.** A first Green Fields clear is `bossBonus 150 + boss.reward.coins 200` on the boss
  line plus `world:wind_valley`; a repeat is 150. `boss_corridor_1` pays on the challenge
  line only (`challengeBonus 100 + 500` once, then 100): a challenge boss is not a world boss
  (E26) and enters neither the boss coin term nor `xp.bossBonus`.
- **The pinned run.** `RunConfig.classic` keeps `bossEnabled` off, so `--headless-run 3000
  --seed 42` still prints `hash=eaaa01685261a433 ticks=3000 gates=36 points=36` — that run
  passes gate 30, where Green Fields' boss would have started, and the golden fixture is
  untouched. Every profile run, challenge, balancing cell and feasibility row has the boss on.

### 11.4 The music renders

Every world's loop is rendered synchronously — at boot for the menu, at run start for the world —
so the render cost is a startup/budget number, not a frame number. `MusicSequencerTest` times one
render per shipped world and prints it; the run of 2026-09-03 on the development machine
(JDK 17) measured:

| world | base loop | boss variant (×1.15 tempo) | loop length |
| --- | --- | --- | --- |
| green_fields | 17 ms | 13 ms | 756 032 frames |
| wind_valley | 27 ms | 23 ms | 881 984 frames |
| iron_forge | 12 ms | 10 ms | 672 000 frames |
| storm_sky | 14 ms | 13 ms | 631 872 frames |
| void | 21 ms | 19 ms | 814 144 frames |

The cost scales with tempo (a faster 8-bar loop is shorter in frames but draws more notes) and
with the layer count; the range across the shipped content is 10–27 ms against the 150 ms budget
the test asserts (`RENDER_BUDGET_MS`), so a new world has room to be denser than any shipped
one. The numbers move a few ms between runs and machines — the assertion is the budget, not
these cells; re-run `./gradlew test --tests '*MusicSequencerTest*'` after retuning a block.

## 12. M9 tier balance and the `glide_1` retune

Both retunes are data-only: `difficulty.json`'s two tier blocks and `upgrades.json`'s `glide_1`
node, plus the one boss-phase softening the M9 review demanded (`forge_boss_p2`'s press length,
§11.2.1), each verified against the published hash (`hash=eaaa01685261a433`) afterwards — the
classic headless run carries no tier, no upgrades and no boss, so none of the three can move it.

### 12.1 The hard and nightmare tiers (D20, E19, E25)

The expert's boss-gate reach per world × tier cell is `ContentFeasibilityTest`'s first method
(50 seeds per cell, `--world all --tier all --skill expert --seeds 50`), held to the same 30 %
bar (`MIN_RATE = 0.30`) the M8 challenge and boss rows use. On the D20 shape the table measured
(reach of `boss.atGate`, percent of 50 seeds, review pass of 2026-09-04):

| world | normal | hard | nightmare |
| --- | --- | --- | --- |
| green_fields | 100 | 84 | **32** |
| wind_valley | 98 | 98 | 92 |
| iron_forge | 98 | 80 | 64 |
| storm_sky | 90 | **38** | **38** |
| void | 98 | 92 | 58 |

Every cell clears the bar, but the three tightest sit two to eight points over it — inside the
noise of a 50-seed binomial (the 95 % interval of a 32 % cell spans roughly 20–47 %). The shipped
shape softens the speed and gap multipliers only:

* `hard`: `SCROLL_SPEED ×1.15 → ×1.10`, `GAP_SIZE ×0.9 → ×0.92`;
* `nightmare`: `SCROLL_SPEED ×1.3 → ×1.20`, `GAP_SIZE ×0.8 → ×0.85`.

The flags (`ALL_OBSTACLES_MOVE`, `LETHAL_CEILING`), the reward multipliers (1.5 / 2.5), the
classic and standard curves, the normal tier and `birds.json` are untouched; the diff of
`difficulty.json` against the M8 commit moves exactly the two tier blocks and this section's
comment. The table after the retune:

| world | normal | hard | nightmare |
| --- | --- | --- | --- |
| green_fields | 100 | 96 | 64 |
| wind_valley | 98 | 94 | 96 |
| iron_forge | 98 | 84 | 86 |
| storm_sky | 90 | 52 | 46 |
| void | 98 | 92 | 78 |

The tightest cell moves from 32 % to 46 % (storm_sky nightmare) and no cell lost ground on hard
beyond noise; the bar is now cleared with a margin a 50-seed cell can actually resolve.

### 12.2 `glide_1`: from a dead node to a binding one (§6.3 closed)

§6.3's M9 fork was "give `glide_1` an effect that binds inside the reachable range … or drop it
from `updraft_1.prereqs`". The measurement closes it with the first option, in data.

**The census.** Driven by the section 6.2 methodology (bird `classic`, tier `normal`, Green
Fields, `average` bot), the deepest per-run dive over the seed families 1–1000 and
100000–100999 (2000 runs) is p50 1035, p75 1065, p90 1095, p95 1125, p99 1155, max 1215 px/s —
far under both the 1500 px/s cap and §6.3's 1447 px/s free-fall bound, so the plan's `−0.10`
(cap 1350) never engages and caps 1200 and 1125 engage on almost no seed.

**The sweep.** Mean ΔGates / ΔPayout against the no-node baseline (71.92 gates / 375.30 payout
on seeds 1–1000), one row per per-level value, `average` bot:

| per-level value | L1 cap | L2 cap | L1 ΔGates / ΔPayout | L2 ΔGates / ΔPayout |
| --- | --- | --- | --- | --- |
| `−0.10` (plan) | 1350 | 1200 | **+0.00 / +0.00** | **+0.00 / +0.00** |
| `−0.25` (first M9 draft) | 1125 | 750 | +0.00 / +0.00 (300 seeds) | −5.97 / −31.15 (300 seeds) |
| `−0.30` linear | 1050 | 600 | +2.53 / +10.25 (300 seeds); +1.40 / +6.16 (1000) | +1.17 / +6.08 (300 seeds); **−4.17 / −20.54** (1000) |
| `−0.35` linear | 975 | 450 | +1.47 / +4.57 (300 seeds) | −13.73 / −64.76 (300 seeds) |
| `−0.40` linear | 900 | 300 (floor) | −3.17 / −15.57 (300 seeds) | −39.92 / −198.13 (300 seeds) |

Two facts fall out. Every cap that binds shallowly (1350, 1200, 1125) measures exactly zero —
the node cannot register until its cap cuts into the dive distribution, whose top is ~1155–1215.
And every cap that binds deeply (900 and below) measures two to three sigma negative: the clamped
dive arrives at the low band too late. The only operating points that are not zero or negative
are caps 1050 and 975.

**The shipped shape.** `effectsPerLevel −0.30` (L1 cap 1050) with the format's `levelOverrides`
carrying its first use: level 2 is overridden to `−0.35` (cap 975) instead of the linear `−0.60`
(cap 600, the 1000-seed negative above). Pooled over both 1000-seed families, against the
no-node baseline (71.90 gates / 375.17 payout over 2000 runs):

| owned level | cap | ΔGates | ΔPayout | deaths per 2000 |
| --- | --- | --- | --- | --- |
| L1 (90 coins) | 1050 | +0.71 | +2.90 | 925 vs 937 (−12) |
| L2 (290 total) | 975 | +1.09 | +3.60 | 918 vs 937 (−19) |

The deltas are small — about one sigma — but they agree in direction across both families and
the death count, and the expert bot is unchanged (240.45 / 239.83 / 242.05 gates at L0/L1/L2 on
300 seeds, well inside its own spread). The plan's shape measured *exactly* nothing; the shipped
shape measurably survives longer and pays more, which is what E25's spender needs from the
second-cheapest node on the flight tree. `updraft_1` keeps `glide_1` as a prerequisite now that
the prerequisite is alive. Prices are untouched (90/200), so `NewPlayerJourneyTest`'s run-window
pins hold; the economy needs no compensation because neither the tier reward multipliers nor any
node cost moved.

*Measurement note:* the harness was the section 6.2 cell methodology driven through
`RunConfig.Builder.permanentEffects` + `UpgradeDef.effectsAt` (the same binding the run loadout
uses), deleted after recording; re-derive any row with a five-line variant of the §6.2 harness.

## 13. MetaSim (M9)

§6 measured the meta-progression cell by cell; this section closes it with the measurement E25
demands — whole careers. `MetaSim` (`gameplay/harness`) plays a fresh profile run after run
through the *real* progression stack (`ProgressionManager.apply` with the shipped
`AchievementEvaluator` and `UnlockEvaluator`, purchases through `UnlockManager` and
`UpgradeManager`) under one of the two purchase policies, and `BalancingSim --meta` prints the
runs-to-unlock table: per non-cosmetic unlockable id, the run index at which the id was first
owned, averaged over the seed lines (a purchase made in the shopping pass after run *r* counts
as run *r*; the defaults the fresh profile already owns, E18, are recorded as owned at run 0 —
that is why `bird:classic` and `tree:flight` read 0.0).

**The fixed rules.** Both policies fly the same runs and differ only at the shop, so a table row
means one thing:

* the run is the default cell (classic bird, Green Fields, normal tier) with the *hard* tier as
  soon as `tier:hard` is owned (E25 asks the spender to max the trees "playing `tier:hard` once
  unlocked"); the owned abilities are auto-equipped in content order — the first owned `ACTIVE`
  ability into the active slot, every owned `PASSIVE` ability into the passive slots (the run
  strips what the bird's slots cannot hold, D9);
* `spender` empties the wallet every run by priority class — features, then worlds, then birds
  and abilities, then ability levels, then trees and nodes — always the cheapest affordable item
  of the current class, skipping to the next class when nothing there is affordable, never
  hoarding (trees ride with the node class: a node whose tree is locked is bought by unlocking
  the tree first). Cosmetics and the three purchasable modifiers sit outside the classes E25
  names, so the spender never buys them; the modifiers arrive through their level branch instead;
* `saver` buys at most one item per run, the cheapest not-yet-owned world or feature, and keeps
  the rest — the player who saves for the next world and nothing else;
* the skill is a shipped `BotPilot` preset (novice 12/24, average 8/12). Nothing here tunes the
  bot: when a threshold below failed, the lever was a price or a reward in `data/*.json`, never
  the pilot;
* determinism: seed line *l* plays run *i* on seed `1_000_000 × (firstSeed + l) + i`, iteration
  is content order everywhere, the clock is an injected `TimeSource` stepped once per run. Every
  number in this section reproduces from the command lines verbatim.

**Commands.** `./gradlew --offline balancing -PtoolArgs="--meta --policy spender --skill average
--runs 250"` (the E25 gate cell, 20 seed lines from seed 1, tick budget 20 000), `--policy saver
--skill average --runs 60`, `--policy saver --skill novice --runs 60` and `--policy spender
--skill novice --runs 8` (the E17 journey cell). `MetaSimTest` (@sim) asserts the same cells.

### 13.1 The E25 thresholds, measured

| threshold | bound | measured | where asserted |
| --- | --- | --- | --- |
| spender-average owns every non-cosmetic unlockable | ≤ 200 runs | **mean 25.0**, worst 25, 20/20 lines | `MetaSimTest` |
| spender-average maxes every node and ability level | ≤ 600 runs | **mean 15.3**, 20/20 lines | `MetaSimTest` |
| saver-average reaches `world:wind_valley` | ≤ 10 runs | **mean 2.2**, worst 4 | `MetaSimTest` |
| saver-novice reaches `world:wind_valley` | ≤ 15 runs | **mean 7.0**, worst 9 | `MetaSimTest` |
| spender-novice buys `feather_1` (E17) | ≤ run 3 | **mean 1.15**, worst 3 | `MetaSimTest` |
| spender-novice owns `bird:guardian` (E17) | ≤ run 3 | **mean 3.0**, worst 3 | `MetaSimTest` |
| spender-novice owns `ability:shield` (E17) | ≤ run 5 | **mean 5.0**, worst 5 | `MetaSimTest` |
| spender-novice owns `feature:modifiers` (E17) | ≤ run 7 | **mean 5.6**, worst 7 | `MetaSimTest` |
| synergy activation among runs reaching offer 3 (M6) | ≥ 20 % | **69.9 %** (304 of 435) | `MetaSimTest` |

All nine gates pass on the shipped data. **No data tuning was needed for the meta thresholds** —
the only M9 data changes remain §12's blocks (the two tiers, `glide_1` and the `forge_boss_p2`
softening of §11.2.1), none of which carries a meta price or reward. The
spender's lines end early: everything is owned and maxed by run 25 on average, so the 250-run
budget is never reached (the simulation stops once both milestones are met). The synergy rate is
measured over the spender-average runs that opened the third modifier offer; the novice cells
almost never reach offer 3 (one run in the saver-novice cell did), so the M6 criterion is only
meaningful for the average bot, which is the bot the M6 criterion names.

### 13.2 The runs-to-unlock table (spender-average, 20 seed lines)

`runs<=250`, seed family from seed 1; "mean / worst" is the run index at which the id was first
owned; "owned" is the lines that owned it. Every line owns everything, so the ≤ 200 gate reads
directly off the worst column: the last id to land is `challenge:one_life_1` at run 25 (its
`all_of` of endgame purchases is the designed tail, and it *is* the completion row of §13.1).

| id | mean | worst | owned |
| --- | --- | --- | --- |
| bird:classic | 0.0 | 0 | 20/20 |
| bird:swift | 1.4 | 4 | 20/20 |
| bird:heavy | 2.1 | 4 | 20/20 |
| bird:guardian | 1.8 | 3 | 20/20 |
| bird:gambler | 1.7 | 6 | 20/20 |
| bird:mystic | 3.5 | 6 | 20/20 |
| bird:forge | 2.7 | 6 | 20/20 |
| ability:double_flap | 0.0 | 0 | 20/20 |
| ability:shield | 2.8 | 5 | 20/20 |
| ability:dash | 1.4 | 3 | 20/20 |
| ability:coin_magnet | 1.5 | 5 | 20/20 |
| ability:slow_time | 1.6 | 4 | 20/20 |
| ability:emergency_recovery | 3.3 | 6 | 20/20 |
| ability:score_multiplier | 1.5 | 5 | 20/20 |
| ability:invulnerability | 3.5 | 6 | 20/20 |
| modifier:tailwind | 1.0 | 1 | 20/20 |
| modifier:score_plus | 1.0 | 1 | 20/20 |
| modifier:coin_drops | 1.0 | 1 | 20/20 |
| modifier:slower_obstacles | 1.0 | 1 | 20/20 |
| modifier:quick_hands | 1.0 | 1 | 20/20 |
| modifier:light_frame | 1.0 | 1 | 20/20 |
| modifier:streak_bounty | 1.0 | 1 | 20/20 |
| modifier:temp_shield | 1.0 | 1 | 20/20 |
| modifier:magnet_burst | 1.0 | 1 | 20/20 |
| modifier:wide_gaps | 1.0 | 1 | 20/20 |
| modifier:heavy_wallet | 1.0 | 1 | 20/20 |
| modifier:glass_wings | 1.0 | 1 | 20/20 |
| modifier:second_wind | 1.0 | 1 | 20/20 |
| modifier:long_fuse | 1.0 | 1 | 20/20 |
| modifier:gold_rush | 2.6 | 6 | 20/20 |
| modifier:phoenix | 2.6 | 6 | 20/20 |
| modifier:stormrider | 2.6 | 6 | 20/20 |
| tree:flight | 0.0 | 0 | 20/20 |
| tree:economy | 1.4 | 4 | 20/20 |
| tree:forge | 7.6 | 13 | 20/20 |
| tier:normal | 0.0 | 0 | 20/20 |
| tier:hard | 2.5 | 6 | 20/20 |
| tier:nightmare | 5.2 | 8 | 20/20 |
| world:green_fields | 0.0 | 0 | 20/20 |
| world:wind_valley | 2.5 | 6 | 20/20 |
| world:iron_forge | 2.7 | 6 | 20/20 |
| world:storm_sky | 3.2 | 6 | 20/20 |
| world:void | 4.2 | 6 | 20/20 |
| challenge:no_shield_1 | 1.5 | 5 | 20/20 |
| challenge:speed_run_1 | 1.7 | 6 | 20/20 |
| challenge:tiny_wings_1 | 1.4 | 4 | 20/20 |
| challenge:moving_world_1 | 2.7 | 6 | 20/20 |
| challenge:one_life_1 | 25.0 | 25 | 20/20 |
| challenge:coin_rush_1 | 2.3 | 5 | 20/20 |
| challenge:boss_corridor_1 | 2.7 | 6 | 20/20 |
| feature:modifiers | 1.6 | 5 | 20/20 |
| feature:seeded_runs | 1.4 | 3 | 20/20 |

The headline for the reader: an average player who buys something every run has everything the
game sells by run 25 — 12.5 % of E25's 200-run budget — and the trees maxed by run 15, because the
hard tier's ×1.5 reward multiplier arrives at run 2.5 and the economy compounds from there. The
tail is one designed id (`challenge:one_life_1`, an `all_of` of endgame purchases), not a
grind wall; if a future content change wants a longer progression curve, the lever is the
challenge's condition and the late prices, not the reward rate that nine thresholds above sit
on.

### 13.3 The saver cells

The saver never buys birds, abilities, tiers or nodes, so its table is sparse by construction:
birds arrive through their unlock conditions (`bird:heavy` mean 4.0, `bird:mystic` 19/20 lines at
60 runs under saver-novice), and 0/20 lines complete or max — the saver is not a completion
policy, it is the "how fast is world 2" instrument:

| cell | `world:wind_valley` mean / worst | bound |
| --- | --- | --- |
| saver-average, 60 runs | **2.2 / 4** | ≤ 10 |
| saver-novice, 60 runs | **7.0 / 9** | ≤ 15 |

Under saver-novice the later worlds land at `world:iron_forge` 14.0, `world:storm_sky` 19.3,
`world:void` 34.7 — a rough "one world every 7–15 novice runs" cadence that the M4 prices were
designed for and that these cells now measure.

### 13.4 What the cells do not cover

* The spender-novice cell at 60 runs does *not* complete (0/20 maxed): a novice spender needs
  roughly 5× the average bot's run count to max the trees. E25 sets no novice maxing gate, so
  this is recorded, not thresholded.
* `MetaSim` tracks node first-buys (`Outcome.meanFirstBuy`) for the E17 journey but the printed
  table covers unlockables only; §13.1's `feather_1` row was read from `Outcome` directly.
* Prestige is out of scope for the simulation: its spender lines end at run 25 on average (the
  simulation stops once everything is owned and maxed), so it never plays a second career, and
  E25's thresholds are written about a first one. `PrestigeSystem`'s semantics carry their own
  test (`PrestigeSystemTest`) and its conditions are cosmetic-only (E20).
