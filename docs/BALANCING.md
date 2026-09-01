# Balancing — the conversion table (M1) and the run economy (M3)

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

Sections 1–4 are the M1 conversion; section 5 is the M3 economy, and the same rule holds there —
its numbers are `BalancingSim` output over 500 seeds per skill, not estimates.

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

## 6. What is not yet measured here

Upgrades and bird stat spreads (M4), abilities (M5), modifiers and synergies (M6), the other four
worlds (M7), and the runs-to-unlock table that `BalancingSim --meta` prints (M9, E25) all extend
this document as they land. The rule stays the same: a row is added only once a test or a tool
measures it — §5's numbers are `BalancingSim` output, not estimates, and the shape of the
distributions matters as much as the means (an expert's coins are almost a constant because the bot
reaches the tick budget; a novice's are not).
