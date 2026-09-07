_Language:_ **English** · [[Português (Brasil)|Compilando-e-Contribuindo-(pt-BR)]]

# Building and Contributing

Flapforge is a single Gradle module of plain Java 17 — no game engine, no native code, one
runtime dependency (Gson) — under the MIT licence. This page is the short version of
[`docs/DEVELOPMENT.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/DEVELOPMENT.md)
and [`CONTRIBUTING.md`](https://github.com/michelbr84/Flapforge/blob/main/CONTRIBUTING.md):
enough to build the game, run its tests, add content and open a pull request. If you only want
to play, [[Getting Started]] covers the downloads.

## Prerequisites

| Requirement | Notes |
| --- | --- |
| JDK 17 or newer | any distribution (Temurin, Microsoft, Zulu, the distro package); the build compiles against `--release 17`, so a newer JDK is fine. `java -version` must work in the shell |
| Git | to clone and contribute |
| A desktop session | Linux (X11 or Wayland/XWayland), Windows 10+, macOS 12+; the game is pure AWT/Java2D |
| Nothing else | no Gradle installation: the committed wrapper downloads Gradle 9.7.1 on first use and caches it under `~/.gradle` |

## Bootstrapping

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge
./gradlew build        # first run downloads Gradle 9.7.1 + Gson + JUnit
./gradlew run          # opens the game window
```

`./gradlew --offline build` works as soon as the dependencies are in the local cache. Gradle can
take a few minutes on a cold cache; keep going. On Windows use `gradlew.bat` (or the
`scripts\*.ps1` wrappers).

## Gradle tasks

| Task | Purpose |
| --- | --- |
| `./gradlew build` | compile (`-Xlint:all,-serial -Werror -parameters`, UTF-8, `--release 17`) and run the default test suite; **the gate every pull request must pass** |
| `./gradlew test` | the default suite: pure and headless tests only (`java.awt.headless=true`; the `gui`, `perf` and `sim` tags are excluded) |
| `./gradlew smokeTest` | tests tagged `gui`: a real window, fullscreen toggled twice, Robot-driven menu navigation, the real quit path, screenshots in `build/smoke/`. **Needs a display**; without one the tests are skipped, not failed |
| `./gradlew simTest` | tests tagged `sim`: long bot simulations (content feasibility, the new-player journey, meta-progression) |
| `./gradlew perfTest` | tests tagged `perf`: local performance budgets (not run in CI) |
| `./gradlew contentCheck` | runs the content validator and the string check on the shipped JSON and prints the unlock graph |
| `./gradlew fatJar` | the self-contained `build/libs/flapforge-<version>-all.jar` (Gson bundled) |
| `./gradlew iconExport` | exports the procedural icon as `.png`, `.ico` and `.icns` for `jpackage` |
| `./gradlew run` | starts the game from source; launch flags go through `--args`, e.g. `./gradlew run --args="--seed 42 --scale 2"` |

Wrapper scripts: `scripts/build.sh` / `scripts\build.ps1` run `build fatJar`; `scripts/run.sh` /
`scripts\run.ps1` run `run` and forward every argument to the game; `scripts/package.sh` runs
`fatJar iconExport` and then `jpackage --type app-image` into `build/dist/` (needs the `jpackage`
that ships with JDK 14+). The Android port is a separate Gradle build under `android/`
(`./gradlew -p android assembleDebug`, on a machine with the Android SDK); CI builds it on every
push and the release workflow attaches the APK to each `v*` release.

On Linux the smoke tests need an X server: set `DISPLAY=:0`, or run
`xvfb-run -a ./gradlew smokeTest` as CI does. On Wayland the screenshot capture may come back
black; the test then falls back to an off-screen render and still passes.

## Repository layout

| Path | Contents |
| --- | --- |
| `src/main/java` | the game, package `io.github.michelbr84.flapforge`, in four layers: `app` (window, loop, input bridge), presentation (`render`, `audio`, `ui`, `event`), the pure simulation (`core`, `input`, `gameplay`, `ability`, `modifier`) and the pure meta layer (`content`, `progression`, `persistence`) |
| `src/main/resources` | `data/` (birds, difficulty, economy, upgrades, abilities, modifiers, worlds, patterns, challenges, achievements as JSON), `data/strings/` (`en.json`, the source of truth, and `pt_BR.json`), `assets/` (the manifest and the bundled font), `version.properties` |
| `src/test` | unit, property, simulation, headless render and GUI smoke tests, plus fixtures |
| `src/tools` | the balancing simulator, save inspector, content check, asset validator and icon export |
| `scripts/`, `docs/`, `.github/` | build/run/package wrappers, the engineering docs, CI and release workflows, issue and PR templates |
| `android/` | the Android port's own Gradle build |
| `wiki/` | this wiki (see below) |

Dependencies only point downwards, and the two lower layers never touch AWT, the clock, threads
or global randomness — `ArchitectureTest` fails the build on a violation. The full package tree
is in [`docs/ARCHITECTURE.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/ARCHITECTURE.md).

## Contributing

1. Fork the repository (or create a branch if you have write access).
2. Branch from `main` with a type prefix and a short lowercase, hyphenated description:
   `feat/`, `fix/`, `docs/`, `refactor/`, `test/`, `chore/` or `content/` (JSON content and
   balancing) — for example `feat/wind-valley-gusts` or `fix/pause-on-focus-loss`.
3. Make focused commits following [Conventional Commits](https://www.conventionalcommits.org/):
   `<type>(<scope>): <short imperative summary>`, with `type` one of `feat`, `fix`, `docs`,
   `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `content`; the summary is English,
   imperative, without a trailing full stop and within 72 characters.
4. Run `./gradlew build` locally; it must be green with zero compiler warnings (`-Werror`).
5. Run `./gradlew smokeTest` when you touched anything under `app`, `render`, `audio` or `ui`.
6. Open a pull request against `main` and fill in the template.

The pull request checklist:

- [ ] `./gradlew build` passes locally with no warnings.
- [ ] `./gradlew smokeTest` passes if presentation code changed.
- [ ] New or changed behaviour is covered by tests.
- [ ] Docs (`README.md`, `docs/*.md`, `CHANGELOG.md` under `[Unreleased]`) are updated.
- [ ] Commits follow Conventional Commits and the branch follows the naming scheme.
- [ ] No new third-party asset or dependency without a licence note.

Keep pull requests small and single-purpose, and open an issue first for substantial gameplay or
architecture changes. The coding rules in short: Java 17 without preview features, warnings are
errors, no Swing, the pure packages never import AWT or read the clock, all randomness through
seeded streams, content as data rather than code, English everywhere (player-facing text goes
through the string tables), tests accompany behaviour, and no inherited or unlicensed assets.

## Content is JSON

Birds, upgrades, abilities, modifiers, worlds, patterns, challenges, achievements and the UI
strings are JSON files under `src/main/resources/data`, validated at start-up. Unknown keys are
errors. Many additions need no Java at all; the recipes are in
[`docs/CONTENT.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/CONTENT.md) §3
("How to add things"):

- **A bird** — append an entry to `birds.json` (every shipped bird uses the hitbox
  `{w: 33, h: 31, ox: -17, oy: -12}`; `baseStats` lists only what differs from the defaults),
  give it a `default` palette and a `prestige` palette, an `unlock` with a cumulative way in
  (`any_of[<something skilful>, {"type": "purchase", "amount": N}]`), and add
  `bird.<id>.name` / `.desc` and `cosmetic.<id>.<palette>.name` / `.desc` to **both** string
  files.
- **A world** — one entry of `worlds.json`: `id` and `order`, a difficulty `curve`, a parallax
  `style` (`hills`, `canyon`, `factory`, `storm`, `void`), the `unlock` (every world but the first
  is `any_of[world_cleared <previous>, purchase N]`), a seven-colour `palette`, `effects` and
  `flags`, the `spawnWeights` table, `patterns`, `ambient` (darkness, wind, lightning), optional
  `ruleCycles`, the `boss` block with its reward, and `music` / `sfxSet`. See "A world (M7)".
- **A challenge** — one entry of `challenges.json`: `world`, `tier` and `curve` (the world is a
  place, never a requirement), `allowOffers`, `flags` and `effects`, `forcedModifiers`, an
  optional `forcedPattern` and its own `boss`, the `objective` (`SURVIVE_GATES`, `SURVIVE_TICKS`,
  `COLLECT_COINS`, `REACH_POINTS` or `BOSS_CLEARED`), the first-clear `rewards` and the `unlock`.
  Then measure it with the bot:
  `./gradlew balancing -PtoolArgs="--challenge my_challenge_1 --skill expert --seeds 50"`.
  See "A challenge (M8)" and [[Challenges and Goals]].

Whatever you add, finish with `./gradlew contentCheck` (`-PtoolArgs="--quiet"` skips the graph
print-out). It loads the files exactly as a player's install does, runs the validator and the
string check, prints the unlock graph and the cheapest path to every unlockable, and fails on
any error. A failure names the file, a JSON pointer and the rule, for example
`upgrades.json#/nodes/11/prereqs/0: unknown upgrade node 'scholar_2'` — the twelfth entry of
`nodes`, its first `prereqs` entry. Every content id needs `<kind>.<id>.name` and `.desc` in
both `en.json` and `pt_BR.json`; the two files must carry exactly the same keys.

## This wiki

The wiki is not edited on GitHub: its sources are the `wiki/` directory on `main`, reviewed like
any other change, and `.github/workflows/wiki.yml` publishes them to the GitHub wiki on every
push that touches `wiki/`. A pull request that changes `wiki/` runs `scripts/check-wiki.sh`,
which verifies the double-bracket page links, the images under `wiki/images/`, the
language-switch line at the top of every page and the sidebar. **Never edit a page in the web
UI** — the next sync overwrites it. Every page exists in English and in Brazilian Portuguese
(`…-(pt-BR).md`).

## Further reading

| Document | Contents |
| --- | --- |
| [`docs/README.md`](https://github.com/michelbr84/Flapforge/blob/main/docs/README.md) | the index of the engineering and design documentation |
| [`CONTRIBUTING.md`](https://github.com/michelbr84/Flapforge/blob/main/CONTRIBUTING.md) | the full workflow, coding rules and checklist |
| [`CODE_OF_CONDUCT.md`](https://github.com/michelbr84/Flapforge/blob/main/CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |
| [`SECURITY.md`](https://github.com/michelbr84/Flapforge/blob/main/SECURITY.md) | scope and how to report a vulnerability |
| [`CHANGELOG.md`](https://github.com/michelbr84/Flapforge/blob/main/CHANGELOG.md) | every release, plus the inherited upstream history |
