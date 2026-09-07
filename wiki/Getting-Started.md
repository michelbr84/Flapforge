_Language:_ **English** · [[Português (Brasil)|Primeiros-Passos-(pt-BR)]]

# Getting Started

Flapforge is a skill-based arcade roguelite: flap, dodge, survive, score — and keep the coins,
XP, birds and upgrades you earn from one run to the next. This page covers what the game needs,
the ways to run it, the launch flags, where it keeps your settings and save, and what to expect
the first time it opens. How to play once you are in is on [[Controls]] and [[Playing a Run]].

## Requirements

| Need | Details |
| --- | --- |
| Java | **JDK 17 or newer** — any distribution (Temurin, Microsoft, Zulu, your distro's `openjdk-17-jdk`, ...). The project is compiled with `--release 17`, so a newer JDK is fine. The fat jar needs only a JRE/JDK 17+; the packaged app images are self-contained. |
| Gradle | **Nothing to install.** The repository ships the Gradle wrapper (`gradlew` / `gradlew.bat`), which downloads Gradle 9.7.1 on first use. Only needed when building from source. |
| Desktop | Linux (X11 or Wayland), Windows 10+, or macOS 12+. Flapforge is a plain AWT/Java2D application: no OpenGL, no native libraries, no game engine. |
| Android | A phone or tablet on Android 13 or newer (the APK's minimum is API 33). |
| Git | Only if you clone the source repository. |

Check the Java on your `PATH` before anything else:

```bash
java -version
```

## Ways to run

### Release downloads

Every tagged release on
[github.com/michelbr84/Flapforge/releases](https://github.com/michelbr84/Flapforge/releases)
carries the same set of files, built and tested by CI on all three desktop operating systems:

| File | What it is | How to run it |
| --- | --- | --- |
| `Flapforge-<version>-linux.zip` | self-contained app image (`Flapforge/`) | unzip and start the `Flapforge` launcher inside |
| `Flapforge-<version>-macos.zip` | self-contained app image (`Flapforge.app`) | unzip and open `Flapforge.app` |
| `Flapforge-<version>-windows.zip` | self-contained app image (`Flapforge/`) | unzip and start the `Flapforge` launcher inside |
| `flapforge-<version>-all.jar` | the fat jar (bundles Gson) | `java -jar flapforge-<version>-all.jar` — needs a JRE/JDK 17+ |
| `Flapforge-<version>-android.apk` | the Android build, for sideloading | copy it to the device, allow installs from that source, open it |

The app images carry their platform's icon (`.png` / `.icns` / `.ico`). Launch flags go after
the jar name: `java -jar flapforge-<version>-all.jar --fullscreen --no-audio`.

### From source

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge
./gradlew run                                   # Windows: gradlew.bat run
./gradlew run --args="--seed 42 --scale 2"      # launch flags go through --args
```

The first run downloads Gradle and the two dependencies (Gson, JUnit); after that
`./gradlew --offline build` works without network. `./gradlew build` compiles with every lint
warning treated as an error and runs the default test suite — see
[[Building and Contributing]] for the other Gradle tasks.

### Fat jar and app image

```bash
./gradlew fatJar
java -jar build/libs/flapforge-0.2.0-all.jar
java -jar build/libs/flapforge-0.2.0-all.jar --fullscreen --no-audio
java -jar build/libs/flapforge-0.2.0-all.jar --lang pt_BR
```

`scripts/package.sh` (Linux/macOS, or Git Bash on Windows) builds the fat jar, exports the
procedurally drawn icon in three formats and runs `jpackage` to write a self-contained app image
to `build/dist/` — `Flapforge/` on Linux, `Flapforge.app` on macOS, `Flapforge/` on Windows.
`jpackage` ships with JDK 14+; the script exits with a clear message when it cannot find one.

### Scripts

| Script | Does |
| --- | --- |
| `scripts/run.sh` / `scripts\run.ps1` | starts the game through Gradle and forwards every argument to it |
| `scripts/build.sh` / `scripts\build.ps1` | runs `build fatJar` |
| `scripts/package.sh` | produces the app image described above |

```bash
scripts/run.sh --seed 42 --world wind_valley --bird zephyr   # --world needs an owned world
```

## Launch flags

Flags are parsed before anything else starts. Pass them through `--args="..."`, the run scripts,
or directly to the fat jar. An unknown flag or a malformed value prints the message and the usage
text and the game does not start; without a display a windowed launch prints
`No display available; use --headless-run N or --no-window.`

| Flag | Meaning |
| --- | --- |
| `--seed N` | fixed RNG seed for a reproducible run |
| `--world ID` | select a world for this launch (`green_fields`, `wind_valley`, `iron_forge`, `storm_sky`, `void`). Not an unlock: an owned world is written to the profile's selection, which the hub's plaque and START RUN then play; a locked one is refused with a line on stdout and the selection stays as it was. An unknown id is reported and ignored. |
| `--bird ID` | start with the given bird |
| `--tier ID` | difficulty tier (`normal`, `hard`, `nightmare`) |
| `--scale N` | initial window scale, an integer multiple of the 420×640 playfield. Default: the largest scale whose window fits the screen — 2× on a 1440-class display, 1× on 1080p |
| `--fullscreen` | start in borderless fullscreen (`F11` toggles) |
| `--no-audio` | start silent: no sound device is opened at all; the game plays exactly the same |
| `--home DIR` | use `DIR` instead of the default settings/save directory (see below) |
| `--headless-run N` | simulate `N` frames without a window and print a summary line plus the determinism hash CI compares across platforms |
| `--no-window` | run without a window |
| `--help`, `-h` | print the usage text and exit |
| `--reset-save` | start from a fresh profile; the old save and its backup are moved aside, never deleted (see below) |
| `--lang CODE` | UI language for this launch: `auto` (system locale), `en`, `pt_BR`. Overrides the saved language setting; an unknown code is ignored and `auto` applies |

> **Tip:** `--lang pt_BR` switches the whole interface to Brazilian Portuguese for that launch.
> The language can also be changed live in Settings, and both take effect immediately.

## Where settings and saves live

The profile directory is resolved in this order: `--home DIR`, then the `flapforge.home` system
property, then the `FLAPFORGE_HOME` environment variable, then the per-OS default:

| OS | Profile directory |
| --- | --- |
| Linux / BSD | `~/.flapforge` |
| Windows | `%APPDATA%\Flapforge` (falls back to `~/AppData/Roaming/Flapforge`) |
| macOS | `~/Library/Application Support/Flapforge` |

Nothing creates the directory until something is written into it. It holds:

| File | Purpose |
| --- | --- |
| `settings.json` | options (language, volumes, key bindings, display, accessibility) — not progress |
| `save.json` | the profile: coins, XP, unlocks, upgrades, statistics, records |
| `save.json.bak` | yesterday's profile: written once per session, right after a successful load |
| `backups/save.v<N>.pre-migration.json` | the save as it was before a schema migration from version `N` moved it |
| `save.corrupt-<time>.json` | a save that could not be read, moved aside by a failed load |
| `save.reset-<time>.json` | the save you asked `--reset-save` to abandon |

Every write is crash-safe (temp file, fsync, atomic rename), and the one rule everything follows
is that **your progress is never destroyed by the game**: a crash mid-write, a corrupt file, a
downgrade or a bug in a migration moves a file aside; nothing deletes one.

`--reset-save` starts a fresh profile and moves the old save and its backup aside as
`save.reset-<time>.json` and `save.bak.reset-<time>.json`. If you ever want the old progress
back, quit, rename the reset file to `save.json` and start again.

A `settings.json` written by a different version of the game is not loaded: the defaults are
restored, the old file is kept as `settings.v<N>.json` and a toast says so.

## Your first launch

1. **Boot.** A short splash while the content files are validated and the bundled font is
   installed.
2. **The home hub.** Your player card (avatar, level, XP), the coin chip, the settings gear, the
   world plaque, the forge scene, the **Next unlock** card, the gold **START RUN** button showing
   the world and difficulty it will play, and the bottom navigation — **Shop**, **Birds**,
   **Play**, **Forge**, **Goals**. Until you have flown once it reads "No run yet — press START
   RUN". The tour is on [[Home Hub]].
3. **START RUN.** A fresh profile flies Forgewing with Double Flap in Green Fields on Normal.
   Flap with `Space`, `Up arrow` or a left click; the first flap starts the run. Even a run that
   ends at gate 0 pays about 50 coins, enough to start on the [[Shop and Upgrades]]. The whole
   loop — gates, drafts, bosses, the game-over strip and the summary — is on [[Playing a Run]].

`Esc` on the hub asks "Press again to quit"; idle there for twenty seconds and a demo run plays
behind it until you press anything.
