# Flapforge documentation

This folder holds the engineering and design documentation for Flapforge.
The player-facing overview lives in the root [`README.md`](../README.md).

## Available now

| Document | Contents |
| --- | --- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | The four layers, the pure/presentation split and its enforcement, the loop / presenter / input design, run modes, the daily challenge, prestige, attract mode, the Android port (source transform and its gate, shim surface policy, host seam, the Android host's threads and gestures, what Robolectric can verify), the package tree with milestone tags, and how the original `STRUCTURE.md` proposal was reconciled |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Prerequisites, bootstrapping the Gradle wrapper, every Gradle task, launch flags, coding rules, running GUI tests locally and in CI, packaging and the release flow, the Android build (SDK prerequisites, the three Gradle commands, the transform self-test, where results land, the `~/.flapforge` rule for the activity tests, the launcher-icon generator) |
| [`BALANCING.md`](BALANCING.md) | Physics conversion table from the upstream integer loop to the 60 Hz simulation, cosmetic-feel rows, balancing simulation results, the M9 tier balance and the MetaSim career measurements |
| [`SAVE_SYSTEM.md`](SAVE_SYSTEM.md) | Save envelope, atomic writes, backups and quarantine, migrations, forward-compatibility overlay, reset policy, the v1 frozen at the `v0.1.0` tag |
| [`CONTENT.md`](CONTENT.md) | JSON schemas for every content file, validator rules, how to add birds, worlds, patterns, challenges, tiers |
| [`PROGRESSION.md`](PROGRESSION.md) | Currencies, levels, the unlock graph and its conditions, purchases, upgrade trees, effective stats, the meta-progression screens, prestige, the daily challenge |
| [`GAME_DESIGN.md`](GAME_DESIGN.md) | Consolidated design document (pillars, systems, worlds, bosses, deferred ideas) |
| [`ROADMAP.md`](ROADMAP.md) | What shipped in 0.1.0, the M10 Android port and its release-asset note, post-1.0 plans and the explicit deferred list |

## Root-level documents

| Document | Contents |
| --- | --- |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | Workflow, branch naming, Conventional Commits, coding rules, PR checklist |
| [`../CODE_OF_CONDUCT.md`](../CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |
| [`../SECURITY.md`](../SECURITY.md) | Scope and how to report a vulnerability |
| [`../THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) | Upstream attribution, dependencies, assets and fonts |
| [`../CHANGELOG.md`](../CHANGELOG.md) | Flapforge's changelog plus the inherited upstream history |

## Conventions

- Documents are written in English, in Markdown, wrapped at roughly 80
  columns, with tables for reference material and fenced blocks for commands.
- A document is created by the milestone that implements its subject and is
  kept current by every later change to that subject; a PR that changes
  behaviour described here must update the matching document.
- Milestone tags such as `[M3]` mark when a feature landed. The M0–M9 plan
  shipped complete in `0.1.0`; the tags stay in the docs as historical
  orientation (which milestone introduced what), not as a statement about what
  is still planned — for that, see [`ROADMAP.md`](ROADMAP.md). `[M10]` marks
  the Android port, which joined the same `0.1.0` release after the tag.
