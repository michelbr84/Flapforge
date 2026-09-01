# Architecture

_Draft — written at milestone M0. Sections marked `[M#]` describe code that
lands with that milestone; the design is fixed, the implementation arrives
incrementally._

Flapforge is a from-scratch rewrite of a Java Flappy Bird clone as an arcade
roguelite. The code base is a single Gradle module under
`io.github.michelbr84.flapforge`, built for Java 17, using nothing but AWT and
Java2D for presentation, Gson for JSON and JUnit for tests. The two ideas that
shape every package are:

1. **The whole game runs headless.** Simulation, abilities, modifiers,
   content, progression and persistence are plain Java with no window, no
   clock, no threads and no global randomness. Bots, balancing tools,
   determinism checks and the test suite drive exactly the code the player
   plays.
2. **One thread owns time.** A dedicated loop thread steps the simulation at
   a fixed 60 Hz and renders with interpolation through a presenter
   abstraction; the AWT event thread only enqueues immutable input records.

## The four layers

```text
┌──────────────────────────────────────────────────────────────────────┐
│ 1. Application shell   Flapforge, app.*                              │
│    window, loop thread, AWT input bridge, presenters, clocks, flags  │
├──────────────────────────────────────────────────────────────────────┤
│ 2. Presentation        render, audio, ui, event                      │
│    procedural art, software mixer, screens & components, event bus   │
├──────────────────────────────────────────────────────────────────────┤
│ 3. Simulation          core, input, gameplay.*, ability, modifier    │
│    playfield, geometry, per-tick input, bird/obstacles/collision,    │
│    stats pipeline, run state machine, abilities, roguelite drafts    │
├──────────────────────────────────────────────────────────────────────┤
│ 4. Meta                content, progression, persistence             │
│    JSON loading & validation, strings, profile, wallet, unlocks,     │
│    upgrades, achievements, settings and save files                   │
└──────────────────────────────────────────────────────────────────────┘
```

Dependencies point downwards only: the shell knows everything, presentation
knows simulation and meta, simulation and meta know only each other's data
types (`content.defs` records feed `gameplay.stats`; `gameplay.run.RunResult`
feeds `progression`). Layers 3 and 4 are the **pure** packages; layers 1 and 2
are the **AWT side**.

## Pure vs presentation, and how it is enforced

| Pure (headless) | AWT side |
| --- | --- |
| `core`, `input`, `gameplay.*`, `ability`, `modifier`, `content`, `progression`, `persistence` | `app`, `render`, `audio`, `ui`, `event` |

Pure packages use their own value types (`core.geom.Vec2`, `Aabb`, `Circle`;
palettes are `0xRRGGBB` ints; timestamps are epoch millis from an injected
`core.TimeSource`). They never see a window, a sound line, a wall clock, a
thread or `Math.random`.

`ArchitectureTest` `[M0]` walks every source file under the pure packages and
fails the build if it finds any of:

- `java.awt`, `javax.swing`, `javax.sound`, `javax.imageio`, `sun.`
- `Math.random`, `System.currentTimeMillis`, `System.nanoTime`, `new Random(`
  with no seed argument
- `Thread.`, `Executors.` (`persistence` and `progression` may accept a
  `java.util.concurrent.Executor` — the interface only — through their
  constructors; `app.Threads` owns every real thread and executor)
- `Math.(sin|cos|tan|atan|atan2|asin|acos|sinh|cosh|tanh|exp|expm1|pow|log|log10|log1p|cbrt)(`
  — results differ across JDKs and platforms, which would break the
  cross-OS determinism guarantee; `sqrt`, `floor`, `ceil`, `round`, `abs`,
  `min`, `max`, `hypot` and `fma` are exact and allowed. Oscillators use
  triangle waves or lookup tables.
- `import …event.` inside `gameplay` or `progression` — the event bus is a
  presentation-only concern. `progression` returns plain facts in
  `ProgressionOutcome`; `ui.screens.GameScreen` turns them into events.

Injected seams: `core.TimeSource` (epoch millis; `app.SystemTimeSource` in the
game, `FixedTimeSource` in tests), `app.Clock` (monotonic nanos for the loop;
`ManualClock` in tests), `app.Threads` (loop thread, save executor, audio
thread; a direct executor in tests), and `core.RandomProvider` (seeded, named
streams). The rejected alternative — exempting `persistence`/`progression`
from the bans — would let wall-clock time creep into save fixtures and tests.

## Loop, presenter and input `[M0]`

### Game loop (D1)

A non-daemon thread named `flapforge-loop` runs `GameLoop.frame()`:

```text
acc += min(now − prev, 100 ms)
ticks = 0
while acc ≥ TICK_NS and ticks < 6:      # TICK = 1/60 s
    screens.tick(input.nextTick())
    acc −= TICK_NS; ticks++
if ticks == 6: acc = 0                  # a stall never kills the bird
presenter.present(acc / TICK_NS)        # alpha ∈ [0, 1) for interpolation
limiter.pace()                          # target fps, default 60
```

60 Hz is exactly two ticks per upstream frame, so the classic feel converts
without rounding while input resolution doubles. `GameLoop` depends only on
`app.Clock`, `input.InputQueue`, `ui.ScreenManager`, `app.FramePresenter` and
`app.FrameLimiter`; `frame()` is public so `GameLoopTest` (manual clock, null
presenter), `SmokeWindowTest` and `--headless-run N` can drive it.
`FrameLimiter` waits until the deadline minus a self-calibrated overshoot
margin and then spins. At startup it measures how far `LockSupport.parkNanos`
overshoots a 1 ms wait; if that exceeds about 2 ms (Windows inherits the
15.6 ms timer period for `parkNanos`) it switches to 1 ms `Thread.sleep`
chunks, for which HotSpot raises the timer resolution, and the measured
overshoot (plus 25 %) becomes the margin, capped at 20 ms so the frame rate is
held even on a coarse timer. Unknown refresh rate means 60, and the target is
clamped to [30, 240].

Rendering uses **interpolation**: every renderable keeps its previous and
current position and the renderer blends them by `alpha`, accepting at most
one tick of visual latency. Simulation and rendering run on the same thread,
so no snapshot copies are needed.

### Presenter (D4, D24)

```java
interface FramePresenter {
    void present(double alpha);
    void onResize(int w, int h);
    void setFullscreen(boolean on);
    boolean isFullscreen();   // the presenter owns the state; --fullscreen starts true
    void dispose();
}
```

`BufferStrategyPresenter` wraps the window: a `java.awt.Frame` (no Swing
anywhere) with a single `Canvas`, `setIgnoreRepaint(true)`, a two-buffer
`BufferStrategy` created on the EDT before the loop starts, the canonical
`do { do { draw } while (contentsRestored()) ; show() } while (contentsLost())`
plus `Toolkit.sync()`, skipped while iconified. F11 runs a handshake: the loop
suspends rendering, the EDT disposes and recreates the frame undecorated on the
device bounds (or restores the saved windowed bounds), the strategy is
refetched, rendering resumes. The frame icon is generated by
`render.ProceduralArt`. `NullPresenter` counts presents and can render into a
`BufferedImage` for tests and headless runs.

Quit is a `CLOSE_REQUESTED` input: the loop finishes its frame, the save
manager flushes, the frame is disposed on the EDT and the JVM exits naturally;
a shutdown hook also flushes; `System.exit` only from a 5 s watchdog.

### Logical resolution and viewport (D3)

The playfield is a fixed **420×640** logical space (`core.Playfield`, which
also carries the classic constants: ground at 598, bird x 105, gap 128,
gate interval 160, and so on). `render.Viewport` computes
`s = min(w/420, h/640)` (integer-snapped when the setting is on), letterboxes
with the world palette's tone, and maps mouse coordinates back. Drawing goes
straight into the presenter surface with `translate/scale/clip`, so the JDK's
HiDPI transform composes underneath and vector art and text stay crisp. The
default window is the largest integer scale whose decorated window (playfield
plus a 48 px allowance for the title bar) fits the usable screen height
(2× on a 1440-class display); minimum size 420×640 plus insets; fullscreen is
borderless (exclusive display modes are flaky on Linux).

### Input (D2)

`app.AwtInputBridge` registers a `KeyEventDispatcher` and mouse/window
listeners and converts every AWT event into an immutable `input.RawInput`
record (`KEY_DOWN/UP`, `MOUSE_DOWN/UP/MOVE`, `WHEEL`, `FOCUS_LOST`,
`ICONIFIED`, `RESIZED`, `CLOSE_REQUESTED`, `FULLSCREEN_TOGGLED`) carrying
window coordinates. Its `KeyRepeatFilter` drops a `KEY_RELEASED` immediately
followed by a `KEY_PRESSED` of the same key with the same timestamp
(XWayland/VNC auto-repeat), and attaching ends with a synthetic `RESIZED` so
the first tick reconciles the viewport with the real canvas size. Events go
into a bounded `InputQueue` (512, drop-oldest; consecutive `MOUSE_MOVE`s and
`RESIZED`s are coalesced so pointer motion never pushes a key event out).

`InputQueue.nextTick()` runs only inside the tick loop and owns the per-key
held state: a repeated `KEY_DOWN` for a held key is ignored, `KEY_UP` produces
a release edge, `FOCUS_LOST` synthesises releases and keeps the released keys
as ghosts for one second — a `KEY_DOWN` of a ghost inside that window is the
resumed auto-repeat of a key that was never physically released (F11 held
across the fullscreen handshake, which disposes the window) and re-arms the
key without a press edge; a same-timestamp release/press pair that straddles
a tick boundary is treated the same way. The result is an
`InputFrame` — `justPressed`, `held`, `justReleased` as `EnumSet<InputAction>`,
raw key codes for rebinding capture, mouse state and system events. Edges are
therefore computed **per tick**: a tap shorter than a tick is never lost, and
auto-repeat never doubles a flap. `ScreenManager.tick` maps the window
coordinates through the loop-owned `Viewport`, which is updated only when the
loop drains `RESIZED`. `KeyBindings` map key codes to actions (flap
Space/Up/left click, ability X/Shift/right click, pause Esc, confirm Enter,
mute M, debug F3, fullscreen F11); mouse buttons are fixed.

### Screens `[M0]`

`ui.Screen { onEnter, onExit, tick(InputFrame), render(Graphics2D, alpha),
isOverlay() }` on a `ScreenManager` stack with overlays; one `FocusRing` per
screen handles keyboard (arrows/Tab, Enter/Space, Esc) and mouse (hover,
click) focus. M0 ships a minimal `MainMenuScreen` (Play → placeholder
`GameStubScreen`, deleted when `GameScreen` lands in M1; Settings stub; Quit)
and the `Button`/`Label`/`Panel` components; later milestones add the
remaining screens on the same stack. UI strings live in `ui.UiText` only
until `content.Strings`/`StringKey` replace it in M2.

## Simulation and meta layers (overview, `[M1+]`)

- `gameplay.run.Run.tick(RunInput) → TickReport` is the single entry point
  for both the UI and headless tools. Phases: `READY → FLYING ↔ {BREATHER →
  CHOOSING_MODIFIER → RESUME_HOLD} ↔ {BOSS_WARNING → BOSS} → DYING →
  FINISHED`. `TickReport` is a list of immutable facts (`GatePassed`,
  `CoinCollected`, `Crashed`, `SynergyActivated`, ...).
- Physics is the upstream loop converted to 60 Hz: flap 405 px/s, gravity
  1800 px/s², scroll 120 px/s; pinned by `ClassicFeelTest` against a literal
  transliteration of the upstream integer code.
- Obstacles: abstract `Obstacle` + sealed `core.geom.Hitbox` (`Aabb`,
  `Circle`); `PipeGate` (+`Oscillator`) replaces the three upstream pipe
  classes; `Gear`, `Piston`, `WindZone`, `LightningStrike` arrive with their
  worlds. Bosses, corridors and rule cycles are data.
- Stats: one commutative pipeline
  `clamp((base + ΣFLAT) × (1 + ΣPCT) × ΠMUL)` with layered
  `[{stat, op, value}]` lists from birds, upgrades, world, tier, challenge,
  modifiers, synergies, abilities and prestige; rule flags zero
  `SHIELD_CHARGES`/`REVIVES`.
- Content: 12 JSON files + 2 string tables, bound strictly (unknown key =
  error with a JSON pointer) and validated fail-fast, including an unlock
  graph that must be a DAG with a cumulative path to every non-cosmetic
  unlockable.
- Progression: `ProgressionManager.apply(RunResult)` once per run, on the
  game-over overlay, so instant retry never loses rewards.
- Persistence: versioned envelope in the per-OS data directory, tmp + fsync +
  atomic rename on the injected executor, `.bak` per session, quarantine,
  unknown fields preserved.
- Presentation is procedural-first: `ProceduralArt` draws everything from a
  `WorldPalette` and style; `AssetManager` + `assets/manifest.json` is the
  optional per-id override path for future art packs and the bundled font.

## Package tree with milestone tags

The tree below is the authoritative file plan (Appendix A §3 of the
implementation plan). Milestone tags mark the milestone that creates the file.
Corrections applied by the errata: `Hitbox` lives in `core.geom` (sealed
hierarchies must share a package); `modifier/ModifierTag [M6]`,
`content/UnknownIdException [M1]`, `ui/component/ToastLayer [M2]` and
`test/support/FixedSpawnTable [M6]` are added; `test_flat_corridor.json` is
`[M7]`; no inherited images are kept anywhere in the tree. Only files that
will actually be created are listed.

```text
Flapforge/
├── README.md [M0 structure/badges/requirements/running/controls/technology rewrite; M9 final]   LICENSE (renamed from License) [M0]
├── CHANGELOG.md [M0: inherited entries under "Inherited upstream history (kingyuluk/FlappyBird)"; Flapforge from [Unreleased]]
├── CONTRIBUTING.md CODE_OF_CONDUCT.md SECURITY.md THIRD_PARTY_NOTICES.md [M0]   .gitignore (Gradle) .editorconfig .gitattributes [M0]
├── build.gradle settings.gradle gradle.properties gradlew gradlew.bat gradle/wrapper/{gradle-wrapper.jar,gradle-wrapper.properties} [M0]
├── .github/workflows/{build.yml,test.yml} [M0]  release.yml [M9]  .github/dependabot.yml [M0]
│   .github/ISSUE_TEMPLATE/{bug_report.md,feature_request.md} pull_request_template.md [M0]
├── docs/README.md ARCHITECTURE.md DEVELOPMENT.md [M0 drafts]  BALANCING.md [M1]  SAVE_SYSTEM.md [M3]  PROGRESSION.md CONTENT.md [M4]  GAME_DESIGN.md ROADMAP.md [M9]
│   (no inherited images are kept: `resources/readme_img` is deleted in M0 — E31.i)
├── scripts/{build.sh,build.ps1,run.sh,run.ps1} [M0]  package.sh [M9]
├── tools/balancing/{README.md,run.sh,run.ps1} [M1]  tools/save-inspector/{README.md,run.sh,run.ps1} [M3]  tools/asset-validator/{README.md,run.sh,run.ps1} [M7]
└── src/
    ├── main/java/io/github/michelbr84/flapforge/
    │   ├── Flapforge.java [M0]
    │   ├── app/        GameApplication GameContext GameLoop GameWindow AwtInputBridge KeyRepeatFilter FrameLimiter LaunchOptions Threads Clock SystemClock SystemTimeSource
    │   │               FramePresenter BufferStrategyPresenter NullPresenter [M0]  BootSequence [M2]
    │   ├── core/       Playfield MathUtil RandomProvider Ids TimeSource [M0]   geom/{Vec2,Aabb,Circle} [M0]
    │   ├── input/      InputAction Keys RawInput InputQueue InputFrame KeyBindings [M0]
    │   ├── gameplay/   Simulation SimContext SimInput TickReport [M1]  WorldEffects [M7]
    │   │   ├── bird/       Bird BirdPhysics [M1]
    │   │   ├── obstacle/   Obstacle ObstacleKind PipeGate Oscillator ObstacleLayer ObstacleSpawner SpawnTable [M1]  Gear Piston WindZone LightningStrike PatternStreamer ParamSpec [M7]
    │   │   ├── pickup/     Coin PickupLayer [M3]
    │   │   ├── collision/  Hitbox CollisionSystem CollisionReport [M1]
    │   │   ├── stats/      StatId StatOp StatModifier StatSheet EffectStack Layer RuleFlag RuleSet [M1 minimal, M4 complete]
    │   │   ├── difficulty/ DifficultyCurve DifficultyState [M1]
    │   │   ├── run/        RunConfig RunMode RunPhase RunStats Run RunInput RunResult [M1]  RunRewardCalculator RewardSummary StreakTracker [M3]
    │   │   │               ShieldSystem ReviveSystem [M5]  ModifierDirector [M6]  ObjectiveEvaluator BossEncounter [M8]
    │   │   └── harness/    BotPilot HeadlessRunner [M1]  Oracles (per-kind hazard predictors) [M7]  MetaSim [M9]
    │   ├── ability/    AbilityBehavior AbilityContext AbilityInstance AbilityManager BehaviorRegistry AbilityTag [M5]
    │   │   └── behaviors/  ShieldBehavior DoubleFlapBehavior DashBehavior SlowTimeBehavior EmergencyRecoveryBehavior CoinMagnetBehavior ScoreMultiplierBehavior InvulnerabilityBehavior [M5]
    │   ├── modifier/   ModifierPool ModifierOffer Rarity SynergyResolver [M6]
    │   ├── content/    ContentLoader StrictBinder GameContent Registry Strings StringKey [M1 loader/registry; M2 Strings]  ContentValidator ContentException UnlockGraph [M1 minimal; M4 full]
    │   │   └── defs/       BirdDef HitboxDef PaletteDef StatModifierDef RampEffectDef SynergyEffectDef UnlockConditionDef DifficultyDef CurveDef TierDef [M1]
    │   │                   EconomyDef FeatureDef DailyDef [M3]  UpgradeDef TreeDef AliasDef [M4]  AbilityDef [M5]  ModifierDef SynergyDef [M6]  WorldDef PatternDef [M7]  ChallengeDef AchievementDef [M8]
    │   ├── progression/ PlayerProfile ProgressionManager ProgressionOutcome Wallet PlayerLevel Statistics StatisticKey [M3]  UnlockEvaluator UnlockManager UpgradeManager [M4]
    │   │               AchievementEvaluator CollectionProgress [M8]  DailyChallenge PrestigeSystem [M9]
    │   ├── persistence/ SavePaths AtomicFiles JsonCodec Settings SettingsStore [M2]  SaveFile SaveManager SaveMigrator Migration [M3]
    │   ├── event/      GameEvent EventBus [M2]
    │   ├── render/     FrameRenderer Viewport Fonts TextPainter DebugOverlay ProceduralArt WorldPalette [M0]  BackgroundRenderer CloudLayer BirdRenderer ObstacleRenderer HudRenderer GameRenderer [M1]
    │   │               AssetManager AssetResolver Sprite SpriteSheet Animation Camera ParticleSystem [M2]  PickupRenderer [M3]  ObstacleRendererRegistry [M7]
    │   ├── audio/      AudioBackend SoftwareMixer NullAudio Voice SoundBank ToneSynth AudioManager [M2]  MusicSequencer [M8]
    │   └── ui/         Screen ScreenManager UiNode FocusRing [M0]
    │       ├── component/  Button Label Panel [M0]  Slider Toggle ListView Toast [M2]  ProgressBar CurrencyDisplay [M3]  Tooltip CardGrid TabBar [M4]
    │       └── screens/    MainMenuScreen (minimal) SettingsScreen (stub) [M0]  GameScreen PauseOverlay GameOverOverlay [M1]  BootScreen [M2; MainMenu/Settings completed]
    │                       RunSummaryScreen StatisticsScreen [M3]  BirdSelectionScreen UpgradeTreeScreen ShopScreen [M4]  ModifierChoiceOverlay [M6]
    │                       RuleShiftBanner [M7]  ChallengesScreen AchievementsScreen BossBanner [M8]
    ├── main/resources/
    │   ├── assets/manifest.json [M2, empty asset list]  assets/sprites/{birds,obstacles,worlds,ui}/.gitkeep assets/audio/{sfx,music}/.gitkeep [M2]  assets/fonts/{<ofl-font>.ttf,LICENSE} [M8]
    │   ├── data/birds.json difficulty.json [M1]  economy.json [M3]  upgrades.json aliases.json [M4]  abilities.json [M5]  modifiers.json [M6]  worlds.json patterns.json [M7]  challenges.json achievements.json [M8]
    │   ├── data/strings/en.json pt_BR.json [M2]
    │   └── version.properties [M0]
    ├── tools/java/io/github/michelbr84/flapforge/tools/  BalancingSim [M1]  SaveInspector [M3]  ContentCheck [M4]  AssetValidator [M7]  IconExport [M9]
    └── test/
        ├── java/io/github/michelbr84/flapforge/
        │   ├── support/    ManualClock FixedTimeSource DirectExecutor CaptureAudioBackend TestContent (frozen fixture loader) ScriptedPilot [M0/M1/M2]  FixedSpawnTable [M6]
        │   ├── ArchitectureTest ViewportTest InputQueueTest GameLoopTest FrameLimiterTest KeyRepeatFilterTest WindowScaleTest FocusRingTest MenuNavigationTest ProceduralRenderTest(min) [M0]  EventBusTest StringsTest FontsTest [M2]  SmokeWindowTest(@gui) [M0]
        │   ├── gameplay/   ClassicReference ClassicFeelTest GroundRuleTest BirdPhysicsTest HitboxTest CollisionSystemTest PipeGateLayoutTest ObstacleSpawnerTest StatSheetTest DifficultyCurveTest
        │   │               RunLifecycleTest RunInputTest DeterminismTest GoldenRunTest PerfBudgetTest(@perf) [M1]  PickupTest RunRewardCalculatorTest StreakTrackerTest [M3]
        │   │               ObstacleFamilyTest PatternStreamerTest WorldEffectsTest BotOracleTest [M7]  BossEncounterTest ObjectiveEvaluatorTest UnlockChainTest [M8]
        │   ├── ability/    AbilityManagerTest ShieldSystemTest ReviveSystemTest + one test per behaviour [M5]
        │   ├── modifier/   ModifierPoolTest ModifierDirectorTest SynergyResolverTest [M6]
        │   ├── content/    ContentIntegrityTest ContentValidatorTest [M1 minimal, M4 full]  UnlockGraphTest [M4]  ContentFeasibilityTest(@sim) [M7 patterns/tiers; M8 challenges/bosses]
        │   ├── progression/ WalletTest PlayerLevelTest ProgressionManagerTest [M3]  UnlockEvaluatorTest UpgradeManagerTest NewPlayerJourneyTest(@sim) [M4]
        │   │               AchievementEvaluatorTest CollectionProgressTest [M8]  DailyChallengeTest PrestigeSystemTest MetaSimTest(@sim) [M9]
        │   ├── audio/      MusicSequencerTest ToneSynthTest [M8]
        │   └── persistence/ SettingsStoreTest [M2]  SaveManagerTest SaveMigrationTest SaveCorruptRecoveryTest [M3]
        └── resources/fixtures/  golden_seed42.txt content_frozen/*.json [M1]  save_v1.json save_corrupt.json save_future.json save_unknown_fields.json save_v1_to_v2/{in,expected}.json [M3]
                                 content_bad/{unknown_key,bad_enum,duplicate_id,cycle,unreachable,missing_string,contradiction}.json [M4]  test_flat_corridor.json (pattern) [M7]
```


## Original layout proposal (STRUCTURE.md) and how it was reconciled

Before the rewrite started, a file named `STRUCTURE.md` proposed a ~200-file
layout for the new package. It was a useful inventory of the systems the game
needs, but many of its files were empty shells or duplicates. The plan folded
it into the tree above with these rules:

- **Merge** classes that would only forward to each other
  (`Pipe/MovingPipe/FloatingPipe → PipeGate`;
  `RunManager → Run + ModifierDirector + BossEncounter`).
- **Turn per-world and per-challenge classes into data**
  (`worlds/*.java`, `challenges/*.java` → `worlds.json`, `challenges.json`).
- **Rename** where a name collided or misled (`stats/` for player statistics
  became `progression.Statistics`, leaving `gameplay.stats` for the attribute
  pipeline; `core.Time` split into `app.Clock` and `core.TimeSource`).
- **Add** the seams the design needs that the proposal lacked
  (`FramePresenter`, `TimeSource`, `StrictBinder`, `UnlockGraph`,
  `Strings/StringKey`, `StreakTracker`, `SynergyResolver`, `DailyChallenge`,
  `ToneSynth`, `SoftwareMixer`, `MusicSequencer`, `patterns.json`,
  `aliases.json`, `manifest.json`).
- **Drop** what the build or the procedural renderer makes unnecessary
  (`dist/.gitkeep`, `util/GameUtils`, image asset folders that would only
  hold placeholders).

The result is roughly 140 real classes instead of 200 stubs. The complete
mapping, one row per proposed item, follows. `STRUCTURE.md` itself was
removed from the tree once this table superseded it.

| STRUCTURE.md item | Disposition | Flapforge home |
|---|---|---|
| README, LICENSE, CHANGELOG, CONTRIBUTING, CODE_OF_CONDUCT, SECURITY, THIRD_PARTY_NOTICES, .gitignore, .editorconfig | kept | root [M0]; `.gitattributes` added |
| build.gradle, settings.gradle, gradlew(.bat), gradle/wrapper/* | kept | root [M0] + `gradle.properties` |
| .github/workflows/{build,test,release}.yml, ISSUE_TEMPLATE/*, pull_request_template.md | kept | [M0]/[M9]; `dependabot.yml` added |
| docs/{README, GAME_DESIGN, ARCHITECTURE, PROGRESSION, BALANCING, CONTENT, SAVE_SYSTEM, ROADMAP, DEVELOPMENT}.md | kept | docs/ per milestone |
| Flapforge.java | kept | entry point |
| app/GameApplication, GameContext, GameLoop | kept | `app` |
| app/Game, GameState | merged | `ui.ScreenManager` stack + `gameplay.run.RunPhase` |
| core/GameConfig, Constants | merged | `core.Playfield` + `app.LaunchOptions` + `persistence.Settings` |
| core/Time | renamed/split | `app.Clock` (monotonic) + `core.TimeSource` (epoch) |
| core/RandomProvider | kept | `core.RandomProvider` |
| bird/Bird, BirdPhysics | kept | `gameplay.bird` |
| bird/BirdController | merged | `Run` (input → flap) |
| bird/BirdStats, BirdType | data | `content.defs.BirdDef` + `gameplay.stats.StatSheet` |
| obstacle/Obstacle, ObstacleSpawner | kept | `gameplay.obstacle` |
| obstacle/Pipe, MovingPipe, FloatingPipe | merged | `PipeGate` (+`Oscillator`) |
| obstacle/ObstacleFactory | merged | `SpawnTable` + `ObstacleKind` registry + `PatternStreamer` |
| collision/CollisionSystem, Hitbox | kept | `gameplay.collision` (+`CollisionReport`) |
| score/ScoreManager, ScoreMultiplier | merged | `RunStats` (gatesPassed/points) + `SCORE_MULT` stat |
| score/HighScoreManager | merged | `progression.Statistics` (`bestGates*`) |
| run/Run, RunStats, RunResult, RunRewardCalculator | kept | `gameplay.run` |
| run/RunManager | merged | `Run` + `ModifierDirector` + `BossEncounter` |
| run/RunState | renamed | `RunPhase` |
| difficulty/DifficultyCurve | kept | `gameplay.difficulty` |
| difficulty/DifficultyManager | renamed | `DifficultyState` |
| difficulty/DifficultyTier | data | `content.defs.TierDef` |
| progression/ProgressionManager, PlayerLevel | kept | `progression` |
| progression/PlayerProgress | renamed | `PlayerProfile` |
| currency/Currency, Wallet, CurrencyReward | merged | `Wallet` + `economy.json.currencies` + `RewardSummary` |
| unlock/Unlock, UnlockCondition, UnlockType, UnlockManager | merged | `UnlockConditionDef` + `UnlockEvaluator` + `UnlockManager` + namespaced id strings |
| upgrade/Upgrade, UpgradeNode, UpgradeTree, UpgradeEffect, UpgradeManager | merged | `UpgradeDef` + `TreeDef` + `StatModifierDef` + `UpgradeManager` |
| prestige/PrestigeSystem, PrestigeReward | deferred to M9 | `PrestigeSystem` + `economy.json.prestige` |
| ability/Ability, AbilityManager | kept | `ability.AbilityInstance`/`AbilityManager` |
| ability/AbilityType, AbilityCooldown | merged | `AbilityDef.kind` + `AbilityInstance` |
| abilities/Shield, DoubleFlap, Dash, SlowTime, CoinMagnet | kept (+3) | `ability.behaviors.*` (8 behaviours) |
| modifier/Modifier, ModifierManager, ModifierRarity, ModifierPool | merged | `ModifierDef` + `ModifierDirector` + `Rarity` + `ModifierPool` + `SynergyResolver` |
| world/World, WorldManager, Environment, WeatherSystem | data | `WorldDef` + `WorldEffects` (ambient/darkness/wind/lightning) |
| world/worlds/{GreenFields, WindValley, IronForge, StormSky, VoidWorld} | data | `worlds.json` entries |
| challenge/Challenge, ChallengeReward | data | `ChallengeDef` |
| challenge/ChallengeManager, ChallengeResult | merged | `ObjectiveEvaluator` + `RunResult.objectiveMet` + `profile.challenges` |
| challenges/{NoShield, SpeedRun, TinyWings, CoinRush, BossCorridor} | data | `challenges.json` (7 entries) |
| boss/Boss, BossManager, BossPhase, BossPattern | merged | `BossEncounter` + `worlds.json.boss`/`ChallengeDef.boss` + `patterns.json` |
| achievement/Achievement, AchievementCondition | data | `AchievementDef` |
| achievement/AchievementManager | renamed | `AchievementEvaluator` |
| achievement/AchievementProgress | merged | `AchievementEvaluator.progressOf` + Milestones tab |
| economy/EconomyManager, Reward, RewardTable | merged | `EconomyDef` + `RunRewardCalculator` |
| economy/Shop | kept as screen | `ShopScreen` over purchase unlocks + ability levels |
| persistence/SaveManager | kept | `persistence` |
| persistence/SaveData | renamed | `SaveFile` + `PlayerProfile` |
| persistence/SaveVersion, SaveMigration | merged | `SaveMigrator` + `Migration` |
| persistence/SettingsRepository | renamed | `SettingsStore` |
| input/InputManager, KeyboardInput | merged | `InputQueue` + `app.AwtInputBridge` |
| input/InputAction | kept | `input.InputAction` |
| render/Renderer | merged | `FrameRenderer` + per-thing renderers |
| render/Sprite, Animation, Camera, ParticleSystem | kept | `render` (+`SpriteSheet`) |
| audio/AudioManager | kept | `audio` |
| audio/SoundEffect | merged | `SoundBank` + `Voice` + `ToneSynth` |
| audio/MusicManager | renamed | `MusicSequencer` |
| ui/Screen, ScreenManager | kept | `ui` (+`UiNode`, `FocusRing`) |
| screens/MainMenu, Game, RunSummary, BirdSelection, UpgradeTree, Shop, Challenges, Achievements, Settings | kept | `ui.screens` |
| screens/GameOverScreen | merged | `GameOverOverlay` (overlay, instant retry) |
| component/Button, Panel, ProgressBar, Tooltip, CurrencyDisplay | kept (+7) | `ui.component` |
| event/GameEvent, EventBus | kept | `event` |
| event/GameEventListener | merged | `Consumer<T>` |
| stats/PlayerStatistics, StatisticsManager, RunHistory | merged/renamed | `progression.Statistics` (+`runHistory`) — avoids clash with `gameplay.stats` |
| config/Settings, SettingsManager | merged | `persistence.Settings` + `SettingsStore` |
| config/KeyBindings | kept | `input.KeyBindings` |
| util/MathUtils | renamed | `core.MathUtil` |
| util/FileUtils | renamed | `persistence.AtomicFiles` |
| util/GameUtils | dropped | responsibilities live in `Playfield`/`TextPainter` |
| assets/sprites/{birds,obstacles,worlds,ui} | kept (empty) | manifest-driven override folders |
| assets/sprites/effects, backgrounds, animations, particles, icons | dropped | procedural rendering; icon generated (`ui/icon` manifest id optional) |
| assets/audio/{music,sfx}, assets/fonts | kept | `sfx`/`music` empty (+ font at M8) |
| data/*.json (9) | kept (+3) | + `patterns.json`, `aliases.json`, `strings/*.json`; `manifest.json` under assets |
| config/default.properties | data | `Settings` field defaults |
| test/java/{gameplay, progression, ability, persistence} | kept | same packages |
| test/java/challenge, economy | merged | `gameplay/` (`ObjectiveEvaluatorTest`, `RunRewardCalculatorTest`) |
| test/resources/fixtures | kept | fixtures listed above |
| tools/{asset-validator, save-inspector, balancing} | kept | wrappers (`run.sh` + `run.ps1`) over Gradle tasks; code in `src/tools` |
| scripts/{build.sh, build.ps1, run.sh, run.ps1, package.sh} | kept | scripts/ |
| dist/.gitkeep | dropped | build output (`build/dist`) |
| (added) | — | `patterns.json`, `aliases.json`, `manifest.json`, `strings/`, `gameplay.pickup`, `gameplay.harness`, `content.defs`, `StrictBinder`, `UnlockGraph`, `Strings/StringKey`, `TimeSource`, `FramePresenter`+2 impls, `StreakTracker`, `SynergyResolver`, `DailyChallenge`, `ToneSynth`, `SoftwareMixer`, `MusicSequencer`, `Viewport`, `RuleShiftBanner`, `src/tools` source set |


## Further reading

- [`DEVELOPMENT.md`](DEVELOPMENT.md) — building, running, testing, flags,
  coding rules.
- `BALANCING.md` `[M1]` — the physics conversion table and simulation
  results.
- `SAVE_SYSTEM.md` `[M3]`, `PROGRESSION.md` and `CONTENT.md` `[M4]`,
  `GAME_DESIGN.md` and `ROADMAP.md` `[M9]`.
