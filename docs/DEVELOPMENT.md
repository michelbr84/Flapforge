# Development guide

_Draft — written at milestone M0; later milestones extend it._

This page is the practical companion to [`ARCHITECTURE.md`](ARCHITECTURE.md):
how to build, run and test Flapforge, which flags and tasks exist, and the
rules that every change must respect.

## Prerequisites

| Requirement | Notes |
| --- | --- |
| JDK 17 or newer | Any distribution (Temurin, Microsoft, Zulu, the distro package). The Gradle toolchain compiles against `--release 17`, so a newer JDK on the machine is fine. `java -version` must work in the shell. |
| Git | to clone and contribute |
| A desktop session | Linux (X11 or Wayland/XWayland), Windows 10+, macOS 12+. The game is a pure AWT/Java2D application: no OpenGL, no native libraries. |
| Nothing else | No Gradle installation: the repository ships the Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), which downloads Gradle 9.7.1 on first use and caches it under `~/.gradle`. |

## Bootstrapping

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge
./gradlew build        # first run downloads Gradle 9.7.1 + Gson + JUnit
./gradlew run          # opens the game window
```

`./gradlew --offline build` works as soon as the dependencies are in the
local cache; CI and the dev machine use it to keep builds reproducible.

### Regenerating the wrapper

The wrapper is committed, so you normally never touch it. If it has to be
regenerated (new Gradle version, corrupted `gradle/wrapper` folder), run any
locally available Gradle 8+ launcher against the project — the one Gradle
already cached for the wrapper works and needs no download:

```bash
# generic
gradle wrapper --gradle-version 9.7.1 --distribution-type bin

# using the launcher that the wrapper cached (path differs per machine)
~/.gradle/wrapper/dists/gradle-9.7.1-bin/<hash>/gradle-9.7.1/bin/gradle \
    wrapper --gradle-version 9.7.1 --distribution-type bin
```

Optionally pin `distributionSha256Sum` in
`gradle/wrapper/gradle-wrapper.properties` from
<https://services.gradle.org/distributions/gradle-9.7.1-bin.zip.sha256>.

## Gradle tasks

| Task | Purpose |
| --- | --- |
| `./gradlew build` | compile (`-Xlint:all,-serial -Werror -parameters`, UTF-8, `--release 17`) and run the default test suite; **this is the gate every milestone and PR must pass** |
| `./gradlew run` | start the game from source; flags go through `--args`, e.g. `./gradlew run --args="--seed 42 --scale 2"` |
| `./gradlew test` | default suite: pure and headless tests only (`java.awt.headless=true`; JUnit tags `gui`, `perf`, `sim` excluded) |
| `./gradlew smokeTest` | tests tagged `gui`: a real window, real `BufferStrategy`, fullscreen toggled twice, Robot-driven menu navigation, the real quit path, screenshots written to `build/smoke/`; never up-to-date or cached (the window opens on every invocation); skipped, not failed, without a display |
| `./gradlew perfTest` | tests tagged `perf`: local performance budgets (not run in CI) |
| `./gradlew simTest` | tests tagged `sim`: long bot simulations (feasibility, new-player journey, meta-progression) — populated from M4 |
| `./gradlew jar` | plain jar with `Main-Class` and `Implementation-Version` |
| `./gradlew fatJar` | self-contained `build/libs/flapforge-<version>-all.jar` (Gson bundled) |
| `./gradlew balancing -PtoolArgs="..."` | `[M1]` balancing simulation (`src/tools`; the `tools` source set and the five `JavaExec` tasks exist from M0, the tools themselves arrive with their milestones) |
| `./gradlew saveInspector -PtoolArgs="..."` | `[M3]` inspect/validate a save directory |
| `./gradlew contentCheck` | `[M4]` run the content validator on the shipped JSON |
| `./gradlew assetValidator` | `[M7]` validate `assets/manifest.json` and referenced files |
| `./gradlew iconExport` | `[M9]` export the procedural icon for `jpackage` |
| `./gradlew test -Pflapforge.updateGolden` | `[M1]` regenerate golden fixtures on purpose |

Wrapper scripts: `scripts/build.sh` / `scripts\build.ps1` run `build fatJar`;
`scripts/run.sh` / `scripts\run.ps1` run `run` and forward every argument to
the game. Gradle can take a few minutes on a cold cache; keep going.

## Launch flags

All flags are parsed by `app.LaunchOptions` before anything else starts
(`--headless-run` and `--no-window` set `java.awt.headless=true` before any
AWT class loads). Pass them through `--args="..."`, the run scripts, or
directly to the fat jar. Every flag is accepted from M0; the "available from"
column says when the feature behind it does something. An unknown flag or a
malformed value prints the message and the usage text to stderr and returns
without starting the game (`System.exit` is reserved for the shutdown
watchdog, D4). Without a display a windowed launch prints
`No display available; use --headless-run N or --no-window.` and returns.

| Flag | Meaning | Available from |
| --- | --- | --- |
| `--scale N` | initial window scale (integer multiple of the 420×640 playfield); default = largest integer scale whose decorated window (playfield plus a 48 px allowance for the title bar) fits the usable screen height, so 2× on a 1440-class display and 1× on 1080p | M0 |
| `--fullscreen` | start in borderless fullscreen (F11 toggles) | M0 |
| `--no-window` | run without a window (headless); with nothing to simulate yet it returns at once | M0 |
| `--help`, `-h` | print the usage text and exit | M0 |
| `--seed N` | fixed RNG seed for a reproducible run | M1 |
| `--headless-run N` | simulate N frames with no window; M0 prints a summary line (`headless-run frames=N ticks=N presents=N seed=S`), from M1 the last line is `hash=<hex>` (the state hash CI compares across OS/JDK) | M0 (hash: M1) |
| `--no-audio` | use the `NullAudio` backend | M2 |
| `--lang CODE` | UI language: `auto`, `en`, `pt_BR` | M2 |
| `--home DIR` | save/settings directory instead of the per-OS default | M3 |
| `--reset-save` | delete the save file (a backup is kept) and start fresh | M3 |
| `--bird ID` | start with the given bird | M4 |
| `--tier ID` | difficulty tier (`normal`, `hard`, `nightmare`) | M4 |
| `--world ID` | start in the given world (`green_fields`, `wind_valley`, `iron_forge`, `storm_sky`, `void`) | M7 |

Default data directories: `~/.flapforge` (Linux), `%APPDATA%\Flapforge`
(Windows), `~/Library/Application Support/Flapforge` (macOS).

## In-game keys

Flap `Space` / `Up` / left click · ability `X` / `Shift` / right click ·
pause `Esc` · confirm `Enter` · mute `M` · debug overlay `F3` · fullscreen
`F11`. Key bindings become rebindable in Settings at M2.

## Running the GUI tests locally

`smokeTest` needs a display. On the development machine:

```bash
DISPLAY=:0 ./gradlew smokeTest
```

- On X11 the test captures the real canvas with `java.awt.Robot` and asserts
  the frame is not uniform. On Wayland (XWayland) the capture can come back
  black; the test then asserts on the same frame rendered through the
  presenter into a `BufferedImage` and prints a warning. Both images always
  land in `build/smoke/` as `<name>-capture.png` and `<name>-render.png`.
- The menu navigation test drives the window with real `Robot` keys and
  clicks and requires every event to take effect; if the canvas cannot get
  keyboard focus (another window keeps stealing it) the test is reported as
  skipped. Keep the desktop idle while it runs (it holds `F11` for 0.4 s).
  The same navigation through the input queue alone is `MenuNavigationTest`
  in the default headless suite.
- Never leave a Java process behind when scripting: wrap manual window checks
  in `timeout 10 ./gradlew run`.
- CI (`.github/workflows/test.yml`) installs `xvfb` and runs
  `xvfb-run -a ./gradlew smokeTest`.

## Coding rules

The compiler and `ArchitectureTest` enforce most of these; reviewers enforce
the rest. See also [`../CONTRIBUTING.md`](../CONTRIBUTING.md).

### Language level

- Java 17, `options.release = 17`, no preview features.
- **No pattern-matching `switch` and no record patterns** (they need Java 21).
  Use `instanceof` patterns:

  ```java
  if (hitbox instanceof Aabb box) { ... } else if (hitbox instanceof Circle c) { ... }
  ```

- `sealed` interfaces are fine, but every permitted subtype must live in the
  same package as the sealed type (the project is an unnamed module); e.g.
  `core.geom.Hitbox` with `Aabb`/`Circle`.
- Records, `switch` expressions (with `->` arms and no patterns), text blocks
  and `var` are all available and welcome.

### Compiler flags

`-Xlint:all,-serial -Werror -parameters`, UTF-8 sources. Consequences:

- Every lint category except `serial` is an error: unchecked casts, raw
  types, fall-through, missing `@Override`, deprecation, unused `try`
  resources, and so on. Fix the cause; `@SuppressWarnings` needs a comment.
- `-serial` is disabled on purpose: AWT subclasses (`Frame`, `Canvas`) are
  `Serializable`, and **no `serialVersionUID` is needed anywhere**.
- Gson and generics: use `TypeToken.getParameterized(List.class, Foo.class)`
  instead of anonymous `new TypeToken<List<Foo>>() {}` subclasses, which
  trip `-Xlint` under `-Werror` and are fragile across JDKs.

### Purity (see ARCHITECTURE.md)

`core`, `input`, `gameplay.*`, `ability`, `modifier`, `content`,
`progression`, `persistence` must not reference `java.awt`, `javax.*`,
`sun.*`, `Math.random`, `System.currentTimeMillis`, `System.nanoTime`,
unseeded `new Random(`, `Thread.`, `Executors.`, or
`Math.(sin|cos|tan|atan|atan2|asin|acos|sinh|cosh|tanh|exp|expm1|pow|log|log10|log1p|cbrt)`.
Allowed: `Math.sqrt/floor/ceil/round/abs/min/max/hypot/fma`. Time is a
`core.TimeSource`; executors come from `app.Threads` as a plain
`java.util.concurrent.Executor`. `gameplay` and `progression` never import
`event`. Oscillators use triangle waves or lookup tables, not `Math.sin`.

### Other rules

- No Swing anywhere. The window is `java.awt.Frame` + `Canvas` +
  `BufferStrategy`; UI components are drawn by the game.
- Determinism: all randomness through `core.RandomProvider` named streams.
- Content lives in JSON under `src/main/resources/data`; unknown keys are
  errors, so update the `content.defs` record when you add a field.
- English only in code, comments, docs and commits. Player-facing strings go
  through `content.Strings` (`en.json` is the source of truth).
- Conventional Commits; milestone commits are `feat(M#): ...`.

## Repository layout

See [`ARCHITECTURE.md`](ARCHITECTURE.md) for the full package tree. In
short: `src/main/java` (game), `src/main/resources` (JSON content, strings,
asset manifest, `version.properties`), `src/test` (unit, property,
simulation, headless render and GUI smoke tests, fixtures), `src/tools`
(balancing, save inspector, content check, asset validator, icon export),
`scripts/`, `docs/`, `.github/`.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `Could not resolve ...` on the first build | you are offline and the cache is cold; drop `--offline` once |
| Toolchain error: no Java 17 found | install a JDK 17 or point Gradle at one: `-Porg.gradle.java.installations.paths=/path/to/jdk17` |
| `smokeTest` reports every gui test as skipped | no display: set `DISPLAY` (or use `xvfb-run -a`) |
| `smokeTest` menu test skipped "never obtained keyboard focus" | another window steals focus; leave the desktop idle and rerun |
| Uniform black screenshot on Wayland | expected; the test falls back to the off-screen render (`<name>-render.png`) |
| A build fails only in CI with a lint error | the JDK version differs; the CI JDK is Temurin 17 (and 21 on one job) — reproduce with the same version |
