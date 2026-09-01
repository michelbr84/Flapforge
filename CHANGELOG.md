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

### Changed

- `README.md`: badges, Controls, Getting Started, Running the Game,
  Technology, Project Structure and Assets sections rewritten for the new
  code base; design prose kept for the M9 pass.
- `CHANGELOG.md`: inherited entries relabelled as upstream history with links
  pointing at the upstream repository.

### Removed

- The inherited upstream implementation: `src/main/java/com/kingyu/...`,
  `FlappyBird.jar` and the `resources/` tree (`img`, `wav`, `readme_img`,
  `score`). They remain in git history but are not part of any Flapforge
  build or release (see `THIRD_PARTY_NOTICES.md`).
- `STRUCTURE.md` (the pre-rewrite layout proposal), superseded by the
  reconciliation table in `docs/ARCHITECTURE.md`.

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
