# Progression

What a player keeps between runs: coins, experience and levels, unlocked content, upgrade
levels, the selection they play with, and the statistics everything else is measured against.
The data lives in `src/main/resources/data/` and is described in [`CONTENT.md`](CONTENT.md);
this document describes the **code that reads and writes it** — the `progression` package.

The decisions behind it are D13 (meta-progression), D14 (the write path), D8 (the stat
pipeline), D15 (the save triggers) and errata E3, E5, E17, E18, E20, E21, E23 and E31.f of the
implementation plan.

Two rules everything else follows from:

1. **One write path.** A finished run and a purchase both go through `ProgressionManager`, in a
   fixed order, once. Nothing else writes to a `PlayerProfile`.
2. **Every write is atomic.** A purchase either debits, grants, accounts, propagates and saves,
   or it changes nothing at all. There is no state in which the coins are gone and the thing is
   missing.

---

## 1. What a profile holds

`PlayerProfile` is the single persisted object (its shape and its repair rules are in
[`SAVE_SYSTEM.md`](SAVE_SYSTEM.md)). The parts this document is about:

| Field | Holds |
| --- | --- |
| `wallet` | balance per currency; 1.0 ships one, `coins` |
| `xp`, `level` | experience since the last prestige and the level it resolves to |
| `unlocked` | namespaced unlock ids the player owns |
| `upgrades` | owned level per upgrade node, by bare id (never an `upgrade:` unlock id, E21) |
| `abilityLevels`, `abilityLevelCap`, `passiveSlotBonus` | ability ownership and the E3 ceilings |
| `achievements`, `challenges`, `daily` | records the unlock conditions read |
| `selected` | bird, palette, world, tier and equipped abilities |
| `statistics` | every lifetime counter, plus the capped run history |
| `prestigeCount`, `prestigeBaseline` | how many prestiges, and the lifetime totals frozen by the last one (E23) |

### Namespaces

An unlockable is a namespaced string: `bird:`, `cosmetic:<bird>:<palette>`, `ability:`,
`world:`, `challenge:`, `tier:`, `tree:`, `modifier:`, `feature:`. **Upgrade nodes are not
unlockables** (E21): what has to be unlocked to buy a node is its tree, `tree:<tree>`, and the
node itself lives only in `profile.upgrades`.

A fresh profile owns exactly E18's default set: `bird:classic`, `ability:double_flap`,
`world:green_fields`, `tree:flight`, `tier:normal`, `cosmetic:classic:default` (plus every
modifier whose unlock is `default`, from M6).

---

## 2. Earning: `UnlockEvaluator`

`UnlockEvaluator` answers "what has this player earned?". Its table of *id → condition* is the
node list of `content.UnlockGraph` — the same table the validator proves is a reachable, cycle-
free graph — so there is one definition of what an unlockable is.

| Condition | Reads | Notes |
| --- | --- | --- |
| `default` | — | always true |
| `best_gates`, `best_points` | `statistics.bestGates` / `bestPoints` | skill; lifetime, never reset |
| `total_gates`, `runs`, `coins_earned_total` | the matching lifetime total **minus `prestigeBaseline`** | cumulative, "since prestige" (E23) |
| `level` | `profile.level` | the current level; a prestige resets it |
| `challenge` | `challenges.<id>.completed` | attempts are not completions |
| `achievement` | `achievements.<id>` | achievements survive a prestige |
| `world_cleared` | `statistics.bossesCleared` minus the baseline | E23, E26: only a *world* boss writes it |
| `purchase` | nothing | **never satisfied** — it means "buyable for N coins" |
| `all_of`, `any_of` | their children | an empty `any_of` is false; an empty `all_of` is rejected by the validator |
| `prestige` | `prestigeCount` | cosmetics only (E20) |
| `counter` | any achievement counter, or `collection.<category>.percent` | cosmetics only (E20) |

Two rules are in the code rather than in the JSON, and both exist to make E18's default set come
out right:

- a `purchase` branch is never *earned*; `UnlockManager` is the only path that grants it, so an
  `any_of[skill, price]` is earned by the skill branch alone;
- a `cosmetic:<bird>:<palette>` is granted only once `bird:<bird>` is owned — every bird's
  default palette carries `{"type": "default"}`, and without this rule a fresh profile would own
  the default palette of all seven birds.

`evaluate(profile)` iterates to a fixed point (at most `MAX_PASSES` rounds) because a collection
counter reads how much of a category is owned: buying the last bird can satisfy a cosmetic in
the same call. It never writes — the caller decides what to do with the list, which is what lets
a screen preview it.

---

## 3. Writing: `ProgressionManager`

`apply(profile, result, rules, multipliers)` writes a finished run in D14's fixed order:

```
rewards → wallet → xp/level (+ level rewards) → statistics → challenge → daily
        → achievements → unlocks → dirty
```

The order is not cosmetic: rewards are computed before the run is counted (the first-run bonus
reads `totalRuns == 0`), level rewards are credited before the statistics step so `coinsEarned`
counts every coin the pass paid, achievements run after the statistics they test, and unlocks
run after the achievements they may depend on. `apply` is called exactly once per run, when it
reaches `FINISHED`; a second call with the same result returns the first outcome again.

`applyPurchase(profile)` runs the trailing steps only — achievements → unlocks → dirty — after
every atomic purchase (D14, E17). That is why buying the last bird fires its collection
immediately instead of at the end of the next run.

Both return a `ProgressionOutcome`: plain facts (rewards, levels, achievements, unlocks, the
challenge and daily flags) and nothing else. `progression` never imports `event` (E31.b); the
screen that asked maps the facts to events and to wording.

The achievement step is a hook that stays empty until M8; the unlock step is `UnlockEvaluator`
from M4 on.

---

## 4. Spending

| Class | Buys | Refuses with |
| --- | --- | --- |
| `UnlockManager` | anything with a `purchase` branch: birds, abilities, worlds, tiers, trees, features | `UNKNOWN_ID`, `ALREADY_OWNED`, `NOT_FOR_SALE`, `INSUFFICIENT_FUNDS` |
| `UpgradeManager` | one level of one upgrade node | `UNKNOWN_ID`, `MAX_LEVEL`, `ALREADY_OWNED`, `TREE_LOCKED`, `MISSING_PREREQ`, `INSUFFICIENT_FUNDS` |

Both follow the same shape, and the order is the point:

```
check (all of it) → deduct → grant → statistics.coinsSpent → applyPurchase → save now
```

Every failure is a `PurchaseResult` value, not an exception, because a shop screen has to explain
the refusal and a simulation policy has to branch on it. `UnlockManager.offers(profile, content)`
is the shop listing: every priced id the player does not own, cheapest first, flagged by whether
the wallet can pay.

### Upgrade nodes

A node is bought level by level; `costs[level]` is the price of the next one and
`costs.length == maxLevel` is a validator rule. Prerequisites must be owned at level 1 or higher
and the node's tree must be unlocked.

Grants (E31.f) are applied **once**, when the node reaches level 1:

| Grant | Effect | Ceiling |
| --- | --- | --- |
| `UNLOCK` | adds an unlockable id (`hard_tier_1` → `tier:hard`) | — |
| `ABILITY_CAP` | raises `abilityLevelCap` (`master_forge_1`, the only one that ships) | the levels the thinnest ability ships, so 3 (E3) |
| `PASSIVE_SLOT` | raises `passiveSlotBonus` (`ability_scholar_1`) | `MAX_PASSIVE_SLOT_BONUS`, so +1 (E3) |

The validator proves the shipped data respects those ceilings; the managers clamp anyway, so a
hand-edited save or a future data set cannot push a profile into a state the UI cannot render.
The ceiling is one number, `UpgradeManager.abilityLevelCeiling(content)` — E3's `baseCap + Σ
ability_cap grants`, capped by the levels the thinnest ability ships — and `ProfileSchema` carries
it, so `normalize()` clamps `abilityLevelCap` from above as well as from below.

**A node that would grant nothing is refused before the debit.** `hard_tier_1` has no effects and
its only value is `tier:hard`, which `difficulty.json` also gives away at 40 gates in a run or 400
across the profile. A player who walks that path first would otherwise still be offered the node,
pay 400 coins and receive nothing. `UpgradeManager.isRedundant` answers that question — no
effects, and every grant already at its ceiling — and `buy` returns `ALREADY_OWNED` while
`isAvailable` returns `false`, so the upgrade screen badges the card *Already unlocked* instead of
a price.

### Selections

`SelectionManager` writes `profile.selected`. A selection is only ever set to something the
player owns and this build can play — E19 keeps every world but Green Fields locked to free play
until M7 — and changing the bird repairs the palette, because a palette belongs to one bird.
Every accepted change marks the profile dirty and saves immediately (D15); a rejected one writes
nothing.

### Old ids

`UpgradeManager.reconcile(profile, aliases, currency)` applies `aliases.json` (E21): renames per
field, removed nodes dropped, and their refunds credited **once** — `profile.reconciled` records
what has already been applied, so a second load pays nothing again.

A refund is what was spent on a node, so it is paid only to a profile that **owned** it: the
removal and the credit are bound together, because a refund also counts in
`statistics.coinsEarned`, which is a live unlock condition (`ability:coin_magnet` at 500,
`challenge:coin_rush_1` at 1000). Paying it to everyone would hand every player free coins — and
free unlocks with them — on the first launch after any content removal.

The reconciliation runs **inside** the load, between binding and normalisation, through
`SaveManager.profileAliasStep` (E21's order: parse → migrate → bind → aliases → normalize). The
position is load-bearing: `normalize()` resets every `selected.*` id no registry knows and writes
the unlocks an owned id implies, so renaming afterwards would find the bird already reset to the
default and `ability:<oldId>` already written into `unlocked`. The report is returned with the
load's other repairs (`LoadResult.repairs()`), which the launch prints.

A node key that survives the alias table is a node this build does not ship, so `normalize()`
drops it — `upgradeLevelsTotal()` feeds Cinder's `BIRD_SYNERGY` layer, and a stale key would
inflate the synergy of every run.

### What was already earned

The unlock evaluator otherwise runs in exactly two places — the end of a run and a purchase — so
a profile written before an unlockable existed would open the game with what it has already earned
still locked, and the shop would sell it back. Every profile carried over from M3 into M4 is in
that state, and so is every profile after a threshold is lowered or a new unlockable ships. The
launch therefore runs one catch-up pass right after the progression manager is built
(`GameApplication.grantWhatIsAlreadyEarned`), which is `applyPurchase` — D14's *achievements →
unlocks → dirty* tail — and writes the profile only when something was actually granted.

---

## 5. From owned to played

`RunLoadout` turns a profile into the `RunConfig` of the next run, which is what makes a
purchase visible in the game:

| Layer (D8) | Comes from |
| --- | --- |
| `BIRD` | `BirdDef.effects` of the selected bird |
| `BIRD_RAMP` | `BirdDef.rampEffects`, re-evaluated on every passed gate |
| `BIRD_SYNERGY` | `BirdDef.synergyEffects` × `Σ profile.upgrades.values()`, resolved **once** at run start |
| `UPGRADES` | every owned node's `effectsPerLevel` — `FLAT_ADD`/`PERCENT_ADD` scale linearly with the level, `MULTIPLY` compounds as `value^level` |
| `WORLD`, `DIFFICULTY`, `TIER` | the world, its curve and the tier of the run |

Buying `feather_1` at level 1 therefore resolves `GRAVITY` to `1800 × (1 − 0.03) = 1746`, and
`StatSheet.breakdown(stat)` names each contribution by source (`bird:forge`,
`upgrade:feather_1`, `synergy:forge`) and by layer, which is what the selection screen shows.
`RunLoadout.previewStats(profile, content)` builds the run the player is about to start and
reads its sheet, so the screen and the bird can never disagree.

A synergy is resolved once because it is a property of the build the run started with: buying a
node mid-session changes the next run, never the one in progress.

---

## 6. The screens

Three screens spend what the sections above earn (D17). All three read the same evaluators, so
the words the player reads and the arithmetic the run uses cannot drift apart.

| Screen | What it shows | What it writes |
| --- | --- | --- |
| `BirdSelectionScreen` | the seven birds as a `CardGrid` with a procedural portrait in the selected palette, the archetype, and — for a locked one — the **cheapest** way to open it in words; the palette swatches with their conditions; the tier picker with the locked tiers marked (E19); the ability slot area, which says the slots arrive in M5 while `GameContent.playable(ABILITY)` is false; and the stat breakdown of the run that would start right now | `SelectionManager` (bird, palette, tier), `UnlockManager` (Buy) |
| `UpgradeTreeScreen` | one tab per tree, nodes laid out by tier with a line from every prerequisite, each card carrying level/maximum, what one level does in words, the price of the next level and its state (tree locked / prerequisite missing / affordable / maxed / already unlocked); a locked tree shows its condition instead | `UpgradeManager.buy` |
| `ShopScreen` | everything with a `purchase` branch the profile does not own, grouped into four tabs (birds, abilities, worlds, features), cheapest first, each with its price and whether the wallet covers it; an offer that is not playable yet says which milestone it arrives in | `UnlockManager.purchase` |

**Nothing pretends to work (E19).** Three places carry a milestone note instead:

| Where | What it says |
| --- | --- |
| `ShopScreen` | abilities *Arrives in M5*, worlds other than Green Fields *M7*, challenges and achievements *M8*, and — per id, from `GameContent.featureMilestone` — `feature:modifiers` *M6* and `feature:seeded_runs` *M9*. Both features are buyable in M4 and read by nothing until then, so `GameContent.playable(FEATURE, id)` is false for them |
| `UpgradeTreeScreen` | the seven nodes whose whole effect is `ABILITY_COOLDOWN_MULT`, `ABILITY_DURATION_MULT`, `SHIELD_CHARGES` or `REVIVES`, or whose grant is `ABILITY_CAP` / `PASSIVE_SLOT`, on the card and on the stat row: *Arrives in M5*. The stat pipeline resolves all four today and no system consumes any of them |
| `BirdSelectionScreen` | the ability line, which names the bird's innate passives and adds *Arrives in M5* — Ironbeak pays −20 % coins for a shield that does not exist yet, and a line that counted only the slots would present that as a straight upgrade |

The card text of a `CardGrid` is measured and ends in an ellipsis when it does not fit the space
the badge leaves, so a long name or translation says "there is more" instead of stopping mid-word.

The wording comes from `ui/screens/ProgressionText`: stat names and effects
(`-3% Gravity`), modifier sources (`upgrade:feather_1` → the node's name, `synergy:forge` → "Cinder
synergy"), prices, and unlock conditions. For an `any_of` the phrase is the branch the profile is
closest to finishing, measured as the fraction of the threshold still missing and, for the coin
branch, against the wallet — so a beginner reads "Play 3 runs" and a player with 150 coins in hand
reads "150 coins" for the same bird.

The breakdown panel is `RunLoadout.previewStats(profile, content)` and `StatSheet.breakdown(stat)`,
listed as one header row per stat (the resolved value) and one indented row per contribution (the
source and what it does). Buying `feather_1` in the upgrade screen therefore adds a line under
Gravity and drops the number from 1800 to 1746 on both screens at once.

Purchases and selections are atomic and saved immediately (D14, D15): every refusal is a
`PurchaseResult` the screen turns into a toast, and nothing is debited unless everything succeeds.

New components: `Tooltip` (appears after 20 ticks on the hovered or focused node, wraps its text
and is clamped into the playfield), `CardGrid` (cards as focus-ring nodes, so the arrows, Tab,
hover and clicks work as they do for buttons; `locked` draws a padlock, `dimmed` only the veil)
and `TabBar` (left/right while focused, clicks anywhere, disabled tabs skipped by the keyboard but
still clickable).

---

## 7. Prestige (M9)

`PrestigeSystem` lands in M9; the data it will read is already final. At level 25 a prestige
snapshots `prestigeBaseline` from the lifetime statistics, resets coins, experience, level,
upgrades, ability levels, the caps, challenge records and the daily pick, keeps birds,
cosmetics, achievements and statistics, increments `prestigeCount` (max 5), grants
`cosmetic:<selectedBird>:prestige` and pushes `bonusPerPrestige × prestigeCount` into the
`PRESTIGE` layer (E4, E23).

The evaluator already implements the half of it that matters to every other milestone: the
cumulative conditions read "since prestige", so nothing condition-derived is granted twice.

---

## 8. Tests

| Test | Covers |
| --- | --- |
| `UnlockEvaluatorTest` | every condition type, the "since prestige" reading, the purchase and cosmetic rules, collection counters |
| `UnlockManagerTest` | the shop: debit, grant, account, propagate, save; every refusal leaves the profile untouched |
| `UpgradeManagerTest` | costs, prerequisites, level scaling (`GRAVITY` 1746, `MULTIPLY` compounding), grants and their E3 caps, a node whose grant is already owned refused before the debit, and a refund paid only to the profile that owned the removed node |
| `SelectionManagerTest` | a selection is owned, playable and written now |
| `ProgressionManagerTest` | D14's order, applied once per run, and `purchaseTriggersAchievementsAndUnlocks` |
| `ContentWiringTest` | what the profile owns reaches the run the screens play |
| `NewPlayerJourneyTest` (`@Tag("sim")`) | the novice bot on the real economy: `feather_1` bought after run 1 and Ironbeak by run 3 (E17), with the run-by-run table in the failure message. It pins *which* node and *which* run, so doubling a price fails it |
| `SaveManagerTest` | E21's load order end to end (a renamed bird survives normalisation), an owned node the content dropped, and `abilityLevelCap` clamped from above |
| `SmokeWindowTest` (`@Tag("gui")`) | a launch grants the unlocks a saved profile has already earned, and persists them |
| `BirdSelectionScreenTest` | the roster, the cheapest path in words, selection writing and saving, a purchase that moves the wallet and one that cannot, and the breakdown against `StatSheet.breakdown` |
| `UpgradeTreeScreenTest` | the tabs, a locked tree's condition, the tier layout and prerequisites, a bought level moving wallet, card and live stats, every refusal, the M5 note on a node nothing reads yet, and a node whose grant is already owned |
| `ShopScreenTest` | the four tabs, cheapest first, affordability, a purchase leaving the list, and the milestone note on what is not playable yet — including the two features |
| `ProceduralRenderTest`, `SmokeWindowTest` | the three screens headless in both languages, and through a real window with the Robot buying the cheapest bird |

Run them with `./gradlew test` and `./gradlew simTest`.
