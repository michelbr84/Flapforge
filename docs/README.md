# Flapforge documentation

This folder holds the engineering and design documentation for Flapforge.
The player-facing overview lives in the root [`README.md`](../README.md).

## Available now

| Document | Contents |
| --- | --- |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | The four layers, the pure/presentation split and its enforcement, the loop / presenter / input design, the package tree with milestone tags, and how the original `STRUCTURE.md` proposal was reconciled |
| [`DEVELOPMENT.md`](DEVELOPMENT.md) | Prerequisites, bootstrapping the Gradle wrapper, every Gradle task, launch flags, coding rules, running GUI tests locally and in CI |
| [`BALANCING.md`](BALANCING.md) | Physics conversion table from the upstream integer loop to the 60 Hz simulation, cosmetic-feel rows, balancing simulation results |
| [`SAVE_SYSTEM.md`](SAVE_SYSTEM.md) | Save envelope, atomic writes, backups and quarantine, migrations, forward-compatibility overlay, reset policy |
| [`CONTENT.md`](CONTENT.md) | JSON schemas for every content file, validator rules, how to add birds, worlds, patterns, challenges |
| [`PROGRESSION.md`](PROGRESSION.md) | Currencies, levels, the unlock graph and its conditions, purchases, upgrade trees, effective stats, the meta-progression screens, prestige |

## Planned (added by the milestone that implements the subject)

| Document | Milestone | Contents |
| --- | --- | --- |
| `GAME_DESIGN.md` | M9 | Consolidated design document (pillars, systems, worlds, bosses, deferred ideas) |
| `ROADMAP.md` | M9 | Post-1.0 plans and the explicit deferred list |

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
- Milestone tags such as `[M3]` mark when something lands; they are removed
  from the docs once the whole plan has shipped.
