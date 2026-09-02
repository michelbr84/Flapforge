# Balancing — the conversion table (M1), the run economy (M3), the meta-progression (M4) and the abilities (M5)

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
  the whole trail without a magnet — it is a comfort node for a human, and M9's MetaSim should
  re-measure it against a policy that misses coins.
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
  level — 222.26 plain and 171.79 maxed. It is the clearest retune candidate for M9.
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

The other four worlds (M7) and the runs-to-unlock table that `BalancingSim --meta` prints (M9,
E25) extend this document as they land. Four M4 measurements, three M5 ones and two M6 ones are
recorded above as open questions for M9 rather than as settled balance:
`glide_1` cannot bind (§6.3), the bot is not monotone in the stats the tree sells (§6.4), Jackdaw and
Oracle do not pay for themselves (§6.5), the participation gate zeroes one novice run in five (§6.6),
`coin_magnet` needs off-path coins to be worth anything (§7.3), `emergency_recovery` and `shield` are
priced apart but measure the same (§7.4), the ground save has no animation (§7.5), `glass_wings` is
net-negative at every preset (§8.4) and a draft resume can be spent as a free altitude reset (§8.4).
The rule stays
the same: a row is added only once a test or a tool measures it — §5's, §7's and §8's numbers are
`BalancingSim` output, not estimates, and the shape of the distributions matters as much as the means
(an expert's coins are almost a constant because the bot reaches the tick budget; a novice's are not).
