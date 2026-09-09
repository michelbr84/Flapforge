# CLAUDE.md

Flapforge is a Flappy-Bird-like roguelite: a pure AWT/Java2D desktop game with an Android
port. 60 Hz fixed-step simulation, deterministic seeded runs, data-driven content under
`src/main/resources/data`, procedural art (no image assets).

**Read [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) before your first change** — it is the
authoritative build/test/flag reference. This file is the short list of rules that break a
build or a release when broken.

## Build and verify

```bash
./gradlew --offline build          # THE gate: compile (-Werror) + default test suite
./gradlew --offline test           # headless: unit, property, render (gui/perf/sim excluded)
./gradlew --offline contentCheck   # content validator on the shipped JSON
DISPLAY=:0 ./gradlew smokeTest     # tagged gui: real window + Robot; needs an idle X session
./gradlew --offline simTest        # tagged sim: long bot simulations
./gradlew --offline fatJar         # build/libs/flapforge-<version>-all.jar
```

Every change must pass `build` and `contentCheck`. A milestone or PR is not done until
both are green.

## Non-negotiable rules

- **Java 17** (`--release 17`), `-Xlint:all,-serial -Werror -parameters`. Every lint
  category except `serial` is an error. **No pattern-matching `switch`, no record
  patterns** (both need 21). Records, `switch` expressions, text blocks and `var` are fine.
  No `serialVersionUID` is needed anywhere.
- **No Swing.** `java.awt.Frame` + `Canvas` + `BufferStrategy`; the UI is drawn by the
  game. UI components live in `ui/component/`.
- **Purity.** `core`, `input`, `gameplay.*`, `ability`, `modifier`, `content`,
  `progression`, `persistence` must not reference `java.awt`, `javax.*`, `sun.*`,
  `Math.random`, `System.currentTimeMillis`, `System.nanoTime`, unseeded `new Random(`,
  `Thread.`, `Executors.`, or `Math.(sin|cos|tan|atan|atan2|asin|acos|sinh|cosh|tanh|exp|expm1|pow|log|log10|log1p|cbrt)`.
  Allowed: `sqrt/floor/ceil/round/abs/min/max/hypot/fma`. Time is a `core.TimeSource`;
  executors come from `app.Threads`. Oscillators use triangle waves or lookup tables.
  `ArchitectureTest` enforces this.
- **Determinism.** All randomness through `core.RandomProvider` named streams.
- **No literal the player can read.** Every string goes through `content.StringKey` plus
  `data/strings/en.json` **and** `data/strings/pt_BR.json`. The two files must carry
  *exactly* the same key set — `StringsTest` fails the build on drift. `en.json` is the
  source of truth. Fetch with `strings.get(...)` / `strings.format(...)`. A screen that
  caches rendered text must re-render when `strings.language()` changes.
- **Content is JSON.** Unknown keys are errors, so adding a field to
  `src/main/resources/data/*.json` means updating the matching `content.defs` record in the
  same change.
- **Never write to the real profile directory.** Tests use `@TempDir` plus
  `SavePaths.override(...)` and always pass `--home <dir>`; smoke tests write under
  `build/smoke/`. `ls ~/.flapforge` after a full build must still say it does not exist on
  a machine that has never run the game. A subagent once overwrote the real save and the
  original bytes were unrecoverable — treat this as a hard rule.
- **English** in code, comments, docs and commits. Only the string tables are localized
  (`en`, `pt_BR`). Conventional Commits; milestone commits are `feat(M#): ...`.
- **Android has no runtime record reflection.** D8 desugars records below API 34 and ART
  returns fields in dex order, so `java.lang.Record` reflection crashes on device while
  compiling against `android.jar` and passing under Robolectric. Verify every Android
  change on an emulator, not just in Robolectric.

## Release invariants

- `--headless-run 3000 --seed 42` must keep printing `hash=eaaa01685261a433` — the pinned
  classic configuration (classic bird, Green Fields, normal tier, no abilities, no drafts,
  no boss). CI re-verifies it cross-platform; a release must not move it.
- Tag `v0.1.0` **froze save v1**. Any change to the persisted shape ships a migration.
- The version lives in `src/main/resources/version.properties`. A release needs its
  `CHANGELOG.md` section before the tag is cut.

## Known traps

- `smokeTest` is **environmentally flaky**: on a busy desktop the Robot's clicks are stolen
  and Xvfb drops synthetic input under load. Rerun isolated, or under
  `Xephyr :7 -screen 1280x1024 -ac -noreset &` with `DISPLAY=:7`, before blaming the code.
  Never run the Android Gradle build concurrently with the desktop battery.
- Desktop mouse-wheel direction is inverted relative to browsers and Swing
  (`AwtInputBridge` forwards `getWheelRotation()`, screens do `scroll -= wheel * WHEEL_STEP`).
  It is a known, deliberately unfixed issue: flipping the sign also requires flipping
  `AndroidInputBridge.move()` and its drag test.
- Changing any physics, price or reward number invalidates `docs/BALANCING.md`
  measurements. Re-measure with `./gradlew balancing -PtoolArgs="..."` in the same change.
- **Never assert a measured text width against a literal.** Until a font is installed the
  family is the logical `SansSerif`, so `TextPainter.width` is the runner's font, not yours:
  CI's is ~a sixth wider and a test built on "this string is N px here" passed locally and
  failed on `ubuntu-latest`. Assert the geometry (rooms, edges, monotonicity) or run the same
  assertion at `Fonts.setTextScale(Fonts.MAX_TEXT_SCALE)`, which is the same problem.
- **The Android shim has no `AffineTransform`** (`android/src/main/java/awt/`), so no
  `getTransform`/`setTransform`. A nested transform goes on `g.create()` and is thrown away
  with `dispose()`: undoing a `scale` with its inverse leaves a rounding residue in the
  matrix, and a context that is no longer exactly axis-aligned costs every later draw a
  transformed path — the Forge frame went from 27 KB to 131 KB of allocation that way.

## Docs

| File | What it covers |
| --- | --- |
| `docs/ARCHITECTURE.md` | full package tree and module boundaries |
| `docs/DEVELOPMENT.md` | build, tasks, launch flags, coding rules |
| `docs/PROGRESSION.md` | coins, XP, unlocks, upgrade nodes, prestige |
| `docs/SAVE_SYSTEM.md` | save schema, migrations, crash-safe writes |
| `docs/CONTENT.md` | the JSON content model |
| `docs/BALANCING.md` | measured balance tables |
| `docs/ROADMAP.md` | milestones and future work |

## Agent team

A standing 12-agent team lives in [`.claude/agents/`](.claude/agents/) (`ff-*` prefix).
Use it for multi-part features.

- **The lead has no vision.** Never interpret a screenshot, mockup or render in the main
  session. Any feature carrying images starts with `ff-vision`, which writes the canonical
  spec; nothing else starts until that spec exists.
- **Delegate only to `ff-*` agents.** The built-in agent types (`Explore`,
  `general-purpose`, `Plan`) carry no pinned model and fall back to the default subagent
  model, which silently defeats the team's cost strategy.
- Agents added to `.claude/agents/` are **not discovered by a running session** — restart
  (or reload `/agents`) before the first use.
