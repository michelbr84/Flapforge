# Responsive migration — QA fixes

Companion to [responsive-migration-visual-qa.md](responsive-migration-visual-qa.md): what each
of the four findings actually was, what changed, and what the classic surface kept. All
after-numbers are measured on the regenerated `build/responsive-qa/*.png`, the same way the
review measured them (pixel scans; phone renders are 1080x2400, classic 420x640).

Gate: `./gradlew --offline build contentCheck` green with the four
`classicSurfaceKeepsTheFixed*Geometry` assertions untouched.

## 1. CRITICAL — statistics: the "Keeps" row

**Root cause.** There is no wrap helper and nothing wrapped. `StatisticsScreen.renderRow`
drew every row value right-aligned at the row's right edge (`CONTENT_X + CONTENT_W`) with no
fit constraint, so a value wider than the row overflowed *leftwards* past its own label, past
the panel edge and off the screen, clipped mid-word at x=0. What the review read as one
wrapped value was two adjacent rows: line 1 was the Keeps row's complete value
("birds, achievements, cosmetics, lifetime statistics", 289 logical px — it fits), and
"line 2" was the *Resets* row's value ("coins, upgrades, ability levels, ... the daily pick",
471 logical px in a 372 px column, right-aligned to x = 396 − 471 = −75) overprinting its own
grey "Resets" label. The "duplicated s" was a coincidence: "…statistic|s," straddles the two
rows. The bug never showed on the classic control because the row is below the fold there.

**Fix.** A real wrap path in the row renderer, keyed to the row, not to one string:

- `StatisticsScreen.java:837` `measureWraps(g)` — measures every row's value lazily with the
  render context's own font (the same trade `TextPainter.ellipsise` already makes), cached on
  `language + Fonts.textScale() + rowsVersion`, so a live language switch re-measures.
- `StatisticsScreen.java:871` `wrapValue(...)` — breaks at spaces only, never mid-word; the
  first line must fit between the label and the row's right edge, every continuation line
  gets the full row width; a single word wider than its room is ellipsised instead of
  overflowing.
- `StatisticsScreen.java:984-992` `renderRow` — line 1 keeps the value column's right
  alignment; every continuation line starts at the row's inner x (`CONTENT_X`, physical 62
  at 1080x2400 — inside the panel edge at 32), one `WRAP_LINE_H` (= `ROW_H`) below, pushing
  later rows down.
- `StatisticsScreen.java:822,1002` — `maxScroll` and the scrollbar thumb use the measured
  height (`contentHeight` + all wrap growth), so the scroll range covers the wrapped lines.

**After.** Zero light-text pixels between the panel edge (x=32) and the inner margin (x=64)
anywhere in the render; the Resets value reads "coins, upgrades, ability levels, passive
slots," right-aligned, then "challenge records and the daily pick" starting at the row's
inner x; both labels fully legible.

**Classic.** Identical layout; the Keeps row still fits on one line. The Resets value wraps
there too, but that row is below the fold at rest, so the only classic-pixel change is the
scrollbar thumb being ~6 logical px shorter — correct, because the wrapped line genuinely
extends the content the thumb measures. All four `classicSurfaceKeepsTheFixed*Geometry`
assertions pass unchanged.

## 2. HIGH — worldselect: the 33% hole

**Root cause.** `WorldSelectScreen.relayout` anchored the detail panel to the footer
(`tierTop = footerTop − 222`) and gave nothing to the card rows, so every row the tall band
freed landed in the one gap between the grid and the panel (498 − 193 = 305 logical =
785 px measured).

**Fix.** `WorldSelectScreen.java:212-247` — the panel keeps the classic adjacency to the
card list instead of the footer: the classic gap between the last card's cell and the panel
top (`PANEL_TOP − (GRID_TOP + heightFor(...))` = 12 logical with the shipped five worlds) is
computed from the constants, and the card cells grow to end the grid exactly one adjacency
above the panel's classic top, floored so the panel can never sit lower than its classic
anchor. The difficulty row and the description keep their classic offsets inside the panel
(`tierTop = panelTop + 10`, `descriptionBaseline = panelTop + 62`).

**After.** Card pitch 149 → 298 px (cell 52 → 110 logical), last card ends y=1618, panel
starts y=1651 — a 33 px gap, the classic 12 logical adjacency at scale. The 782 px mid-band
is gone; what remains between the panel and Back is the classic design's own 158-logical
backdrop proportion (classic: 158/640 of the surface; phone: 17% of the picture).

**Classic.** The same formula reproduces the constants exactly on 420x640 (cell stays 52,
panel top 352): `worldselect-classic.png` comes out identical (same byte size; the relayout
arithmetic lands on 52/352/362/414/426) and the classic test assertions (584, 56, 362, 414)
pass unchanged.

## 3. HIGH — runsummary: the 68% void

**Root cause.** Pre-existing in the classic design (a short run's breakdown fills 185 of the
classic view's 518 logical px, 64% empty) and amplified by the migration: the panel stretches
to the footer while the row pitch stayed fixed, growing the empty interior from 340 to
1620 px.

**Fix.** `RunSummaryScreen.java:222-277` — `build()` lays the rows out twice: once with the
classic pitches to learn the run's natural height, then, on an elastic surface with room to
spare, again with the pitches grown so the block fills the panel. The row and section bands
share one factor capped at `MAX_FILL = 4.0` (`RunSummaryScreen.java:108`); what the cap
leaves is spread around the section headers, capped at `SECTION_GAP_MAX = 60` each
(`RunSummaryScreen.java:110`); anything still over stays as the panel's bottom padding. A
surface change re-spaces the rows (`RunSummaryScreen.java:715-718`) and re-clamps the scroll
(`RunSummaryScreen.java:216`). The classic surface is excluded by construction
(`fillPitches` returns null for `LayoutMetrics.classic()`).

**After.** Content height 185 → 811 logical = exactly the view height; the last row's text
ends 21 px above the panel bottom edge (was 1620 px of void). The largest single empty run
left is a 438 px section break — bounded, distributed, not a band.

**Classic.** `fillPitches` bails out on the classic metrics, so the pitches are the constants
and `runsummary-classic.png` is unchanged (same byte size, same geometry assertions 584/584,
56, 574).

## 4. MEDIUM — settings: the keymap cut

**Root cause.** The scroll viewport cuts the third keymap button mid-body exactly as the
review measured (it cannot be "ended on a row boundary" without moving `viewBottom`, which
the migration's contract pins to `contentBottom − 62`), and the only overflow signal was a
4 px thumb at 31% alpha — invisible over the panel.

**Fix.** Inside the settings screen only — its scrollbar is its own private
`renderScrollbar`, so no other screen's lists change: a track that is drawn only while the
content overflows (`SettingsScreen.java:146-148, 1158-1173`; track 19% alpha, thumb 75%
alpha, 6 logical px wide). A thumb that stops short of a visible track is the "there is more
below" sign the faint bar never was.

**After.** The thumb reads at RGB ≈ (202, 213, 214) over the panel (was ≈ (107, …)), the
track runs the full band and extends well below the thumb, and the viewport cut is self-
explanatory.

**Classic.** The classic surface also overflows, so its scrollbar gains the same track and
the brighter thumb — a deliberate shared legibility improvement, not a layout change: every
asserted value (582/582, 78, 578) is untouched and the rows are where they were.

## Test change (the one authorised rewrite)

`ResponsiveSecondaryTest.worldSelectPinsFooterToTheBottomOfATallSurface` asserted the old
footer-anchored panel (`tierTop == footerTop − 222`, `descriptionBaseline == footerTop −
170`) and encoded finding 2's hole as intent with `assertTrue(tallGap > classicGap)`. It now
asserts the new intent: `tallGap == classicGap` (the panel keeps its classic distance below
the grid), `worldGrid().cellHeight() > CARD_H` (the freed height goes into the card rows) and
`descriptionBaseline == tierList.y() + 52` (the panel's internal layout is the classic one).
Nothing was deleted; the run-summary test additionally asserts
`contentHeight() >= viewBottom − viewTop − 1`, the fill. Every other assertion in the file
stands as it was.
