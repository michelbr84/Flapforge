# Goals Collections tab — audit of the collection counts (ff-tree read-only audit)

Audited 2026-09-09. Question under test: does every "owned / total (percentage)" figure the
Goals screen's Collections tab shows come from canonical content, or is any denominator a
hardcoded literal copied from the mockups?

## Data path

The tab never names a number. `GoalsScreen.buildCollections()`
(`src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java:508`) reads
`evaluator.collections().all(profile)` — one `CollectionProgress.Entry {owned, total}` per
category — and formats `COLLECTIONS_VALUE` with `entry.owned()`, `entry.total()`,
`entry.percent()` (`GoalsScreen.java:515-516`). Every total is computed by
`CollectionProgress` from loaded content:

- `birds`, `abilities`, `worlds`: `CollectionProgress.counted(kind, owned)`
  (`CollectionProgress.java:247-260`) walks the id→kind table `kindsOf`
  (`CollectionProgress.java:93-101`), which is built from the unlock graph
  (`UnlockGraph.of(content).nodes()`) — the same table the content validator proves reachable.
- `cosmetics`: `CollectionProgress.cosmetics(owned)` (`CollectionProgress.java:262-277`)
  walks `content.birds()` and sums `bird.palettes()`; owned ids are `bird.<id>:<palette>`.
- `challenges`: `CollectionProgress.challenges(profile)` (`CollectionProgress.java:279-293`)
  walks `content.challenges()`, owned = records marked completed.
- `achievements`: `CollectionProgress.achievements(profile)`
  (`CollectionProgress.java:295-308`) walks `content.achievements()`.
- `upgrades`: `CollectionProgress.upgrades(profile)` (`CollectionProgress.java:310-321`)
  walks `content.upgrades()` and sums `node.maxLevel()` — **a level count, not a node count**
  (documented in the class javadoc, lines 33-38).
- `all` (Everything): `CollectionProgress.allCategories`
  (`CollectionProgress.java:203-216`) iterates the seven other categories, calls the same
  `oneCategory` each row renders with, and sums owned over total. Same denominators, no
  independent literal.

The category id list is `AchievementConditionDef.COLLECTION_CATEGORIES`
(`.../content/defs/AchievementConditionDef.java:38-39`), surfaced at
`CollectionProgress.java:48`. The released category id for the mockup's "colours" is
`cosmetics`.

## Per-category table (real totals counted from the shipped files)

| category (tab id) | authoritative source | file:line where the tab reads it | real total today | hardcoded? |
| --- | --- | --- | --- | --- |
| birds | `data/birds.json` (7 entries) + profile `unlocked` (`bird:` ids) | `GoalsScreen.java:509` (`collections().all`) → `CollectionProgress.java:247` (`counted`, kinds table from `UnlockGraph`) | 7 | no |
| abilities | `data/abilities.json` (8 entries) + profile `unlocked` (`ability:` ids) | `GoalsScreen.java:509` → `CollectionProgress.java:247` | 8 | no |
| worlds | `data/worlds.json` (5 entries) + profile `unlocked` (`world:` ids) | `GoalsScreen.java:509` → `CollectionProgress.java:247` | 5 | no |
| challenges | `data/challenges.json` (7 entries) + profile `challenges` (completed) | `GoalsScreen.java:509` → `CollectionProgress.java:279` | 7 | no |
| cosmetics ("colours") | `data/birds.json` palette lists (22 = Σ `bird.palettes`) + profile `unlocked` (`cosmetic:` ids) | `GoalsScreen.java:509` → `CollectionProgress.java:262` | 22 | no |
| achievements | `data/achievements.json` (41 entries) + profile `achievements` | `GoalsScreen.java:509` → `CollectionProgress.java:295` | 41 | no |
| upgrades | `data/upgrades.json` nodes (18) — total is Σ `maxLevel` = 43 — + profile `upgrades` | `GoalsScreen.java:509` → `CollectionProgress.java:310` | 43 (level count) | no |
| all (Everything) | the same seven denominators, summed (`owned`/`total`) | `GoalsScreen.java:509` → `CollectionProgress.java:203` | 7+8+5+7+22+41+43 = 133 | no |

Fresh-profile owned, for completeness: birds 1 (default bird), worlds 1 (`green_fields` is
default-unlocked), challenges/abilities/cosmetics/achievements/upgrades 0. Everything card
would show 2 / 133.

Counts verified by reading the JSON directly: `birds.json` = 7, `abilities.json` = 8,
`worlds.json` = 5 (its own `_comment` states "The five worlds"), `challenges.json` = 7,
`achievements.json` = 41, `upgrades.json` = 3 trees / 18 nodes / Σ maxLevel 43.

## Hardcoded literals

Searched `GoalsScreen.java`, `CollectionProgress.java`, `AchievementConditionDef.java`.
No count literal exists: no `43`, no `41`, no `22`, no `7`-as-total near the collection rows.
The integer constants near those rows are layout metrics, all legitimate:
`COLLECTION_H = 55` / `COLLECTION_MAX_H = 65` (px row heights, `GoalsScreen.java:164,168`),
`EVERYTHING_H = 67` / `EVERYTHING_MAX_H = 78` (`GoalsScreen.java:169-170`),
`FOOTER_H = 24` (`:171`), `CARD_GAP = 6` (`:172`), `counterRoom = 84` (`:1497`).
None is a denominator.

## Upgrades cross-check

`data/upgrades.json` today defines **3 trees** and **18 nodes**. The tab's Upgrades row
shows **43** — which equals Σ `maxLevel` over the shipped nodes
(6 flight nodes 3+2+3+3+2+2=15, 6 economy 4+4+3+3+1+1=16, 6 forge 2+3+3+2+1+1=12;
15+16+12=43), computed at `CollectionProgress.java:316-318`. So the number is canonical and
tracks content slavishly; it is a *level* total by deliberate, documented design
(`CollectionProgress.java:35-37`). Two notes for the reader:

- 43 differs from the *node* count (18). If the product intent is "upgrade nodes", the
  number is semantically a level count — but that is a design decision, not a hardcode.
- 43 coincidentally equals the old mockup's 43. It is a coincidence: the code derives it, it
  does not copy it. Change `maxLevel` on any node and the tab follows.

## Verdict

The counts are canonical. Every denominator the Collections tab displays is computed at
runtime from the shipped content — the unlock graph for birds/abilities/worlds, bird
palettes for cosmetics, and the content lists for challenges, achievements and upgrade
levels — and the Everything card is the sum of the exact same per-category totals
(`CollectionProgress.allCategories`). Nothing is copied from the mockups; in particular the
worried-about "43 upgrades" survives as a coincidence of the level-count definition, not as
a literal. The tab cannot disagree with the data unless the JSON itself changes, and a JSON
change would move both the tab and the evaluators together, by construction.

## Defects

None.