# Contributing to Flapforge

Thanks for taking the time to contribute. Flapforge is a small, opinionated
Java desktop game; this page explains how the repository is organised, how to
build and test it, and the rules every change must follow. The detailed
engineering notes live in [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Table of contents

- [Ways to contribute](#ways-to-contribute)
- [Workflow](#workflow)
- [Branch naming](#branch-naming)
- [Commit messages](#commit-messages)
- [Building and testing](#building-and-testing)
- [Coding rules](#coding-rules)
- [Pull request checklist](#pull-request-checklist)
- [Reporting bugs and proposing features](#reporting-bugs-and-proposing-features)

## Ways to contribute

- Gameplay mechanics, obstacle patterns, worlds and challenges (content is
  data-driven JSON; many additions need no Java at all).
- Balancing: run the simulation tools, report the numbers, propose data changes.
- Bug fixes, performance work, accessibility, tests and documentation.
- Original artwork, sound effects and music, delivered through the asset
  manifest (see `THIRD_PARTY_NOTICES.md` for the licensing expectations).

For substantial gameplay or architecture changes, open an issue first so the
design can be discussed before code is written.

## Workflow

1. Fork the repository (or create a branch if you have write access).
2. Create a branch from `main` (see [Branch naming](#branch-naming)).
3. Make focused commits (see [Commit messages](#commit-messages)).
4. Run `./gradlew build` locally; it must be green with zero compiler
   warnings (the build uses `-Werror`).
5. Run `./gradlew smokeTest` when you touched anything under `app`, `render`,
   `audio` or `ui` (needs a display; see below).
6. Open a pull request against `main` and fill in the template.

Keep pull requests small and single-purpose. A PR that mixes a refactor with a
behaviour change is much harder to review than two PRs.

## Branch naming

Use a short, lowercase, hyphen-separated description with a type prefix:

| Prefix | Use for |
| --- | --- |
| `feat/` | new functionality (`feat/wind-valley-gusts`) |
| `fix/` | bug fixes (`fix/pause-on-focus-loss`) |
| `docs/` | documentation only |
| `refactor/` | behaviour-preserving restructuring |
| `test/` | tests only |
| `chore/` | build, CI, tooling, dependency updates |
| `content/` | JSON content and balancing changes |

The rewrite itself lives on `rewrite/flapforge` until it is merged.

## Commit messages

Flapforge uses [Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<scope>): <short imperative summary>

<optional body: what and why, not how>

<optional footer: BREAKING CHANGE:, Closes #123, Co-Authored-By: ...>
```

- `type` is one of `feat`, `fix`, `docs`, `refactor`, `perf`, `test`,
  `build`, `ci`, `chore`, `content`.
- `scope` is optional; milestone commits use the milestone id
  (`feat(M1): classic core`), other commits use a package or area
  (`fix(input): drop synthetic auto-repeat edges`).
- The summary is written in English, in the imperative mood, without a
  trailing full stop, and fits in 72 characters.

## Building and testing

Prerequisites: JDK 17 or newer on your `PATH`. No Gradle installation is
required; the wrapper downloads (or reuses) Gradle 9.7.1.

| Command | What it does |
| --- | --- |
| `./gradlew build` | compile with `-Xlint:all -Werror`, run the default test suite |
| `./gradlew run` | start the game from source (`--args="--seed 42"` passes flags) |
| `./gradlew test` | default suite only (pure/headless tests; excludes `gui`, `perf`, `sim` tags) |
| `./gradlew smokeTest` | real-window GUI smoke tests (`@Tag("gui")`), needs a display |
| `./gradlew perfTest` | local performance budgets (`@Tag("perf")`) |
| `./gradlew simTest` | long simulation/balancing tests (`@Tag("sim")`) |
| `./gradlew fatJar` | self-contained `build/libs/flapforge-<version>-all.jar` |
| `scripts/build.sh` / `scripts\build.ps1` | `build fatJar` in one go |
| `scripts/run.sh` / `scripts\run.ps1` | `run` with all flags passed through |

On Linux the smoke tests need an X server: run them with `DISPLAY=:0` set, or
under `xvfb-run -a ./gradlew smokeTest` (this is what CI does). On Wayland
sessions the screenshot capture may come back black; the test then falls back
to an off-screen render and still passes.

Gradle commands can take a few minutes on a cold cache; `./gradlew --offline`
works once the dependencies have been downloaded.

## Coding rules

These rules are enforced by the compiler, by `ArchitectureTest`, or by review.
A PR that breaks any of them will not be merged.

1. **Java 17, no preview features.** No pattern-matching `switch`, no record
   patterns, no `sealed` tricks that need a newer `--release`. Use
   `instanceof` patterns and plain `switch` expressions.
2. **Warnings are errors.** The compiler runs with
   `-Xlint:all,-serial -Werror -parameters`. Fix the warning; do not suppress
   it unless there is genuinely no alternative, and then explain why in a
   comment next to the `@SuppressWarnings`.
3. **No Swing. Pure AWT/Java2D.** The window is a `java.awt.Frame` with a
   `Canvas` and a `BufferStrategy`; the UI is drawn by the game. `javax.swing`
   must not appear anywhere in the code base.
4. **Pure packages stay pure.** `core`, `input`, `gameplay.*`, `ability`,
   `modifier`, `content`, `progression` and `persistence` must not import
   `java.awt`, `javax.*` or `sun.*`, must not use `Math.random`,
   `System.currentTimeMillis`, `System.nanoTime`, `new Random()` without a
   seed, `Thread.`, `Executors.`, or the transcendental `Math` functions
   (`sin`, `cos`, `tan`, `atan`, `atan2`, `asin`, `acos`, `sinh`, `cosh`,
   `tanh`, `exp`, `expm1`, `pow`, `log`, `log10`, `log1p`, `cbrt`);
   `sqrt`, `floor`, `ceil`, `round`, `abs`, `min`, `max`, `hypot` and `fma`
   are fine. `gameplay` and `progression` never import `event`. Time comes
   from an injected `core.TimeSource`; threads and executors come from
   `app.Threads`. `ArchitectureTest` greps the sources and fails the build on
   violations.
5. **Determinism.** Everything that affects a run must be reproducible from
   the seed and the input trace. Randomness goes through `RandomProvider`
   streams; never through ad-hoc `Random` instances.
6. **Data over code.** Birds, upgrades, abilities, modifiers, worlds,
   patterns, challenges and achievements are JSON under
   `src/main/resources/data`. Add content there and let the validator check
   it; add Java only for genuinely new behaviour.
7. **English everywhere.** Code, identifiers, comments, Javadoc, commit
   messages, docs and issues are written in English. Player-facing text goes
   through the string tables (`en.json` is the source of truth; `pt_BR.json`
   overlays it).
8. **Tests accompany behaviour.** New gameplay or progression logic comes
   with a unit test; new content comes with validator coverage; new window
   plumbing comes with a smoke test.
9. **No inherited assets.** Do not add images, audio or fonts from the
   upstream project or from unknown sources. Everything shipped must be either
   procedurally generated or listed with its licence in
   `THIRD_PARTY_NOTICES.md` and the asset manifest.

## Pull request checklist

- [ ] `./gradlew build` passes locally with no warnings.
- [ ] `./gradlew smokeTest` passes if presentation code changed.
- [ ] New or changed behaviour is covered by tests.
- [ ] Docs (`README.md`, `docs/*.md`, `CHANGELOG.md` under `[Unreleased]`) are updated.
- [ ] Commits follow Conventional Commits and the branch follows the naming scheme.
- [ ] No new third-party asset or dependency without a licence note.

## Reporting bugs and proposing features

Use the issue templates under `.github/ISSUE_TEMPLATE`. For bugs, include the
OS, the JDK (`java -version`), how you launched the game, the seed if shown on
the debug overlay (`F3`), and the exact steps. For security-sensitive reports
follow [`SECURITY.md`](SECURITY.md).

By contributing you agree that your contributions are licensed under the MIT
licence in [`LICENSE`](LICENSE).
