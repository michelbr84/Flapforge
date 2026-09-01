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
