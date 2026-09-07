_Language:_ **English** · [[Português (Brasil)|Inicio-(pt-BR)]]

# Flapforge

**A skill-based arcade roguelite where every flight makes the next one stronger.**

Flapforge keeps the one-button loop of the classic — *flap, dodge, survive, score* — and gives
every run a larger purpose. Dying still ends the flight, but never the journey: each run pays
coins and experience, and what you earn unlocks birds, abilities, worlds, modifiers, challenges
and permanent upgrades that make the next run different from the last.

![The home hub](images/hub.png)

*The home hub: your bird on the forge, the world you will fly next, the nearest unlock and one
big START RUN.*

## The loop

```text
Start a run → fly → dodge → score → draft a modifier → face the boss → die or clear
       ↑                                                                    ↓
   choose the next build ← unlock or upgrade ← receive coins and XP ←──────┘
```

Skill decides the run; meta-progression decides what skill gets to work with. Everything is
local and deterministic: seeds, dailies and records live in a crash-safe save on your machine,
and there are no online services, accounts or leaderboards.

## Start here

1. [[Getting Started]] — requirements, downloads, running from source, launch flags and where
   your save lives.
2. [[Home Hub]] — a tour of the first screen: the player card, the world plaque, the forge, the
   next-unlock card, START RUN and the bottom navigation.
3. [[Controls]] — keys, mouse, touch and rebinding.
4. [[Playing a Run]] — what happens between READY and the run summary, and how rewards are
   paid.

## The systems

| Page | What it covers |
| --- | --- |
| [[Modifiers and Synergies]] | The mid-run draft: 17 modifiers, rarities, stacks and the four synergies. |
| [[Birds and Abilities]] | The seven birds, their palettes, the eight abilities and the loadout. |
| [[Worlds and Bosses]] | The five worlds, their hazards and bosses, and the World Select. |
| [[Shop and Upgrades]] | Coins, the Shop's tabs and the three upgrade trees behind the hub's Forge item. |
| [[Game Modes and Difficulty]] | Standard, Seeded, Daily and Challenge runs, the difficulty tiers and prestige. |
| [[Challenges and Goals]] | The Goals screen: seven challenges, 41 achievements, milestones and collections. |
| [[Settings and Accessibility]] | Language, sound, display, game options, key bindings and the About section. |
| [[Building and Contributing]] | Building from source, the Gradle tasks, adding content and contributing. |
| [[FAQ]] | Short answers to the questions that come up most. |

## Facts at a glance

| | |
| --- | --- |
| Platforms | Linux, Windows 10+, macOS 12+ (Java 17+, plain AWT/Java2D); Android 13+ (sideloadable APK) |
| Playfield | 420×640 logical pixels at 60 Hz, scaled to any window; tall screens are filled edge to edge |
| Content | 7 birds, 8 abilities, 17 modifiers, 3 upgrade trees, 5 worlds, 7 challenges, 41 achievements |
| Languages | English and Português (Brasil), switchable live in Settings or with `--lang pt_BR` |
| Licence | MIT — see the [repository](https://github.com/michelbr84/Flapforge) |
| Downloads | [Releases](https://github.com/michelbr84/Flapforge/releases): per-OS app images, a fat jar and the Android APK |

> **Tip:** this wiki is generated from the `wiki/` directory of the repository. Found a
> mistake? Open a pull request against `wiki/` rather than editing the page here — the next
> sync would overwrite it.
