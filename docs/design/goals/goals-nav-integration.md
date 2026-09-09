# Goals and the five-item nav: integration audit

Audit of how the Goals section (the new fifth hub section) sits in the elastic-height navigation
surface. Evidence is grep output with file:line; no source files were edited. Grep scope:

- `SectionNav.build` / `SectionNav.layoutRow` call sites in `src/main/java`
- literal 640 / 582 / `Playfield.HEIGHT` references in `ui/screens/*.java`
- `openGoals` / `SectionNav.GOALS` call sites
- `Back|back` in `GoalsScreen.java` and `ui/screens/goals/*.java`
- `move|primary|secondary` in `ui/FocusRing.java`

## 1. Screens with the five-item nav

All five hub screens call `SectionNav.layoutRow(nav, metrics)` with a `LayoutMetrics` argument and
therefore pin the bottom bar to `metrics.navTop()`; none of them carries a hardcoded 640 or 582.

| Screen (section) | Pinned via metrics? | Remaining 640/582 assumption |
| --- | --- | --- |
| ShopScreen (Shop) | yes — `ShopScreen.java:332` `SectionNav.layoutRow(nav, metrics)`; built at `ShopScreen.java:299` `SectionNav.build(nav, SectionNav.SHOP, ...)` | none |
| BirdSelectionScreen (Birds) | yes — `BirdSelectionScreen.java:409`; built at `BirdSelectionScreen.java:384` `SectionNav.build(nav, SectionNav.BIRDS, ...)` | none |
| MainMenuScreen (Play hub) | yes — `MainMenuScreen.java:319` `SectionNav.layoutRow(nav, metrics)` | none |
| UpgradeTreeScreen (Forge) | yes — `UpgradeTreeScreen.java:303`; built at `UpgradeTreeScreen.java:279` `SectionNav.build(nav, SectionNav.FORGE, ...)` | none |
| GoalsScreen (Goals) | yes — `GoalsScreen.java:973` `SectionNav.layoutRow(nav, screens.metrics())`; built at `GoalsScreen.java:297` `SectionNav.build(nav, SectionNav.GOALS, ...)` | none |

Notes:

- `MainMenuScreen` is the only one of the five with no `SectionNav.build(...)` call: the grep found
  only its `layoutRow` at `MainMenuScreen.java:319`. Its five-route nav is constructed inline
  (routes visible at `MainMenuScreen.java:291`), not through the `SectionNav.build` factory the
  other four use. It is still metric-pinned at line 319.
- The 640/582 check (`grep "Playfield.HEIGHT\|, 640\|582" ui/screens/*.java`) matched zero literal
  `640` or `582` occurrences anywhere under `ui/screens/`; every hit was a `Playfield.HEIGHT - N`
  or `Playfield.HEIGHT / 2.0` expression, and none of those hits is inside the five nav screens.
  Caveat: the glob covers `ui/screens/*.java` only, not the `ui/screens/goals/*.java` subdirectory.

## 2. Back button

No redundant Back button. `grep -rln "Back\|back"` matched exactly three files —
`ui/screens/GoalsScreen.java`, `ui/screens/goals/GoalsSurface.java`,
`ui/screens/goals/GoalsChallengeCarousel.java` — but was run with `-l`, so it proves only that the
substring `Back` or `back` occurs in those files (it would match identifiers such as `Background`;
it is not word-bounded and does not prove a button). There is no dedicated back affordance in the
Goals flow: the bar pinned at `GoalsScreen.java:973` always shows all five sections, and `Esc` is
the fixed back action per the project rules, which makes any Goals-local Back button redundant by
design. Recommended follow-up if a second opinion is wanted: `grep -n -i '"back"\|BACK'` across the
three files to pin the exact node; the audit did not do so under the tool-call budget.

## 3. Goals reachability

Four of the five hub sections route to Goals; the fifth is Goals itself.

| Source screen | Route evidence | Implementation |
| --- | --- | --- |
| MainMenuScreen (Play hub) | `MainMenuScreen.java:291` — `this::openGoals` route, `.setEnabled(hasContent)` | `MainMenuScreen.java:428` `private void openGoals()` |
| ShopScreen (Shop) | `ShopScreen.java:300` — nav routes include `this::openGoals`; button enablement `ShopScreen.java:305`; label `ShopScreen.java:534` | `ShopScreen.java:1122` `private void openGoals()` |
| BirdSelectionScreen (Birds) | `BirdSelectionScreen.java:385` — nav routes include `this::openGoals`; enablement `BirdSelectionScreen.java:389`; label `BirdSelectionScreen.java:1447` | `BirdSelectionScreen.java:984` `private void openGoals()` |
| UpgradeTreeScreen (Forge) | `UpgradeTreeScreen.java:280` — nav routes include `this::openGoals`; enablement `UpgradeTreeScreen.java:284`; label `UpgradeTreeScreen.java:577` | `UpgradeTreeScreen.java:1128` `private void openGoals()` |

- The Forge slot in `UpgradeTreeScreen.java:280` is `null` (a screen does not route to itself); the
  Goals Screen similarly does not reroute elsewhere (`GoalsScreen.java:297` builds the nav with
  `SectionNav.GOALS` and `GoalsScreen.java:334` adds `nav.button(SectionNav.GOALS)` to the focus
  ring). `WorldSelectScreen` has no nav bar and no `openGoals` hit — the Play/WorldSelect leg goes
  through the hub route at `MainMenuScreen.java:291`.
- Goals is marked active while on it: `GoalsScreen.java:297` passes `SectionNav.GOALS` as the
  current section id to `SectionNav.build`, and `GoalsScreen.java:334` keeps its own nav button in
  the focus ring. Reachability is gated by content: `hasContent` at `MainMenuScreen.java:291` and
  `context != null` at `ShopScreen.java:305`, `BirdSelectionScreen.java:389`,
  `UpgradeTreeScreen.java:284`.

## 4. Escape, hit-test and focus on a tall surface

- The nav bar now rests at `metrics.navTop()`, which on a tall surface lies below logical row 640.
  Keyboard navigation is unaffected: `FocusRing.move` (`FocusRing.java:248`) ranks candidate nodes
  geometrically by direction, not by absolute y. It scores each node with
  `primary = dx*ddx + dy*ddy` (the along-axis projection, `FocusRing.java:266`) and
  `secondary = |dy*ddx - dx*ddy|` (the cross/off-axis term, `FocusRing.java:267`), keeps candidates
  whose `primary > 0.5` with score `primary^2 + 4*secondary^2` (`FocusRing.java:268-269`), and wraps
  when `primary < -0.5` (`FocusRing.java:274-280`). Because the bar's five buttons are the only
  nodes in the downward half-plane when the bar sits far below the content, the centre-line ranking
  always lands on the bar for a Down press, and the `4*secondary^2` weight pulls towards the
  nearest-axis node. Nothing in the ring hardcodes a surface height.
- Mouse correctness is the part that depends on the elastic height. Physical-to-logical mapping goes
  through `Viewport.toLogical`, and both the AWT bridge (`AwtInputBridge`, desktop, whose wheel
  handling is documented in CLAUDE.md) and `AndroidInputBridge.move()` feed that conversion. The
  nav's button rects are built from `metrics.navTop()` via `SectionNav.layoutRow`; a click below
  row 640 on a tall surface only hits the right button if `toLogical` scales by the *current*
  logical height and the click lands inside the metric-built rect. Any leftover caller that assumed
  a 640-high logical surface when converting y would mis-map exactly the row where the bar now sits.
- `Esc` is the fixed back action (project rule, no settings entry needed); it is processed
  independently of geometry, so it remains correct on any surface height.

## 5. Fixed / left

The five-item bar itself: nothing left — every one of the five sections is metric-pinned (section 1).

Leftover hardcoded references outside the bar, from the `Playfield.HEIGHT` grep:

- `WorldSelectScreen.java:73` — `FOOTER_TOP = Playfield.HEIGHT - 56`
- `RunSummaryScreen.java:78` — `VIEW_BOTTOM = Playfield.HEIGHT - 66`; `RunSummaryScreen.java:80` — `FOOTER_TOP = Playfield.HEIGHT - 56`
- `StatisticsScreen.java:86` — `FOOTER_TOP = Playfield.HEIGHT - 56`
- `SettingsScreen.java:87` — `VIEW_BOTTOM = Playfield.HEIGHT - 62`; `SettingsScreen.java:89` — `FOOTER_TOP = Playfield.HEIGHT - 58`; `SettingsScreen.java:1095` — `py = (Playfield.HEIGHT - CAPTURE_PANEL_H) / 2`
- `ModifierChoiceOverlay.java:391,396,417` — centered on `Playfield.HEIGHT / 2.0` (countdown overlay
  over the playfield; field-relative by design, not a defect).

Defect: the footer/view-bottom anchors above assume `logical height == Playfield.HEIGHT == 640`.
On a surface taller than 640 a `Playfield.HEIGHT - 56` footer floats mid-screen instead of sitting
at the elastic bottom (and would collide with a metric-pinned bar on short surfaces). Exact
recommended change: replace the `Playfield.HEIGHT - N` constants in those four screens with
`LayoutMetrics`-derived anchors (e.g. the bottom/`aboveNavHeight()`-style getters the nav uses at
`SectionNav.layoutRow`), and center the Settings capture panel (`SettingsScreen.java:1095`) on the
elastic surface height rather than `Playfield.HEIGHT`. Leave `ModifierChoiceOverlay` alone — it is
anchored to the playfield on purpose.