# Changelog

All notable changes to Flapforge are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Flapforge's own history starts at `[Unreleased]`. The section
"Inherited upstream history" at the end preserves the release notes of the
project Flapforge was forked from, [kingyuluk/FlappyBird](https://github.com/kingyuluk/FlappyBird),
for attribution; those versions were never Flapforge releases.

## [Unreleased]

### Added — M0: skeleton, window, loop, input, menu shell

- Gradle 9.7.1 project (Groovy DSL, committed wrapper, Java 17 toolchain,
  `-Xlint:all,-serial -Werror -parameters`, UTF-8, `--release 17`) with the
  `application` plugin, Gson 2.11.0, JUnit Jupiter via `junit-bom:6.1.3`,
  `test` / `smokeTest` / `perfTest` / `simTest` tasks and a `fatJar` task
  producing `build/libs/flapforge-<version>-all.jar`.
- New package `io.github.michelbr84.flapforge` with the `Flapforge` entry
  point, launch-option parsing, the `app` shell (window, `flapforge-loop`
  thread, AWT input bridge, `FramePresenter` with `BufferStrategy` and null
  implementations, frame limiter, clocks, `Threads`), the pure `core` and
  `input` packages (playfield constants, geometry, seeded RNG, `TimeSource`,
  per-tick `InputQueue`/`InputFrame`, key bindings), the `render` base
  (`FrameRenderer`, `Viewport`, `Fonts`, `TextPainter`, `DebugOverlay`,
  `ProceduralArt`, `WorldPalette`) and the `ui` base (`Screen`,
  `ScreenManager`, `FocusRing`, `Button`/`Label`/`Panel`, a minimal
  `MainMenuScreen` and a `SettingsScreen` stub).
- Scaled, resizable 420×640 window with a procedurally generated icon (the
  default scale is the largest whose decorated window fits the screen), fixed
  60 Hz tick with interpolated rendering, `F3` debug overlay, `F11`
  borderless fullscreen (also from `--fullscreen`; a held key toggles once),
  clean quit. The frame limiter calibrates its wait primitive at startup and
  switches from `parkNanos` to 1 ms `Thread.sleep` chunks where the timer is
  coarse (Windows), so 60 fps holds there too. The input queue keeps keys
  released by a focus loss as short-lived ghosts (a key still held when focus
  returns is re-armed without a second press), treats a same-timestamp
  release/press pair as auto-repeat even across ticks, and coalesces pointer
  motion and resizes so they cannot push key events out of the queue.
- Tests: `ArchitectureTest` (purity bans incl. static `Math` imports,
  `ThreadLocalRandom`, `java.time` clocks, `System.exit` outside the
  watchdog, Swing in any source set), `ViewportTest`, `InputQueueTest`,
  `GameLoopTest`, `FrameLimiterTest`, `KeyRepeatFilterTest`,
  `WindowScaleTest`, `FocusRingTest`, `MenuNavigationTest` (menu through the
  queue, headless), `ProceduralRenderTest` (minimal) and the `@Tag("gui")`
  `SmokeWindowTest` (window/loop/fullscreen/capture, fullscreen start,
  Robot-driven menu navigation with a held `F11`, the real quit path).
- Build: `tools` source set (`src/tools/java`) with the `balancing`,
  `saveInspector`, `contentCheck`, `assetValidator` and `iconExport`
  `JavaExec` tasks (`-PtoolArgs`), ready for the tools of later milestones.

### M0-only scaffolding (to be replaced, not kept)

- `ui/UiText` holds the English UI strings until `content.Strings` /
  `StringKey` land in M2, which replaces it.
- `ui/screens/GameStubScreen` is the placeholder behind Play; it is deleted
  when `GameScreen` lands in M1.
- `render.TextPainter.drawOutlined` is the plan's `TextPainter.outlined()`.
- `app/KeyRepeatFilter` (the XWayland/VNC auto-repeat pair filter extracted
  from `AwtInputBridge` so it can be unit-tested headless) and the extra
  tests listed above are kept.
- Repository hygiene: `LICENSE` (renamed from `License`), `.gitignore`,
  `.editorconfig`, `.gitattributes`, `THIRD_PARTY_NOTICES.md`,
  `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, issue and pull
  request templates, `build.yml` (3 OSes + JDK 21, cross-platform
  determinism check), `test.yml` (unit, `xvfb` smoke, simulation, JUnit
  report), `dependabot.yml`, `docs/` (index, architecture and development
  drafts), `scripts/` build and run wrappers.

### Changed — M0

- `README.md`: badges, Controls, Getting Started, Running the Game,
  Technology, Project Structure and Assets sections rewritten for the new
  code base; design prose kept for the M9 pass.
- `CHANGELOG.md`: inherited entries relabelled as upstream history with links
  pointing at the upstream repository.

### Removed — M0

- The inherited upstream implementation: `src/main/java/com/kingyu/...`,
  `FlappyBird.jar` and the `resources/` tree (`img`, `wav`, `readme_img`,
  `score`). They remain in git history but are not part of any Flapforge
  build or release (see `THIRD_PARTY_NOTICES.md`).
- `STRUCTURE.md` (the pre-rewrite layout proposal), superseded by the
  reconciliation table in `docs/ARCHITECTURE.md`.

### Added — M1: classic core (upstream feel, procedural Green Fields)

- Pure simulation under `gameplay`: `Simulation` + `SimContext`/`SimInput`/
  `TickReport` (immutable per-tick facts), `bird/{Bird,BirdPhysics,HitboxSpec}`,
  `obstacle/{Obstacle,ObstacleKind,PipeGate,Oscillator,ObstacleLayer,
  ObstacleSpawner,SpawnTable,SpawnDecision}`,
  `collision/{CollisionSystem,CollisionReport,CollisionCause}` with
  displacement sub-stepping, the commutative stat pipeline
  `stats/{StatId,StatOp,StatModifier,StatSheet,StatBreakdown,EffectStack,Layer,
  RuleFlag,RuleSet}`, `difficulty/{DifficultyCurve,DifficultyState}`, the run
  state machine `run/{RunConfig,RunMode,RunPhase,RunStats,Run,RunInput,
  RunResult,RunSetup}`, the seam records `spec/*` and the deterministic harness
  `harness/{Pilot,BotPilot,HeadlessRunner}`.
- Upstream parity: flap 405 px/s, gravity 1800 px/s², scroll 120 px/s, gap 128,
  gate interval 160, `top ∈ [80, 400]`, floating gates, the moving-chance ramp
  `0.05 + 0.05 × gates`, the spawn cursor (`last.x + 40 < 420`) and upstream's
  dedicated first-pair branch (always a static standard gate). The one
  intentional deviation is the ground rule (death at `y ≥ 581.5` instead of the
  buried-bird window); every number and its measurement is recorded in
  `docs/BALANCING.md`.
- Content pipeline (`content`): `ContentLoader`, `StrictBinder` (unknown key =
  error with a JSON pointer), `GameContent`, `Registry`, `RunFactory`,
  `ContentValidator` (ids, enums, the classic table at gate 0 and gate 25),
  `ContentException`/`UnknownIdException` and the `defs/*` records, plus the
  shipped `data/birds.json` (Forgewing and its four palettes) and
  `data/difficulty.json` (curves `classic`/`standard`, tiers
  `normal`/`hard`/`nightmare`). Both launches load and validate the content
  before opening a window; a broken data file aborts the launch.
- Rendering (`render`): `BackgroundRenderer` (sky, two parallax hill bands, the
  253 px ground band), `CloudLayer`, `BirdRenderer` (8-frame wing cycle, pose
  from the velocity sign), `ObstacleRenderer` and `GameRenderer`, all
  procedural and interpolated with the frame alpha; `HudRenderer` with the
  blinking READY hint and the outlined score.
- Screens (`ui.screens`): `GameScreen`, `PauseOverlay`, `GameOverOverlay`, the
  `SeededRunSource` seam with `ClassicRunFactory`/`ContentRunFactory`, and
  `SeedSequence`. Play opens a run; the first flap starts it; `Esc`, a focus
  loss or an iconify pauses it (and the resume drops the banked loop time);
  game over offers an instant retry with the next seed (`--seed N` walks
  `N, N+1, N+2 …`).
- `--headless-run N` now prints the determinism line
  `hash=<16 hex> ticks=<n> gates=<g> points=<p>` from the shipped content, the
  artefact the cross-OS CI job compares.
- Tools: `src/tools` `BalancingSim` (`./gradlew balancing -PtoolArgs="..."`)
  with its `tools/balancing` wrappers.
- Tests: `ClassicReference` (literal transliteration of the upstream integer
  loop), `ClassicFeelTest` (air trajectory to 0.0 px), `GroundRuleTest`,
  `BirdPhysicsTest`, `HitboxTest`, `CollisionSystemTest`, `PipeGateLayoutTest`,
  `ObstacleSpawnerTest`, `StatSheetTest`, `DifficultyCurveTest`,
  `RunLifecycleTest`, `RunInputTest`, `DeterminismTest`, `GoldenRunTest`
  (frozen fixture), `PerfBudgetTest` (`@perf`), `ContentIntegrityTest`,
  `ContentValidatorTest`, `StrictBinderTest`, `ContentWiringTest`,
  `RandomProviderTest`, `GameScreenTest`, `RenderLayerTest`, plus the fixtures
  `golden_seed42.txt` and `content_frozen/*.json`.
- `docs/BALANCING.md`: the full physics conversion table (air, ground rule,
  cosmetic rows, difficulty), every "measured" entry produced by a test.

### Changed — M1

- `RandomProvider` runs the composed stream seed through the SplitMix64
  finaliser before handing it to `java.util.Random`. Without it, consecutive
  seeds — which is what instant retry and the balancing sweeps produce — share
  an almost linear first draw (measured over seeds 42–2041: the first `spawn`
  draw never left `[0.31, 0.50)`), biasing the opening of every run.
- `CloudLayer` derives its speed from the run's resolved
  `SCROLL_SPEED × TIME_SCALE` instead of the hard-coded 240 px/s, so the sky
  cannot desynchronise from the ground on the faster tiers or under Slow Time;
  the dead drift stays an absolute 30 px/s, as upstream's was.
- The clouds keep drifting under the game-over overlay and survive an instant
  retry, matching upstream (`resetGame()` reset the bird and the pipes, never
  `GameForeground`).
- `RuleSet` is backed by a flag bit mask, so its `hashCode` (and that of every
  record carrying it) is a specified value instead of `EnumSet`'s sum of
  identity hashes; `StatModifier`, `CurveEntry`, `RampEffect`, `BirdProfile`
  and `WorldSpec` hash their enums by ordinal for the same reason.
- `ScreenManager` gained the fullscreen handshake window (a focus loss caused
  by an `F11` toggle must not pause a run) and the accumulator-reset request
  the game loop honours after a pause.
- The `GameScreen` run seam is `SeededRunSource` (was `RunFactory`, which
  collided with `content.RunFactory`).
- `build.yml`: the cross-platform determinism job now matches the real
  `hash=<16 hex> ticks=… gates=… points=…` line (the old pattern required the
  hash to be the whole line and never matched) and no longer runs with
  `continue-on-error`, so a divergence between operating systems or JDKs fails
  the workflow — the M1 TODO both steps carried.

### Removed — M1

- `ui/screens/GameStubScreen` and the `UiText` strings that went with it: the
  real `GameScreen` replaces the M0 placeholder.
- `gameplay/harness/ScriptedPilot` moved to `src/test/.../support` — a replay
  helper has no production caller, and Appendix A §3 puts it there.

### Added — M2: presentation base (boot, settings, sound, strings)

- Persistence (`persistence`): `SavePaths` (per-OS profile directory,
  `--home`/`flapforge.home`/`FLAPFORGE_HOME` overrides), `AtomicFiles`
  (temp file + fsync + `ATOMIC_MOVE`, three immediate retries, non-atomic
  `REPLACE_EXISTING` fallback, then `IO_FAILED`; never sleeps, never throws),
  `JsonCodec`, `Settings` (the full §4 shape) and `SettingsStore` — missing
  keys default, unknown keys survive a round trip, a version mismatch archives
  the old file as `settings.v<N>.json` and restores the defaults with a warning
  toast. Writes run on `Threads.saveExecutor()`; a failed write comes back to
  the loop thread as a `SaveFailed` event and a toast.
- Event bus (`event`): `EventBus` (synchronous, thread-confined, exact-type
  delivery, nested publishes queued rather than nested) and the complete
  `GameEvent` hierarchy, defined in full from this milestone so later
  milestones add subscribers instead of reopening the file.
- Audio (`audio`): `AudioBackend` with `SoftwareMixer` (one pre-opened
  44.1 kHz/16-bit/stereo `SourceDataLine`, one daemon mixing thread, bounded
  drop-on-full command queue, soft `tanh` limiter, underrun counter),
  `SoundBank` (manifest → classpath → synthesis, resampled and widened once at
  load time), `ToneSynth` (16 procedural cues), `Voice`, `NullAudio` and
  `AudioManager`, which is the only thing that decides which cue a moment
  deserves and owns the three volumes and the mute flag.
- Internationalisation (`content`): `Strings` + `StringKey` with
  `data/strings/en.json` (source of truth) and `pt_BR.json`, `{n}` placeholder
  substitution, English fallback for a missing translation, `language: auto`
  against the system locale, and a live switch that re-labels every screen.
- Rendering (`render`): `AssetManager` + `AssetResolver` (manifest-only id
  resolution, `worlds/<world>/<id>` → `<id>` → procedural), `Sprite`,
  `SpriteSheet`, `Animation`, `Camera` and `ParticleSystem`, plus
  `assets/manifest.json` (empty list on purpose) and the `.gitkeep` folders for
  future art packs. `GameRenderer` resolves a bird sheet per run, so a manifest
  entry really replaces the procedural art.
- UI (`ui`): `Slider`, `Toggle`, `ListView`, `Toast`/`ToastLayer`, `UiCues`,
  `BootScreen` + `app/BootSequence` (font and audio warm-up on a daemon thread
  while the splash is on screen), the completed `MainMenuScreen` and the real
  `SettingsScreen` — language, three volumes, mute, fullscreen, integer
  scaling, frame-rate cap, smoothing, FPS overlay, reduce flashing, text scale,
  hold-to-flap and key rebinding, all applied live and written after a debounce.
- `app.GameContext` gained `applySettings` as the single place that knows which
  engine object owns which setting, `applyAndSave`, `drainSaveResults` and the
  three global hotkey actions; `--no-audio` and `--lang CODE` reach the game.
- Tests: `SettingsStoreTest`, `AtomicFilesTest`, `EventBusTest`, `StringsTest`,
  `FontsTest`, `AudioWiringTest`, `SettingsScreenTest`, the `audio/*` suite,
  `render/AssetManagerTest`, `render/ParticleSystemTest`, and an extended
  `ProceduralRenderTest` (every screen in both languages, a manifest entry
  replacing the procedural bird) and `SmokeWindowTest` (the settings screen
  driven through the toolkit).
- Docs: `docs/ARCHITECTURE.md` gained the audio pipeline, the event-bus rules
  and the i18n layer; `docs/DEVELOPMENT.md` gained the profile-directory
  layout, the `--no-audio`/`--lang`/`--home` flags and the recipe for adding a
  string key.

### Changed — M2

- `F11`, `F3` and `M` no longer poke the engine directly. `ScreenManager` still
  owns the keys, but runs handlers that flip the matching field of
  `settings.json`, apply it and persist it — otherwise `applySettings` pushed
  the stored value straight back and the state was lost on restart (§4 persists
  `fullscreen` and `showFps`).
- `SettingsStore` gained `hold(...)`, which adopts a state as the one in force
  without writing it, and `GameContext.applySettings` calls it: with a debounced
  write pending, `settings()` used to report the last *saved* state, so a hotkey
  built on it reverted the edit the player was making. `SettingsScreen` also
  subscribes to `SettingsChanged` and re-reads its working copy from a change it
  did not raise, so the pending flush cannot resurrect a stale copy.
- A failed settings write is reported by draining the store's completed-write
  queue on the loop thread instead of reading `lastWrite()` on the line after
  `save()`, which returned the *previous* write's result on the real
  (asynchronous) save executor.
- The audio device is opened in the `BOOT_AUDIO` boot step, on the boot thread,
  instead of on the launch thread: `AudioBackend.create` costs ~240 ms on a cold
  device and the window was already visible and unpainted. The step now also
  decodes the sound bank synchronously (`warmUpBlocking`), so the splash's
  progress reflects work that happened and the decode never lands between two
  device writes.
- `SoftwareMixer` opens the line with four write-passes of headroom (it had
  exactly one, so any slow pass starved the device) and samples the underrun
  counter immediately before the write instead of at the top of the pass, where
  the buffer has just been refilled and a drained line can never be seen.
- `SoundBank`'s multichannel downmix folds channels above the second into
  *both* outputs, as its javadoc always said; the even/odd split it implemented
  would have sent a 5.1 centre channel only to the left and the LFE only to the
  right.
- `Fonts` keeps its caches in `AtomicReferenceArray`s with acquire/release
  access: the boot warm-up fills them from `flapforge-boot` while the loop
  thread draws the splash, and `java.awt.Font` has no final fields.
- `GameApplication.detectRefreshRate()` caches the display mode. Resolving it
  is a 6.5 ms XRandR round trip and `applySettings` resolves the frame-rate cap
  on every slider step.
- One `AssetManager` is now built per launch and shared by the sound bank and
  the new `AssetResolver` the renderers read; manifest paths accept both the
  documented `sprites/...` form and §4's `assets/...` form.
- `KeyBindings.toMap()` writes only the seven rebindable actions, matching §4;
  the focus arrows and `BACK` are fixed defaults and are no longer dead keys in
  the file.
- `SettingsStore` treats a `settings.json` with no `version` key as a file with
  a missing key (defaults fill it in) rather than a version mismatch, and a
  second reset from the same version keeps the first archive
  (`settings.v<N>-2.json`).
- `GameWindow` is created with the merged fullscreen state, so a stored
  `fullscreen: true` opens fullscreen instead of opening windowed and jumping on
  the first loop tick.
- `gameover.retry_hint` no longer advertises `Enter: summary`; the summary
  screen arrives in M3 and the key does nothing in M2.
- `SmokeWindowTest`: Robot key taps get the same bounded retry clicks already
  had (re-sent only when the press had no effect at all, so the "exactly one
  edge" assertions still hold), every focus wait is a wall-clock deadline rather
  than a frame count, each rig starts from a fresh `build/smoke/home`, and the
  test that starts the real application passes `--home build/smoke/app-home` —
  it previously read and could rewrite the developer's own `~/.flapforge`.
- `ProceduralRenderTest`'s per-frame allocation budget moved to the `perf` tag
  (§7 keeps budgets in `perfTest`), and its two-language sweep now asserts the
  English and Portuguese frames differ instead of only being non-uniform.

### Removed — M2

- `ui/UiText`, the M0 placeholder for player-facing strings: `content.Strings`
  and `StringKey` replace it, and every screen now reads the shipped tables.

---

## Inherited upstream history (kingyuluk/FlappyBird)

The entries below are the original release notes of
[kingyuluk/FlappyBird](https://github.com/kingyuluk/FlappyBird), the MIT-licensed
Java Flappy Bird implementation Flapforge was forked from. They are kept for
attribution only; all links point at the upstream repository.

### [Unreleased upstream](https://github.com/kingyuluk/FlappyBird/compare/v1.2.2...master) (2020-08-06)

### [1.2.2](https://github.com/kingyuluk/FlappyBird/compare/v1.2.1...v1.2.2) (2020-07-14)

#### Features

* Improved the scoring system ([33ad51a](https://github.com/kingyuluk/FlappyBird/commit/33ad51a97bcb6c2adce3fc944fa5aea00d210198))

#### BREAKING CHANGES

* Removed the timer and updated the scoring system, resulting in more accurate score tracking.

### [1.2.1](https://github.com/kingyuluk/FlappyBird/compare/v1.2.0...v1.2.1) (2020-07-12)

#### Features

* Changed the audio playback method ([9429be6](https://github.com/kingyuluk/FlappyBird/commit/9429be613a21752d2c61e38ca7df87fb4a0b51b9))

#### BREAKING CHANGES

* Repeatedly playing short audio clips using the `AudioClip` class could cause thread conflicts and make the game freeze. Audio playback was changed to use the `AudioPlayer` implementation from `sun.audio`.

### [1.2.0](https://github.com/kingyuluk/FlappyBird/compare/v1.1.0...v1.2.0) (2020-07-11)

#### Features

* Added randomly generated pipes that can move vertically ([ab33686](https://github.com/kingyuluk/FlappyBird/commit/ab33686c8c2ace54da3ddffe220b40a33100989f))

#### BREAKING CHANGES

* The probability of spawning moving pipes now increases as the player's current score increases.

### [1.1.0](https://github.com/kingyuluk/FlappyBird/compare/v1.0.0...v1.1.0) (2020-07-11)

#### Features

* Added floating pipes ([074595b](https://github.com/kingyuluk/FlappyBird/commit/074595b3408a1323b41226d4b4259c6aff696888))

### [1.0.0](https://github.com/kingyuluk/FlappyBird/compare/d158fa5ca5927e1febcd460e8d61b5a16756c761...v1.0.0) (2020-07-09)

#### Features

* Implemented the core gameplay features of the original Flappy Bird ([d158fa5](https://github.com/kingyuluk/FlappyBird/commit/d158fa5ca5927e1febcd460e8d61b5a16756c761))
