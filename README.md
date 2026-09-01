# Flapforge

> **A skill-based arcade roguelite where every flight makes the next one stronger.**

[![Build](https://github.com/michelbr84/Flapforge/actions/workflows/build.yml/badge.svg)](https://github.com/michelbr84/Flapforge/actions/workflows/build.yml)
[![Tests](https://github.com/michelbr84/Flapforge/actions/workflows/test.yml/badge.svg)](https://github.com/michelbr84/Flapforge/actions/workflows/test.yml)
[![Java](https://img.shields.io/badge/Java-17%2B-orange?logo=openjdk&logoColor=white)](https://adoptium.net/)
[![Gradle](https://img.shields.io/badge/Gradle-9.7-02303A?logo=gradle&logoColor=white)](https://gradle.org/)
![Genre](https://img.shields.io/badge/Genre-Arcade%20Roguelite-blueviolet)
![Meta Progression](https://img.shields.io/badge/Progression-Persistent-success)
![Status](https://img.shields.io/badge/Status-In%20Development%20(rewrite)-yellow)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

**Flapforge** reimagines the classic Flappy Bird formula as a **Skill-Based Arcade Roguelite with Persistent Meta-Progression**.

The core mechanic remains deliberately simple:

**Flap. Dodge. Survive. Score.**

But dying is no longer the complete end of the journey.

Every run can earn resources, unlock new birds, abilities, modifiers, challenges, environments, and permanent upgrades that expand what becomes possible in future runs.

The result is a game that preserves the instant accessibility and mechanical precision of Flappy Bird while introducing a long-term progression loop inspired by modern roguelites.

---

## Table of Contents

- [About](#about)
- [Genre](#genre)
- [Core Concept](#core-concept)
- [Gameplay Loop](#gameplay-loop)
- [Meta-Progression](#meta-progression)
- [Example Progression](#example-progression)
- [Features](#features)
- [Birds and Builds](#birds-and-builds)
- [Upgrades](#upgrades)
- [Challenges and Boss Runs](#challenges-and-boss-runs)
- [World Progression](#world-progression)
- [Difficulty](#difficulty)
- [Controls](#controls)
- [Getting Started](#getting-started)
- [Running the Game](#running-the-game)
- [Technology](#technology)
- [Project Structure](#project-structure)
- [Design Philosophy](#design-philosophy)
- [Roadmap](#roadmap)
- [Original Project](#original-project)
- [Assets and Third-Party Content](#assets-and-third-party-content)
- [Contributing](#contributing)
- [License](#license)
- [Disclaimer](#disclaimer)
- [Acknowledgments](#acknowledgments)

---

## About

Traditional Flappy Bird is almost entirely based on immediate player skill.

You start.

You flap.

You avoid pipes.

You score.

You crash.

You start again.

**Flapforge keeps that loop but gives every run a larger purpose.**

A failed run may still provide:

- Coins.
- Experience.
- Upgrade materials.
- New birds.
- Passive abilities.
- New modifiers.
- New environments.
- Challenge access.
- Upgrade-tree progression.
- Permanent account progression.

The player becomes better at the game while the game itself gradually becomes deeper.

That distinction is at the center of Flapforge.

---

## Genre

> **Skill-Based Arcade Roguelite with Persistent Meta-Progression**

Flapforge is intentionally described as an **Arcade Roguelite**, rather than a traditional roguelike.

The game combines three major ideas:

### Arcade

Runs are:

- Fast.
- Immediate.
- Score-driven.
- Mechanically simple.
- Primarily based on player skill.

### Roguelite

Each run can introduce:

- Different upgrades.
- Different modifiers.
- Different strategies.
- Increasing difficulty.
- Special challenges.
- Meaningful risk and reward.

### Meta-Progression

Some progress survives death.

Players gradually unlock permanent content that changes future runs and expands the game's possibilities.

---

## Core Concept

The original Flappy Bird question is:

> **How far can you fly?**

Flapforge adds another:

> **What will this run unlock for the next one?**

A single input still controls the game's fundamental movement.

The complexity comes from everything surrounding that mechanic.

Players must decide how to combine skill, unlocked abilities, birds, upgrades, temporary run bonuses, and long-term progression to push farther into the game.

---

## Gameplay Loop

```mermaid
flowchart LR
    A[Start Run] --> B[Fly]
    B --> C[Dodge Obstacles]
    C --> D[Gain Score]
    D --> E[Collect Rewards]
    E --> F{Survive?}

    F -- Yes --> B
    F -- No --> G[Run Ends]

    G --> H[Receive Coins / XP]
    H --> I[Unlock or Upgrade]
    I --> J[Choose Next Build]
    J --> A
```

At its simplest:

```text
Play
  ↓
Survive
  ↓
Score
  ↓
Earn
  ↓
Die
  ↓
Upgrade
  ↓
Unlock
  ↓
Play Again
```

Each cycle should make the player feel that the next run has the potential to be different from the last.

---

## Meta-Progression

The most important difference between Flapforge and a traditional Flappy Bird game is **persistent progression**.

A run may end, but the player's overall journey continues.

Persistent progression can include:

| System | Description |
| --- | --- |
| **Currency** | Earn resources during runs and spend them between games |
| **Birds** | Unlock playable birds with different characteristics |
| **Abilities** | Unlock new mechanics and special actions |
| **Upgrade Trees** | Permanently improve specific aspects of future runs |
| **Worlds** | Unlock new environments and obstacle sets |
| **Challenges** | Access special runs with unique rules |
| **Milestones** | Reward long-term achievements |
| **Difficulty Tiers** | Open harder variants with better rewards |
| **Modifiers** | Change the rules of future runs |
| **Collections** | Give players long-term completion goals |

Progress should increase **possibilities**, not simply remove the need for skill.

A skilled new player should still be capable of impressive runs.

A progressed player should have more options for approaching those runs.

---

## Example Progression

A simplified early-game progression could look like this:

| Run | Progression |
| --- | --- |
| **Run 1** | Classic flight → earn 50 coins |
| **Run 2** | Purchase a movement upgrade |
| **Run 3** | Unlock a bird with a special ability |
| **Run 5** | Unlock a shield |
| **Run 7** | Gain access to run modifiers |
| **Run 10** | Unlock a new environment and obstacle family |
| **Challenge** | Complete a special objective |
| **Boss Run** | Defeat a major challenge |
| **Reward** | Unlock a new upgrade tree |

This progression is not intended to make every run easier.

Instead, it continuously introduces new decisions.

---

## Features

### Classic skill-based flight

The heart of the game remains familiar:

- Gravity constantly pulls the bird downward.
- Pressing the flap control generates upward movement.
- Obstacles move toward the player.
- Passing obstacles increases the score.
- Collision ends the run.

The control scheme remains intentionally minimal.

### Persistent progression

Earn resources during runs and use them to expand future possibilities.

### Unlockable birds

Different birds can support different play styles.

Examples:

- Balanced bird.
- Lightweight bird.
- Heavy bird.
- Defensive bird.
- High-risk bird.
- Economy-focused bird.
- Ability-focused bird.

### Abilities

Possible abilities include:

- Double Flap.
- Shield.
- Dash.
- Slow Time.
- Emergency Recovery.
- Coin Magnet.
- Score Multiplier.
- Temporary Invulnerability.

Abilities must be balanced carefully so that precision remains the foundation of the game.

### Upgrade trees

Progress through permanent upgrade paths that allow players to specialize their account.

### Dynamic obstacles

The Java project that inspired Flapforge already expands on basic Flappy Bird mechanics with moving and floating pipe behavior.

Flapforge uses dynamic obstacle design as a foundation for increasingly complex environments.

### Increasing difficulty

Runs become progressively harder through combinations of:

- Faster scrolling.
- Smaller openings.
- Moving obstacles.
- Changing obstacle patterns.
- Environmental hazards.
- Run modifiers.
- Challenge-specific rules.

### Challenges

Special runs can change normal gameplay conditions and reward mastery.

### Boss encounters

Bosses do not need to behave like traditional action-game enemies.

In Flapforge, a boss can instead be a carefully designed sequence of mechanics.

For example:

```text
Normal Run
   ↓
Warning Zone
   ↓
Boss Challenge
   ↓
Complex Obstacle Pattern
   ↓
Survive for 30 Seconds
   ↓
Permanent Unlock
```

---

## Birds and Builds

Birds should be more than cosmetic skins.

Each bird can represent a different approach to the game.

Example:

| Bird Archetype | Strength | Weakness |
| --- | --- | --- |
| **Balanced** | Predictable movement | No major advantage |
| **Swift** | Fast reaction potential | Harder to control |
| **Heavy** | Stable descent | Requires stronger timing |
| **Guardian** | Defensive ability | Lower reward multiplier |
| **Gambler** | Increased rewards | Increased difficulty |
| **Mystic** | Ability-focused | Longer cooldowns |
| **Forge Bird** | Upgrade synergy | Weak early in a run |

Combined with upgrades and modifiers, birds can create recognizable **builds**.

For example:

```text
Guardian Bird
+ Shield Recharge
+ Extra Shield Charge
+ Reduced Coin Gain
= Defensive Build
```

or:

```text
Gambler Bird
+ Score Multiplier
+ Faster Pipes
+ Bonus Coins
= High-Risk Economy Build
```

---

## Upgrades

Flapforge can support two major categories of upgrades.

### Run Upgrades

Temporary upgrades exist only during the current run.

Examples:

- +10% score.
- Temporary shield.
- Increased coin drops.
- Slower obstacles.
- Faster ability recharge.
- Smaller bird hitbox.
- Bonus reward for consecutive pipes.

These disappear when the run ends.

### Permanent Upgrades

Meta-progression upgrades remain unlocked.

Examples:

- Start with one shield charge.
- Increase maximum ability level.
- Unlock additional birds.
- Unlock new upgrade choices.
- Improve reward generation.
- Unlock challenge tiers.
- Unlock additional worlds.

This creates two connected progression layers:

```text
RUN PROGRESSION
Temporary power gained during one attempt

        +

META-PROGRESSION
Permanent options unlocked across attempts
```

---

## Challenges and Boss Runs

Challenges provide structured goals beyond simply beating a high score.

Examples:

### No Shield

Survive a target distance without defensive abilities.

### Speed Run

Obstacle speed continually increases.

### Tiny Wings

Reduced flap strength changes the normal movement rhythm.

### Moving World

Every obstacle uses movement patterns.

### One Life

No shields, revives, or recovery abilities.

### Coin Rush

Score matters less than collecting as many resources as possible.

### Boss Corridor

Survive a handcrafted sequence of increasingly difficult obstacles.

Successful challenges can unlock:

- Birds.
- Abilities.
- Upgrade branches.
- Worlds.
- Difficulty levels.
- Cosmetic rewards.
- Permanent modifiers.

---

## World Progression

New environments should introduce mechanical changes, not only visual changes.

Example progression:

```text
World 1 — Green Fields
Classic pipes and basic movement.

World 2 — Wind Valley
Horizontal and vertical wind effects.

World 3 — Iron Forge
Mechanical obstacles and moving gates.

World 4 — Storm Sky
Lightning, visibility changes and faster patterns.

World 5 — Void
Advanced obstacle combinations and unstable rules.
```

A world can contain:

- Unique visuals.
- Unique obstacle families.
- Different music and sound design.
- Exclusive challenges.
- Exclusive unlocks.
- Different difficulty curves.

---

## Difficulty

Flapforge should remain fundamentally skill-based.

Progression is intended to provide **new tools and strategies**, not turn the game into an automatic win.

The ideal difficulty curve is:

```text
Easy to understand
        ↓
Difficult to master
        ↓
Progression introduces options
        ↓
Options enable deeper strategies
        ↓
New content introduces new challenges
        ↓
Player mastery remains important forever
```

This allows both systems to progress simultaneously:

**Player skill**

and

**Player account progression**

---

## Controls

| Input | Action |
| --- | --- |
| `Space`, `Up arrow`, left mouse button | Flap |
| `X`, `Shift`, right mouse button | Use the equipped active ability |
| `Esc` | Pause the run / go back a screen |
| `Enter` | Confirm the focused item |
| `M` | Mute / unmute audio |
| `F3` | Toggle the debug overlay (tick rate, frame time, seed) |
| `F11` | Toggle borderless fullscreen |

Menus are fully usable with either input device: arrow keys or `Tab` move
focus, `Enter`/`Space` activate, `Esc` goes back, and every control also
responds to mouse hover and click. Keyboard bindings become rebindable in the
Settings screen; the mouse buttons are fixed.

Input is sampled per simulation tick (60 Hz), so a tap shorter than a frame
is never lost and key auto-repeat never produces an extra flap.

The game intentionally keeps the primary control simple so that difficulty
comes from timing, positioning, obstacle recognition, and build decisions
rather than complicated input combinations.

---

## Getting Started

### Requirements

- **JDK 17 or newer** (any distribution: Temurin, Microsoft, Zulu, your
  distro's `openjdk-17-jdk`, ...). The project is compiled with
  `--release 17`, so a newer JDK on the machine is fine.
- **No Gradle installation.** The repository ships the Gradle wrapper
  (`gradlew` / `gradlew.bat`), which downloads Gradle 9.7.1 on first use.
- A desktop session: Linux (X11 or Wayland), Windows 10+, or macOS 12+.
  Flapforge is a plain AWT/Java2D application; it needs no OpenGL, no native
  libraries and no game engine.
- Git, if cloning the source repository.

Verify Java:

```bash
java -version
```

### Clone and build

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge
./gradlew build          # Windows: gradlew.bat build
```

`build` compiles with all lint warnings treated as errors and runs the
default test suite. The first run downloads Gradle and the two dependencies
(Gson, JUnit); afterwards `./gradlew --offline build` works without network.

See [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) for every Gradle task, the
GUI smoke tests and the coding rules, and [`CONTRIBUTING.md`](CONTRIBUTING.md)
if you want to send a change.

---

## Running the Game

### From source

```bash
./gradlew run
./gradlew run --args="--seed 42 --scale 2"     # pass launch flags through --args
```

### Self-contained jar

```bash
./gradlew fatJar
java -jar build/libs/flapforge-<version>-all.jar
java -jar build/libs/flapforge-<version>-all.jar --fullscreen --no-audio
```

The jar bundles Gson and needs only a JRE/JDK 17+. Packaged app images
(`jpackage`) arrive with the release milestone.

### Scripts

`scripts/run.sh` (Linux/macOS) and `scripts\run.ps1` (Windows) start the
game through Gradle and forward every argument to it; `scripts/build.sh` /
`scripts\build.ps1` run `build fatJar`.

```bash
scripts/run.sh --seed 42 --world storm_sky --bird zephyr
```

Launch flags (some arrive with later milestones, see
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md)):

| Flag | Meaning |
| --- | --- |
| `--seed N` | fixed RNG seed for a reproducible run |
| `--world ID` | start in a given world (`green_fields`, `wind_valley`, `iron_forge`, `storm_sky`, `void`) |
| `--bird ID` | start with a given bird |
| `--tier ID` | difficulty tier (`normal`, `hard`, `nightmare`) |
| `--scale N` | initial window scale (integer multiple of the 420×640 playfield); default: the largest scale whose window fits the screen |
| `--fullscreen` | start in borderless fullscreen (`F11` toggles) |
| `--no-audio` | disable the audio backend |
| `--home DIR` | use `DIR` instead of the default save/settings directory |
| `--headless-run N` | simulate `N` frames without a window and print a summary line (the state hash used by CI arrives with the simulation) |
| `--no-window` | run without a window |
| `--help`, `-h` | print the usage text |
| `--reset-save` | delete the save file (a backup is kept) and start fresh |
| `--lang CODE` | UI language: `auto`, `en`, `pt_BR` |

---

## Technology

Flapforge is written in **Java 17** on top of the standard desktop libraries
only. There is no game engine and no native code.

| Technology | Purpose |
| --- | --- |
| **Java 17** | language level (`--release 17`), compiled warning-free with `-Xlint:all -Werror` |
| **AWT / Java2D** | a `java.awt.Frame` with a `Canvas` and a double-buffered `BufferStrategy`; every screen, sprite and effect is drawn with Java2D. No Swing anywhere. |
| **Gradle 9.7.1** (wrapper) | build, tests (`test`, `smokeTest`, `perfTest`, `simTest`), tools, fat jar |
| **Gson 2.11.0** | the only runtime dependency: JSON content, settings and save files |
| **JUnit Jupiter** | unit, property, simulation, headless-render and real-window smoke tests |
| **Procedural art and audio** | all visuals are generated from per-world palettes and bird archetypes; sound effects and music come from a software synthesiser and sequencer — no image, audio or font files are required to play |
| **Data-driven content** | birds, upgrades, abilities, modifiers, worlds, obstacle patterns, challenges, achievements and UI strings are JSON files, validated at start-up |
| **Local persistence** | a versioned save file and settings under `~/.flapforge` (Linux), `%APPDATA%\Flapforge` (Windows) or `~/Library/Application Support/Flapforge` (macOS), written atomically with backups and migrations |

Under the hood the game runs a fixed **60 Hz simulation** on a dedicated loop
thread with interpolated rendering, and the whole simulation, progression
and save stack is **headless**: the same code that the player runs powers
the bots, the balancing tools and the cross-platform determinism check in
CI. The architecture is described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

---

## Project Structure

Flapforge is a single Gradle module under the package
`io.github.michelbr84.flapforge`, organised in four layers. Dependencies only
point downwards, and the two lower layers never touch AWT, the clock,
threads or global randomness — a rule enforced by a test.

```text
Flapforge/
├── build.gradle, settings.gradle, gradle.properties, gradlew*   Gradle 9.7.1 project (wrapper included)
├── src/main/java/io/github/michelbr84/flapforge/
│   ├── Flapforge.java      entry point: parses launch flags, boots the application
│   │
│   │   ── 1. application shell ──
│   ├── app/                window, loop thread, AWT input bridge, frame presenter, clocks, threads
│   │
│   │   ── 2. presentation ──
│   ├── render/             procedural art, viewport scaling, fonts, renderers, debug overlay
│   ├── audio/              software mixer, tone synthesiser, music sequencer
│   ├── ui/                 screen stack, focus handling, components, screens
│   ├── event/              presentation-side event bus (audio, particles, toasts)
│   │
│   │   ── 3. simulation (pure) ──
│   ├── core/               playfield constants, geometry, math, seeded RNG, TimeSource
│   ├── input/              actions, key codes, per-tick input frames, bindings
│   ├── gameplay/           bird, obstacles, collision, stats pipeline, difficulty, run state machine, bot harness
│   ├── ability/            active and passive ability behaviours
│   ├── modifier/           roguelite modifier pool, rarity and synergies
│   │
│   │   ── 4. meta (pure) ──
│   ├── content/            JSON loader, strict binding, validator, unlock graph, strings
│   ├── progression/        player profile, wallet, level, unlocks, upgrades, achievements, prestige
│   └── persistence/        settings and save files: atomic writes, backups, migrations
├── src/main/resources/
│   ├── data/               birds, difficulty, economy, upgrades, abilities, modifiers, worlds, patterns, challenges, achievements (JSON)
│   ├── data/strings/       en.json (source of truth), pt_BR.json
│   ├── assets/             manifest.json plus optional override folders for future art, audio and fonts
│   └── version.properties
├── src/test/               unit, property, simulation, headless render and GUI smoke tests; fixtures
├── src/tools/              balancing simulator, save inspector, content check, asset validator, icon export
├── scripts/                build and run wrappers (sh + ps1)
├── docs/                   architecture, development, balancing, content, save system, roadmap
└── .github/                CI workflows, dependabot, issue and pull request templates
```

Files arrive milestone by milestone; the full package tree with milestone
tags, the layer rules and the loop/presenter/input design are documented in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

Keeping the roguelite systems as plain data and pure code, separate from the
window and the renderer, is what lets the game be simulated, balanced and
verified without ever opening a window.

---

## Design Philosophy

Flapforge is guided by several principles.

### 1. One button should still be enough

The original mechanic is powerful because anyone can immediately understand it.

Flapforge should preserve that accessibility.

### 2. Skill comes first

Meta-progression should complement player skill rather than replace it.

### 3. Every run should matter

Even a failed run should be capable of contributing something:

- Currency.
- Experience.
- Knowledge.
- Unlock progress.
- Challenge progress.
- Achievement progress.

### 4. Unlock options, not just numbers

A new ability is generally more interesting than a permanent `+1%` bonus.

Progression should increasingly change **how the player can play**.

### 5. Short runs, long journey

Individual runs should remain fast.

The complete progression journey can last much longer.

### 6. Death should create motivation

The desired reaction after losing is not:

> "I have to do the exact same thing again."

It is:

> "One more run — I can unlock something."

---

## Roadmap

### Phase 1 — Core Arcade Foundation

- [x] Java-based Flappy Bird gameplay foundation
- [x] Gravity and flap mechanics
- [x] Collision detection
- [x] Scoring
- [x] Randomized obstacle generation
- [x] Moving obstacles
- [ ] Flapforge branding and UI

### Phase 2 — Run Economy

- [ ] Run rewards
- [ ] Persistent currency
- [ ] Reward summary screen
- [ ] Player profile
- [ ] Save/load system
- [ ] Progress tracking

### Phase 3 — Meta-Progression

- [ ] Permanent upgrade system
- [ ] Upgrade tree
- [ ] Unlock conditions
- [ ] Multiple playable birds
- [ ] Bird-specific abilities
- [ ] Ability progression

### Phase 4 — Roguelite Layer

- [ ] Temporary run upgrades
- [ ] Random upgrade choices
- [ ] Build synergies
- [ ] Run modifiers
- [ ] Risk/reward mechanics
- [ ] Rarity system

### Phase 5 — Content

- [ ] Multiple environments
- [ ] New obstacle families
- [ ] Challenge runs
- [ ] Boss challenges
- [ ] Milestones
- [ ] Achievements

### Phase 6 — Polish

- [ ] Updated original artwork
- [ ] Original sound effects
- [ ] Music
- [ ] Settings
- [ ] Accessibility options
- [ ] Difficulty balancing
- [ ] Performance optimization
- [ ] Automated tests
- [ ] Packaged releases

### Long-Term Ideas

- [ ] Daily challenges
- [ ] Seeded runs
- [ ] Endless difficulty tiers
- [ ] Leaderboards
- [ ] Challenge sharing
- [ ] Statistics dashboard
- [ ] New Game+
- [ ] Prestige progression
- [ ] Mod support
- [ ] Community-created challenge packs

The roadmap represents the intended direction of the project and may evolve during development.

---

## Original Project

Flapforge is based on and inspired by:

### kingyuluk/FlappyBird

**Repository:**  
[github.com/kingyuluk/FlappyBird](https://github.com/kingyuluk/FlappyBird)

The original Java project provides the core Flappy Bird desktop-game foundation, including:

- Bird physics.
- Gravity.
- Flap controls.
- Collision detection.
- Scoring.
- Pipe generation.
- Moving pipes.
- Floating pipes.
- Difficulty progression.
- Graphics.
- Sound playback.
- Java desktop application structure.

Flapforge extends this foundation toward a substantially different game design centered on:

- Roguelite runs.
- Persistent progression.
- Unlockable content.
- Character variety.
- Abilities.
- Upgrade trees.
- Challenges.
- Additional worlds.
- Long-term replayability.

Huge credit goes to **Kingyu Luk / kingyuluk** for making the original Java implementation publicly available.

---

## Assets and Third-Party Content

**Flapforge ships no inherited assets.** The upstream project bundled sprites,
backgrounds, number fonts, title artwork and sound effects that it described
as "obtained from the internet for learning purposes", and some of them
reproduced the Flappy Bird wordmark and character. Because the MIT licence
covers the upstream *source code* and says nothing about those files, they
were removed from the working tree at the start of the rewrite. They survive
only in git history and are not part of any build, jar or release.

Instead, the game is **procedural-first**:

- every image — backgrounds, pipes, gears, pistons, lightning, birds, UI,
  the application icon — is drawn at runtime from per-world palettes and
  bird archetypes;
- every sound effect and music track is synthesised by a software mixer;
- text uses the JDK's logical font until an open (OFL) font is bundled.

Original artwork, audio and fonts are welcome later and have a defined
drop-in path: `src/main/resources/assets/manifest.json` maps an asset id to a
file, its kind, its licence and its provenance, and a listed sprite overrides
the procedural drawing for that id without code changes. Anything added
there must carry a licence that allows redistribution and must be recorded
in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md), which also holds the
upstream attribution and the notices for the bundled runtime dependency
(Gson, Apache 2.0).

Software licensing and asset licensing remain separate concerns: the MIT
licence in [`LICENSE`](LICENSE) covers Flapforge's code, not any third-party
image, sound, font or trademark.

---

## Contributing

Contributions are welcome.

Good contribution areas include:

- Gameplay mechanics.
- Progression balancing.
- New birds.
- New abilities.
- Upgrade-system design.
- Challenge modes.
- New obstacle patterns.
- New worlds.
- Performance improvements.
- Bug fixes.
- UI improvements.
- Accessibility.
- Tests.
- Documentation.
- Original artwork and audio.

A typical workflow is:

```bash
git clone https://github.com/michelbr84/Flapforge.git
cd Flapforge

git checkout -b feature/my-feature
```

Make your changes, test them, and commit:

```bash
git add .
git commit -m "feat: add my feature"
```

Push the branch:

```bash
git push origin feature/my-feature
```

Then open a pull request.

For substantial gameplay or architecture changes, opening an issue or discussion before implementation is recommended.

---

## License

The software foundation used by Flapforge comes from the MIT-licensed
[`kingyuluk/FlappyBird`](https://github.com/kingyuluk/FlappyBird) project.

Flapforge should preserve all notices and attribution required by applicable upstream licenses.

See [`LICENSE`](LICENSE) for the licensing terms of this repository.

Third-party graphics, audio, fonts, trademarks, and other assets may be governed by separate licenses or rights.

---

## Disclaimer

Flapforge is an independent game project inspired by the gameplay concept popularized by **Flappy Bird**.

It is not an official Flappy Bird release and is not affiliated with or endorsed by the creators or rights holders of the original game.

"Flappy Bird" and any related names, characters, artwork, trademarks, or intellectual property remain the property of their respective owners.

The goal of Flapforge is to create a distinct arcade roguelite experience built around a simple flap-based flight mechanic and persistent progression.

---

## Acknowledgments

Special thanks to:

- **Kingyu Luk / kingyuluk** for the original Java Flappy Bird implementation.
- The Java and open-source communities.
- Developers who continue experimenting with simple arcade mechanics in new ways.
- Everyone who contributes code, testing, balancing, artwork, audio, ideas, and feedback to Flapforge.

---

## The Idea in One Sentence

> **Flapforge is Flappy Bird where every death ends the run — but not the journey.**

---

**Flap. Fail. Forge. Fly farther.**
