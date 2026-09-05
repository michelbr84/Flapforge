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

`core.RandomProvider` derives each named stream as
`new Random(mix64(seed ^ fnv1a64(name)))`. The SplitMix64 finaliser is load
bearing: `java.util.Random` only scrambles its seed with
`(s ^ 0x5DEECE66D) & mask`, so for consecutive seeds — which is exactly what
instant retry (`N, N+1, N+2 …`) and `BalancingSim` produce — the *first* draw
of a stream is nearly linear in the seed and covers a narrow band. Mixing
first restores a uniform first draw without touching reproducibility.

**One documented exception to E32.c** ("production never imports `harness`"):
`app.GameApplication` imports `gameplay.harness.{BotPilot, HeadlessRunner}` to
produce the `--headless-run` determinism line
(`hash=… ticks=… gates=… points=…`) that the cross-OS CI job compares, which
Appendix A §6 requires from the shipped binary — so the bot harness ships in
the jar. Nothing in the simulation, the screens or the renderers depends on it,
and the replay-only `ScriptedPilot` lives in `test/support` (§3) rather than in
the harness package.

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
HiDPI transform composes underneath and vector art and text stay crisp.
On windows taller than the playfield aspect (portrait phones foremost) the
scale and offsets stay exactly the letterboxed ones, but — while
`settings.fillScreen` is on, the default — the clip of `Viewport.apply`
extends vertically and the presenters publish the visible logical range
through `render.Overscan`, so the sky, the ground, the pipes and the
full-frame fills paint the rows that used to be top/bottom bars. Cosmetic
only: the simulation never reads `Overscan`, all interactive UI stays inside
420×640, and horizontal bars (wide windows) keep the palette letterbox. The
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
click) focus. M0 shipped a minimal `MainMenuScreen` (Play → a placeholder;
Settings stub; Quit) and the `Button`/`Label`/`Panel` components; M1 replaced
the placeholder with `GameScreen` + `PauseOverlay` + `GameOverOverlay`, and
later milestones add the remaining screens on the same stack. The screen
manager ticks only the top screen, so anything that must keep moving under an
overlay is driven by that overlay (the game-over screen ticks the renderer's
cloud drift). M2 replaced the M0 `ui.UiText` placeholder with
`content.Strings`/`StringKey` and added the boot splash, the real settings
screen and the `Slider`/`Toggle`/`ListView`/`Toast` components. M3 added the
`ProgressBar` and `CurrencyDisplay` components, `RunSummaryScreen` (opened with
`Enter` from the game-over strip: every term of the reward formula on its own
row, the level bar the XP moved, the seed with its mode, Retry / Menu) and
`StatisticsScreen` (reachable from the menu: the lifetime counters grouped, and
`statistics.runHistory` paged newest first in a `ListView`). Both keep their
rows in a content space scrolled under a clip, the way the settings screen
does, and neither writes anything: they read the profile the run was already
written into (D14, D29).

Three keys belong to no screen and are handled by the manager: `F11`
(fullscreen), `F3` (the debug overlay) and `M` (mute). All three change a
**setting**, so the manager only owns the key — it runs a handler that
`GameApplication` points at `GameContext.toggleFullscreen()` /
`toggleDebugOverlay()` / `toggleMute()`, each of which flips one field, applies
it and persists it. Nothing may poke the presenter or the overlay flag behind
the stored state's back: §4 persists `fullscreen` and `showFps`, so a hotkey
that only changed the engine would be undone by the next `applySettings` and
lost across restarts. The manager also runs one application-installed
`tickTask` at the top of every tick; that is how a settings write that failed
on the save thread re-enters the loop thread as an event and a toast.

## Presentation services `[M2]`

### Settings and how a change reaches the engine

`GameContext.applySettings(Settings)` is the **single** place that knows which
engine object owns which behaviour: it pushes `integerScaling` and `fillScreen` to the viewport,
`maxFps` to the frame limiter, the key bindings to the input queue, `textScale`
to `Fonts`, `smoothing` to `ProceduralArt`, `reduceFlashing` to
`ParticleSystem`, `showFps` and `fullscreen` to the screen manager, reloads the
string table when the language changed, and publishes `SettingsChanged` for
everything that subscribes instead (the audio manager reads the three volumes
and the mute flag off that event). A screen never reaches into the engine: it
edits a `Settings` copy and hands it here.

It also calls `SettingsStore.hold(...)`, which adopts the state as the one in
force **without writing it**. That matters because the settings screen debounces
its writes: without `hold`, `context.settings()` would still report the last
*saved* state, and a hotkey that built on it would silently revert whatever the
player was in the middle of editing. Writing is a separate step
(`saveSettings` → `SettingsStore.save`), and the settings screen additionally
subscribes to `SettingsChanged` so a hotkey pressed while it is open re-syncs its
working copy instead of being overwritten by the pending flush.

The write itself runs on `Threads.saveExecutor()` (one daemon thread that runs
every task it is given; `SaveManager` does the "latest state wins" coalescing
itself, because a task the executor discards never reports and its bookkeeping
leaks), so its outcome is **not** readable when `save()` returns. Each finished write is queued inside the store and drained by
`GameContext.drainSaveResults()` on the loop thread, which turns a failure into
a `SaveFailed` event and a warning toast — the write that actually failed, once
(D15). `AtomicFiles` does tmp + fsync + `ATOMIC_MOVE`, three immediate retries,
then a non-atomic `REPLACE_EXISTING` fallback, then `IO_FAILED`; it never
sleeps and never throws (E13).

### Audio pipeline (D19)

```
game fact ──► EventBus ──► AudioManager ──► AudioBackend ──► SourceDataLine
              (loop)       (picks the cue)   (bounded queue)  (mixing thread)
```

- **`AudioManager`** is the only thing that decides which cue a moment
  deserves. It subscribes once, to `GameEvent` itself, so a new event type
  cannot slip past a missing registration; `sfxIdFor` is total over the sealed
  hierarchy and answers `null` for the deliberately silent events. It owns the
  three volumes and the mute flag: the master fader goes to the backend, the
  effect volume is folded into each play, and muting both drops the fader to
  zero and skips the play, so a muted game queues nothing at all. Screens and
  systems publish facts and never name a sound id; menu blips go through
  `ui.UiCues`, which the application points at the manager.
- **`SoftwareMixer`** opens exactly one 44.1 kHz/16-bit/stereo line and sums
  every active `Voice` into it on one daemon thread. The loop never touches the
  line: `play` offers a small command to a bounded queue and returns, dropping
  the request when the queue is full (a dropped blip is invisible; a stalled
  loop is a dropped frame). The device buffer is four write-passes deep so a
  single slow pass cannot starve it, and the underrun counter is sampled
  immediately *before* the write, which is the only moment a drained line is
  visible. A soft `tanh` limiter above 0.8 keeps a burst of simultaneous sounds
  from clipping.
- **`SoundBank`** resolves an id to a ready-to-mix buffer: `assets/manifest.json`
  first, then the classpath, then `ToneSynth`. Resampling and mono-to-stereo
  widening happen once, at load time. The shipped manifest is empty, so every
  cue is currently synthesised.
- **`NullAudio`** is the fallback, and it is a real implementation, not a stub:
  `--no-audio` selects it, and so does any machine where no line opens.
  `AudioBackend.create` never throws — every failure becomes one logged line and
  a `NullAudio` (E30.j).
- **Where the device is opened.** Opening a line costs hundreds of milliseconds
  on a cold device, so it happens in the `BOOT_AUDIO` step of `BootSequence`, on
  the boot thread, while the splash is on screen — never on the launch thread
  with a visible unpainted window, and never on the loop. The manager starts on
  `NullAudio` and the step hands it the opened mixer with `setBackend`, then
  decodes the bank with `warmUpBlocking()` so the splash's progress reflects
  work that actually happened. A manager already closed by a quit closes the
  incoming backend instead of adopting it.

### Event bus rules (D16)

`event.EventBus` is synchronous, single-threaded and delivery is by **exact**
event type: a listener for `GatePassed` sees gate events and nothing else, and a
listener for `GameEvent` itself sees everything. The rules:

- **One thread.** The first `subscribe` or `publish` claims the bus; every later
  call from another thread fails fast with a message naming both threads.
  `adopt()` hands ownership to the loop thread, which is what
  `GameApplication` does when the loop starts on a bus built during start-up. A
  listener that touched renderer or mixer state from a foreign thread would be a
  race that surfaced as a rare visual glitch, so it is an error, not a warning.
- **Nested publishes are queued, not nested.** An event published from inside a
  listener is appended and drained by the outermost `publish`, first in first
  out, so listeners always observe events in publication order and the stack
  never grows.
- **The pure layers never touch it.** `gameplay` and `progression` do not import
  `event` (E31.b): they return immutable facts (`TickReport`,
  `ProgressionOutcome`) and `GameScreen` converts those into events. That is
  what keeps the simulation replayable and the bus free of gameplay logic.
- **The full event list exists from M2** even where only later milestones have
  producers, so a milestone adds subscribers instead of reopening the file.

### Internationalisation (D25)

Every player-facing string goes through `content.Strings`. `data/strings/en.json`
is the source of truth and `pt_BR.json` carries exactly the same keys; both are
flat `key → value` tables. `StringKey` is an enum over the keys the code names,
so a typo is a compile error, and `ContentValidator` reports a key that the code
uses but a table lacks.

- `Strings.load(language)` merges the requested table **over** `en.json`, so a
  missing translation falls back to English instead of showing a raw key. The
  test suite asserts that both shipped tables carry identical key sets, because
  the fallback would otherwise hide a dropped key.
- `{0}`, `{1}` … placeholders are substituted by `format`; the validator checks
  that a translation uses the same placeholders as the source.
- `language: auto` resolves against the default locale; `--lang CODE` overrides
  it for the launch.
- **Switching live.** `GameContext.applyLanguage` reloads the *shared* `Strings`
  instance in place and republishes it, so every screen holding a reference sees
  the new table. Each screen remembers the language it last drew and re-labels
  itself when it differs — that is what makes the main menu behind the settings
  screen come back translated. `LanguageChanged` is published for anything that
  wants to react instead of poll.
- Fonts must be able to draw the result: `Fonts.canDisplay` is asserted over
  every shipped string, and the font caches are lock-free arrays with
  acquire/release access because the boot warm-up fills them from another thread
  while the loop draws the splash.

## Run economy and the progression write path `[M3]`

One finished run travels through five objects, in one direction, and no step
knows the next one's package:

```
Run.result()  ──►  RunRewardCalculator.compute(result, EconomyDef, RewardContext)
                              │                         ▲
                              ▼                         │ first run? first clears?
                        RewardSummary          ProgressionManager.rewardContext(profile, …)
                              │
                              ▼
   ProgressionManager.apply(profile, result, ProgressionRules, RewardMultipliers)
     rewards → wallet → xp/level (+ level rewards) → statistics → challenge →
     daily → achievements → unlocks → dirty                       (D14, fixed order)
                              │
                              ▼
                     ProgressionOutcome  ──►  GameScreen  ──►  GameEvent + toasts + save
```

- **The formula is pure and lives in `gameplay.run`.** It reads the run and
  `economy.json` and nothing else; the two things it cannot know — whether this
  is the profile's first run, and which bosses it cleared for the first time —
  arrive in a `RewardContext` the progression layer fills in *before* any step
  has written to the profile. That ordering is why `REWARDS` is the first step.
- **`ProgressionRules.fromEconomy(EconomyDef)` is the only adapter.** It turns
  the currencies, `xp.curve` and `xp.levelRewards` into a `PlayerLevel`, and
  points its `RewardSource` at the calculator. A test builds one from three
  literals instead, which is what keeps the write order testable without the
  content pipeline.
- **`progression` never imports `event`** (`ArchitectureTest` enforces it).
  `ProgressionOutcome` carries facts — coins, levels crossed, unlock ids — and
  `GameScreen` maps them to `CurrencyChanged`, `XpGained`, `LevelUp`,
  `AchievementUnlocked`, `UnlockGranted` and the toasts that go with them.
- **`apply` runs exactly once per run** (D29): on the tick that reaches
  `FINISHED`, before the game-over overlay is pushed, and immediately followed
  by a save. A second call with the same `RunResult` returns the first outcome
  instead of paying twice. The overlay only *shows* the outcome, so the instant
  retry cannot lose a reward.
- **Coins and the streak are simulation state**, not progression state:
  `PickupLayer` spawns a coin trail per scoring gate from the `coins` random
  stream (E2), `StreakTracker` counts clean gates (D26), and both fold into
  `Simulation.stateHash()` — which is why adding them changed the golden run.

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
  classes; `Gear`, `Piston`, `WindZone`, `LightningStrike` arrived with their
  worlds in M7 (next section). Bosses, corridors and rule cycles are data.
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

## Worlds and obstacle families `[M7]`

**The obstacle model (D6).** `gameplay.obstacle.Obstacle` is a column at `x` scrolling left with
the world; a subclass owns its geometry and phases and answers three questions the rest of the
engine asks: `hitboxesAt(t)` (the lethal boxes interpolated over the tick, which
`CollisionSystem` sub-steps through when `maxDisplacement()` says the column moved far),
`safeBandY(x)` (the y a bird crosses it in — the coin trail of E2 and the harness oracles both
read it, E32.c) and `hashGeometry` (every per-tick field, D12). Five kinds:

| Kind | Geometry | Per-tick state |
| --- | --- | --- |
| `PipeGate` | two `Aabb` segments with a gap, standard or floating, optionally on an `Oscillator` (triangle wave, no trig) | the oscillator phase |
| `Gear` | a `Circle` of radius 24–56, optionally sweeping a vertical rail (another triangle wave), 2×radius wide, scores | the rail phase, the cosmetic angle in turns |
| `Piston` | an `Aabb` head extending from the top or the ground: `TELEGRAPH → EXTEND → HOLD → RETRACT` from spawn, advanced by `worldDt`; raises `PISTON_TELEGRAPH` while on the playfield | the phase clock and the extension |
| `WindZone` | a non-lethal `Aabb`; `affectBird` adds `accelY` to the bird's gravity and `scrollDelta` to the world scroll while the box overlaps it (the bird's x is fixed, so horizontal wind is a scroll change) | whether it is acting on the bird |
| `LightningStrike` | a 24 px column, `IDLE` until `warningTicks` of scroll from the bird, `WARNING` (no hitbox, `LIGHTNING_WARNING` once), `STRIKE` for `strikeTicks` when its centre reaches the bird, then `SPENT`; the bolt lights `lengthFrac` of the height from one edge, so a safe band always exists | the state and the strike clock |

**Spawning (D7, E32.d).** `SpawnTable` draws a `SpawnDecision` — what the streams decided and
nothing else: kind, layout, the *rolled* moving flag, the geometry (`KindParams` per kind). Rules
are applied when the decision is materialised (`materialize(decision, x, gap, forceMoving)`):
`GAP_SIZE`, a pattern gate's scaled `gapSize`, `ALL_OBSTACLES_MOVE` per kind. That split is what
makes the decision hash — the sequence `ObstacleSpawner.decisionHashes()` — depend on the seed
alone whatever the pilot does, even in the Void where a rule cycle lands the flag on a tick that
depends on the draft. `ObstacleSpawner` runs upstream's cursor (`x = last.x + (last.width − 40)
+ 160`, the next column when the last one is fully inside the playfield) and the two M7 fairness
rules: a lightning column is drawn reachable from the previous decision's reference band
(`SpawnDecision.referenceBandY`), and a breather's deferral is an absolute clearance behind the
last column. `PatternStreamer` rides the same cursor with authored set pieces
(`patterns.json`), drawing only from the `patterns` stream; a step's geometry goes through
`SpawnTable.decisionFor` and the `obstacle` stream like any other spawn.

**`WorldEffects` (D8, E8).** What a world does beyond its columns: the ambient wind (the
`WindZone` mechanism made permanent), the darkness the renderer reads, the cosmetic sky flash
(`TickFact.AmbientFlash`, no hitbox) and the rule cycles — every `everyGates` gates the next
option is drawn from the `cycles` stream, announced with `TickFact.RuleShift` and landed
`telegraphTicks` flying ticks later, never inside a draft and never while another option is
pending; its flags replace the previous option's in the run's rules (three sources kept apart:
base, drafted, cycle) and its effects the `WORLD_CYCLE` layer. A world with none of it draws
nothing and folds nothing, which is what keeps the published hash where M6 left it.

**Renderer registry (D18).** `render.ObstacleRendererRegistry` dispatches by `ObstacleKind` to
one renderer per family — `ObstacleRenderer` (gates), `GearRenderer`, `PistonRenderer`,
`WindZoneRenderer`, `LightningRenderer` — each interpolating with the frame alpha, keeping its
shapes and colour ramps, and allocating nothing per frame. `BackgroundRenderer` draws the five
parallax styles keyed by `worlds.json.style`, `DarknessOverlay` the veil, `GameRenderer` the
sky flash; the rule-shift banner (`ui.screens.RuleShiftBanner`) is a non-blocking panel in the
ground strip, not a screen on the stack.

**The pilot's oracles (D21).** `gameplay.harness.Oracles` predicts each kind at the crossing
tick — a piston's head over the ticks the column overlaps the bird, a bolt's unlit side from the
moment it is in the window, both sides of a gear from the chord its circle cuts through the
bird's x range — and `BotPilot` picks a gear's side by where the column after it leads and what
a flap arc fits in. Production never imports `harness`.

## Bosses, challenges and the achievement pipeline `[M8]`

**`BossEncounter` (D11, E7, E26).** A small state machine owned by the run's simulation, driven
by the tick the spawner already runs on. `RunConfig.bossEnabled` gates the whole thing: on for
every profile run, challenge, balancing cell and feasibility row, pinned off by
`RunConfig.classic` so the published headless hash never meets the Green Fields boss at gate 30.
When the run reaches `boss.atGate` the encounter enters `BOSS_WARNING` for `warningTicks` —
spawning is suppressed (`ObstacleSpawner.setSuppressed`), a `TickFact.BossWarning` carrying the
boss and world ids goes out — then `BOSS`, where the phases stream through the same
`PatternStreamer` machinery in order and loop back to phase 1 until `surviveTicks` of flying
time have passed (scoring steps keep scoring, coins, streaks and the difficulty curve keep
going; `phasesReached` is the furthest phase, monotonic across the wrap), then a single
`BossCleared`: the remaining boss columns scroll out and ordinary spawning resumes 1.5 gate
intervals behind the last phase column, with the resume floor keeping that first column at the
right edge instead of inside the playfield. Dying during the encounter clears nothing; a clear
granted is kept at run end even if the bird crashes later. A *world* boss writes
`RunStats.bossesCleared` and pays `boss.reward`; a *challenge* boss (`worldId == null`) writes
neither — it only sets the flag a `BOSS_CLEARED` objective reads (E26). The modifier director
reads `DraftWorld.bossPending()`/`bossActive()` so a schedule gate falling inside
`BOSS_WARNING`/`BOSS` defers its offer to the first spawn interval after the clear (E7), and
every per-tick field of the encounter — phase, timers, pattern index — folds into the
simulation's state hash.

**`ObjectiveEvaluator` (D11).** A pure function from the run's tallies to a verdict, evaluated
every tick: `SURVIVE_GATES`, `SURVIVE_TICKS`, `COLLECT_COINS`, `REACH_POINTS` and
`BOSS_CLEARED` over `RunStats` (`gatesPassed`, `ticksAlive`, `coinsCollected`, `points`,
`bossesCleared`/the flag above). It latches once — one `TickFact.ObjectiveMet`, whatever the
tick that satisfies it also does — and the run continues after the objective is met; the banner
and the game-over strip are the player-facing half. The evaluator owns no state of its own, so
there is nothing to hash.

**The achievement pipeline (D13, D14, E17).** The write path stays the M3 one;
`AchievementEvaluator` fills the hook that milestone left empty. `ProgressionManager.apply`
runs rewards → wallet → XP/level → statistics → challenge record → daily record →
achievements → unlocks in order, and that order is load-bearing: achievements are judged on the
statistics the pass just wrote, and unlocks on the achievements they may depend on.
`AchievementEvaluator` is a pure listing pass — scope-resolved conditions over
`Statistics.resolve` (`LIFETIME`), the finished `RunResult` (`RUN`), and
`CollectionProgress.percent` (`COLLECTION`) — returning ids in content order, so the same
profile and run always grant the same ids in the same order; granting, recording and paying
stay in the manager. `applyPurchase` re-runs the trailing achievements → unlocks steps after
every purchase, which is what makes buying the last bird fire its collection achievement
immediately (E17). `ProgressionOutcome` carries plain facts (E31.b): the ids, the levels, the
coin grants — and the screens convert those into events and toasts, where a newly earned
achievement names the coins it paid and a granted unlock names itself.

**The sequencer (D19).** `audio.MusicSequencer` renders a world's `music` block into a
deterministic 8-bar loop — every layer drawing from its own seeded stream, note tails wrapped
across the loop point, peak-normalised — synchronously at boot (menu) and run start (world),
never on a new thread; the boss variant is the same block at ×1.15 tempo, re-rendered on
`BossStarted`/`BossCleared`. It plays through the existing mixer: `SoftwareMixer.playLooping`
keeps one looping voice per id (retargeted, never stacked) and every gain change — volume
slider, mute, the pause duck, the boss crossfade — is a linear ramp of `MUSIC_RAMP_FRAMES`
against a target, so two equal ramps at one target crossfade without a dip. A slide of the
music volume to zero stops the loop instead of leaving it at the old gain, and raising it
re-issues the loop the screens last asked for.

## Run modes, daily, prestige and attract `[M9]`

M9 adds four systems on top of the write path above. None of them changes its order; they only
add readers of the profile and one new record.

**The daily challenge (D28, E27).** `progression.DailyChallenge` derives the whole
configuration from the UTC date: `seed = fnv1a("daily:" + yyyy-MM-dd)`, the date from the
injected `core.TimeSource`, the pick streamed from `RandomProvider(seed).stream("daily")` — a
named stream no simulation shares. One world, one tier from `economy.daily.tierPool` and two
forced modifiers are drawn from *unlocked* content only, the modifier pair re-checked against
the pool's compatibility rules after every draw. The first read settles the day: the pick is
written to `profile.daily` (`date, seed, worldId, tierId, modifierIds, attempts, bestGates`) and
reused for the date even if new content is unlocked afterwards, so a stored pick is rebuilt at
most once and only when the content can no longer play it. `ui/screens.DailyRunSource` builds
the run and ignores the retry seed — one day is one configuration, and `ProgressionManager.apply`
records the attempts and the best gate count under `RunMode.DAILY` (the reward calculator
applies `economy.daily.rewardMult`, ×1.25, for the mode).

**Run modes (D28).** `RunMode` is `STANDARD / SEEDED / DAILY / CHALLENGE`; `RunConfig` carries
the mode and `RunRewardCalculator` reads it. The bird selection screen's mode row lists
Standard and Seeded always and Daily when it has a clock, marks the two gated ones with their
`feature:seeded_runs` condition, and falls back to a standard run when Play is pressed on a
locked mode. Seeded replays `profile.lastSeed`; a challenge keeps its own screen and source
(M8).

**Prestige (D13, E4, E23).** `progression.PrestigeSystem.prestige` is the one writer of the
reset: it snapshots `profile.prestigeBaseline` from the lifetime statistics, resets wallet, XP,
level, upgrades, ability levels and caps, challenge records and the daily pick, rebuilds
`unlocked` as the defaults union the kept `bird:*`/`cosmetic:*` ids, keeps achievements and
statistics, raises `prestigeCount` (max 5), grants `cosmetic:<selectedBird>:prestige` and pushes
`bonusPerPrestige × prestigeCount` into the `PRESTIGE` stat layer via `effectsOf`. The
"since prestige" reading of the cumulative conditions lives where it always has, in
`UnlockEvaluator`, which subtracts the baseline — so the reset grants nothing twice. The
statistics screen owns the two-step confirm and writes the save; the menu draws the badge.

**Attract mode (M9).** `ui/screens.DemoScreen` plays a real `Run` with the `average` bot on a
fixed attract seed, drawn from the named `attract` stream, behind the main menu: the menu starts
it after twenty seconds without input (20 × tick-rate ticks), dims it under its own veil, and
any input — a key, a click, a focus change that reaches the menu — cancels the demo and resets
the idle timer; a focus loss or iconify freezes it. The demo runs profile-less (the content
path of `ContentRunFactory`, boss off, no drafts, no banked rewards), so it can never depend on
or mutate a save.

**MetaSim (E25).** `gameplay.harness.MetaSim` is the career-scale harness: a fresh profile plays
run after run through the real progression stack under one of two purchase policies
(`spender`, `saver`) until a run budget is spent or nothing is left to buy, and
`tools.BalancingSim --meta` prints the runs-to-unlock table `docs/BALANCING.md` §13 records.
The policies branch on `PurchaseResult` values (§ "Spending" of `docs/PROGRESSION.md`), never
on exceptions; the pilot is a shipped `BotPilot` preset, and thresholds are met by data, never
by tuning the bot.

**IconExport (E9).** `tools.IconExport` renders the procedural icon from its vector form and
writes `build/icon/flapforge.png` (256²), `flapforge.ico` (PNG-in-ICO, 16/32/48/256) and
`flapforge.icns` (PNG-in-ICNS, ic07–ic10); `scripts/package.sh` and `release.yml` hand the
per-OS file to `jpackage --icon`. The tool is presentation-adjacent but ships in the `tools`
source set, run by `./gradlew iconExport`.

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
    │       └── screens/    MainMenuScreen (minimal) SettingsScreen (stub) [M0]  GameScreen PauseOverlay GameOverOverlay SeededRunSource ClassicRunFactory ContentRunFactory SeedSequence [M1]  BootScreen [M2; MainMenu/Settings completed]
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
| prestige/PrestigeSystem, PrestigeReward | landed [M9] | `PrestigeSystem` + `economy.json.prestige` |
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
