# Forge / Upgrades architecture — M13

## Current state

This is a **rebuild of an existing Forge, not a greenfield screen**. The current implementation is
`src/main/java/io/github/michelbr84/flapforge/ui/screens/UpgradeTreeScreen.java:76`, which already
renders the three data-driven trees, two-column tier bands, prerequisite elbows, node state, a
text-only detail panel, a live stat panel, a wallet readout, and the old full-width Back button.
`src/main/java/io/github/michelbr84/flapforge/ui/screens/ForgeScene.java:29` is a different class:
it is the anvil/bird illustration in the hub and is not the upgrade-tree screen.

The shipped content is already three trees and 18 nodes in
`src/main/resources/data/upgrades.json`; `UpgradesDef`, `TreeDef`, and `UpgradeDef` bind that shape.
The canonical visual specification and its companion JSON are target designs for this same content,
not a request to duplicate a new content set. The implementation must preserve the shipped graph,
including `updraft_1`'s prerequisites and the absence of a prerequisite on `quick_recharge_1`.
The one real content correction in M13 is `upgrade.glide_1.desc`: it must stop saying -25% per
level and agree with the shipped effect of -30% (and the L2 override of -35%). ff-tree and ff-l10n
own that correction as specified in the ownership table below.

### Seam summary

The primary seam is the existing `UpgradeTreeScreen` at class declaration line 76 and its existing
state/purchase methods:

* `rebuild()` at line 364 constructs cards and data-driven prerequisite links.
* `refreshState()` at line 436 derives ownership, lock, affordability, max, redundancy, prices, and
  the selected detail/stat state.
* `buildDetail()` at line 656 and `buildStats()` at line 696 are the presentation-model seams for
  the enlarged panel.
* `buy()` at line 733 currently calls `UpgradeManager.buy(profile, nodeId, content)` directly from
  a card. M13 changes card activation to selection and makes the detail CTA the only purchase
  activation path.
* `render()` at line 831 remains the AWT/Java2D drawing seam. New drawing stays in `ui` and
  `ui/component`; no content, progression, or gameplay class draws.

The rebuilt screen remains an existing `Screen` pushed/replaced through `ui/ScreenManager`; it is
not a new progression service and does not require a second Forge screen. The screen should follow
the M12 split pattern used by `ShopScreen`, `ShopArt`, and `ShopCard`: reusable UI geometry/art may
move to new `ui/component` classes owned by ff-ui, while `UpgradeTreeScreen` coordinates selection,
layout, content, state, and routes. `ForgeScene` remains the hub art seam and is changed only if the
new Forge illustration is deliberately shared with it; otherwise the screen owns its compact header
art.

The existing progression APIs are the authoritative transaction seams:

* A level purchase is `UpgradeManager.buy(profile, id, content)`.
* A tree purchase is `UnlockManager.purchase(profile, "tree:<id>", content)`; the unlock id comes
  from `TreeDef.unlockableId()` rather than a second hard-coded content convention.
* Preview values are obtained from `RunLoadout.previewStats(profile, content)`, then
  `StatSheet.resolve(stat)`.
* Availability and no-op protection use `UpgradeManager.isAvailable(profile, id, content)` and
  `UpgradeManager.isRedundant(profile, id, content)` (the latter must be evaluated before debit).
* Both transactions report `PurchaseResult` and `PurchaseStatus`; only `PurchaseStatus.OK` mutates
  the profile. The screen must not infer success from wallet arithmetic.

## Layout budget for the fixed 420x640 canvas

`SectionNav.TOP` is 582 and `SectionNav.HEIGHT` is 58. The following bands are logical pixels and
cover every row exactly once.

| Region | y-range (end exclusive) | Height | Holds |
|---|---:|---:|---|
| Compact header | 0..100 | 100 | Screen title, per-tree subtitle, wallet/coin pill, and a compact version of the tree illustration |
| Category tabs | 100..140 | 40 | Three icon-over-label tree tabs |
| Scrollable tree viewport | 140..330 | 190 | Tier pills, cards, and yellow prerequisite connectors; clipped scrolling content |
| Detail and stat panel | 330..582 | 252 | Selected-node hero/details, status, CTA/notice, divider, and live stat rows |
| Section navigation | 582..640 | 58 | `SectionNav`, five labelled tiles, Forja active |
| **Total** | **0..640** | **640** | **Complete canvas** |

The mock's 721-row budget (134 + 45 + 228 + 232 + 82) is not scaled by growing the shared
playfield. It is compressed by 81 rows: the header loses 34 rows (the illustration is compacted to
fit the title/subtitle/wallet rather than receiving a 134-row scene), tabs lose 5 rows, the tree
viewport loses 38 rows and remains scrollable, the detail panel gains 20 rows relative to the mock's
usable 232-row panel after its neighbouring bands are compressed, and the nav is the fixed 58-row
component rather than the mock's 82-row phone/safe-area treatment. No interactive foot strip is
added. The illustration is decorative and compact; it must never displace the wallet, tabs, or CTA.

The detail panel is 252 rows. It uses a smaller hero icon and glow than the reference, a compact
single-line-or-wrapped description/status block, and a fixed CTA region. It must reserve enough
vertical space for the canonical stat set: five rows for flight and forge and four for economy.
Long localized strings wrap or ellipsise within the panel; they do not expand the canvas. The tree
viewport scrolls exactly as today's tree does (`scroll -= wheel * WHEEL_STEP`, with the existing
input convention), and cards outside the clipped viewport do not answer pointer input. Cards are
selected on tap; tapping a card does not buy it.

The nav remains `SectionNav` at `ui/component/SectionNav.java:27-30`. Its shared Play glyph is
**not** changed: the shipped `icon("play")` at line 88 draws crossed hammers, intentionally
 differing from the reference triangle. This is a deliberate M13 deviation, not a Forge-local art
fix.

## Data flow and workstream touch points

```text
src/main/resources/data/upgrades.json
        |
        v
content.defs: UpgradesDef -> TreeDef / UpgradeDef
        |  (content/StrictBinder; unknown JSON keys rejected)
        v
content/GameContent  <---------------- src/main/resources/data/economy.json
        |
        +--> content/UnlockGraph / progression/UnlockEvaluator
        |
        +--> progression/UnlockManager.purchase(profile, "tree:<id>", content)
        |          \--> Wallet + PlayerProfile + ProgressionManager + save trigger
        |
        +--> progression/UpgradeManager.buy(profile, id, content)
                   \--> Wallet + profile.upgrades/grants + ProgressionManager + save trigger
        |
        v
progression/RunLoadout.previewStats(profile, content)
        |
        v
gameplay/stats/StatSheet.resolve(stat)
        |
        v
gameplay/Simulation (the same run/stat pipeline used by the next run)
```

M13's UI reads the graph/content path for tree/node names, tiers, effects, prerequisites, prices,
and unlock amounts; it reads the progression path for `PurchaseStatus`; it reads
`RunLoadout.previewStats` for the live detail summary; and it reads `StatId.min()`/`max()` for
pips. No UI value is a second physics calculation. The three purchase paths are:

* ff-tree owns JSON/records/graph and the upgrade and tree transaction rules.
* ff-economy owns wallet/currency primitives and purchase result plumbing shared by transactions.
* ff-gameplay owns the resolved stat and simulation consumers; it must not know about drawing.
* ff-ui owns selection, scrolling, CTA state presentation, rendering, and localization lookups.
* ff-l10n owns the two tables and `StringKey` additions; all player-visible new copy is looked up,
  never embedded in Java.

The `StatId` range link already exists: `StatId` exposes `defaultValue()`, `min()`, `max()`, and
`clamp()` (`src/main/java/io/github/michelbr84/flapforge/gameplay/stats/StatId.java:49-97`). No
new gameplay API is required for pip normalization. For a resolved value `v`, compute the filled
count as `round(clamp((v - min) / (max - min), 0, 1) * 5)`, with the zero-width range case
specified as 0 pips if a future stat introduces one. Use the resolved value, not owned level and
not the mock's literal pip counts.

## Purchase-state machine

The selected card always supplies the node id. The CTA is enabled only for a transaction that the
corresponding manager can validly perform. A card's visual lock/dimming is not permission to debit.
The states are evaluated in transaction order, with tree lock taking precedence for the CTA route.

| Panel/CTA state | Producing condition | Existing status / route | CTA reachability and panel behavior |
|---|---|---|---|
| Buyable level | Tree unlocked, node below max, not redundant, all prerequisites owned, wallet covers next cost | `PurchaseStatus.OK` from `UpgradeManager.buy` | Enabled `MELHORAR • {price}`. CTA calls `buy(profile, id, content)`; refresh wallet, card, details, and stats. |
| Unaffordable level | Same structural conditions, wallet below next cost | `INSUFFICIENT_FUNDS` | CTA displays the price but is disabled (or performs a no-op failure presentation); red insufficient-funds copy is shown. A click must never debit. |
| Tree locked | `profile` lacks `tree:<tree>`; the selected node may or may not have prerequisites | `UnlockManager.purchase(profile, "tree:<tree>", content)` returns `OK` or `INSUFFICIENT_FUNDS`, not an upgrade purchase | CTA is `DESBLOQUEAR ÁRVORE • {amount}`. It sells the tree unlock, including economy/forge. The panel shows the tree-lock amount and does not expose the node's level price as a purchase. |
| Maxed | Owned level is at `maxLevel` | `MAX_LEVEL` | No purchase CTA; show maxed state and keep the CTA disabled/non-actionable. |
| Already-owned/redundant | Node is below max but `UpgradeManager.isRedundant(...)` is true; shipped example is `hard_tier_1` after `tier:hard` is already owned | `ALREADY_OWNED` | No purchase CTA; show already-owned/redundant state and disable the CTA. `isRedundant` must remain before any debit. |
| Missing prerequisite | Tree unlocked, below max, non-redundant, but one or more `prereqs` is below level 1 | `MISSING_PREREQ` | No level-buy CTA; show missing-prerequisite explanation and disabled CTA. The card remains locked/padlocked and has no price badge. |
| Tree locked **and** a prerequisite is missing | Both conditions hold | The tree-unlock route is selected first; after unlock, a refresh evaluates `MISSING_PREREQ` | Before unlock, show `Árvore bloqueada` and the tree price; the CTA sells only `tree:<id>`. Do not incorrectly claim that the node can be bought or show its node price. After a successful unlock, the same selection refreshes to the missing-prerequisite state until prerequisites are bought. |
| Tree-unlock unaffordable | Tree locked and wallet below the tree's unlock amount | `INSUFFICIENT_FUNDS` from `UnlockManager.purchase` | Show the unlock CTA with amount and insufficient-funds warning; disabled/non-mutating. |
| Tree-unlock already owned race | The profile becomes unlocked between selection and activation | `ALREADY_OWNED` from `UnlockManager.purchase` | Refresh and reevaluate as an unlocked-node state; never debit. This is defensive even though the normal loop is single-threaded. |

`TREE_LOCKED` remains a valid result from `UpgradeManager.buy` for non-CTA callers and defensive
failure paths, but the Forge CTA must not call that method while the tree is locked. `UNKNOWN_ID`,
`NOT_FOR_SALE`, and (for ability-level code outside this screen) `LEVEL_CAPPED` are not normal
selected-node CTA states; unexpected results are shown through the existing failure/toast path and
must leave all profile fields unchanged. There is no need for a new `PurchaseStatus` outcome for
M13: the unlock CTA uses the existing `UnlockManager` route and the existing `OK`/
`INSUFFICIENT_FUNDS`/`ALREADY_OWNED` values.

The critical invariant is unchanged: a purchase that would deliver nothing is refused before debit.
`UpgradeManager.isRedundant` is already explicitly used before wallet spending, and `hard_tier_1`
must remain `ALREADY_OWNED` when its only grant is already present. The UI may predict a state, but
only the manager's returned `PurchaseResult` determines mutation and feedback.

## File ownership map

One implementation owner is assigned per file. A reviewer may inspect every file and make only a
small mechanical correction after the owning agent agrees; that review privilege is not a second
feature owner. If two workstreams need the same file, the ordered build sequence below makes the
content/progression edit precede the UI edit.

| Owner | Files / scope | Verified status and M13 responsibility |
|---|---|---|
| ff-tree | `src/main/resources/data/upgrades.json`; `content/defs/UpgradeDef.java`, `UpgradesDef.java`, `TreeDef.java`; `progression/UpgradeManager.java`; `content/UnlockGraph.java`; **`progression/UnlockManager.java`**; **`progression/PurchaseStatus.java`** | Confirmed records and graph exist. Owns the `glide_1.desc` data correction and the Forge-facing tree-unlock purchase path. `UnlockManager` is not an orphan: assign it here because it owns tree purchase semantics. `PurchaseStatus` also belongs here; no new enum value is currently required, but any outcome change must be made and tested here. Preserve pre-debit redundancy checks. |
| ff-gameplay | `progression/RunLoadout.java`; `gameplay/stats/StatId.java`, `StatOp.java`, `EffectStack.java`, `StatSheet.java`; `gameplay/run/ShieldSystem.java`, `ReviveSystem.java`; `gameplay/WorldEffects.java`; `gameplay/Simulation.java` | Confirmed all named files exist. `StatId` already exposes min/max, so expose nothing new for pips. This owner verifies that preview stats and consumers remain unchanged; no UI imports into these packages. |
| ff-economy | `progression/Wallet.java`, `PurchaseResult.java`, `ProgressionRules.java`; `src/main/resources/data/economy.json` | Confirmed named Java files and economy content seam. Owns currency/result contracts if the CTA needs no new status; do not duplicate tree unlock logic. |
| ff-persistence | `progression/ProfileSchema.java`, `PlayerProfile.java`, `ProgressionManager.java`; `src/main/java/io/github/michelbr84/flapforge/persistence/**`; `docs/SAVE_SYSTEM.md`; `docs/PROGRESSION.md` | Confirmed persistence and profile files exist. M13 should not alter persisted shape. Any proposed profile/save change requires a v1 migration owned here, not a silent field swap. |
| ff-nav | `ui/component/SectionNav.java`, `NavBar.java`; `ui/ScreenManager.java`; `ui/screens/MainMenuScreen.java` | Confirmed all named files exist. SectionNav geometry/glyph is shared and already fixed at 582..640; do not change the Play glyph for Forge. Routes into/out of Forge belong here. |
| ff-l10n | `src/main/resources/data/strings/en.json`, `pt_BR.json`; `content/StringKey.java` | Confirmed both tables and StringKey are the localization seam. Add identical keys for CTA labels, subtitles, nav labels, detail/stat headings and any new status copy. Keep shipped pt-BR translations (`Camada`, `Nv`, `Peso Pena`, `Difícil`, `Ressurreições`, `Área de colisão`) and do not copy the mock's `multiplicaddr` typo. |
| ff-ui | `ui/screens/UpgradeTreeScreen.java`; `ui/screens/ForgeScene.java`; new `ui/component/**` Forge art/card/detail helpers | Confirmed `UpgradeTreeScreen` is the existing screen and `ForgeScene` is separate hub art. Owns selection-on-tap, CTA focus/activation, 420x640 layout, clipping/scrolling, connector styling, hero/pips, and language refresh. Reuse the M12 `ShopScreen` split pattern without moving progression logic into components. |
| ff-ui (corrected from the lead's provisional map) | `ui/screens/ProgressionText.java` | This is a UI presentation formatter in the `ui/screens` package, not a content/l10n table. It reads `Strings` and formats player-facing progression text, so ff-ui owns the file. ff-l10n owns only keys/tables; coordinate key names and translated values. |
| ff-qa | `src/test/**` | Confirmed Forge, nav, render, content, allocation, and localization tests are under this tree. Add/adjust tests for selection-versus-buy, tree unlock CTA, status ordering, pips, clipping, language parity, and render budgets. |
| ff-reviewer | Read-only review of all touched files; minimal mechanical patches only after the owning agent's approval | Not a feature owner and must not split ownership or invent API. |
| lead | `CHANGELOG.md`; `docs/BALANCING.md`; merge and commits | Owns milestone bookkeeping, balancing-note coordination if numbers change, and integration. M13 must not change prices/physics without the required balancing remeasurement. |

`content/UnlockGraph.java` is verified to exist and is used by `UnlockEvaluator`; it is content
model/graph construction, so ff-tree is the correct owner. No `UnlockGraph` class should be added.

## Purity and determinism check

The rebuild can be completed without violating the package purity boundary. AWT/Java2D references
remain confined to `ui`, `render`, and the existing screen/component layer. `content`,
`progression`, `gameplay.*`, and `persistence` continue to exchange records, enums, values, and
`PurchaseResult`, never `Graphics2D` or `java.awt` types. Pips are arithmetic over a resolved
`StatSheet`; they do not require randomness, time, or rendering imports.

No M13 work needs `Math.random`, wall-clock time, an unseeded `Random`, threads, or executors. The
screen's scroll, selection, and glow are presentation state; if an illustration animates, it must
use the existing tick-driven deterministic pattern, not wall-clock time. The headless classic run
must remain pinned at `hash=eaaa01685261a433`: the Forge is not in that simulation path, and no
content/stat/physics number may be changed as a visual shortcut.

Java remains 17 with `--release 17`, `-Xlint:all,-serial -Werror -parameters`; use no pattern-
matching `switch` or record patterns. Do not use runtime record reflection, especially in any path
that can reach Android. Every new player-facing phrase must have a `StringKey` and entries in both
language tables; screens must rebuild or reread cached text when `strings.language()` changes.

## Ordered build sequence and parallel work

1. **ff-tree first:** verify the actual shipped JSON/records/graph and correct
   `upgrade.glide_1.desc`; verify the existing `UnlockManager` tree purchase route and
   `PurchaseStatus` ordering. ff-l10n can inspect the required key inventory in parallel, but its
   table edit should land after the final copy decisions.
2. **ff-gameplay and ff-economy in parallel:** confirm `StatId.min/max` and the unchanged preview
   path; confirm wallet/result contracts and that no new status is needed. No gameplay code should
   wait on the UI.
3. **ff-persistence in parallel with step 2:** explicitly freeze save shape. If no profile field is
   added, no migration is needed; if one is proposed, stop and sequence a migration before UI.
4. **ff-nav:** can proceed in parallel with steps 2–3 because SectionNav already exists, but must
   not change its shared glyph or geometry without lead approval. Route integration must be
   complete before the Forge focus ring is finalized.
5. **ff-l10n:** after the UI copy inventory is agreed, add `StringKey` constants and exactly matching
   EN/pt-BR keys. Content correction and translation correction are separate concerns; run the
   identical-key-set check before ff-ui consumes new keys.
6. **ff-ui:** after steps 1 and 5, rebuild `UpgradeTreeScreen` and add only the needed UI components.
   It can develop art/layout scaffolding in parallel with ff-qa, but purchase-state integration
   must use the already verified manager APIs. Sequence any `ProgressionText` edits with ff-l10n
   key availability.
7. **ff-qa:** can write focused tests in parallel once public UI contracts are agreed; final tests
   must follow the integrated UI and content. Include both locked-tree selections and unlocked
   prereq failures.
8. **ff-reviewer and lead:** review all seams, update balancing/changelog only if applicable, then
   integrate. The project gate remains `build` and `contentCheck`; Android record behavior needs an
   emulator check for any Android-touching change, not merely Robolectric.

## Risk list and mitigations

* **Determinism hash `eaaa01685261a433`.** A visual rebuild must not alter run construction,
  upgrade effects, prices, or stat resolution. Keep all changes in UI/content-copy/transaction seams,
  run the pinned headless assertion, and reject any accidental gameplay-number edit.
* **StringsTest identical key sets.** New CTA, subtitle, nav, status, and heading copy can drift
  between languages. Add every key to `en.json` and `pt_BR.json` in one ff-l10n change and test
  language switching plus cached-text refresh at 1.5x scale.
* **ProceduralRenderTest `Meta.upgrades` and per-language non-identical assertion.** Preserve the
  render metadata label and ensure the Forge has real language-dependent text without copying EN
  literals into pt-BR. Keep all procedural art deterministic and allocation-aware.
* **UpgradeTreeScreenTest.** Existing tests assume card activation buys, a Back button, old bounds,
  and old detail/stat accessors. Update the contract deliberately: card activation selects, CTA buys,
  Back key remains, nav replaces the visible Back button, tree scrolling and real prerequisite
  endpoints remain. Do not make old test assumptions silently pass with a second purchase route.
* **SectionNavTest/NavBarTest.** SectionNav is shared by hub screens. Do not alter `TOP`, `HEIGHT`,
  layout constants, route ids, or the crossed-hammers Play glyph for a Forge-only visual match.
  Test Forge's active Forja item and every route independently.
* **1.5x text-scale reflow.** The compressed 640 layout has less spare height than the mock. Use
  measured/wrapped/ellipsised localized text, keep CTA and nav inside their bands, and test all
  three trees in both languages at `Fonts.textScale() == 1.5`.
* **`MENU_ALLOCATION_BUDGET_BYTES` (96 KiB).** A large detail glow, five pip bars, card icons,
  connectors, and text measurement can allocate every frame. Precompute colors/shapes/painters,
  cache measurements by source/room/scale, avoid per-render collections and strings, and profile
  the Forge render specifically rather than assuming the existing bird-selection budget covers it.
* **Save-v1 compatibility.** UI purchases still use existing atomic managers and existing profile
  fields. Do not persist selection, pip state, scroll, or CTA state. Any new persisted field must
  be versioned and migrated from frozen v1 by ff-persistence.
* **Tree-unlock debit/refund bugs.** The new CTA creates a second visual entry point to an existing
  sale. Call `UnlockManager.purchase` with exactly `tree:<id>`, use its `PurchaseResult`, and never
  manually spend coins or unlock the profile. This preserves pre-debit affordability and atomic
  save/propagation behavior.
* **Redundant grant loss.** `hard_tier_1` can appear buyable while its grant is already owned.
  Keep `UpgradeManager.isRedundant` before debit, render `ALREADY_OWNED`, and test wallet,
  upgrades, grants, and statistics are unchanged on refusal.
* **Mock/data divergence.** The mock's literal connectors, pip counts, English fragments, and
  `multiplicaddr` typo are not authoritative. Derive endpoints from `UpgradeDef.prereqs`, pips
  from the live `StatSheet` and `StatId` range, and text from shipped localization.
* **Android runtime record reflection.** Do not introduce reflection to inspect content or UI
  records. Use declared accessors and ordinary iteration; an emulator is required for any Android
  build-path change.
* **Wheel direction and clipping regressions.** Preserve the existing intentional desktop wheel
  convention and mirror the same scroll semantics on Android. Clamp hit testing to the visible tree
  viewport so an offscreen card cannot buy or select through the CTA.
* **Balancing documentation drift.** M13 must not change a price, reward, or physics number merely
  to match the picture. If the `glide_1` copy correction reveals a numerical content change rather
  than stale text, stop and have the lead rerun `docs/BALANCING.md` measurements.

## Open questions

* **OPEN QUESTION — exact compact illustration geometry.** The visual spec marks the procedural
  banner/anvil details as uncertain and gives no 420x640 crop. The ff-ui implementation should settle
  this by measuring the rendered header at the fixed budget and ensuring title, subtitle, wallet, and
  tabs never overlap; no content or API decision depends on it.
* **OPEN QUESTION — exact connector route when multiple edges cross several rows.** The required
  style is fixed (opaque yellow, about 3 logical pixels, Manhattan, under cards, merge/T junctions),
  but the spec does not prescribe the elbow x-coordinate. ff-ui should settle it with a deterministic
  centre-anchor routing rule and ff-qa should assert endpoint identity against `prereqs`.
* **OPEN QUESTION — exact disabled-CTA affordance.** The state machine is fixed, including the
  status and non-mutation rules, but the mock does not show maxed/redundant/missing-prereq CTA
  treatment. ff-ui should choose a disabled button/neutral status treatment that remains readable
  at 1.5x; it must not invent a new `PurchaseStatus` merely for appearance.
* **OPEN QUESTION — exact detail text wrapping and status-line priority.** The reference is
  inconsistent about showing both next-level and tree-lock lines. The architecture requires the
  tree-lock line and unlock CTA to win while locked; ff-ui must settle the compact line breaks and
  whether the next-level effect is secondary explanatory text, without making it actionable before
  unlock.
* **OPEN QUESTION — foot-strip semantics.** The reference may include phone chrome below the nav,
  but the fixed `SectionNav` consumes 582..640 and no interactive foot strip is planned. Only a
  future platform-specific safe-area requirement could change this; it would need a lead ruling and
  Android measurement.
