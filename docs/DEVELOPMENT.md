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
| Android SDK (APK only) | platform `android-36` and build-tools `36.0.0`, plus `android/local.properties` — see "Android build" `[M10]`. Not needed for anything desktop. |

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
| `./gradlew fatJar` | self-contained `build/libs/flapforge-<version>-all.jar` (Gson bundled; `0.1.0` ships `flapforge-0.1.0-all.jar`) |
| `./gradlew balancing -PtoolArgs="..."` | `[M1]` balancing simulation (`src/tools`; the `tools` source set and the five `JavaExec` tasks exist from M0, the tools themselves arrive with their milestones) |
| `./gradlew saveInspector -PtoolArgs="..."` | `[M3]` inspect/validate a save directory |
| `./gradlew contentCheck` | `[M4]` run the content validator on the shipped JSON |
| `./gradlew assetValidator` | `[M7]` validate `assets/manifest.json` and referenced files |
| `./gradlew iconExport` | `[M9]` export the procedural icon for `jpackage` |
| `./gradlew test -Pflapforge.updateGolden` | `[M1]` regenerate golden fixtures on purpose |
| `./gradlew -p android transformSources assembleRelease test` | `[M10]` the Android port: a separate build under `android/` (source transform, APK, Robolectric tests) — see "Android build" below |

Wrapper scripts: `scripts/build.sh` / `scripts\build.ps1` run `build fatJar`;
`scripts/run.sh` / `scripts\run.ps1` run `run` and forward every argument to
the game. `scripts/package.sh` (`[M9]`, bash) runs `fatJar iconExport` and then
`jpackage --type app-image` with the per-OS icon (E9) to write a self-contained
app image to `build/dist/`; it needs `jpackage`, which ships with JDK 14+.
Gradle can take a few minutes on a cold cache; keep going.

### The asset validator `[M7]`

`./gradlew assetValidator` (or `tools/asset-validator/run.sh` / `run.ps1`)
reads `assets/manifest.json` and checks every entry: the file resolves on the
classpath under `/assets/`, it carries a licence, and it starts with the
magic number of its kind (PNG for sprites and sheets, RIFF/WAVE for audio,
TrueType/OpenType for fonts). Parse errors count as problems. Exit status 1
on any problem, 0 for the shipped empty manifest — run it before adding an
art or sound pack through the manifest. From M8 the manifest is no longer
empty: the `font/ui` entry ships the bundled OFL font (`assets/fonts/`), which
`BootSequence` installs through `Fonts.install` before any screen paints; see
`docs/CONTENT.md` ("A font asset") for the entry's fields and how to swap it.

### The balancing tool's world flags `[M7]`

`./gradlew balancing -PtoolArgs="..."` sweeps content with the bot:

| Flag | Meaning |
| --- | --- |
| `--world ID` / `--world all` | the world of every run, or every world of `worlds.json` in order |
| `--tier ID` / `--tier all` | the tier, or every tier of `difficulty.json` |
| `--pattern ID` / `--pattern all` | stream one pattern of `patterns.json` in isolation, looped from the first spawn, in the pattern's own world unless `--world` is given; `none` (default) plays the world's spawn table |
| `--challenge ID` / `--challenge all` `[M8]` | play a challenge of `challenges.json` as the player would — its world, tier, curve, flags, effects, forced cards, forced pattern and boss — and print the objective and boss clear rates, the phases reached and the deaths by kind; how `docs/BALANCING.md` §11.1 is produced |
| `--boss ID` / `--boss all` `[M8]` | a world boss encounter on its own, started at the boss (`RunSetup.startingAtBoss`: the warning fires at the first gate and the curve is shifted so that gate plays under the difficulty of the authored `atGate`), so the cell measures the fight and not the road to it; how §11.2 is produced |
| `--drafts` | enable modifier drafts (off by default, so a cell measures the base run) |
| `--skill NAME` | `novice`, `average`, `expert`, `perfect` or `all` |
| `--meta` `[M9]` | run the meta-progression simulation (`MetaSim`): a fresh profile plays run after run through the real progression stack under a purchase policy, and the tool prints the runs-to-unlock table that `docs/BALANCING.md` §13 records |
| `--policy NAME` `[M9]` | the `--meta` purchase policy: `spender` (default; empties the wallet every run by priority class — features, worlds, birds/abilities, ability levels, nodes — cheapest within a class) or `saver` (one world or feature per run, cheapest first) |
| `--runs N` `[M9]` | run budget per `--meta` seed line (default 250) |
| `--meta-seeds N` `[M9]` | seed lines per `--meta` cell (default 20; seed line *l* plays run *i* on seed `1_000_000 × (firstSeed + l) + i`) |

The deaths line groups obstacle deaths by kind (`PIPE_GATE`, `GEAR`, `PISTON`,
`LIGHTNING`) plus the non-obstacle exits (`alive` = the tick budget reached),
which is how `docs/BALANCING.md` §10 and §11 are produced.

### The release flow `[M9]`

1. **The version lives in `src/main/resources/version.properties`** (`version=0.1.0`); Gradle
   reads it, so the plain jar's `Implementation-Version`, the fat jar's file name
   (`build/libs/flapforge-0.1.0-all.jar`) and the `jpackage` app version all follow it. A
   release candidate drops its `-SNAPSHOT` suffix here — as M9 did.
2. **`./gradlew fatJar iconExport`** builds the self-contained jar and renders
   `build/icon/flapforge.png` (256²), `flapforge.ico` (16/32/48/256) and `flapforge.icns`
   (ic07–ic10) from the procedural icon (E9); `IconExportTest` parses all three containers back.
3. **`scripts/package.sh`** runs those two Gradle tasks and then `jpackage --type app-image`
   with the current OS's icon into `build/dist/` (Linux `.png`, macOS `.icns`, Windows `.ico`
   under Git Bash).
4. **Tagging `v*` runs `.github/workflows/release.yml`**: build + test + package on
   ubuntu/windows/macos, the app image zipped per OS, and the zips plus the fat jar attached to
   the GitHub release. `build.yml` additionally re-verifies the cross-platform determinism hash
   of the classic headless run, which a release must not move.
5. **The `v0.1.0` tag freezes save v1** (see `docs/SAVE_SYSTEM.md` §8): from it on, every change
   to the persisted shape ships a migration.

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
| `--headless-run N` | simulate N frames with no window; M0 prints a summary line (`headless-run frames=N ticks=N presents=N seed=S`), from M1 the last line is `hash=<hex>` (the state hash CI compares across OS/JDK). The run behind the line is the *pinned classic configuration* (`RunConfig.classic`): classic bird, Green Fields, normal tier, no abilities, no drafts and — from M8 — no boss (`RunConfig.bossEnabled` is off there and on for every profile run, every challenge, the balancing tool and the feasibility tests). It reaches 36 gates and Green Fields' boss is at gate 30, so the pin is what keeps `hash=eaaa01685261a433` for `--headless-run 3000 --seed 42` where M1 recorded it. A session without a profile (`ContentRunFactory` with no supplier) plays the same pinned configuration. | M0 (hash: M1; boss pin: M8) |
| `--no-audio` | never open a sound device: `AudioBackend.create` returns `NullAudio` without touching the sound system. Use it in scripts, on CI and on machines whose audio stack is slow to open; the game plays exactly the same, silently. A machine where no line opens ends up here anyway, after one line on stderr. | M2 |
| `--lang CODE` | UI language for this launch: `auto` (default locale), `en`, `pt_BR`. Overrides `settings.language`; an unknown code is ignored and `auto` applies. The language can also be changed live in Settings, and both take effect immediately (the menu behind the settings screen re-labels itself). | M2 |
| `--home DIR` | profile directory (`settings.json`, and from M3 `save.json`) instead of the per-OS default. Always pass it in tests and scripts so nothing writes to the real profile. | M2 (settings) |
| `--reset-save` | start from a fresh profile; the old save and its backup are moved aside as `save.reset-<time>.json` / `save.bak.reset-<time>.json`, never deleted | M3 |
| `--bird ID` | start with the given bird | M4 |
| `--tier ID` | difficulty tier (`normal`, `hard`, `nightmare`) | M4 |
| `--world ID` | start in the given world (`green_fields`, `wind_valley`, `iron_forge`, `storm_sky`, `void`). A launch override, not an unlock: every run of this launch is played there. When the profile owns the world the selection is written too (as the world picker would); when it does not, the profile is left alone, a line on stdout says `--world <id>: not unlocked in this profile ... playing it for this launch only`, and the next launch without the flag is back to the owned selection. An unknown id is reported on stderr and ignored. With `--headless-run` the hash line is computed in that world (without the flag it is the classic configuration, so the published hash is untouched). | M7 |

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

## Android build `[M10]`

The Android port is a second Gradle build under `android/`, driven by the
same wrapper with `-p android`; the root build never configures it and the
desktop tasks above never see it. It compiles a *transformed copy* of
`src/main/java` against the shim packages — the design is in
[`ARCHITECTURE.md`](ARCHITECTURE.md) ("Android port") — so an Android build
never edits the desktop tree.

### Prerequisites

| Requirement | Notes |
| --- | --- |
| JDK 17 | the same toolchain as the desktop build (on the dev machine `~/.gradle/gradle.properties` pins `org.gradle.java.home`; the repository's `gradle.properties` does not) |
| Android SDK: platform `android-36`, build-tools `36.0.0` | `compileSdk 36` under AGP 9.4.0. Install with `sdkmanager --install "platforms;android-36" "build-tools;36.0.0"` (`cmdline-tools/latest/bin/sdkmanager` in the SDK); no emulator, no NDK, no Android Studio |
| `android/local.properties` | one line, `sdk.dir=/path/to/Android/Sdk`; git-ignored, so create it per machine — or export `ANDROID_HOME`, which AGP reads when the file is absent (CI does that) |
| Network, once | the first build resolves AGP, Gson 2.14.0, JUnit 4.13.2 and Robolectric 4.16; the first test run also fetches Robolectric's `android-all-instrumented` jar for SDK 35 into `~/.m2` |

### The three commands

```bash
./gradlew -p android transformSources   # rewrite src/main/java -> android/build/transformed/java, gated
./gradlew -p android assembleRelease    # transform (if stale) + compile + dex + sign -> the APK
./gradlew -p android test               # transform + Robolectric unit tests; no device, no emulator
```

They compose: `./gradlew -p android transformSources assembleRelease test` is
the gate CI runs (`.github/workflows/android.yml`) and the one every M10
change must pass. `assembleRelease` and `test` depend on the transform, so the
first command on its own only matters when you want to inspect the rewritten
tree.

`transformSources` prints its tally —
`318 files transformed, 6 excluded (desktop-only), round-trip OK, 0 surviving refs`
— and fails the build with `INTEGRITY GATE FAILED`, listing the offending
files, when either half of the gate trips: (a) reversing the rules must
reproduce every source byte for byte, (b) no `java.awt.` /
`javax.sound.sampled.` / `javax.imageio.` reference may survive. A renamed
desktop-only file fails the task too (`excluded files missing under ...`), so
the exclusion list in `android/build.gradle` must follow a rename.

### Proving the gate bites

`-PtransformSelfTest=<mode>` deliberately breaks one half and expects the
failure (the default build never sets it):

```bash
./gradlew -p android transformSources -PtransformSelfTest=breakReverse   # gate (a): 1 file does not round-trip
./gradlew -p android transformSources -PtransformSelfTest=breakForward   # gate (b): 1 surviving ref, AssetManager.java:27
```

`breakReverse` appends a comment to the first output file after the rewrite;
`breakForward` drops rule T2, so `import javax.imageio.ImageIO;` survives.
Both must end in `BUILD FAILED` with `INTEGRITY GATE FAILED` — a green run
here is a bug. Any other mode value is rejected. Both modes leave a poisoned
output tree behind on purpose: the failed task is not up to date, so the next
plain build regenerates `build/transformed/java` from scratch. CI runs
`breakForward` on every Android build and fails when it passes.

### Where things land

| Output | Path |
| --- | --- |
| transformed sources | `android/build/transformed/java/` (mirrors `src/main/java` minus the six excluded files) |
| release APK | `android/build/outputs/apk/release/Flapforge-android-release.apk` (about 1.5 MiB; `output-metadata.json` beside it carries `versionName`/`versionCode`). The release asset is this file renamed `Flapforge-<version>-android.apk` |
| JUnit XML | `android/build/test-results/testDebugUnitTest/TEST-*.xml`, one per class (what CI publishes) |
| HTML report | `android/build/reports/tests/testDebugUnitTest/index.html` |

Check what went into the APK with
`unzip -l android/build/outputs/apk/release/Flapforge-android-release.apk`
(40 entries): two dex files (`classes.dex` — the transformed game, the shims,
the host and Gson, 1059 classes; `classes2.dex` — one class),
`AndroidManifest.xml`, the desktop resources at the root (`data/*.json`,
`data/strings/*.json`, `assets/manifest.json`, `assets/fonts/*`,
`version.properties`), the launcher icon under `res/` (fifteen PNGs and two
adaptive-icon XMLs, under the short names AAPT2 assigns) with
`resources.arsc`, and two `META-INF/` build-metadata files. Nothing from the
Kotlin standard library: AGP 9's built-in Kotlin support adds `kotlin-stdlib`
to the runtime classpath of a project without Kotlin sources, and
`android/gradle.properties` (`kotlin.stdlib.default.dependency=false`) keeps
it out — no `kotlin/*` entry, no `Lkotlin/` descriptor in either dex. No
`java.awt`, `javax.sound` or `javax.imageio` descriptor exists in the dex
either (`build-tools/36.0.0/dexdump -d classes.dex | grep -cE
'Ljava/awt|Ljavax/sound|Ljavax/imageio'` prints 0) — what gate (b) guarantees
at the source level. `aapt2 dump badging` on the APK prints the package,
`versionCode`/`versionName`, the `Flapforge` label and the icon resource for
every density.

### Tests and the real profile

The unit tests run under Robolectric 4.16 (`@RunWith(RobolectricTestRunner)`,
`@Config(sdk = 35)`, `@GraphicsMode(NATIVE)` for real Skia rasterisation);
the pure-JVM shim tests need no runner. Two things to know:

- **`~/.flapforge` is off limits, and the suite proves it.** The activity
  tests boot the *real* game — content, settings, profile, fonts, the audio
  warm-up — inside `MainActivity`, which calls
  `SavePaths.override(getFilesDir())` before anything else, unconditionally.
  `DesktopProfileGuard` fingerprints the desktop profile directory before the
  activity is created and again after the game has shut down, and fails on
  any difference. Keep that order: nothing that runs before the override may
  touch `SavePaths`, and no Android test may set `--home` or the
  `flapforge.home` property. After a run, `md5sum ~/.flapforge/*.json` must
  be what it was.
- **Robolectric's `SurfaceView` has no canvas**, so a present on a "created"
  surface is a counted skip in the tests; the presenter's frame body is
  tested through a `BufferedImage`-backed `awt.Graphics2D`. A test that
  expects `presentCount` to grow will never pass under Robolectric.

The results are counted per class in the XML: the M10 suite is 201 tests in
20 classes, 0 failures, and the count is part of every M10 change's gate.

### Launcher icon

The manifest declares `android:icon="@mipmap/ic_launcher"` and
`android:roundIcon="@mipmap/ic_launcher_round"`, and everything under
`android/src/main/res/` derives from the desktop procedural icon —
`render.ProceduralArt.icon(int)`, the same vector `drawIcon` that
`./gradlew iconExport` renders for the desktop bundles — so the launcher icon
cannot drift from the window icon. `android/tools/IconGen.java` writes, per
density bucket (`mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}`, 1x to 4x):
`ic_launcher.png` (the 48 dp legacy icon: the full tile),
`ic_launcher_round.png` (the tile cut to its inscribed disc) and
`ic_launcher_foreground.png` (the adaptive-icon foreground: a transparent
108 dp canvas with the tile scaled to the 66 dp safe zone), plus
`values/colors.xml` with `ic_launcher_background` — the Green Fields sky-top
colour (`WorldPalette.GREEN_FIELDS.skyTop()`, `#4BC4CF`) behind the adaptive
foreground. The two `mipmap-anydpi-v26/*.xml` adaptive-icon definitions are
static and reference those resources; `aapt2 dump badging` on the APK lists
the icon for every density.

The tool needs `java.desktop`, which the Android project cannot see, so it is
not a Gradle task. Run it from the repository root against the compiled
desktop classes (`build/classes/java/main`, which the desktop `classes` task
— part of every desktop build above — produces) whenever
`ProceduralArt.drawIcon` or the palette changes, then commit the output:

```bash
javac -d build/icongen -cp build/classes/java/main android/tools/IconGen.java
java -Djava.awt.headless=true -cp build/icongen:build/classes/java/main IconGen
```

An optional argument names the `res` directory (default
`android/src/main/res`). The output is deterministic: re-running the tool over
the checked-in tree rewrites every file byte for byte, so a diff after a run
shows exactly what an icon or palette change did.

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
`scripts/`, `docs/`, `.github/`, and `android/` `[M10]` (the Android port: its
own Gradle build, the shim packages, the Android host and their Robolectric
tests — see "Android build").

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
| The Android build cannot find the SDK | create `android/local.properties` with `sdk.dir=...`, or export `ANDROID_HOME`; then install `platforms;android-36` and `build-tools;36.0.0` with `sdkmanager` |
| `INTEGRITY GATE FAILED` from a `-PtransformSelfTest=breakReverse` or `breakForward` run, or `android/build/transformed/java` left poisoned by one | expected: the flagged run is the self-test proving the gate, and it leaves the poisoned tree behind; the next plain `./gradlew -p android transformSources` re-executes and rebuilds it. If the gate fails *without* the flag, a rule ate or missed a reference — read the listed files |
| An Android activity test fails in `DesktopProfileGuard` | the boot reached `~/.flapforge`: something ran before `SavePaths.override` in `MainActivity.onCreate`, or a test set `--home` / `flapforge.home`; restore the order, then check `md5sum ~/.flapforge/*.json` |
