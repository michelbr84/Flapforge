# Changelog

All notable changes to Flapforge are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the
project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Flapforge's own history starts at `0.1.0` (2026-09-03), the milestone M0–M9
release; the M10 Android port joined that release on 2026-09-05 as an addendum
inside the same section, not as a new version. The section
"Inherited upstream history" at the end preserves the release notes of the
project Flapforge was forked from, [kingyuluk/FlappyBird](https://github.com/kingyuluk/FlappyBird),
for attribution; those versions were never Flapforge releases.

## [Unreleased]

## 0.1.0 — 2026-09-03

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

### Added — M3: run economy, streaks and save

- Run economy (`gameplay`): `gameplay/pickup/{Coin,PickupLayer}` — coins are
  spawned as a trail through the safe band of every scoring obstacle,
  `COIN_SPAWN_RATE` being the *expected* number of coins per gate (E2, exactly
  one draw from the `coins` stream per scoring spawn, none at all under
  `NO_COINS`), scroll with the world, are attracted by `MAGNET_RADIUS` and are
  collected by the bird's hitbox. `run/StreakTracker` counts clean gates (D26:
  no near miss on that column, no shield absorb, no revive) and the reward
  steps they earn. Both fold into `Simulation.stateHash()`.
- `run/{RewardSummary,RewardContext,RunRewardCalculator}`: the E32.a coin and
  XP formula, pure and term by term, so the game-over strip can show the
  breakdown instead of one number. Participation is gated (a 0-gate, sub-180
  tick dive earns nothing) while the first-run bonus is not, so a profile's
  first run always pays.
- Content (`content`): `defs/{EconomyDef,RewardsDef,StreakRewardDef,XpDef,
  XpCurveDef,LevelRewardDef,FeatureDef,DailyDef,PrestigeDef}` and
  `data/economy.json` (currencies, run rewards, the XP curve with its level
  rewards, the two feature unlocks, the daily block and the prestige block —
  no shards, E4). `GameContent.economy()` exposes it; `ContentValidator` gained
  the economy rules (currencies, integer level-reward keys inside the curve,
  unique feature ids, known prestige keeps).
- Progression (`progression`): `PlayerProfile` — the **complete v1 field set**
  of §4 as patched by E3/E4/E21/E23/E27 — with `normalize()`/
  `normalizeAndReport()`, `Statistics` + `StatisticKey` (the §4 list plus a
  capped 100-entry run history), `Wallet`, `PlayerLevel` (thresholds by
  repeated multiplication, never `Math.pow`), `ProgressionRules`
  (`fromEconomy` is the single adapter between `economy.json` and the write
  path), `ProgressionManager` — D14's fixed order, applied exactly once per
  run, with the M4 unlock and M8 achievement evaluators already in their slots
  as hooks — and `ProgressionOutcome`. The package imports neither `event` nor
  the toolkit.
- Persistence (`persistence`): `SaveFile` (versioned envelope), `SaveManager`
  (parse → migrate → aliases → bind → normalise; write = the remembered JSON
  tree deep-overlaid with the serialised profile, so an unknown field at any
  depth survives while every map and list node is replaced wholesale, E22),
  `SaveMigrator` + `Migration` (ordered, idempotent, pre-migration backup, a
  save newer than the build is refused and the session plays without saving)
  and the quarantine/backup policy — nothing is ever deleted, `--reset-save`
  included.
- Application wiring: `GameApplication` opens the save on
  `Threads.saveExecutor()` with the injected `SystemTimeSource`, loads the
  profile before the window exists and turns a load notice into a toast;
  `GameContext` carries `save`/`progression`/`progressionRules`, exposes
  `profile()` and `saveProfile()`, and drains both write queues into one
  `SaveFailed` event and toast per failure. `GameScreen` writes each finished
  run through `ProgressionManager.apply` once, publishes `CurrencyChanged`,
  `XpGained`, `LevelUp`, `AchievementUnlocked`, `UnlockGranted`,
  `ChallengeCompleted` and `DailyRecorded`, and saves — all before the
  game-over overlay is pushed, so the instant retry keeps every reward (D29).
  `--reset-save` quarantines the old file and confirms on stdout.
- UI and rendering: `render/PickupRenderer` draws the coins as discs turning on
  their vertical axis — the phase comes from the simulation tick, so two
  captures of the same tick are identical — and turns every coin the bird takes
  into a flourish in the shared `ParticleSystem`, because the model drops a
  collected coin on the next tick. The HUD grew a coin counter behind a small
  spinning icon and a clean-gate streak line under the score that lights a
  flame once the streak reaches `economy.rewards.streak.step`. The score keeps
  its upstream place (centred, baseline 64, bold 32).
- `GameOverOverlay` grew into a reward strip — gates, points, time, coins (with
  the level-reward bonus in brackets), XP, best streak and the level reached —
  sized to its rows, and `Enter` on it opens `ui/screens/RunSummaryScreen`: the
  full breakdown with **every** term of `RewardSummary` on its own row
  (participation, first-run bonus, gates, points, streak steps, bosses,
  challenge, their base sum, the three multipliers, the coins collected in the
  world and the total), the XP with the level `ProgressBar` it moved and any
  level-up, the personal-best markers, the run duration, and the seed with its
  mode; Retry starts a new run with a new seed and Menu returns (D29).
- `ui/screens/StatisticsScreen`, reachable from the main menu: the lifetime
  counters grouped (flights, distance, economy, streaks, deaths by cause) and
  the last runs of `statistics.runHistory` paged newest first in a `ListView`.
- `ui/component/{ProgressBar,CurrencyDisplay}`: a labelled 0..1 bar (the level
  progress; M8's milestones reuse it) and an icon-plus-amount wallet readout
  that rolls its number up on the simulation clock. The main menu gained the
  Statistics entry and, when the session has a profile, the wallet readout.
- New string keys in both languages: `stat.coins`, `stat.xp`,
  `stat.streak_best`, `gameover.level_up`, `toast.save_restored`,
  `toast.save_reset`, `toast.save_read_only`, `toast.level_up`,
  `menu.statistics`, `hud.coins`, `toast.save_unreadable`, the `summary.*`,
  `mode.*`, `reward.*`, `stats.*` and `death.*` families.
- Tools and docs: `tools/SaveInspector` (+ `tools/save-inspector/`) reads a
  profile directory through the same `SaveManager` the game uses and fails when
  the save was unusable, refused or repaired; `docs/SAVE_SYSTEM.md` documents
  the layout, the load order, migrations, the overlay rule, the failure policy,
  the pre-1.0 reset policy and how the application wires it;
  `docs/ARCHITECTURE.md` gained the progression write path.
- Tests (475 at M2 → **653** in `test`, all green, plus 7 in `smokeTest`):
  `PickupTest`, `StreakTrackerTest`,
  `RunRewardCalculatorTest`, `WalletTest`, `PlayerLevelTest`,
  `ProgressionManagerTest`, `SaveManagerTest`, `SaveCorruptRecoveryTest`,
  `SaveMigrationTest`, plus the two integration suites written when the halves
  were joined: `ProgressionEconomyTest` (the shipped `economy.json` really pays
  E32.a's 75 coins and 115 XP for a first 10-gate run, and its level 2 reward
  on top) and `ProgressionWiringTest` (the game-over path end to end on the
  loop, every write in a `@TempDir`). Fixtures: `save_v1.json`,
  `save_corrupt.json`, `save_future.json`, `save_unknown_fields.json`, the
  `save_v1_to_v2` pair and `content_frozen/economy.json`. The UI half adds
  `RunSummaryScreenTest` and `StatisticsScreenTest` (queue-driven on the real
  loop: the breakdown rows carry the numbers the formula produced, Retry starts
  a new run with a new seed, Menu returns, an empty profile renders),
  `ProceduralRenderTest` covering both new screens in both languages and with
  an empty profile, and a `SmokeWindowTest` case that plays a scripted run to
  death with the save layer wired and screenshots `reward-strip` and
  `run-summary` into `build/smoke/`. `RenderLayerTest` gained the coin rows:
  the spin is a pure function of the tick, one pickup is exactly one flourish
  (counted off the layer, so a collected coin still in the list cannot fire
  twice) and the HUD lights its streak flame at the economy's reward step.

### Changed — M3

- The golden run and the `--headless-run` hash moved: coins are part of the
  simulation now, so they are part of its state hash, and the streak resolves
  one step later than the score (see *Fixed* below). `fixtures/golden_seed42
  .txt` was re-recorded; `--headless-run 3000 --seed 42` prints
  `hash=eaaa01685261a433 ticks=3000 gates=36 points=36` and the 600-frame line
  CI compares is `hash=b014de5e0ccf63dc ticks=600 gates=6 points=6`, both
  identical on JDK 17 and JDK 21.
- `RunSetup` gained `streakStep` (from `economy.json.rewards.streak.step`);
  the four-argument constructor still exists and defaults it, so the hard-coded
  classic seam and the tests that load no economy are unaffected.
- `ContentLoader.FILES` is the set the game loads (`birds`, `difficulty`,
  `economy`); `M1_FILES` stays for the fixtures that predate the economy.
- `Threads.awaitBootIdle(long)`: the quit path now waits (bounded) for the boot
  warm-up before closing the audio. A quit within the first few hundred
  milliseconds could otherwise close the `AudioManager` a moment before the
  warm-up handed it a freshly opened line, leaking that line — and left a
  `flapforge-boot` thread alive past the shutdown, which
  `SmokeWindowTest.quitPathThroughGameApplicationEndsBeforeTheWatchdog` was
  already failing on before this milestone.
- D15's **60-second autosave** is wired after all (it needed no M4 screen): the
  per-tick loop task saves a dirty profile every 3600 ticks unless the top
  screen is running a live run (`Screen.blocksAutosave()`, overridden by
  `GameScreen`). It is also what retries a write that failed, since a failure
  now leaves the profile dirty.
- `--reset-save` moves `save.json.bak` aside as well
  (`save.bak.reset-<epochMs>.json`). Without that the next launch would restore
  the abandoned profile from the backup and silently undo the flag; the help
  text and the README/DEVELOPMENT tables say what the flag does — move the save
  aside — instead of "delete", which it never did.
- `Threads.saveExecutor()` no longer collapses a burst of writes with
  `DiscardOldestPolicy` (see *Fixed*); it is a plain single daemon thread with
  an unbounded queue, and `SaveManager` does the coalescing itself.

### Fixed — M3 (review pass)

Three reviewers audited the milestone; every finding of theirs that is a defect
is fixed here, with a test that fails without the fix.

- **A restored backup is written back to `save.json` immediately**, and a
  missing `save.json` now consults `save.json.bak` before starting a fresh
  profile. Previously a player who recovered from the backup, browsed the menu
  and quit found `NEW_PROFILE` next session, and that session's first write
  replaced the good `.bak` with a 0-coin profile — the whole profile was gone
  two sessions after the corruption, which is exactly what `SAVE_SYSTEM.md`
  promises can never happen.
- **The once-per-session backup goes through `AtomicFiles`.** It was a plain
  `Files.copy(REPLACE_EXISTING)`, which truncates the destination before it
  writes: a crash in the middle of it destroyed the previous good
  `save.json.bak`, the one artefact the recovery policy rests on. The copy is
  also no longer recorded as done unless it reported OK, so a transient failure
  is retried on the next load instead of skipped for the session.
- **A save that cannot be *opened* is no longer treated as corrupt.** An
  antivirus lock, a cloud-sync placeholder or a transient `EIO` used to
  quarantine an intact file as `save.corrupt-<epochMs>.json` and never look at
  it again. There is a new status `UNREADABLE`: the file is left exactly where
  it is, the backup carries the session if there is one, and the session runs
  read-only so it cannot overwrite what it failed to read
  (`toast.save_unreadable` in both languages).
- **The dirty flag is cleared by a write that landed, not by a write that was
  queued.** A read-only profile directory or a full disk made every write fail
  while `saveOnExit` was told there was nothing left to save. `isDirty()` is now
  a version comparison (`markSaveQueued`/`confirmSave`), so a change made after
  a write was queued also keeps the profile dirty.
- **`SaveManager` coalesces its own writes.** The save executor used to do it by
  discarding the queued task, which never ran and never reported, so `pending`
  leaked one slot per discard and `flush(timeoutMs)` could never return `true`
  again for the life of the manager.
- **`profile.prestigeBaseline` left `REPLACE_WHOLESALE`.** E22 replaces maps and
  lists; `PrestigeBaseline` is a fixed-shape POJO, so listing it only dropped
  unknown fields a newer build had put inside it. Its one list member is a JSON
  array, which the overlay already replaces wholesale.
- **The clean-gate streak is resolved after the graze window closes, not at the
  score line (D26).** A gate scores when its right edge passes the bird's
  hitbox; the near-miss test uses that box inflated by 6 px, so for three more
  ticks the bird could graze a column already counted as clean. Measured over
  200 seeded runs per preset, 98 % of an expert's near misses cost nothing —
  the golden run itself recorded `nearMisses=3` beside a perfect `streakBest=36`
  over 36 gates. The score, the points and the ramp stay exactly where they
  were; only the streak waits. The same seed now records `streakBest=14`.
- **The XP participation is gated like the coin participation.** This is a
  deliberate amendment to E32.a's literal XP formula: XP buys levels and levels
  pay coins, so an ungated XP participation handed the 0-gate instant-retry dive
  back everything the coin gate takes from it. Measured with the shipped
  `economy.json`: 400 dives of 48 ticks reached level 21 and 2725 coins in 5.3
  minutes of simulated time — **511 coins/min against 251** for a bot that
  actually plays. The same 400 dives now pay 25 coins in total (the
  unconditional first-run bonus) and no XP at all.
- **A coin trail follows its gate.** The trail was placed on
  `Obstacle.safeBandY` at spawn time and never moved, while a moving gate swings
  ±51 px around it; the worst-case clearance is `gap / 2 − 51 − 8`, which is
  5.0 px at the shipped `normal` gap but −1.4 px at `hard` and −7.8 px at
  `nightmare` — a coin inside a lethal hitbox, a pickup you could only take by
  dying. Coins now re-read the band every tick and detach the first time the
  magnet pulls them.
- **`ProgressionManager`'s "apply a run once" guard works.** It compares result
  identity, and `Run.result()` built a fresh snapshot on every call, so two
  reads of one finished run paid it twice (measured: 1857 → 3789 coins,
  `totalRuns` 2). A finished run now returns the same `RunResult` instance to
  every caller.
- `Coin` uses `StrictMath.hypot` for the magnet distance: `Math.hypot` is only
  specified to within 1 ulp, so it is not guaranteed bit-identical across JVMs,
  and the magnet lands in M6.
- `ContentValidator.warningsOf(GameContent)` implements E1's missing rule:
  `rewards.coinsPerPoint == 0` leaves `SCORE_MULT` with no sink at all. It is a
  warning, printed at launch, not a load failure.
- `Threads.shutdown(long)`'s answer is used: a drain that did not finish inside
  2000 ms now says so on stderr instead of losing the last write in silence.
- `BalancingSim` reports the economy: `coinsSpawned`, `coinsCollected`,
  `streakBest`, `streakSteps`, `coins` and `xp` per run, in the summary and in
  the CSV. Every M3 number in `docs/BALANCING.md` comes from it.

### Deferred — M3

- The purchase and selection-change write triggers of D15: the run-end, exit
  and 60-second autosave triggers are wired; those two arrive with the shop and
  selection screens that can fire them (M4).
- No advisory lock on the profile directory: two instances on one `save.json`
  still overwrite each other. D15 does not ask for one and the recovery policy
  does not depend on it, so it is a note, not a gap.

### Fixed — M4

- The GUI smoke suite no longer fails when the desktop session is locked: each
  Robot-driven test first checks that an unbound canary key reaches the canvas
  and is skipped with an explicit message when the session swallows synthetic
  input, so a locked screen reports an unavailable desktop instead of a broken
  game. A session that delivers the canary and then drops an event still fails.

### Added — M4: birds, upgrades, unlocks, shop

- Content (`content`): the full seven-bird roster in `data/birds.json` (base
  stats, `BIRD` effects, `rampEffects`, `synergyEffects`, innate passives, three
  palettes each with their E20 conditions, silhouette key and unlock), the three
  upgrade trees and their eighteen nodes in `data/upgrades.json`
  (`defs/{TreeDef,UpgradeDef,GrantDef,GrantType}`), and `data/aliases.json` with
  the per-field id reconciliation table of E21 (`defs/AliasDef`, empty until an
  id is renamed). Stub `data/{abilities,worlds,challenges,achievements}.json`
  ship their **final** unlock and reward blocks so the graph can be proved a
  milestone before the systems that read the rest exist (E19), with
  `defs/{AbilitiesDef,AbilityDef,AbilityKind,AbilityLevelDef,AbilityTag,
  WorldsDef,WorldDef,WorldPaletteDef,AmbientDef,BossDef,MusicDef,SfxSet,
  RuleCyclesDef,RuleCycleOptionDef,ChallengesDef,ChallengeDef,ObjectiveDef,
  ObjectiveType,AchievementsDef,AchievementDef,AchievementConditionDef,
  CompareOp,CounterScope,RewardDef}` and `ContentKind`.
- `ContentValidator` is complete (D10): shape, ids, cross-references, counters
  (E5), cost/level ladders, the prerequisite DAG and its tier consistency, the
  E3 caps, the E20 cosmetic-only conditions, rule contradictions, the classic
  table, `BirdDef.shape` against `BirdDef.SHAPES`, the placement rule for a
  `purchase` condition (root or directly under an `any_of`, never inside an
  `all_of`), the string keys of E31.h, and the warnings (a no-op modifier, a
  points sink with no consumer). One `content_bad` fixture per rule.
- `content/UnlockGraph` (D13): every condition, reward and grant as one directed
  graph, proving no cycle, reachability from the E18 default set, a cumulative
  path for every non-cosmetic unlockable, and that every declared currency has a
  source derived from the reward blocks. An `UNLOCK` upgrade grant is priced at
  the node's first level plus one level of every prerequisite, transitively.
- Progression (`progression`): `UnlockEvaluator` (every D13/E20 condition type,
  "since prestige" per E23, a palette needs its bird, a `purchase` is never
  earned), `UnlockManager` (the shop: check → deduct → grant → account →
  `applyPurchase` → save), `UpgradeManager` (one level of one node, the E31.f
  grants and their E3 ceilings, the `UPGRADES` layer, and `reconcile` for
  `aliases.json`), `SelectionManager`, `RunLoadout` (profile → `RunConfig`),
  `ProgressionManager.applyPurchase`, and `PurchaseResult`/`PurchaseStatus`/
  `SaveTrigger`.
- `gameplay/spec/SynergyEffect` and the `BIRD_SYNERGY` layer: a bird's synergy
  effects scale with `Σ profile.upgrades.values()`, resolved once at run start,
  which is what makes Cinder the late-game bird.
- UI (`ui`): `component/{CardGrid,TabBar,Tooltip}` and
  `screens/{BirdSelectionScreen,UpgradeTreeScreen,ShopScreen,ProgressionText}`.
  The bird screen shows the roster with procedural portraits in the selected
  palette, the palette swatches, the tier picker and the stat breakdown of the
  run that would start now, by source and layer; the upgrade screen lays the
  nodes out by tier with a line from every prerequisite and moves wallet, card
  and live stat panel in the same tick as a purchase; the shop lists every
  priced id the profile does not own, cheapest first. The main menu gained
  Birds, Upgrades and Shop.
- `tools/ContentCheck` and the `contentCheck` Gradle task: the full validator,
  the string check and the unlock graph, failing on any error.
- Docs: `docs/PROGRESSION.md` and `docs/CONTENT.md`; `docs/BALANCING.md` §6 —
  the measured journey table per skill, the per-node value table, the per-bird
  payout table, and four measurements recorded as open questions for M9.

### Changed — M4

- `SaveManager` gained `profileAliasStep`, and the load order is now E21's:
  parse → migrate → bind → **aliases** → normalize. The renames used to run
  after the load completed, so `normalize()` had already reset a renamed
  selection to the default and written `ability:<oldId>` into `unlocked`; two of
  `aliases.json`'s five tables were dead and a third left a dangling id behind.
  The reconciliation report is returned with the load's other repairs.
- A launch now runs the unlock evaluator once over the loaded profile
  (`GameApplication.grantWhatIsAlreadyEarned`) and writes the result. Without
  it, every profile carried over from M3 opened M4 with what it had already
  earned still locked — and the shop offered to sell it back.
- `UpgradeManager.buy` refuses a node that would grant nothing: no
  `effectsPerLevel` and every grant already at its ceiling returns
  `ALREADY_OWNED` before the debit, and `isAvailable` is false. `hard_tier_1`
  could otherwise be bought for 400 coins after `tier:hard` had already been
  earned by playing, and did nothing at all.
- `UpgradeManager.reconcile` pays an alias refund only to a profile that owned
  the removed node. It used to credit every profile unconditionally, which on
  the first launch after any content removal handed every player free coins —
  and, because a refund counts in `statistics.coinsEarned`, free unlocks with
  them.
- `PlayerProfile.normalize` drops owned levels of upgrade nodes the build no
  longer ships (they inflate Cinder's synergy input) and clamps
  `abilityLevelCap` from above as well as from below, against a ceiling
  `ProfileSchema` now carries: E3's `baseCap + Σ ability_cap grants`, capped by
  the levels the thinnest ability ships. `UpgradeManager.abilityLevelCeiling` is
  the one place that number is computed.
- `UnlockEvaluator.priceOf` no longer descends into an `all_of`: a `purchase`
  nested there is one requirement among several, not a price, and selling for it
  would hand over an unlockable its siblings still gate. The validator refuses
  that shape too.
- `GameContent.playable(FEATURE, id)` is false while `featureMilestone(id)`
  names a milestone, and the shop labels `feature:modifiers` *Arrives in M6* and
  `feature:seeded_runs` *M9*. Both are buyable in M4 and read by nothing until
  then; they used to be sold as working switches (E19).
- The upgrade screen marks the seven nodes whose effects no system reads before
  M5 — `ABILITY_COOLDOWN_MULT`, `ABILITY_DURATION_MULT`, `SHIELD_CHARGES`,
  `REVIVES` and the `ABILITY_CAP` / `PASSIVE_SLOT` grants, 10 150 of the 21 400
  coins the tree costs — on the card and on the stat row. The bird screen names
  a bird's innate passives on the ability line with the same note, so Ironbeak's
  −20 % `COIN_MULT` is not presented as a straight upgrade while the shield that
  pays for it is M5 content.
- `CardGrid` measures a card's title and subtitle and ends them in an ellipsis
  instead of clipping mid-word; the upgrade card carries the short effect phrase
  and the detail panel the full one.
- The main menu draws its tagline outlined: M4's re-layout moved the title block
  up into the cloud band to fit the three new buttons, and a cloud crossed it.
- `BalancingSim` gained a `payout` column (the run's own `COIN_MULT`, `XP_MULT`
  and the tier's `rewardMult` applied). Without it `classic`, `guardian` and
  `mystic` print identical numbers and the economy birds cannot be balanced with
  the tool the milestone names as its check.
- `worlds.json` records that the `ambient.darkness` of `iron_forge` and `void`
  are placeholders for M7, not authored balance: §4's world table specifies
  darkness only for `storm_sky`.

### Deferred — M4

- `glide_1` (`MAX_FALL_SPEED −10 %`) cannot bind for five of the seven birds —
  the cap is unreachable inside the playfield — and it is a mandatory
  prerequisite of `updraft_1`. The node is what §4 of the plan specifies and the
  node table is binding, so it ships as written and the measurement is recorded
  in `docs/BALANCING.md` §6.3 for M9.
- `BotPilot` is not monotone in the stats the tree sells: its fixed aim offset
  and arc window re-phase against any change to gravity or flap velocity, so
  survival swings ±25 % with no trend. Making the pilot invariant would change
  the CI `--headless-run` hash, which M4 must not, so it is an M9 change (E25);
  `docs/BALANCING.md` §6.4.
- Jackdaw and Oracle do not pay for themselves against the free default bird,
  and the participation gate zeroes one novice run in five. Both are measured
  and recorded (`docs/BALANCING.md` §6.5, §6.6) for M9's retune.
- Upgrade materials as a second currency (README): D13 maps them to coins, and
  `RewardDef` carries an amount and no currency, so a second declared currency
  has no source. The graph check reports exactly that.

### Added — M5: abilities, shield/revive run systems, ability HUD, ability levels

- New pure package `ability`: `AbilityBehavior` (the hooks `onEquip`,
  `canActivate`, `onActivate`, `onTick`, `onLethalHit`, `onFlap`, `onCoinNear`,
  plus the `holdsBird` / `routesCoins` declarations), `AbilityInstance` (level,
  cooldown, duration, charges, activation count, all scaled by
  `ABILITY_COOLDOWN_MULT` / `ABILITY_DURATION_MULT` at activation time),
  `AbilityManager` (loadout selection, rule stripping, the `ABILITY` stat layer,
  fixed routing order active → passives), `AbilityContext`, `AbilityHost`,
  `ParamSpec` and `BehaviorRegistry`.
- The eight behaviours of D9: `shield`, `double_flap`, `dash`, `slow_time`,
  `emergency_recovery`, `coin_magnet`, `score_multiplier`, `invulnerability`.
  Each one's stat half is authored in `data/abilities.json` and only what the
  stat pipeline cannot express is code (E24): the dash's held line, the double
  flap's impulse, the revive's kick, the two run systems' configuration.
- `gameplay.run.ShieldSystem` and `gameplay.run.ReviveSystem`: stat-driven, so
  `SHIELD_CHARGES > 0` from the forge node `tempered_shield_1` absorbs a hit
  with no ability equipped, and `REVIVES > 0` from `second_chance_1` brings the
  bird back with no ability equipped. `NO_DEFENSIVE_ABILITIES` and `NO_REVIVE`
  zero the stats (D8) and strip the tagged abilities, innate ones included.
- The absorb chain in `Simulation.absorbLethalHit`: i-frames or the ghost state,
  then the behaviours, then a shield charge, then a revive; the column that
  caused the hit is marked dirty either way, so the gate is not clean (D26).
- `data/abilities.json` completed in place (E19): `effects`, `cooldownTicks`,
  `durationTicks` and per-level `params` beside the M4 unlock and cost blocks.
  Level 1 comes with the unlock; levels 2 and 3 are bought in the shop, capped
  by `min(profile.abilityLevelCap, levels.size())` (E3: base cap 2, one
  `ability_cap:1` grant in `forge/master_forge_1`).
- Content validation of the ability contract: the behaviour id must be
  implemented (`BehaviorRegistry`), every `params` key must be one the behaviour
  reads and inside its `ParamSpec` range and trend, a passive declares no
  cooldown or duration, an active declares at least one gate, an active with
  `effects` declares a duration, and level-up columns may not move the wrong way.
- Loadout: one active plus `BirdDef.passiveSlots + profile.passiveSlotBonus`
  passives plus the bird's innate passives, which take no slot, need no unlock
  and cannot be unequipped. `SelectionManager.selectActiveAbility` /
  `setPassiveAbilities` refuse anything locked or of the wrong kind and write
  the profile at once (D15).
- `BirdSelectionScreen` loadout row (active chip, passive chips, innate chip,
  the ability panel with the level, cost and per-level effect lines) and
  `ShopScreen` ability levels. `UpgradeManager.buyAbilityLevel` is the atomic
  purchase.
- Ability HUD (D17): cooldown ring, duration bar, charge pips, shield pips, the
  ready flash and the ability name, plus the `ABILITY` input action (`X`,
  `Shift`, right mouse button) and the refusal toast that says why a press did
  nothing (on cooldown, out of charges, stripped by the run's rules, or nothing
  equipped).
- `TickFact.AbilityActivated` / `AbilityReady` / `ShieldAbsorbed` / `Revived`
  reach the event bus and the audio manager; `RunStats` counts
  `abilitiesUsed`, `shieldAbsorbs` and `revives`.
- `BotPilot` spends the active when it predicts a lethal hit within ten ticks
  (D21), and `BalancingSim` gained `--ability <id|all|none>` and
  `--ability-level`, which is how the per-ability table in `docs/BALANCING.md`
  §7 is produced.

### Changed — M5

- `Simulation.tick` now runs ability timers before the flap, the activation
  after it, and `onTick` after the integration, so a burst that pins the bird
  undoes the gravity step of the tick it is holding and the collision test sees
  the y the renderer draws (E24). A run with an empty loadout takes none of
  those branches and folds no ability state, which is what keeps the published
  `--headless-run` hash comparable across milestones (D12): it is unchanged
  through M5 on JDK 17 and 21 — `hash=eaaa01685261a433 ticks=3000 gates=36
  points=36`, and `hash=b014de5e0ccf63dc ticks=600 gates=6 points=6` for the
  600-frame line CI compares.
- `coin_magnet` is repriced from 250 / 500 / 1000 to 120 / 240 / 480. Measured
  over 200 average-bot seeds on M5 content it is worth +0.13 coins per run,
  because E2 lays the coin trail on the safe band — the line the bird already
  flies. It becomes a real purchase with the M6 coin modifiers and the M7
  obstacle families; `docs/BALANCING.md` §7.3 records the measurement.
- `docs/PROGRESSION.md`, `docs/BALANCING.md` and `docs/CONTENT.md` no longer
  describe abilities, ability slots and the seven ability-facing upgrade nodes
  as *Arrives in M5*.

### Fixed — M5 (review pass)

- The level-1 dash was a net-negative ability: its i-frames ran out on the exact
  tick the held line released, 100 px deeper into the column it had entered.
  Measured over 200 average-bot seeds it passed 13.9 gates against the 79.6 of a
  bird with no ability at all. The burst now asks for "ghost until clear" when
  it releases — D9's shield rule — and measures 87.1. `AbilityBotRunTest` now
  compares every ability's mean gates against the ability-free baseline over a
  seed sweep, so a net-negative ability fails the build.
- A ghost is granted against one hazard: it latches onto the obstacle the bird
  is inside and is dropped the moment a different one hits it. It used to be a
  global "ignore every lethal overlap" flag, so an obstacle that arrived while
  the bird was still ghosting was free too.
- The double flap obeys the ceiling gate that refuses an ordinary flap at
  `y ≤ 32`. It used to set the velocity directly, which pushed the bird about
  95 px above the playfield — an instant death on a `LETHAL_CEILING` tier, from
  the button the game sells as a rescue. A refused press now costs no charge and
  reaches the HUD's existing refusal beat (the new `AbilityBehavior.canActivate`
  hook).
- A flap pressed during a dash is refused instead of being eaten: the hold
  overwrote the velocity a few lines later, but the flap had already restarted
  the wing animation, played the sound and counted in the statistics.
- Invulnerability now covers the ground: `absorbLethalHit` had a `!onGround`
  exception, so a run with 74 i-frames left still died on the ground line while
  the shipped description promised "nothing touches you". Every save that
  cancels a ground hit — i-frames, ghost, shield or revive — lifts the bird
  clear of the ground band, and nothing lifts it in mid-air.
- A revive in mid-air no longer teleports the bird up to the safe band: the lift
  is the ground rule's answer, not the revive's, and the shield's identical lift
  was already guarded that way. A mid-air revive gets its velocity kick alone.
- A regenerated shield charge reports `AbilityReady("shield")`, so the one
  defensive state change that matters most is announced like every other "ready"
  event: the HUD pip lights again and the cue plays. The return value of
  `ShieldSystem.onGatePassed` used to be dropped.
- `TickFact.AbilityActivated` and `TickFact.AbilityReady` now reach the event
  bus. `GameScreen.publishFacts` had no case for either, so the two sounds
  `AudioManager` already maps (`ToneSynth.ABILITY`) had no source and an
  activated ability was silent — the one thing that seam exists to prevent.
- `AbilityInstance.advance` reports "ready" as a transition of `isReady()`
  rather than as the cooldown edge, so a level whose duration outlasts its
  cooldown still announces itself.
- `BirdSelectionScreen.cycleSlot` keeps the passives the current bird cannot
  show. It rebuilt the selection from the visible chips only, so cycling a slot
  on a bird with fewer slots (or on Ironbeak, which grants the shield innately)
  silently dropped what the player had chosen.
- `onCoinNear` is opt-in (`AbilityBehavior.routesCoins`): the per-tick walk over
  every live coin ran in every run with any ability equipped, to call a hook no
  shipped behaviour implements.

### Deferred — M5

- `ShieldSystem` and `ReviveSystem` read `SHIELD_CHARGES` / `REVIVES` once, at
  run start. Nothing in M5 can change those stats mid-run, so the snapshot is
  exact today, but E12 reasons about modifiers that touch them: M6's
  `ModifierDirector` must re-resolve the stat when it applies a card, or keep
  those stats out of the pool. Both classes carry the note.
- `emergency_recovery` and `shield` measure the same in isolation (158.5 against
  158.8 mean gates, both absorbing 0.91 hits per run): each absorbs exactly one
  lethal hit, which is what D9 specifies for both. They are priced and unlocked
  as a pair rather than as a ladder — equipping both is what the two passive
  slots are for, and measures 205.4 — and the difference between them arrives
  with the shield's level-2 regeneration. Recorded in `docs/BALANCING.md` §7.4
  for M9's retune.
- A shield charge absorbs a ground death and bounces the bird 80 px clear of the
  ground line. That is D9's "absorbs one lethal hit" applied to the one hazard
  the classic feel treats as final; it costs a charge, emits `ShieldAbsorbed`
  and plays the shield cue, but it has no distinct animation. `docs/BALANCING.md`
  §7.5 records it for the M7 presentation pass.

### Added — M6: roguelite drafts, modifiers, synergies

- New pure package `modifier`: `Rarity`, `ModifierTag`, `ModifierCatalog` (the
  run-scoped snapshot of the schedule, the offer width, the weights, the cards
  the profile may be shown and the set bonuses), `ModifierPool` (weighted draw
  without replacement from the `offers` stream), `ModifierOffer` (the cards on
  the table as a value), `SynergyResolver` (E16's multiset match) and
  `DraftContext` (what the pool has to know about the run to decide E12's
  derived eligibility).
- `gameplay.run.ModifierDirector` and the `DraftWorld` seam `Simulation`
  implements: the breather at each scheduled gate, the freeze when the air ahead
  of the bird is clear, the choice, the 45-tick resume hold with its 3-2-1 and
  30 i-frames, the `MODIFIERS` and `MOD_SYNERGY` stat layers, the taken multiset
  and the active set bonuses. `RunPhase` gains `BREATHER`,
  `CHOOSING_MODIFIER` and `RESUME_HOLD` (D11); `RunInput.choice` carries the
  answer, `RunInput.SKIP` takes nothing.
- `data/modifiers.json`: the schedule `[10, 25, 45, 70, 100, 140]`, three cards
  an offer, the rarity weights `60/28/10/2`, 17 modifiers over seven tags and
  four synergies (`coin_engine`, `bulwark`, `needle_threader`, `daredevil`), with
  `ModifierDef` / `SynergyDef` / `ModifiersDef` / `StreakBonusDef` and their
  `en.json` / `pt_BR.json` names and descriptions.
- `ui.screens.ModifierChoiceOverlay` (D17): up to three cards on the frozen
  playfield with name, rarity, tags, the effects in words and in numbers, the
  stack a repeat would be and the set bonus taking it would complete; keyboard
  and pointer focus, Skip, and the resume countdown drawn as a digit inside a
  draining ring. The overlay drives the frozen ticks — the simulation is stopped
  by the director, not by the UI — and nothing pressed over the cards reaches the
  run underneath.
- HUD build strip (D27): one chip per taken modifier with its stack count, the
  active set bonuses under them, and the streak line's "what one more step pays"
  readout that M3 deferred (E32.a). A `SynergyActivated` fact raises a toast
  (`toast.synergy`) beside the chip it adds.
- `RunSummaryScreen` build section: every modifier with its stacks, every synergy,
  the modifier half of the streak reward as its own signed row, and — for a run
  that never had the feature — a line naming what the shop sells.
  `StatisticsScreen` gains the lifetime `modifiersTaken` / `synergiesActivated`
  totals.
- Economy: `RunStats.modifiersTaken`, `synergiesActivated` and
  `modifierStreakCoins` feed `RunRewardCalculator` (the `Σ modifier.streakBonus`
  term of E32.a) and the progression pass's two map counters.
- `feature:modifiers` is live: `RunLoadout.allowOffers` opens drafts for a profile
  that owns it (run 7, or 150 coins in the shop) and `RunLoadout.availableModifiers`
  keeps the three earned legendaries out of the pool until they are unlocked.
  `GameContent.FEATURE_MILESTONES` no longer names a milestone for it.
- Content validation of the roguelite contract: ascending schedule, positive
  offer width, non-negative weights, a rarity that has a weight, `maxStacks ≥ 1`,
  at least one tag, resolvable and non-reflexive `excludes`, a card that does
  something, a streak bonus that declares `NO_COINS`, no mid-run `SPEED_RAMP` /
  `ALL_OBSTACLES_MOVE` flag, no effect on a stat the spawn decision reads
  (`MOVING_CHANCE`, E32.d), synergies that need two tags and are reachable by
  some legal build (a warning), and `challenges.json.forcedModifiers` resolved
  against `modifiers.json` — unknown id, more copies than `maxStacks`, a
  mutually-excluding pair and a card the challenge's own flags forbid (E19).
- Tests: `ModifierPoolTest`, `SynergyResolverTest`, `ModifierDirectorTest` (on
  the `FixedSpawnTable` corridor, E17), `ModifierChoiceOverlayTest`,
  `DeterminismTest.theSpawnDecisionSequenceSurvivesADifferentChoice` (E32.d),
  the M6 cases of `GameScreenTest`, `RunSummaryScreenTest`, `HudRendererTest`,
  `StatisticsScreenTest`, `ContentValidatorTest`, `ContentIntegrityTest`,
  `NewPlayerJourneyTest` (`feature:modifiers` by run 7, E17) and a Robot-driven
  draft in `SmokeWindowTest`.
- `BalancingSim --drafts` reports how far runs get into the schedule, the rarity
  mix taken and on the table and how often a build activates a set bonus;
  `--modifier <id|all|build>` and `--modifier-stacks N` force cards and print the
  per-card payout delta against the same seeds without them, which is what
  produced `docs/BALANCING.md` §8.2.

### Changed — M6

- `ShieldSystem.raiseTo` / `ReviveSystem.raiseTo`: both systems snapshot their
  stat at run start (the limit M5 wrote down), so the director re-resolves
  `SHIELD_CHARGES` and `REVIVES` after every card and synergy — a drafted shield
  is a shield the bird actually has.
- `Esc`, a lost focus and an iconify pause the run in `BREATHER` as well as in
  `FLYING`: the breather ticks the world while it waits for clear air, and D2
  makes no exception for it. Measured over 300 average-preset drafting runs:
  962 breathers, 241 ticks at the median, 4.03 s on the mean and 9.9 % of every
  live tick those runs spent. The frozen draft phases pause nothing, because
  nothing moves.
- The draft is labelled with the gate it was scheduled for rather than the gate
  the corridor happened to clear at: the spawner has two or three obstacles
  queued when the breather starts, so the freeze lands about three gates later
  and the overlay used to read "gate 13" for the entry §6 calls gate 10.
- The coin breakdown of the run summary splits E32.a's single streak term in
  two — the shipped `economy.rewards.streak.coins` on the streak row, the drafted
  cards' share on the bonus row — so the column adds up to the Base row it is a
  breakdown of. The wallet was always right; only the printed arithmetic
  double-counted.
- `RunFactory` builds the run's catalogue from the profile's modifiers **and**
  the run's `forcedModifiers`: forced cards are a property of the run source
  (a challenge, M9's daily), not of the profile, and only the offer pool depends
  on ownership.
- Balance, from the per-card sweep now recorded in `docs/BALANCING.md` §8.2
  (200 seeds per cell, four skill presets, the default 20 000-tick budget):
  `temp_shield` moves from RARE ×2 to EPIC ×1: as a RARE it beat every EPIC and
  LEGENDARY in the game (+111.6 % payout over 120 average-preset seeds against
  `second_wind`'s +108.1 %, for 2.8 times the draw weight) and it could be taken
  twice. The SPEED axis loses most of its scroll penalty (`tailwind` ×1.08 →
  ×1.02, `gold_rush` ×1.15 → ×1.05, `stormrider` ×1.25 → ×1.05) because
  `SCROLL_SPEED` is a far steeper difficulty knob than any score payoff can pay
  for: at ×1.25 `stormrider` cost a perfect pilot 93 % of its payout and 95 % of
  its gates over a 120 000-tick budget, and `gold_rush` cost it 67 %.
  `magnet_burst` gains `COIN_MULT +15 %` beside its radius, which on its own
  measured +0.0 % at every skill preset — identical ticks, gates and coins to a
  run without it — because E2 lays the coin trail on the line the bird already
  flies. `phoenix` grants two revives instead of one, so its 30 %
  coin tax buys something `second_wind` cannot have — it was otherwise the same
  card as a free EPIC with a tax on top.

### Deferred — M6

- `glass_wings` (EPIC, `SCORE ×1.5`, `HITBOX +0.15`) measures net-negative at
  every skill preset (−5.7 % novice, −6.5 % average, −16.9 % expert, −13.4 %
  perfect): the hitbox term costs more than the score term pays. It is the plan's
  own RISK card and no reviewer flagged it, so it ships as authored and
  `docs/BALANCING.md` §8.4 records the numbers for M9's balance pass.
- `light_frame` and `wide_gaps` are worth more to a perfect pilot than to an
  average one (+3.3 % / +3.7 % against −14.1 % / +1.0 %). That is the
  non-monotone bot §6.4 already documents rather than a property of the cards.
- The 30 i-frames of a draft resume also cancel a ground hit, so a player who
  dives the moment the run resumes gets a free altitude reset — six times a run
  rather than once per spent charge. The behaviour is M5's (`absorbLethalHit`
  treats the ground like any other lethal hit while invulnerable) and is
  deliberate; `docs/BALANCING.md` §8.4 records it.
- Bosses are M8, so E7's two guards (`DraftWorld.bossPending()` /
  `bossActive()`) answer `false` and are exercised against the seam rather than
  against a boss.

---

### Added — M7: worlds, obstacle families, patterns, ambience, rule cycles

- Five playable worlds. `worlds.json` is complete: every world's `patterns`
  list, the Void's `ruleCycles` (every 5 gates, 90-tick telegraph, four
  `{flags, effects}` options — `ALL_OBSTACLES_MOVE`, `GAP_SIZE ×0.85`,
  `GRAVITY ×1.3`, `LETHAL_CEILING` — never the same one twice in a row) and the
  authored ambience (Wind Valley `windX −20`, Storm Sky darkness 0.5 with a
  cosmetic flash every 3 gates, Iron Forge 0.15, the Void 0.35).
  `GameContent.playable(WORLD, id)` is true for every world; `SelectionManager`
  selects any owned world and the shop no longer labels worlds "Arrives in M7".
- `patterns.json` (`PatternDef`, `PatternStepDef`, `PatternsDef`,
  `GameContent.patterns()`): 21 authored set pieces — eight world patterns
  (`wv_updraft_run`, `wv_crosswind`, `forge_gear_corridor`, `forge_piston_row`,
  `storm_bolt_lane`, `storm_squall`, `void_mixer`, `void_gauntlet`), eleven
  boss phases (`gf/wv/forge/storm_boss_p1/p2`, `void_boss_p1/p2/p3`) and the
  two corridors of `boss_corridor_1` (`corridor_1`, `corridor_boss_p1`), whose
  `forcedPattern` and `boss` blocks are filled in `challenges.json`. The file
  is optional like `worlds.json`, so the frozen golden fixture still loads.
- `PatternStreamer` (D7): set pieces ride the spawn cursor. A step lands at
  `last.x + dx`, its geometry goes through `SpawnTable.decisionFor` — the
  `obstacle` stream is read only for a `"random"` gate centre — and folds into
  the E32.d decision hash. Selection draws from the `patterns` stream with
  `P(start) = Σ eligible weights / (Σ weights + 100)` among the world's
  patterns with `minGate ≤ gatesPassed`, then a weighted pick; one plain spawn
  separates two chunks; Green Fields (no patterns) never touches the stream.
  `RunSetup.forcedPattern` loops one pattern instead of the table (the M8
  challenge seam and `BalancingSim --pattern`). `scoring: false` steps and
  `scoringSteps: false` patterns spawn non-scoring columns
  (`Obstacle.markNonScoring`) that award no gate and get no coin trail.
- `WorldEffects` (D8, E8): the ambient wind is the `WindZone` mechanism made
  permanent (`windX` a scroll change, `windY` an acceleration, sampled before
  the zones every tick); `darkness` is a value the renderer reads; the cosmetic
  flash is a `TickFact.AmbientFlash` with no hitbox; the rule cycles draw the
  next option from the `cycles` stream, announce it with `TickFact.RuleShift`
  (flags, effects, telegraph ticks) and land it that many flying ticks later —
  never inside a draft (breather, choice, hold): a landing due then waits for
  the next flying tick. The run's rules are now three sources kept apart
  (base, drafted, cycle) so a cycle's flags replace the previous option's;
  `ShieldSystem.syncTo` / `ReviveSystem.syncTo` follow a zeroing flag in both
  directions. World effects and the streamer fold into `stateHash` only when
  the world has them, so the published `--headless-run` hash is unchanged.
- `WorldSpec` carries `patterns`, `ambient` (`AmbientSpec`) and `ruleCycles`
  (`RuleCycleSpec`); `RunFactory.setup` reads the whole world from
  `worlds.json` (`GameContent.worldSpec`, cached) and keeps the M1 two-case
  fallback only for content sets without a world file. `StrictBinder` binds
  `Object` components generically (the kind-dependent step `params`).
- `ContentValidator` (E19: the pattern checks are live): every reference
  (`world.patterns`, `boss.patterns`, `forcedPattern`) resolves, a listed
  pattern belongs to its world and has weight > 0, a boss phase or forced
  pattern has weight 0 and a boss phase spans ≥ 480 px, every step's params
  pass the kind's `ObstacleParams` contract (pointer per key), `dx ≥ 100`,
  `gapSize × (tightest tier gap multiplier) × 0.9 ≥ 54.5`, ambient wind in the
  zone ranges, rule cycles with ≥ 2 non-empty options that never touch
  `MOVING_CHANCE`, a world with a positive spawn weight, and E14 (a forced
  pattern under a boss must score). Unreferenced weight-0 patterns warn.
  Fixtures `content_bad/{bad_param,unknown_pattern,infeasible_pattern}.json`.
- `BalancingSim --world all`, `--tier all` and `--pattern <id|all>` (a pattern
  in isolation, looped); rows and the CSV carry world, tier and pattern, and
  obstacle deaths are reported by kind (`RunStats.deathKind`,
  `TickFact.Crashed.kind`).
- `BotPilot`: the corridor is bounded by the nearest column not yet cleared
  (it used to be the farthest one inside the flap window, which dropped the
  gate the bird was still inside as soon as a pattern step 130 px behind it
  entered the window); the flap arc is computed under the wind the bird is in
  (D21's wind-adjusted trajectory — a −600 px/s² updraft turns a 42 px rise
  into 68); the gear oracle keeps its clearance from the chord the circle cuts
  through the bird's x range on each crossing tick instead of the whole
  diameter. In Green Fields none of the three changes a decision, so every
  M1–M6 number stands.
- Tests: `PatternStreamerTest` (placement, params, `"random"` from the obstacle
  stream, `minGate`, the weighted start, looping, scoring flags, the decision
  hash, `test_flat_corridor.json` against `FixedSpawnTable` tick for tick),
  `WorldEffectsTest` (wind, darkness, flash cadence, cycle cadence and
  telegraph, never twice in a row, layer replacement, the shield zeroed and
  restored, deferral during a draft, the hash), `ContentWiringTest` (every
  world from `worlds.json`; Green Fields equals the M6 setup field by field),
  `ContentValidatorTest.Patterns` and the three bad fixtures,
  `DeterminismTest` on every world and E32.d on Storm Sky,
  `ContentFeasibilityTest` (@sim: the expert reaches `boss.atGate` on every
  world × tier and survives every pattern in isolation, 50 seeds each).

### Added — M7 presentation: world art, banner, picker, sound sets, asset check

- `render/ObstacleRendererRegistry` (D18): one renderer per `ObstacleKind`,
  dispatched by kind; `ObstacleRenderer` keeps the gate art. `GearRenderer`
  draws a toothed polygon precomputed once in unit space, scaled to the radius
  and rotated by the obstacle's angle (turns × 2π on the render side only),
  with a faint rail track and end stops for a gear on a rail; `PistonRenderer`
  draws base plate, rod and head, a telegraph glow over the whole reach that
  pulses on a triangle wave and brightens as the telegraph runs out, plus a
  motion smear on extend; `WindZoneRenderer` fills the zone with translucent
  stripes drifting in the wind's direction (render-side animation on the
  registry's clock, stripe table from its own seeded `Random`), brighter while
  the bird is inside; `LightningRenderer` shows, during `WARNING`, a tinted
  band over exactly the bolt's side and extent with a hard bar at its far end
  and chevrons on the anchored edge, brightening with the warning progress
  (the fairness cue), and during `STRIKE` a jittered polyline with a local
  glow, dimmer under `reduceFlashing`. Every kind is interpolated with the
  frame alpha; scratch shapes and colour ramps, nothing allocated per frame.
- Five parallax styles keyed by `worlds.json.style` (`WorldStyle`): hills
  (Green Fields, the M1 code untouched), canyon (layered mesas under a dust
  haze, cracked ground), factory (skyline with chimneys, girder lattice,
  rising embers, riveted plates), storm (two cloud banks, rain streaks, a
  distant flicker that is a tint under `reduceFlashing`, wet rock) and void
  (a slow twinkling star field, floating shards that bob, no grass).
  `WorldPalette.from(WorldPaletteDef)` builds the palette of any world;
  `GameRenderer.setWorld` takes palette and style, `GameScreen` reads them
  from the run's world and sets the letterbox tone with them.
- `DarknessOverlay`: `ambient.darkness` drawn as one cached ARGB mask (three
  playfields tall, blitted against the bird's y) with a fully clear radius of
  96 px around the bird and a smooth fade to the world's darkness at 250 px,
  capped at 0.78 so the next hazard stays readable — asserted at Storm Sky's
  0.5 in `ProceduralRenderTest`.
- The cosmetic sky flash (E8): `TickFact.AmbientFlash` lights
  `GameRenderer.ambientFlash()` (a 9-tick whole-sky brightening, a 12 % tint
  with `reduceFlashing` on, read from `ParticleSystem.defaultReduceFlashing()`
  like the particles) and raises `GameEvent.AmbientFlash`, which the audio
  manager plays as thunder.
- `ui/screens/RuleShiftBanner` (D17): a non-blocking banner — not a screen on
  the stack, so the run keeps flying and every key still reaches it — opened
  by `GameScreen` on `TickFact.RuleShift`, naming the option in words (each
  flag through `rule.<flag>`, each effect through the M6 effect text) with a
  countdown in seconds over `telegraphTicks`, holding at "now" while the
  simulation defers the landing during a draft, then flashing "in effect"
  for 72 ticks once `WorldEffects` lands it. `GameScreen.publishFacts` maps
  `LightningWarning`, `PistonTelegraph` and `AmbientFlash` to events and
  raises `GameEvent.WindGust` when the bird enters a wind zone. The HUD names
  the world above the READY hint and for 120 ticks after the first flap.
- The world picker (D17): `BirdSelectionScreen` gains a `WorldRow` between
  the actions and the tier — the five worlds in `worlds.json` order with a
  palette swatch, the hazards a world spawns (the kinds with a positive spawn
  weight, `obstacle.<kind>.name`) or, for a locked one, the cheapest way in
  (the M4 unlock text) — selecting through `SelectionManager.selectWorld`
  and refusing a locked world with a toast. The main menu shows the selected
  world (`menu.world`); the shop sells worlds without the "Arrives in M7"
  caveat. `--world <id>` pins the world for the launch
  (`ContentRunFactory.withWorld`): an owned world is selected as the picker
  would, a locked one is played for this launch only with a log line and the
  profile untouched, an unknown id is reported and ignored; with
  `--headless-run` the hash line is computed in that world.
- Sound sets (E31.g): `ToneSynth.render(id, SfxSet)` gives every id one
  deterministic flavour per set — FIELDS the canonical bytes, CANYON wider
  and airier, FACTORY harder and percussive, STORM sharper and noisier, VOID
  hollow and detuned — and four new ids: `lightning_warning`, `thunder`,
  `piston_telegraph`, `wind`. `SoundBank` keys a flavoured cue as `id@set`
  (with `sfx/<set>/<id>` overrides tried first) and `AudioManager` picks the
  set from the run's world on `RunStarted` (`setSfxSetResolver`, installed by
  the application from `worlds.json.sfxSet`); menu cues stay canonical.
- `tools/AssetValidator` + `./gradlew assetValidator` +
  `tools/asset-validator/{README.md,run.sh,run.ps1}`: every manifest entry
  must resolve on the classpath under `/assets/`, carry a licence and start
  with the magic number of its kind (PNG for sprites and sheets, RIFF/WAVE
  for audio, TrueType/OpenType for fonts); parse errors count; status 1 on
  any problem, the shipped empty manifest passes.
- Strings (both languages): `menu.world`, `birds.world*`, `rule.<flag>` for
  every flag, `rule_shift.*`, `obstacle.<kind>.name`.
- Tests: `ProceduralRenderTest` renders every world × every kind × both bird
  poses (frames to `build/render/`, worlds distinct by colour histogram), the
  darkness veil keeping the bird and the next gate visible, the lightning
  warning marker on the correct side and inside its column, the banner and
  the picker in both languages; `RuleShiftBannerTest` (the fact, the
  countdown, the deferred landing, both languages, no input consumed, and the
  Void's own shift through `GameScreen`); `BirdSelectionScreenTest` world
  section (order, hazards, locks, persistence, language switch);
  `ToneSynthTest` every id × set audible and deterministic, sets pairwise
  distinct; `SoundBankTest`/`AudioManagerTest` set keys; `SmokeWindowTest`
  steps the picker with real arrow keys and flies Iron Forge, Storm Sky and
  the Void for 600+ frames each on the bot's decisions through the queue,
  capturing a gear/piston, a lightning warning and the rule-shift banner.

### Fixed — M7 (review pass)

- **A decision is what the streams drew (E32.d).** `ALL_OBSTACLES_MOVE` used to
  be folded into every spawn decision (a gate's `moving`, a gear's rail, a
  piston's forced telegraph), and in the Void the flag lands through a rule
  cycle whose tick depends on the draft and the cards, so two runs of one seed
  disagreed on the spawn it applied from (2/40 seeds, perfect and expert).
  `SpawnTable.roll`/`rollFirst`/`decisionFor` no longer take the flag;
  `SpawnTable.materialize(decision, x, gap, forceMoving)` applies it per kind
  (D7) where `GAP_SIZE` already was, and a gate rolled static keeps the static
  layout mix. `DeterminismTest` now plays the Void with offers on, four draft
  answers and two presets over twelve seeds, asserts the decision prefixes
  agree, that the flag landed and that it landed on different spawns.
- **A spawn-table bolt is reachable from the column before it.** Its side is
  the one whose unlit band is nearer the previous decision's reference band
  (`SpawnDecision.referenceBandY`: the gap centre at the default gap, a gear's
  larger side, a piston's free side, a bolt's unlit side — never the resolved
  gap or the oscillator, so the decision stays seed-only) and its lit fraction
  is shortened until the travel fits `LIGHTNING_MAX_TRAVEL_PX` (80 px; the
  scroll between a gate clearing the bird and the strike is 115 px). The same
  draws are consumed either way. Table bolts warn from 75 ticks out
  (`LightningStrike.TABLE_WARNING_TICKS`; patterns keep their authored 45) and
  `LightningRenderer` shows an idle column from spawn — a faint outline of the
  span and an anchor plate on its edge — so a bolt is readable like any other
  hazard before the bird commits to the gap in front of it. Bolt deaths: Storm
  Sky perfect 4 % → 3 % (normal), expert 2 % / 0 % / 6 % (normal / hard /
  nightmare), the Void perfect 5 % → 0 %; a gate+bolt table at ×1.5 scroll over
  the standard ramp is flown 20/20 seeds with no bolt death (`BotOracleTest`).
- **Wide columns keep their clear air.** The cursor measures the interval from
  where a pipe body's right edge would be: `x = last.x + max(0, last.width −
  40) + GATE_INTERVAL`. A 40 px gate is upstream's rule to the pixel (the
  published hash is unchanged); a 112 px gear or a 200 px wind zone pushes the
  next column out by its extra width, so two big gears keep 120 px between
  them and a zone never covers the approach to the gate after it (Wind Valley
  normal, perfect: 59 % → 66 % of runs reach the budget). Authored pattern
  steps keep their `dx` as written.
- **The breather always finds its window.** `Simulation.deferSpawn` passes an
  absolute clearance (`WIDTH − hitboxLeft + 20`) with D11's 1.5 intervals, and
  `ObstacleSpawner` places the deferred column at least that far behind the
  last column's right edge whatever its width or a pattern step's `dx`. Iron
  Forge and the Void had breathers of 600–2565 ticks looping on
  `BREATHER_RETRY_TICKS`; `BreatherClearanceTest` now holds every breather in
  every world (12 seeds × take/skip) under 300 ticks. Green Fields and the 128
  px corridor of `ModifierDirectorTest` get the same x they got before.
- **A gear is cleared on the side that leads on.** `Oracles.gearCorridors`
  returns both corridors from the chord footprint; `BotPilot` aims at the gear
  band nearer the band of the column after it (`Oracles.bandOf`) and, with the
  gear as its current column, takes the side that holds a flap arc, is
  reachable before the crossing and is nearest the aim — the larger side only
  as a tie-break. A band the box fits in but a flap does not (under a gear near
  the ground) is never chosen. Gear-only table: 14/20 → 20/20 seeds; Iron
  Forge perfect gates p50 82 → 94; `Gear.safeBandY` (the coin trail) is
  unchanged. Green Fields decisions are untouched (the bot's Green Fields
  behaviour is pinned by the published hash and the golden run).
- **A pattern gate's `gapSize` is a base value.** `materialize` scales it by
  the run's gap multiplier (`gap / 128`: tier, curve, cycle, cards) and keeps
  the gap centred where it was authored; the decision folds the unscaled top.
  The validator's `gapSize × tightest tier multiplier × 0.9 ≥ 54.5` now
  describes what the nightmare tier plays (`PatternStreamerTest`).
- **One announcement is one landing.** `WorldEffects.onGatePassed` draws no new
  option while one is pending, so a telegraph is never replaced mid-way (the
  Void seed 21 timeline announced five options for one landing);
  `WorldEffects.announcements()` counts them.
- The rule-shift banner moved to the ground strip (`y ≥ 598`, x 14–306, alpha
  `0x70`): it hid the bird and the column 40 px ahead of it for the whole
  telegraph; a long line shrinks to 8 pt or wraps onto two rows in the title's
  place. The world name leaves the playfield after the first flap and sits in
  the top strip under the streak lines for its 120 ticks.
- `Piston` raises its telegraph signal only while the column is on the
  playfield, so the cue never plays for a glow the player cannot see.
- `stateHash` folds `invulnerableTicks`/ghost when they are non-zero in a run
  without run systems (a Void option that zeroes the shield mid-run) and a
  pending breather deferral; `HashFoldTest` moves every M7 per-tick field
  through reflection and asserts the hash follows;
  `PatternStreamerTest.theStreamerStateIsPartOfTheStateHash` compares two
  runs with identical obstacles that differ in the streamer alone.
- `ContentValidator`: a gate right after a bolt has an authored `gapCenter` on
  the bolt's unlit side (`≤ 0.5` after a `BOTTOM` bolt, `≥ 0.5` after a `TOP`
  one, never `"random"`), and a bolt's safe band is no further from the
  previous lethal column's band than the scroll between them (`dx − width −
  5` px); fixture `content_bad/bolt_then_gate.json` pins three pointers.
  `storm_squall` step 3 (`random` → 0.4) and `wv_crosswind` step 3 (`random` →
  0.45) were re-authored, and the `BOTTOM` bolts of `storm_bolt_lane` (0.55 →
  0.45) and `storm_boss_p1` (0.6 → 0.4) shortened: the first was 174 px of
  climb in 160 px of `dx` and killed 14 of 50 expert runs on Storm Sky
  nightmare (measured against five other authorings, BALANCING.md §10.3).
- `BalancingSim --pattern` plays a pattern in its own world unless `--world`
  was given, which is what `ContentFeasibilityTest` measures.
- `BackgroundRenderer` caches the factory skyline and storm bank colours per
  palette; `WindZoneRenderer` cuts its stripes by geometry instead of a clip
  (both were per-frame allocations). `SmokeWindowTest` re-sends a swallowed
  Robot event up to five times instead of three.
- `worlds.json`: Iron Forge and the Void ship `darkness 0` — the plan's world
  table darkens Storm Sky alone (0.5); the 0.15/0.35 were the M4 stub's
  placeholders.

### Changed — M7

- `TickFact.RuleShift` carries the option (`flags`, `effects`,
  `telegraphTicks`); `GameScreen` publishes the announced flags rather than the
  rules in force. `TickFact.Crashed` carries the obstacle kind.
- `ObstacleSpawner.update(ctx, gatesPassed)` (the one-argument form stays);
  `SpawnTable.decisionFor` names its stream parameter `geometry`.
- `ContentLoader.FILES` is the M7 set (`patterns.json` after `worlds.json`).
- `BotOracleTest.thePerfectBotSurvivesEachKindInIsolation` measures 20 seeds
  per kind and requires 20/20 for every kind, gears included (7/20 with the M6
  bot, 14/20 before the review pass).
- `SpawnTable.roll(spawn, obstacle, movingChance[, previousBandY])`,
  `rollFirst(obstacle)`, `decisionFor(params, geometry)` and
  `materialize(decision, x, gap, forceMoving)`; `FixedSpawnTable` overrides
  the new shapes. `ObstacleSpawner.deferNextSpawn(intervals, clearancePx)`.
- `DeterminismTest.anAbilityEquippedRunIsReproducible` plays seed 40 (seed 31
  no longer needs its shield once the bot keeps the gate's corridor during a
  dash).
- `docs/BALANCING.md` §10 records the world × tier × preset table.

### Deferred — M7

- D21's crossing-tick prediction for *moving gates* (the gate oracle uses the
  gate where it is, shrunk by the travel during a flap rise) stays as it is:
  any change to the bot's Green Fields decisions moves the published
  `--headless-run` hash and the golden run, which D12 pins across milestones.
  Green Fields nightmare clears the 30 % bar without it (32 %, up from 22 %,
  once the tier's flag stopped bending the layout roll) and the
  `ContentFeasibilityTest` floor is gone; the oracle is re-measured when the
  baseline is re-recorded on purpose (M9 tier balance).

### Added — M8 bosses: boss encounters, challenge runs, objectives and their rewards

- `gameplay/run/BossEncounter` (D11, E7, E26): at `boss.atGate` the run enters
  `BOSS_WARNING` for `warningTicks` with spawning suppressed and a `BossWarning`
  fact carrying the boss id and the world id; then `BOSS`, where the boss phases
  stream through the spawner's `PatternStreamer` in order and looped until
  `surviveTicks` of flying time (scoring steps keep scoring, coins, streaks and
  the difficulty curve keep going; `phasesReached` is the furthest phase reached);
  then `BossCleared`: the remaining boss columns scroll out and ordinary spawning
  resumes 1.5 gate intervals out. Dying during the encounter clears nothing; a
  clear is kept at run end even if the bird crashes later. Only a world boss
  writes `RunStats.bossesCleared`; a challenge boss (`worldId == null`) only sets
  the flag a `BOSS_CLEARED` objective reads.
- `gameplay/run/ObjectiveEvaluator`: `SURVIVE_GATES`, `SURVIVE_TICKS`,
  `COLLECT_COINS`, `REACH_POINTS` and `BOSS_CLEARED` (D11's full five-type set)
  judged every tick from the run's tallies, latched once with one
  `ObjectiveMet` fact; the run continues.
- `gameplay/spec/{BossSpec, ChallengeSpec}`, `RunSetup.boss` / `.challenge` /
  `startingAtBoss()`, `CurveSpec.shiftedBy`, `PatternStreamer` boss mode
  (`startBoss` / `endBoss`, phases over forced and world patterns, fold into the
  hash), `ObstacleSpawner` resume floor (the first column after a suppression is
  placed at the right edge, never inside the playfield).
- `RunConfig.bossEnabled`: on by default, pinned off by `RunConfig.classic` so
  the published `--headless-run` hash (`eaaa01685261a433` for 3000 ticks, seed
  42) and the golden fixture stay where M1 recorded them; a profile-less
  `ContentRunFactory` plays the same pinned configuration.
- Challenge runs end to end: `RunFactory.challengeConfig` stamps the mode, world,
  tier, flags, forced modifiers and offer switch on a base configuration and
  `RunFactory.setup` resolves the curve override, the `CHALLENGE` layer effects,
  the objective, the forced pattern and the challenge's own boss (a challenge
  without a `boss` block has no boss, whatever its world says);
  `RunLoadout.challengeConfigFor` and `ui/screens/ChallengeRunSource`
  (`ContentRunFactory.forChallenge`) keep the profile's bird, palette and
  loadout. Nothing checks that the challenge's world is unlocked (E6).
  `GameContent.playable(CHALLENGE)` is true; the shop no longer labels
  challenges "Arrives in M8".
- Rewards (E11, E32.a): `RewardContext.firstBossClearCoins` /
  `firstChallengeCoins`, resolved by `ProgressionManager.rewardContext` from
  `ProgressionRules.FirstClearRewards` (`ProgressionRules.fromContent`), feed the
  boss and challenge terms of `RunRewardCalculator`; the unlock step grants
  `boss.reward.unlocks` and `challenge.rewards.unlocks` once, before the
  evaluator's pass. A repeat clear pays `bossBonus` / `challengeBonus` alone.
- `ContentValidator`: a `BOSS_CLEARED` objective needs a boss block; a boss past
  a `SURVIVE_GATES` objective is rejected (E14); `warningTicks ≥ 60` and
  `surviveTicks ≥ 300` on every boss block.
- `speed_run_1` ships `SURVIVE_GATES 30`, one step below the plan table's 40: at
  40 the expert bot met the objective in 14/50 seeds (28 %; 24.5 % over 200),
  under the milestone's ≥ 30 % bar, because `SPEED_RAMP` roughly doubles the
  scroll by gate 30 on top of Wind Valley's wind zones. A deliberate plan
  deviation, measured and recorded in `docs/BALANCING.md` §11.1, pending sign-off
  as an errata item against §4 (the plan file itself is not edited).
- `BalancingSim --challenge <id|all>` (objective and boss clear rates, phases,
  deaths by kind) and `--boss <worldId|all>` (the encounter on its own, started at
  the boss under the difficulty of `atGate`).
- `GameScreen` maps `BossWarning` / `BossStarted` / `BossCleared` /
  `ObjectiveMet` to their events and treats the two boss phases as live;
  `BotPilot` flies through them.
- Tests: `BossEncounterTest`, `ObjectiveEvaluatorTest`, `BossOfferInterplayTest`
  (E7 against the real encounter), `UnlockChainTest` (Green Fields boss →
  `world:wind_valley`; `no_shield_1` → `cosmetic:classic:ember`), M8 rows of
  `HashFoldTest`, `RunLifecycleTest`, `DeterminismTest`, `PatternStreamerTest`,
  `ObstacleSpawnerTest`, `RunRewardCalculatorTest`, `ProgressionManagerTest`,
  `ContentValidatorTest`; `ContentFeasibilityTest` (@sim) covers every challenge
  objective and every world boss at ≥ 30 % expert success.

### Added — M8 music, font and accessibility: world loops, the bundled OFL font, live accessibility modes

- `audio/MusicSequencer` (D19): each world's `worlds.json` music block
  (`tempo`, `scale` from `major_pent`/`minor_pent`/`dorian`/`phrygian`/
  `whole_tone`, a per-world `seed`, `layers` among `bass`/`lead`/`arp`/`pad`/
  `drums`, all validated by `ContentValidator.checkMusic`) renders a
  deterministic 8-bar chiptune loop — square/triangle/noise voices, linear
  envelopes, note tails wrapped across the loop point, peak normalised. Two
  renders of one block are byte-identical; each layer draws from its own seeded
  stream. The boss variant is the same block at ×1.15 tempo (capped at 170 BPM)
  — the switch happens on `BossStarted`/`BossCleared` through the existing music
  ramp. Renders measured at 11–29 ms per loop on the development machine
  (budget 150 ms), synchronously at boot for the menu loop and at run start for
  the world loop, never on a new thread.
- `Voice` gains looping (the cursor wraps, so a rendered loop plays seamlessly)
  and a linear gain ramp with a target (fade-ins, fade-outs, the crossfade, the
  pause duck); `SoftwareMixer` exposes `playLooping`/`stopLooping`/
  `registerLoop` over the same command queue and `MAX_VOICES`; one looping voice
  per id, retargeted rather than stacked. `AudioBackend` carries the loop
  methods as defaults, so `NullAudio` stays silent for free.
- `AudioManager` consumes `settings.musicVolume` at last: every loop plays at
  its base gain × the music volume × the master fader; mute stops the loop and
  unmuting re-issues it; the menu plays the Green Fields loop at −6 dB
  (`MusicSequencer.MENU_GAIN`), a run its world's loop at the run gain, the
  pause overlay ducks it to 35 %.
- Bundled OFL font (D18, D25): Nunito (variable, 277 KB) ships under
  `assets/fonts/` with its `OFL.txt`, declared in `assets/manifest.json` as the
  `font/ui` FONT entry; `AssetManager.font` decodes it with
  `Font.createFont(TRUETYPE_FONT, …)` and `BootSequence`'s first step installs
  it through `Fonts.install` — lazily, never a static initialiser (E10); a
  missing entry or a bad file leaves the logical `SansSerif` in place. Bold is
  a derived style on the single face. `THIRD_PARTY_NOTICES.md` records the
  licence; the jar grows by the font's 277 KB.
- Accessibility settings, all persisted and live (D17, §4): high contrast
  (hazards and bird outline a pixel stronger, HUD panels go opaque, text
  outlines pick a black-or-white fill, the darkness veil is capped at 0.25),
  the Machado 2009 colour-blind palettes (`none`/`protanopia`/`deuteranopia`/
  `tritanopia`, applied to whole world palettes and to the semantic danger,
  coin and flame colours with per-world luminance targets keeping hazard vs
  background, telegraph vs hazard and coin vs accent ≥ 40 luma apart —
  asserted numerically in `ProceduralRenderTest`), text scale (every screen
  reflows at 1.5×, test-covered) and hold-to-flap (wired to
  `RunInput.autoFlapHeld`; the bot never sets it). `SettingsScreen` carries all
  four rows in both languages.
- Tests: `MusicSequencerTest` (audibility through `CaptureAudioBackend` over
  two seconds, byte determinism, per-world difference, boss variant, render
  budget, mute, duck, crossfade, volume retarget), the loop and ramp cases in
  `VoiceTest`/`SoftwareMixerTest` (wrap, monotonic ramps, fade-out drop, the
  crossfade holding loudness), the bundled-font cases in `FontsTest` and
  `AssetManagerTest`, the a11y render cases in `ProceduralRenderTest`, the
  settings rows in `SettingsScreenTest`, and a music-audible assertion in the
  smoke rig's run (through `CaptureAudioBackend`, never a sound device).

### Added — M8 progression screens: challenges, achievements, milestones, collections

- The main menu grows two entries (D17): *Challenges* and *Achievements*.
- `ui/screens/ChallengesScreen`: the seven challenges in content order with a
  detail block — the world (labelled, never checked for unlocks, E6), the tier,
  the objective in words, the rewards, the forced modifiers and the challenge's
  own boss when it has one. *Play* pushes a `GameScreen` over the menu through
  `ui/screens/ChallengeRunSource` (`ContentRunFactory.forChallenge`) with the
  profile's bird, palette and loadout (`RunLoadout.challengeConfigFor`); the
  objective's outcome is told after the run.
- `ui/screens/AchievementsScreen`, the three tabs of D13/D17:
  *Achievements* — every definition in content order, unlocked ones with their
  unlock date, locked ones dimmed, hidden ones a `???` until they fire, header
  counting them; *Milestones* — the level progress bar, then the next five
  thresholds among unclaimed level rewards and not-yet-fired lifetime-threshold
  achievements (nearest first, each with a `ProgressBar` fed by
  `AchievementEvaluator.progressOf`; hidden achievements stay out of the list);
  *Collections* — one bar per category of `progression/CollectionProgress`,
  owned over total with the floored percentage, `all` last, the same arithmetic
  the evaluators act on. Read-only, like `StatisticsScreen`.
- `ui/screens/BossBanner` (a non-blocking overlay modelled on
  `RuleShiftBanner`, parked in the ground strip opposite the rule banner): the
  warning countdown, the survival countdown and the "cleared!" flash; a world
  boss is named by its world, a challenge boss by its challenge (E26).
  `HudRenderer` centres a boss timer over the playfield during
  `BOSS_WARNING`/`BOSS`, with the phase number while the fight runs.
- The finished run tells the player what the objective paid (D29): the
  game-over strip shows MET/missed for a challenge run and how the boss
  encounter went, the summary breaks the same facts out, and
  `GameScreen.publishProgress` raises a toast per newly earned achievement —
  naming the coins it paid when it pays any (`toast.achievement`,
  `toast.achievement_coins`) — and per granted unlock
  (`toast.unlock_granted`), beside the events the audio manager already heard.
- `progression/AchievementEvaluator`: scope (`RUN`/`LIFETIME`/`PRESTIGE`)
  conditions over `Statistics.resolve`, counter conditions
  (`RUN_COUNTERS`, collection categories), `progressOf` for the bars, hidden
  handling; `progression/CollectionProgress` reuses `UnlockEvaluator`'s counter
  arithmetic instead of duplicating it. `ProgressionManager.apply` keeps the
  D14 order (rewards → wallet → XP/level → statistics → challenge record →
  daily record → achievements → unlocks) and `applyPurchase` re-runs
  achievements → unlocks (E17).
- Strings for both languages (`menu.*`, `challenges.*`, `achievements.*`,
  `objective.*`, `hud.boss_*`, the toasts); `ProgressionText` resolves the
  content names the screens and toasts share.
- Tests: `ChallengesScreenTest`, `AchievementsScreenTest` (tabs, bars, hidden),
  `BossBannerTest`, `CollectionProgressTest`, `AchievementEvaluatorTest`,
  `SmokeWindowTest` walks the two menu entries; the game-over strip's objective
  and boss rows are covered in the overlay tests.

### Fixed — M8 (review pass)

- `GameScreen`: newly earned achievements and granted unlocks now push toasts;
  they only fired `GameEvent`s (a sound) before, so the plan's
  "earn achievements with toasts" never reached the screen.
- `AudioManager.setVolumes`: sliding the music volume to zero with a loop live
  now stops the loop instead of leaving it sounding at the old gain until the
  next screen change; raising the volume again re-issues it. Covered by
  `AudioManagerTest`.
- `content/defs/ObjectiveType`: `SURVIVE_TICKS` was missing from the enum, so
  D11's five-type objective set could not be authored or validated. Added with
  `ObjectiveEvaluator`/HUD/screen/`ContentValidator` support; no shipped
  challenge uses it.
- `GameOverOverlay`: the boss row is only added once the encounter actually
  began (`phasesReached > 0` or a clear recorded), so a run that ended before
  `atGate` no longer reads "Boss: Phase 0".
- Test pins the review pass asked for: `BossEncounterTest` drives a three-phase
  boss whose fight loops past the last phase back to phase 1 and asserts
  `phasesReached` stays at 3 (the `Math.max` fold was unguarded); 
  `ProceduralRenderTest` asserts the colour-blind palette separations against
  the 60/45 luma contract numerically and two-sided instead of a weaker
  literal; `SoftwareMixerTest` pins the music crossfade/fade ramp at exactly
  `MUSIC_RAMP_FRAMES`.

### Added — M9: difficulty tiers, daily challenge, prestige, seeded runs, attract mode

- Difficulty tiers balance pass (data-only): `hard` softened to
  `SCROLL_SPEED ×1.10` / `GAP_SIZE ×0.92` and `nightmare` to `×1.20` / `×0.85`
  (from ×1.15/×0.9 and ×1.3/×0.8) because the expert bot's boss-gate reach on
  the tightest world × tier cells measured 32–38 % against the 30 % bar; the
  shipped shape measures 46–96 % on every cell. The flags
  (`ALL_OBSTACLES_MOVE`, `LETHAL_CEILING`), the reward multipliers (1.5/2.5),
  the classic/standard curves and the `normal` tier are untouched
  (`docs/BALANCING.md` §12.1).
- `upgrades.json` `glide_1` retune: `-0.10` per level measured exactly zero
  (the cap never cuts into the reachable dive distribution), so it ships
  `-0.30` at level 1 with the format's first `levelOverrides` use carrying
  level 2 at `-0.35`; costs stay 90/200 (`docs/BALANCING.md` §12.2).
- The daily challenge (`progression.DailyChallenge`, `ui/screens.DailyRunSource`):
  one deterministic configuration per UTC date — the seed is
  `fnv1a("daily:" + yyyy-MM-dd)` from the injected `TimeSource`, and from the
  named `daily` stream the game draws one world, one tier from
  `economy.daily.tierPool` (`normal`, `hard`) and two compatible forced
  modifiers, all from content the profile has unlocked. Daily runs pay the
  `economy.daily.rewardMult` ×1.25, record `attempts` and the best gate count
  per attempt, and retrying keeps the day's seed. The pick is written to
  `profile.daily` the first time the day is viewed or played and then frozen
  for that date, so unlocking content later cannot move it; a stored pick is
  rebuilt at most once and only when the content can no longer play it.
  Playing the daily requires `feature:seeded_runs`; the forced modifiers do
  not require `feature:modifiers` — only mid-run drafting does.
- Seeded mode: replays `profile.lastSeed` so a run can be retried on the exact
  obstacles that ended it. Seeded and Daily open together with
  `feature:seeded_runs` (level 5, or 100 coins in the shop); a locked mode is
  marked with its condition in the bird screen's new run-mode row, and Play on
  a locked mode falls back to a standard run.
- Prestige (`progression.PrestigeSystem`): at level 25 (at most five per
  profile) the statistics screen offers a two-step confirm that banks the
  career — `profile.prestigeBaseline` snapshots the lifetime
  runs/gates/coins/boss clears, then the wallet, XP, level, upgrades, ability
  levels and caps, challenge records and the daily pick reset, and `unlocked`
  is rebuilt as the defaults plus the kept `bird:*`/`cosmetic:*` ids.
  Achievements and lifetime statistics survive, `prestigeCount` rises and
  `cosmetic:<selectedBird>:prestige` (the golden palette) is granted, and
  `bonusPerPrestige` (COIN_MULT +5 %) per stack rides into every later run's
  `PRESTIGE` layer. Cumulative unlock conditions read "since prestige" against
  the baseline, so nothing already earned is granted twice; the main menu
  grows a prestige badge while the count is above zero.
- Attract mode: after twenty seconds without input on the main menu a bot
  plays a real, profile-less demo run behind it on the named `attract` stream,
  dimmed under the menu; any input cancels it, and a focus loss freezes it.
- `gameplay.harness.MetaSim` and `BalancingSim --meta`: whole-career
  simulations of the `spender`/`saver` purchase policies through the real
  progression stack, printing the runs-to-unlock table. `MetaSimTest` (`@sim`)
  asserts the E25 gates: the spender-average owns every non-cosmetic
  unlockable by run 25 (bound 200) with every node and ability level maxed by
  run 15.3 (bound 600), the saver reaches world 2 in a mean of 2.2 runs
  (bound 10; novice 7.0, bound 15), the novice journey cells of the plan hold,
  and the synergy activation rate is 69.9 % (bound ≥ 20 %) — all recorded with
  their tables in `docs/BALANCING.md` §13.
- Tests: `DailyChallengeTest` (same date, same pick; unlocked-only draw;
  compatibility; the stored pick surviving a new unlock; per-attempt records),
  `PrestigeSystemTest` (the reset to the letter; nothing condition-derived
  re-granted), `MetaSimTest` (`@sim`), `DailyModeUiTest`,
  `PrestigeWiringTest`/`PrestigeUiTest`, `AttractModeTest` and
  `IconExportTest`.

### Added — M9 release packaging: icons, jpackage script, release workflow

- `tools/IconExport` (`src/tools`, `./gradlew iconExport`) renders the
  procedural icon from the vector form — never by upscaling a bitmap — and
  writes `build/icon/flapforge.png` (256×256 master), `flapforge.ico` (16/32/48
  /256 entries in a hand-written PNG-in-ICO container) and `flapforge.icns`
  (ic07/ic08/ic09/ic10 chunks carrying the 128/256/512/1024 renders in a
  PNG-in-ICNS container). `IconExportTest` parses all three containers back
  byte by byte, independently of the writer (E9).
- `scripts/package.sh` runs `./gradlew fatJar iconExport` and then `jpackage
  --type app-image` with the per-OS icon (`.png` on Linux, `.ico` on Windows
  from Git Bash, `.icns` on macOS) into `build/dist/`; it exits with a clear
  message when `jpackage` is unavailable.
- `.github/workflows/release.yml` runs on a `v*` tag: build + test + package on
  ubuntu/windows/macos, per-OS app-image zips and the fat jar attached to the
  GitHub release.

### Fixed — M9 (review pass)

- `forge_boss_p2` was unwinnable on `tier:hard`: the four presses' `length 300` left
  `2 × 300 > 598 px` with no overlap between the top and bottom corridors, so survival hung on
  which phase the columns had reached when they crossed the bird — at hard (the world's own
  `×1.10` under the tier's `×1.10`) the expert cleared the encounter 0/30 (every death a
  piston) while normal and nightmare sat at 100 %, which made the boss reward (400 coins) and
  the `world:storm_sky` unlock unreachable on hard, and on the days the daily draws
  iron_forge/hard. Shipped `length 260`, the shape `void_boss_p2` already plays: every tier now
  clears the encounter at 90–100 % (expert) and in-run hard clears are 35–90 %
  (`docs/BALANCING.md` §11.2.1). `ContentFeasibilityTest` now holds the boss encounter itself
  to the same 30 % bar on every tier — before, it measured only the road to the boss per tier
  and the boss on the default tier, so the gap was invisible.
- `PrestigeSystem.check`'s `MAX_REACHED` guard carries a comment naming it the only real cap
  enforcement (the clamp inside `prestige()` is unreachable defence-in-depth), so a future
  editor does not remove the guard on the belief that the clamp enforces the cap.
- Test-only pass; no other shipped code or data changed. `DailyChallengeTest`: the
  forced-modifier sweep now re-derives the drawn world and tier's rule set from
  the content and re-asks `ModifierPool` for every forced card (E12), and a new
  test replays the sweep over content whose hard tier carries `NO_COINS` and
  whose every card declares it in `requiresFlagsAbsent` — on a flagged day
  nothing may be forced, so a draw that dropped the world/tier rules fails.
- macOS packaging: `jpackage` feeds `--app-version` into `CFBundleVersion`, which
  rejects a first component of zero, so the mac bundle of 0.1.0 failed to build
  (the Linux and Windows bundles were unaffected). `scripts/package.sh` now
  repacks a `0.y.z` version as its significant form on Darwin — 0.1.0 becomes
  `1.0` in the bundle's `CFBundleVersion` — while the jar keeps the real version
  in `Implementation-Version`.
  Before, a mutation swapping the daily pool's rule set for the empty one left
  the suite green because the shipped flags happen to be empty.
- `AttractModeTest`: the attract delay is pinned at the literal 1199/1200 ticks
  (20 s × 60 Hz) in the test that owns the plan's number instead of reading
  `MainMenuScreen.ATTRACT_DELAY_TICKS`, so retuning the constant fails there
  (a 10 s mutation survived the suite before).

### Changed — M9 release

- The version is `0.1.0` (the `-SNAPSHOT` suffix is dropped), so the fat jar is
  `build/libs/flapforge-0.1.0-all.jar`. The published determinism hash
  (`hash=eaaa01685261a433` for `--headless-run 3000 --seed 42`) is unchanged
  and re-verified with the new jar name on JDK 17 and JDK 21.

### Added — M10: Android port (addendum; APK attached to the same release, 2026-09-05)

The port ships as `Flapforge-0.1.0-android.apk` on the existing `v0.1.0`
release rather than as a new version: desktop behaviour did not change
(the host seam is a refactor behind the same hash), the published determinism hash (`hash=eaaa01685261a433` for
`--headless-run 3000 --seed 42`) is unchanged, and the APK is versioned
`0.1.0` (`versionCode 1`) like the jar and the bundles.

- **Build-time source transform** (`android/build.gradle`, task
  `transformSources`; the executable spec is
  `android/p0/transform_prototype.py`): copies `src/main/java` into
  `android/build/transformed/java` rewriting `javax.sound.sampled.` →
  `jssound.`, `javax.imageio.` → `jimageio.` and `java.awt.` → `awt.` (the
  last only before a class name or the `geom.` / `image.` / `event.`
  subpackages, so the `"java.awt.headless"` property name survives), and
  skips the six desktop-only files (`app/GameWindow`,
  `app/BufferStrategyPresenter`, `app/AwtInputBridge`, `app/KeyRepeatFilter`,
  `app/AwtHost`, `Flapforge`). A double integrity gate fails the build unless
  reversing the rules reproduces every source byte for byte *and* no desktop
  prefix survives in the output; `-PtransformSelfTest=breakReverse|breakForward`
  breaks one half each to prove the gate bites. 318 files transformed, 6
  excluded; the desktop tree is never edited.
- **Shim packages** under `android/src/main/java`, census-bound (every member
  the game calls, with its call sites in the javadoc, and nothing else):
  `awt` — `Graphics2D` over `android.graphics.Canvas` with a pure-double
  `AwtMatrix`, device-space clipping, even-odd and non-zero paths,
  AWT-oriented arcs, dashed strokes, gradient paints and baseline
  `drawString`; `Color`, `Font`/`FontMetrics` over `Typeface`, `BasicStroke`,
  `GradientPaint`, `RenderingHints`, the `geom` shapes,
  `image.BufferedImage` over an `ARGB_8888` bitmap with AWT's shared-raster
  `getSubimage`, and a `GraphicsEnvironment` that is always headless;
  `jssound` — a RIFF/WAVE 16-bit PCM reader behind
  `AudioSystem.getAudioInputStream`, `SourceDataLine` over `AudioTrack` in
  stream mode, and the host-only `AudioSystem.suspendOutput()` /
  `resumeOutput()` gate that pauses every open track and parks the mixer
  thread while the activity is in the background; `jimageio` —
  `ImageIO.read` via `BitmapFactory`, unpremultiplied. Members outside the
  census either do not exist or throw `UnsupportedOperationException`.
- **`Graphics2D` allocation pass**: the draw path allocates nothing in the
  steady state. `FontMetrics` is created once per (immutable) `Font` and
  cached on it, over the font's one never-mutated measuring paint; a context
  recycles one `Path2D.Double` sink, one `android.graphics.Path`, scratch
  `awt.geom` shapes for the integer convenience methods and the float buffer
  and `Rect` of the image/text pipeline (`AwtMatrix.transformX/Y`,
  `toFloatValues`); clipping brackets a draw with plain save/restore calls
  instead of a captured `Runnable`; a single-entry `LinearGradient` cache
  keyed on the device-space end points and colours serves repeated identical
  gradients within one context (the host opens a fresh context per frame, so
  the sky gradient still costs one `LinearGradient` per frame on the device);
  `Path2D.append` writes straight into the path, and the
  `Shape.appendTo` contract says a shape closes only the subpath it opened
  (a degenerate arc or ellipse appends nothing), so the result equals the
  former fresh-sink copy. `Graphics2DAllocationTest` measures a frame-like
  batch (300 fills, 100 draws, 50 `getFontMetrics` + `drawString`, 10
  `drawImage`): about 480 KB → 19 KB per batch (two runs on the original shim measured 480,087 and 480,725 bytes; 19,133 after).
- **Host seam** in the desktop code: `app.GameHost`, `app.AppWindow` and
  `app.InputBridge`; `GameApplication.start(LaunchOptions, GameHost)` asks the
  host for the window, the presenter, the input bridge and the display
  refresh rate; `app.AwtHost` is the desktop implementation and absorbed
  every toolkit type `GameApplication` used to name; `app.AppVersion` owns
  the `version.properties` reader so nothing the transformed game needs lives
  in the desktop entry point. Desktop behaviour is unchanged.
- **Android host** (`android/src/main/java/io/github/michelbr84/flapforge/android`):
  `MainActivity` — immersive fullscreen, keep-screen-on,
  `SavePaths.override` to the app's files directory before anything else, the
  game booted on a dedicated `flapforge-android-boot` thread once the surface
  has a size, back gesture → `ESCAPE`, `onPause` → `FocusLost` + audio
  suspend, `onResume` → audio resume, `onStop`/`onStart` → `Iconified`,
  `onDestroy` → `CloseRequested` with a bounded wait for the exit save;
  `GameSurfaceView` + `SurfacePresenter` — the desktop presenter's frame body
  on a software `SurfaceView` canvas, with a lifecycle lock so a destroyed
  surface is never drawn; `AndroidWindow`, `AndroidHost` and
  `AndroidInputBridge` — a touch is the mouse: tap = left button on the
  press edge, a mostly vertical drag = wheel notches every 24 px, a second
  finger or a press on the HUD ability badge during a run = right button,
  `surfaceChanged` = `Resized`. On the first run the boot thread writes
  `settings.json` with `holdToFlap = true`; an existing file is never
  rewritten.
- **APK**: AGP 9.4.0 on the Gradle 9.7.1 wrapper as a separate build
  (`./gradlew -p android ...`), `applicationId io.github.michelbr84.flapforge`,
  `minSdk 33`, `compileSdk`/`targetSdk 36`, `versionName 0.1.0` /
  `versionCode 1`, release signed with the debug keystore for sideloading,
  `minifyEnabled false` (Gson reflects over the record types); the desktop
  resources (`data/*.json`, `assets/fonts`, `version.properties`) land at the
  APK root where `Class.getResourceAsStream` finds them; 1,602,691 bytes
  (about 1.5 MiB), 40 entries.
- **Launcher icon** from the desktop procedural icon:
  `android/tools/IconGen.java` renders `render.ProceduralArt.icon(int)` — the
  vector `drawIcon` that `./gradlew iconExport` uses for the desktop bundles
  — into `android/src/main/res/` (legacy, round and adaptive-foreground PNGs
  for mdpi–xxxhdpi, `values/colors.xml` with the Green Fields sky-top
  background) next to two static adaptive-icon XMLs; the manifest declares
  `android:icon` and `android:roundIcon`. The tool needs `java.desktop`, so
  it runs by hand against the desktop classes, not from Gradle, and its
  output is deterministic.
- **No Kotlin standard library**: `android/gradle.properties` sets
  `kotlin.stdlib.default.dependency=false`. AGP 9's built-in Kotlin support
  otherwise adds `kotlin-stdlib` to a project with no Kotlin sources — 1,079
  classes and eight `kotlin/*.kotlin_builtins` entries that nothing
  referenced; the release APK went from 3,588,389 to 1,522,607 bytes before
  the icons were added.
- **CI**: `.github/workflows/android.yml` runs the transform,
  `assembleRelease` and the unit tests on every push that touches `android/`
  or `src/main/`, proves the integrity gate with
  `-PtransformSelfTest=breakForward`, and uploads the APK and the JUnit
  report; `release.yml` gains an `android` job that builds the APK on a `v*`
  tag, checks its `versionName` against `version.properties` and attaches it
  as `Flapforge-<version>-android.apk` beside the bundles.
- **Tests** (`android/src/test`, JUnit 4 + Robolectric 4.16,
  `@Config(sdk = 35)` with native Skia rasterisation): 201 tests in 20
  classes — pixel proofs of the `Graphics2D` semantics, an allocation
  tripwire for its hot path, `Path2D.append` parity with a fresh sink,
  `BufferedImage`,
  fonts (the bundled OFL font through the game's own load path), `ImageIO`,
  byte-for-byte WAVE parsing, `SourceDataLine` and the output gate over
  `ShadowAudioTrack`, the touch bridge through real `MotionEvent`s, the
  presenter, and a real boot of the game inside the activity (Play tapped, a
  run flapped and paused, back to the menu, quit) with `DesktopProfileGuard`
  fingerprinting `~/.flapforge` before and after.

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
