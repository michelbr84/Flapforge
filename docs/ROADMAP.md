# Flapforge — Roadmap

## What shipped in 0.1.0

M0–M9, one commit each on `rewrite/flapforge` (tagged `v0.1.0`):

| milestone | commit | shipped |
| --- | --- | --- |
| M0 | `7610c2e` | Gradle skeleton, scaled window, 60 Hz loop, per-tick input, menu shell, legacy assets removed |
| M1 | `c51333a` | classic core — physics parity (0 px), gates, content pipeline, Green Fields, determinism fixtures |
| M2 | `d14b161` | boot, settings, synthesised audio, i18n (EN/pt-BR), presentation services |
| M3 | `5b25429` | coins, clean-gate streaks, run rewards, crash-safe save with migrations |
| M4 | `224e589` | seven birds, three upgrade trees, unlock graph, purchases and shops |
| M5 | `44da4de` | eight abilities, stat-driven shield and revive, loadout and HUD |
| M6 | `12a47b6` | mid-run modifier drafts, rarity pool, tags and synergies |
| M7 | `cf5b34a` | five worlds, four hazard families, patterns, ambience and rule cycles |
| M8 | `e2ad93f` | challenges, world bosses, achievements, procedural music, OFL font and accessibility |
| M9 | *(this release)* | hard/nightmare tiers, daily challenge, prestige, seeded runs, attract mode, MetaSim balance, release packaging |

Also in 0.1.0: `speed_run_1` retuned 40 → 30 gates (BALANCING.md §11.1) and the `glide_1`
retune (§12) — both measured, both recorded.

## What shipped in 0.2.0

The home-hub release (2026-09-07): the main menu as a visual hub (player card,
coin chip, world plaque, forge scene, next-unlock card, START RUN, bottom
navigation), the four-tab Goals screen, the Profile header, the World Select,
Settings › About with Quit, the bilingual player wiki under `wiki/`, and the
0.1.1 Android line (launch-crash fixes, the APK in every release) merged back
into `main`. See `CHANGELOG.md`.

## What shipped in 0.2.1

The bird-selection release (2026-09-08): the Birds screen rebuilt in the hub's
visual language — the browsed bird as a hero on the anvil with three headline
attributes (`BirdAttributes`), the roster as a carousel, the world/tier/mode
rows moved into a run-setup panel behind a one-line bar, the loadout as ability
cards with the ability list and the stat breakdown behind *See details*, one
gold call to action and the hub's five-item navigation with Birds on the gold
plate. `HubHeader` and `SectionNav` are the reusable half; the Shop adopted
the same shell in 0.2.2, Forge and Goals are next. Also: every default Eclipse
compiler warning over the tree removed. See `CHANGELOG.md`.

## What shipped in 0.2.2

The shop release (2026-09-09): the Shop rebuilt in the hub's visual language
(M12) — the `HubHeader` with the wallet shown once, the four categories as a
`TabBar` with a glyph per tab and the selected one in gold, a scrolling
two-column grid of portrait `ShopCard`s (the thing's own art through
`ShopArt`, its name, the road that costs no coins as its second line, and its
state on a wooden price tag: a price, an owned check, a level and its price,
or the cap word), a wooden detail plaque that never repeats the price, one gold
call to action whose verb is the tab's, and the hub's five-item navigation with
Shop on the gold plate. What the profile has bought stays in the catalogue,
badged owned. `WorldSwatch` is promoted out of the World Select. See
`CHANGELOG.md`.

## What shipped in 0.2.3

The forge release (2026-09-09): the Upgrades screen rebuilt as the **Forge**
(M13) — the `UpgradeTreeScreen` in five bands (compact header with the wallet
and the forge scene, the three tree tabs, the scrolling tree viewport with
tier pills and prerequisite elbows, the detail panel, the hub's
`SectionNav`), the **attribute summary** with a live resolved value and a
five-segment pip bar per stat, and **select-then-buy** through one gold call
to action. A locked tree is sold on the spot through the same atomic route the
shop uses. Nothing that was for sale changed: eighteen nodes, the same prices,
the same `prereqs` graph. `ForgeStatRealityTest` proves every node moves the
stat it claims on the sheet a run is started with; `ForgePersistenceTest`
proves a tree and a level survive a restart; a perf test pins a Forge frame
under the menu allocation budget. See `CHANGELOG.md`.

## What shipped in 0.2.4

The mobile-surface release (2026-09-09): the game uses the screen it is given.
The 420x640 logical playfield was both the layout box and the clip box, so a
tall phone centred it inside the letterbox colour with 377 dead pixels above
and below. `ui/layout/LayoutMetrics` now turns the real viewport into a safe
viewport and then into a header region, a flexible content region and the
persistent bottom navigation; the scale stays the uniform `min(w/420, h/640)`
and nothing is stretched, but the logical band grows with the device and ends
on the physical bottom edge. Android's `systemBars`, cutout and gesture insets
reach the stack, so a gesture bar lifts the navigation. Every hub screen
re-derives its bands when the metrics change and reproduces the classic
constants exactly at 420x640. The hub also gains its fifth section, **Goals**
(M14) — Challenges as a carousel, Achievements as a scrolling list, Milestones
as metric rows, Collections as progress rows — every value read from the
profile or the shipped content. `AspectRatioTest` sweeps 1.48 through 21:9;
`ResponsiveSecondaryTest` pins the four migrated screens at the classic size
and at 1080x2400. See `CHANGELOG.md`.

## Deferred, with next-step anchors

* **Leaderboards** — needs online infrastructure 1.0 does not have. `runHistory` (capped
  100, in the save) already records the local data a future board would upload.
* **Challenge sharing / community packs** — `ChallengeDef` is already strict-bound JSON;
  an export/import format (one file per challenge, validated by the existing
  `ContentValidator` rules) is a small follow-up. Moderation is the open question.
* **Mod packs** — a `~/.flapforge/mods/*.json` overlay follows naturally from the
  data-driven loader (`ContentLoader` reads each file independently); not built in 1.0 to
  keep the validator's guarantees closed. The `assets/manifest.json` override path is the
  safe half of this and already ships.
* **New Game+** — superseded by tiers (`hard`/`nightmare`) plus prestige; revisit only if
  the prestige loop feels stale.
* **"Upgrade materials" as a second currency** — the plan mapped them to coins to keep the
  economy provable; `Wallet` is map-keyed by `economy.json.currencies`, so a second
  currency is a data change plus sink design.
* **Licensed music tracks** — the procedural `MusicSequencer` ships; the mixer already has
  a WAV streaming voice for future tracks (no OGG/MP3 by design).
* **Endless difficulty tiers** — the `tierGenerator` key is reserved in `difficulty.json`;
  needs a per-generated-tier balancing pass (`MetaSim`) before it can ship.
* **Original art and SFX packs** — the procedural default ships; original assets land
  through `assets/manifest.json` entries (id → path, licence, provenance) with zero code
  changes. `IconExport`/`AssetValidator` already validate the manifest.

## Beyond 1.0 (candidate order)

1. **Quality-of-life pack** — run-seed sharing UI, replay of `runHistory` entries, more
   statistics graphs (data already captured).
2. **Challenge export** — smallest new-content lever: JSON out, JSON in, validator in the
   middle.
3. **Second currency** — only with a sink design that `MetaSim` can measure end to end.
4. **Endless tiers** — after a `MetaSim` extension that treats generated tiers as content.
5. **Online anything** — leaderboards first, only if the save/policy story is solved.
