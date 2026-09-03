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

The achievement step is `AchievementEvaluator` (M8, D13): every `achievements.json` definition
is one condition `counter op value` in one of three scopes. **`LIFETIME`** reads the profile
through `Statistics.resolve` — a scalar (`totalRuns`), one entry of a map counter
(`bossClears.void`, `bestGatesByTier.hard`), the size of a list (`bossesCleared`) or a
profile-root scalar (`level`, `xp`, `prestigeCount`; E5). **`RUN`** reads the run that just
finished (`run.gatesPassed`, `run.points`, `run.streakBest`, `run.coinsCollected`) and is only
ever judged inside `apply`, where there is a finished run — the purchase pass has none and never
grants one. **`COLLECTION`** reads `collection.<category>.percent` through `CollectionProgress`.
An achievement fires once (an already-held id is skipped), is recorded with the injected
timestamp, pays its `reward` coins through the wallet (counted in `coinsEarned`, E32.a) and
hands its reward's unlocks to the unlock step below. The `hidden` flag changes nothing in the
evaluation — a secret achievement is judged like any other and only its *display* is withheld
until it fires (a `???` row in the screen, no milestone bar). `progressOf` is the Milestones
tab's number: `current` clamped into `[0, target]`, where a `RUN`-scoped achievement reports the
best matching lifetime statistic (`run.streakBest → streakBest`, `run.gatesPassed → bestGates`,
`run.points → bestPoints`) and an already-held one reports `target / target`. The unlock step is
`UnlockEvaluator` from M4 on, and from M8 it starts with the unlocks a first clear pays outright
(E11, E26): `boss.reward.unlocks` of every world cleared for the first time and
`challenge.rewards.unlocks` of a first completion, then the evaluator's own pass. The matching
coins ride the reward formula — `boss.reward.coins` into the boss term, `challenge.rewards.coins`
into the challenge term — through the `RewardContext` the reward step builds before anything is
written (`firstBossClearCoins`, `firstChallengeCoins`), so a repeat clear pays the economy's
`bossBonus` / `challengeBonus` alone and grants nothing. The amounts come from
`ProgressionRules.fromContent(content)` (`FirstClearRewards`); `fromEconomy` keeps the pre-M8
shape with none. A *challenge* boss writes neither `bossesCleared` nor the boss terms: it only
sets `objectiveMet`, and the challenge pays.

---

## 4. Spending

| Class | Buys | Refuses with |
| --- | --- | --- |
| `UnlockManager` | anything with a `purchase` branch: birds, abilities, worlds, tiers, trees, features | `UNKNOWN_ID`, `ALREADY_OWNED`, `NOT_FOR_SALE`, `INSUFFICIENT_FUNDS` |
| `UpgradeManager` | one level of one upgrade node | `UNKNOWN_ID`, `MAX_LEVEL`, `ALREADY_OWNED`, `TREE_LOCKED`, `MISSING_PREREQ`, `INSUFFICIENT_FUNDS` |
| `UpgradeManager.buyAbilityLevel` | one level of one ability (M5) | `UNKNOWN_ID`, `NOT_FOR_SALE` (not unlocked yet), `MAX_LEVEL`, `LEVEL_CAPPED`, `INSUFFICIENT_FUNDS` |

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
player owns and this build can play — from M7 every world plays, so for a world the gate is
ownership alone — and changing the bird repairs the palette, because a palette belongs to one bird.
Every accepted change marks the profile dirty and saves immediately (D15); a rejected one writes
nothing.

### Worlds (M7)

A world is an unlockable like a bird: `world:green_fields` is a default, every other world is
`any_of[world_cleared <previous world>, purchase N]` — Wind Valley 350, Iron Forge 700, Storm Sky
1 200, the Void 2 000 coins. `world_cleared` is written only by a *world* boss (E26). From M8
the boss route is live: surviving a world's encounter (`BossEncounter`, D11) records the world in
`bossesCleared`, and the unlock step grants the same edge for free plus `boss.reward.coins` (200,
300, 400, 500, 800) and, for Wind Valley, `tree:forge`; `UnlockChainTest` walks the Green Fields
clear into `world:wind_valley`. The purchase route stays, so the graph keeps a cumulative path
(§5 of `docs/CONTENT.md`) and a player can never be locked out of a world. A challenge is played
in its world whether or not the world is owned (E6): `RunLoadout.challengeConfigFor` never checks
it, and `ChallengeRunSource` builds the run from the profile's bird, palette and loadout under the
challenge's world, tier, rules, forced cards and boss.

Owning a world makes it selectable: the bird selection screen's world row shows the five worlds
in `order`, the hazards each spawns and, for a locked one, the cheapest way in; selecting writes
`profile.selected.worldId` through `SelectionManager.selectWorld` (owned worlds only). `--world
<id>` pins a world for one launch: an owned world is selected as the picker would, a locked one
is played for that launch with the profile untouched.

### The loadout (M5)

`SelectionManager.selectActiveAbility` and `setPassiveAbilities` write
`profile.selected.activeAbilityId` and `profile.selected.passiveAbilityIds`. The rules (D9, E3):

* **One active, N passives.** A run carries one `ACTIVE` ability plus
  `BirdDef.passiveSlots + profile.passiveSlotBonus` `PASSIVE` ones, plus the selected bird's
  **innate** passives — which occupy no slot, need no unlock and cannot be unequipped (Ironbeak's
  shield is the shipped case).
* **Only what is owned, and only in its own slot.** A locked ability, an unknown id, a passive in
  the active slot and an active in a passive slot are all refused and nothing is written. This is
  the only ownership check on the equip path: a run resolves the loadout from the profile and
  re-checks only that the id exists.
* **Slots hide, they do not delete.** The passive list is stored dense and deduplicated, and it may
  be longer than the current bird can show: switching from Oracle (three slots) to a two-slot bird
  hides the third choice, and switching back restores it. Cycling a slot on the bird that hides it
  must not drop it either.
* **The rules of the run strip, the profile keeps.** `NO_DEFENSIVE_ABILITIES` and `NO_REVIVE` remove
  the tagged abilities (innate ones included) from the loadout of that run, and the bird screen greys
  them out with the reason; nothing is unequipped in the profile.
* **Levels are capped by `min(profile.abilityLevelCap, levels.size())`** — E3's cap starts at 2 and
  is raised to 3 by the single `ability_cap: 1` grant on `forge/master_forge_1`. Level 1 comes free
  with the unlock; levels 2 and 3 are bought with `UpgradeManager.buyAbilityLevel` and stored in
  `profile.abilityLevels`. `PlayerProfile.normalize` clamps the cap from both sides against
  `UpgradeManager.abilityLevelCeiling`, so a save that carries a higher cap than the build supports
  cannot buy a level that does not exist.
* **The passive slot bonus is capped at +1** (`economy/ability_scholar_1`), and
  `passiveSlots + bonus` may never exceed 4 — the validator enforces both, and the bird screen shows
  the extra chip as soon as the grant is owned.

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
| `ABILITY` | the equipped passives' `effects` for the whole run, plus the active's while its duration runs (M5) |
| `WORLD`, `DIFFICULTY`, `TIER` | the world, its curve and the tier of the run |
| `MODIFIERS`, `MOD_SYNERGY` | the cards drafted mid-run and the set bonuses they complete (M6, §5.1) |

Buying `feather_1` at level 1 therefore resolves `GRAVITY` to `1800 × (1 − 0.03) = 1746`, and
`StatSheet.breakdown(stat)` names each contribution by source (`bird:forge`,
`upgrade:feather_1`, `synergy:forge`) and by layer, which is what the selection screen shows.
`RunLoadout.previewStats(profile, content)` builds the run the player is about to start and
reads its sheet, so the screen and the bird can never disagree.

A synergy is resolved once because it is a property of the build the run started with: buying a
node mid-session changes the next run, never the one in progress.

### 5.1 The draft (M6)

Two fields of `RunConfig` decide whether a run drafts, and both come from `RunLoadout`:

- **`allowOffers`** — `profile.isUnlocked("feature:modifiers")` **and**
  `content.playable(FEATURE, "modifiers")`. The first half is the gate a player passes: the
  feature is earned at 7 runs or bought for 150 coins, and it is on a cumulative path like every
  other non-cosmetic unlockable, so a player who never gets past gate 3 still reaches it. The
  second half was a milestone gate rather than a rule of the game — the pool, the director and
  the two stat layers were complete before `ModifierChoiceOverlay` existed, and a run that froze
  on a draft nobody could answer would have been worse than no draft at all. `GameContent`
  no longer names a milestone for `modifiers`, so today the gate is the unlock alone. A challenge
  may still turn drafts off for its own run (`ChallengeDef.allowOffers`).
- **`availableModifiers`** — every modifier whose `modifier:<id>` the profile owns. The three
  legendaries (`gold_rush`, `phoenix`, `stormrider`) are earned at level 8 or bought for 300
  coins each; everything else ships `unlock: default` and is in the pool from the first draft.
  `ModifierCatalog.of` keeps a `default` card available whether or not the evaluator has written
  it into `unlocked` yet, so a fresh profile never sees a thinner draft than it should.

When both are satisfied, drafts open at gates 10, 25, 45, 70, 100 and 140 (D11): the run enters a
**breather**, the next obstacle is pushed out, and the cards go up once the air ahead of the bird
is clear. The player takes one or skips; a 45-tick hold counts 3-2-1 and the resume grants 30
invulnerability ticks. What was taken is in the HUD's build strip while the run lasts, in
`RunResult.stats().modifiersTaken()` / `synergiesActivated()` when it ends, on the summary screen,
and in the profile's two lifetime map counters (`statistics.modifiersTaken`,
`statistics.synergiesActivated`) that the statistics screen totals.

**Forced modifiers** are the other way a run gets cards. `RunConfig.forcedModifiers` is filled by
the run *source* — a challenge's `forcedModifiers`, and M9's daily — never by the profile, and the
cards are taken before the first tick, so they count for synergies exactly like drafted ones. Two
consequences worth knowing:

- **Ownership does not apply.** `RunFactory` unions the forced ids into the run's catalogue, so a
  challenge that forces `gold_rush` works on a profile that has not unlocked it. Only the *offer*
  pool depends on what the profile owns.
- **The authored rules still apply.** `ModifierDirector` takes them one at a time under
  `maxStacks`, `excludes` and `requiresFlagsAbsent`, so a list that breaks one of those quietly
  loses a card — which is why the validator rejects all three in `challenges.json` (see
  `docs/CONTENT.md` §4). An id that resolves to no card at all is a broken reference and throws.

---

## 6. The screens

Five screens spend what the sections above earn (D17). All five read the same evaluators, so
the words the player reads and the arithmetic the run uses cannot drift apart.

| Screen | What it shows | What it writes |
| --- | --- | --- |
| `BirdSelectionScreen` | the seven birds as a `CardGrid` with a procedural portrait in the selected palette, the archetype, and — for a locked one — the **cheapest** way to open it in words; the palette swatches with their conditions; the tier picker with the locked tiers marked (E19); the loadout row — one active chip, one chip per passive slot the bird and the `passive_slot` grant give, and a fixed chip per innate passive — with the ability panel beside it (level, next level's price, and what each level does); and the stat breakdown of the run that would start right now | `SelectionManager` (bird, palette, tier, loadout), `UnlockManager` (Buy), `UpgradeManager.buyAbilityLevel` |
| `UpgradeTreeScreen` | one tab per tree, nodes laid out by tier with a line from every prerequisite, each card carrying level/maximum, what one level does in words, the price of the next level and its state (tree locked / prerequisite missing / affordable / maxed / already unlocked); a locked tree shows its condition instead | `UpgradeManager.buy` |
| `ShopScreen` | everything with a `purchase` branch the profile does not own, grouped into four tabs (birds, abilities, worlds, features), cheapest first, each with its price and whether the wallet covers it; an offer that is not playable yet says which milestone it arrives in | `UnlockManager.purchase` |
| `ChallengesScreen` (M8) | the seven challenges in content order with a detail block — the world (labelled, never checked for unlocks, E6), the tier, the objective in words, the rewards, the forced modifiers and the challenge's own boss when it has one; each row carries the record `challenges.<id>` holds (attempted/completed) | pushes a `GameScreen` over itself through `ChallengeRunSource` (`RunLoadout.challengeConfigFor`), the profile's bird, palette and loadout under the challenge's world, tier, rules, forced cards and boss |
| `AchievementsScreen` (M8) | the three D13 tabs: *Achievements* — every definition in content order, unlocked ones with their unlock date, locked ones dimmed, hidden ones a `???` until they fire, header counting them; *Milestones* — the level progress bar, then the next five thresholds among unclaimed level rewards and not-yet-fired lifetime-threshold achievements, nearest first, each with a `progressOf` bar (hidden achievements stay out of the list); *Collections* — one bar per category of `CollectionProgress`, owned over total with the floored percentage, `all` last | nothing — a read-only view, like `StatisticsScreen` |

**Nothing pretends to work (E19).** Three places carry a milestone note instead:

| Where | What it says |
| --- | --- |
| `ShopScreen` | — per id, from `GameContent.featureMilestone` — only `feature:seeded_runs` *Arrives in M9* is left, which is buyable and read by nothing until then, so `GameContent.playable(FEATURE, id)` is false for it. Abilities carried the same note until M5 turned them on, `feature:modifiers` until M6 did, the four worlds behind Green Fields until M7 did, and challenges and achievements until M8 did (`BossEncounter`/`ObjectiveEvaluator` and `AchievementEvaluator`) |
| `UpgradeTreeScreen` | nothing any more. The seven nodes whose whole effect is `ABILITY_COOLDOWN_MULT`, `ABILITY_DURATION_MULT`, `SHIELD_CHARGES` or `REVIVES`, or whose grant is `ABILITY_CAP` / `PASSIVE_SLOT`, carried *Arrives in M5* on the card and on the stat row; M5 consumes all of them and the note is gone |
| `BirdSelectionScreen` | the ability line still names the bird's innate passives, without a milestone note: Ironbeak's −20 % `COIN_MULT` buys a shield that is live from M5 (`docs/BALANCING.md` §7.1 measures it at +96 % gates for the `average` bot) |

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
| `NewPlayerJourneyTest` (`@Tag("sim")`) | the novice bot on the real economy: `feather_1` bought after run 1, Ironbeak by run 3, the shield by run 5 and `feature:modifiers` by run 7 (E17), with the run-by-run table in the failure message. It pins *which* node and *which* run, so doubling a price fails it |
| `SaveManagerTest` | E21's load order end to end (a renamed bird survives normalisation), an owned node the content dropped, and `abilityLevelCap` clamped from above |
| `SmokeWindowTest` (`@Tag("gui")`) | a launch grants the unlocks a saved profile has already earned, and persists them |
| `BirdSelectionScreenTest` | the roster, the cheapest path in words, selection writing and saving, a purchase that moves the wallet and one that cannot, the breakdown against `StatSheet.breakdown`, and the M5 loadout row: the chips a bird's slots and the `passive_slot` grant give, cycling one, and that cycling never drops a passive the bird cannot show |
| `SelectionManagerTest` | every selection refusal, including a locked ability, a passive in the active slot and a passive list that mixes locked, non-passive and duplicate ids |
| `UpgradeTreeScreenTest` | the tabs, a locked tree's condition, the tier layout and prerequisites, a bought level moving wallet, card and live stats, every refusal, and a node whose grant is already owned |
| `ShopScreenTest` | the four tabs, cheapest first, affordability, a purchase leaving the list, the three modifier legendaries at 300 coins each, and the milestone note on what is not playable yet |
| `ModifierDirectorTest`, `ModifierPoolTest`, `SynergyResolverTest`, `ModifierChoiceOverlayTest` | the draft the feature gate opens: the schedule, the breather, the freeze, the hold, forced cards under the authored rules, the weighted draw, E12's two halves of eligibility and E16's two-entry rule |
| `AchievementEvaluatorTest` (M8) | the three scopes, every counter shape (scalar, map entry, list size, profile root), the compare ops, a `RUN` achievement never granted by the purchase pass, `progressOf`'s lifetime bests for `RUN` conditions and the full bar for an already-held id |
| `CollectionProgressTest` (M8) | per-category owned/total and the floored percentage, `all` last, agreement with `UnlockEvaluator`'s counter arithmetic |
| `ProgressionManagerTest`, `UnlockChainTest` (M8) | a first world clear granting `boss.reward` and paying the boss term (E26: a challenge boss grants neither), a first challenge completion paying `challenge.rewards` (E11) and repeats paying the bonus alone, hidden achievements evaluating like any other, and E17's purchase-fired achievements |
| `AchievementsScreenTest`, `ChallengesScreenTest` (M8) | the three tabs (unlock dates, `???` rows, the five milestone bars, the collection bars), and the challenge list's detail block, records and locked state with E6's no-unlock-check rule |
| `ProceduralRenderTest`, `SmokeWindowTest` | the three screens headless in both languages, and through a real window with the Robot buying the cheapest bird |

Run them with `./gradlew test` and `./gradlew simTest`.
