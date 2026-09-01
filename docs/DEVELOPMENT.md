# Development guide

_Written at milestone M0; extended at M2 (settings and profile directory,
audio and language flags, adding a string key). Later milestones extend it
further._

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
| `--no-audio` | never open a sound device: `AudioBackend.create` returns `NullAudio` without touching the sound system. Use it in scripts, on CI and on machines whose audio stack is slow to open; the game plays exactly the same, silently. A machine where no line opens ends up here anyway, after one line on stderr. | M2 |
| `--lang CODE` | UI language for this launch: `auto` (default locale), `en`, `pt_BR`. Overrides `settings.language`; an unknown code is ignored and `auto` applies. The language can also be changed live in Settings, and both take effect immediately (the menu behind the settings screen re-labels itself). | M2 |
| `--home DIR` | profile directory (`settings.json`, and from M3 `save.json`) instead of the per-OS default. Always pass it in tests and scripts so nothing writes to the real profile. | M2 (settings) |
| `--reset-save` | start from a fresh profile; the old save and its backup are moved aside as `save.reset-<time>.json` / `save.bak.reset-<time>.json`, never deleted | M3 |
| `--bird ID` | start with the given bird | M4 |
| `--tier ID` | difficulty tier (`normal`, `hard`, `nightmare`) | M4 |
| `--world ID` | start in the given world (`green_fields`, `wind_valley`, `iron_forge`, `storm_sky`, `void`) | M7 |

### Where settings and saves live

`persistence.SavePaths` resolves the profile directory in this order: `--home
DIR` (which installs an override), then the `flapforge.home` system property,
then the `FLAPFORGE_HOME` environment variable, then the per-OS default:

| OS | Profile directory |
| --- | --- |
| Linux / BSD | `~/.flapforge` |
| Windows | `%APPDATA%\Flapforge` |
| macOS | `~/Library/Application Support/Flapforge` |

It holds `settings.json` (§4 of the plan; written from M2) and, from M3,
`save.json`, `save.json.bak` and `backups/`. Every write is crash-safe
(`AtomicFiles`: temp file, fsync, atomic rename) and runs on the save
executor, never on the loop thread.

A `settings.json` whose `version` differs from the build's is **not** loaded:
the defaults are restored and the old file is kept as `settings.v<N>.json`
(`settings.v<N>-2.json`, `-3` … when one is already there), with a warning
toast. A file with no `version` key at all is treated as a missing key, not a
mismatch, so a hand-edited file keeps its values. `keyBindings` carries exactly
the seven rebindable actions; the focus arrows and `BACK` are fixed.

**No test may write to the real profile directory.** Unit tests use `@TempDir`
plus `SavePaths.override(...)`, the smoke tests write under `build/smoke/`, and
the one test that starts the real application passes
`--home build/smoke/app-home` and asserts the resolved profile directory is
under `build/`. `ls ~/.flapforge` after a full build must still say the
directory does not exist on a machine that has never run the game.

## In-game keys

Flap `Space` / `Up` / left click · ability `X` / `Shift` / right click ·
pause `Esc` · confirm `Enter` · mute `M` · debug overlay `F3` · fullscreen
`F11`. All seven are rebindable in Settings from M2 (arrows and `Esc`-to-go-back
are fixed, and so are the mouse buttons).

`F11`, `F3` and `M` work on every screen. They are not engine switches: each one
flips a field of `settings.json`, applies it through
`GameContext.applySettings` and persists it, so the state survives a restart and
the Settings screen always shows what is actually in force.

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
- **A locked screen skips the Robot-driven tests.** A screensaver, a lock
  screen or any client holding a keyboard grab takes every synthetic event
  while the canvas still reports focus, so before driving the window each test
  presses an unbound canary key (`Ctrl`) and checks it arrives. When it does
  not, the test is *skipped* with "the desktop session does not deliver
  synthetic input" instead of blaming the game. Unlock the session, or run the
  suite on a nested X server, which has its own XTEST and ignores the parent
  session's lock:

  ```bash
  Xephyr :7 -screen 1280x1024 -ac -noreset &
  DISPLAY=:7 ./gradlew smokeTest
  ```
- Never leave a Java process behind when scripting: wrap manual window checks
  in `timeout 10 ./gradlew run`.
- CI (`.github/workflows/test.yml`) installs `xvfb` and runs
  `xvfb-run -a ./gradlew smokeTest`.
- The window keys the smoke run presses go through the real settings path, so
  the run writes `build/smoke/home/settings.json`. The rig deletes that file
  before every test and the one test that starts the real application passes
  `--home build/smoke/app-home`; nothing under `~/.flapforge` is ever touched.
- Robot events are waited for against a **wall-clock** deadline, never a frame
  count: the rig drives the loop uncapped, so 60 frames can pass in under a
  millisecond while X still has the event in flight. A key or click that had no
  effect at all is re-sent up to three times (the "exactly one edge" assertions
  live in the tests, after the retry loop, so a double delivery is still
  caught). Keep the desktop idle anyway.
- If `smokeTest` reports a Gradle-side `java.io.EOFException` or
  `NoSuchFileException: build/test-results/smokeTest/binary/...` *after* the
  tests themselves have finished, re-run with `--no-configuration-cache`. It has
  been observed on Gradle 9.7.1 with `org.gradle.configuration-cache=true` and
  could not be reproduced on the development machine (12 consecutive green
  runs); it is a build-tool artefact, not a test failure.

## Adding a player-facing string

No literal the player can read may live in Java code. Every string goes through
`content.Strings`, keyed by a `content.StringKey` constant. To add one:

1. **Add the key to `src/main/resources/data/strings/en.json`.** The file is a
   flat, alphabetically grouped `"key": "value"` table; `en.json` is the source
   of truth. Use `{0}`, `{1}` … for values substituted at runtime.
2. **Add the same key to `pt_BR.json`,** with the same placeholders. Both files
   must carry *exactly* the same key set — `Strings.load` falls back to English
   for a missing key, so a dropped translation would otherwise be invisible;
   `StringsTest.everyShippedFileCarriesExactlyTheSameKeys` fails if they drift.
3. **Add a constant to `content.StringKey`,** naming the same key. The enum is
   what makes a typo a compile error rather than a raw key on screen.
4. **Use it:** `strings.get(StringKey.MY_KEY)` or
   `strings.format(StringKey.MY_KEY, value)`. Screens hold the shared `Strings`
   instance from `GameContext`; never call `Strings.load` per frame.
5. **Re-label on a language switch.** A screen that caches rendered text must
   compare `strings.language()` with the language it last drew and refresh when
   they differ — that is what makes the live `pt_BR` switch work for the screens
   under the settings screen.
6. **Run `./gradlew test`.** `StringsTest` checks the tables and the
   placeholders, `ContentValidatorTest` checks that every `StringKey` resolves
   and that the content ids have names and descriptions, `FontsTest` checks that
   the base font can draw the accents, and `ProceduralRenderTest` renders every
   screen in both languages and asserts the two frames are not identical.

Content ids follow the same path with derived keys: `Strings.name(kind, id)`
and `Strings.desc(kind, id)` resolve `<kind>.<id>.name` / `.desc`.

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
| `smokeTest` tests skipped "does not deliver synthetic input" | the session is locked or a client holds a keyboard grab; unlock it, or run on `Xephyr :7` (see "Running the GUI tests locally") |
| Uniform black screenshot on Wayland | expected; the test falls back to the off-screen render (`<name>-render.png`) |
| A build fails only in CI with a lint error | the JDK version differs; the CI JDK is Temurin 17 (and 21 on one job) — reproduce with the same version |
| `smokeTest` fails with `EOFException` / `NoSuchFileException` under `build/test-results` after the tests passed | Gradle configuration-cache artefact; re-run with `--no-configuration-cache` |
| The game is silent, `Audio: no output device (...)` on stderr | no usable output line (a container, a busy PulseAudio, no sound card); the game runs on `NullAudio`. `--no-audio` selects that path deliberately |
| Settings changes are not remembered | check the profile directory (see above) is writable; a failed write raises a warning toast and a `SaveFailed` event |
