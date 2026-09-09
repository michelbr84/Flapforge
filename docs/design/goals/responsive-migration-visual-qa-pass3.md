# Responsive migration — visual QA pass 3 (closure)

Re-measurement of the four findings from [responsive-migration-visual-qa.md](responsive-migration-visual-qa.md)
against the regenerated `build/responsive-qa/{worldselect,runsummary,statistics,settings}-{phone,classic}.png`
(13:51, after ff-ui's fixes per [responsive-migration-qa-fixes.md](responsive-migration-qa-fixes.md)).

Method: the same pixel scans as pass 1 (row/column classification, ink runs, colour
sampling), plus visual reading of the two judgement-call frames and of crops for every
row that was actually broken. All numbers are pixels in each picture's own coordinate
space (phone 1080x2400, classic 420x640). Measurement scripts: `build/responsive-qa/pass3_measure.py`,
`pass3_measure2.py` (analysis-only, throwaway).

## Verdicts

| Finding | Severity | Status |
| --- | --- | --- |
| statistics — Resets value overflow/clipping | CRITICAL | **CLEAR** |
| worldselect — 782px mid-band hole | HIGH | **CLEAR** |
| runsummary — 68% empty panel interior | HIGH | **CLEAR** |
| settings — keymap cut + invisible scrollbar | MEDIUM | **CLEAR** |

**Remaining open: CRITICAL 0, HIGH 0, MEDIUM 0.** No new defect introduced by the fixes
was found; the two intentional classic-set deltas are confirmed and nothing else in the
classic set moved.

## 1. CRITICAL — statistics Keeps/Resets rows: CLEAR

The root cause ff-ui reported (right-aligned value with no fit constraint overflowing
leftwards past the label, the panel edge and the screen) is gone.

- **Left gutter is clean.** A scan of x=36..63 over the whole render (all rows) finds
  nothing between the panel border and the content margin except: panel-border
  antialiasing at x=30 (outside the panel), and content ink that *starts* exactly at
  x=61-62 — the documented CONTENT_X (a gold coin pixel-run at y=211-215 in the player
  card, and left-aligned text rows elsewhere). Zero ink columns in x=33..61 anywhere.
- **The old garble zone is featureless sky.** At the rows where pass 1 measured the
  clipped second line (y=1830-1855), columns x=0..29 are smooth sky (brightness
  203..211, no glyph pixels).
- **The Resets row now wraps on a word boundary.** Measured at the Prestige section:
  label "Resets" ink x=65..162; value line 1 "coins, upgrades, ability levels, passive
  slots," right-aligned x=339..1015 (ending at the value column edge 1015-1016);
  continuation line "challenge records and the daily pick" starting at x=64 (the row's
  inner x), spanning x=64..622, alone on its baseline — no right-aligned residue, no
  overprint. The Keeps row above it: label x=65..155, value "birds, achievements,
  cosmetics, lifetime statistics" x=252..1016 on one line, a 97px label-value gap.
- **Visual crop check** (y=1600..2100): all five Prestige rows legible; labels intact;
  nothing clipped at either edge.
- **Classic.** Content is unchanged below the fold; the only classic-pixel delta is the
  scrollbar thumb, measured at x=406-407, y=53..543 (490 logical px, single dim bar —
  this screen's own renderer, no track). The claimed ~6px shortening cannot be verified
  directly because the pre-fix classic PNG was overwritten; the mechanism is consistent
  (the wrap adds one row to the measured content, and the phone's bar spans ~97% of its
  panel, i.e. no phone scroll — both as expected). UNCERTAIN on the exact 6px, certain
  that nothing else moved.

## 2. HIGH — worldselect 33% hole: CLEAR, and the recomposition reads as deliberate

- **Cards grew, the panel kept the classic adjacency.** Card tops at y=143, 442, 740,
  1038, 1336 — pitch 298, cell 283px = 110 logical x 2.5710 (exact uniform scale). Last
  card ends y=1618; the difficulty panel spans y=1651..1836 (185px = 72 logical, the
  classic panel); the card-list-to-panel gap is 32px = the classic 12-logical adjacency
  at scale (ff-ui reported 33; same edge within one border row). The 782px mid-band is
  gone — spent on the cards, as documented.
- **Judgement — proportions, hierarchy, targets.** The 110-logical cards read as a
  recomposition, not as stretched cells: the thumbnail (~178px), title, hazard line and
  right-aligned price are vertically centred with generous but even padding; glyph sizes
  are unchanged from pass 1 (only the cell grew); the selected card keeps its gold fill
  and white rim, locked cards their dimming, lock badge and price. Touch targets are
  283px. No bloat signal: the largest white space inside a card is smaller than the
  pass-1 inter-card void ever was.
- **What remains empty is the design's own ground strip.** Panel bottom 1836 to the Back
  button top 2256 = 420px = 17.5% of the picture (pass 1: ~49% total empty backdrop).
  That is proportionally *less* empty than the classic control (162/640 = 25%), because
  the elastic band went into the cards.
- **Classic control unchanged.** Cell 52, pitch 58, gold card 56..107, panel 353..423,
  Back 585..623, bottom margin 16 (pass-1: 15 design px) — the fixes doc's constants
  (52 / 352 / 362 / 414) reproduce within a border row.

## 3. HIGH — runsummary 68% void: CLEAR — receipt with generous spacing, not double-spacing

- **The panel fills edge to edge.** Panel y=129..2239 (border-stroke rows account for
  the ±3 vs pass 1's 2242). Ink rows, top to bottom: "This run" 388..414; Gates
  571..594; Points 746..769; Best streak 919..943; Time alive 1094..1123; Boss
  1269..1293; "Build" 1549..1575; "No drafts…" 1731..1763; "Setup" 2012..2046; "Seed 42
  (Seeded)" 2193..2222. The last ink ends 17px above the panel bottom edge (ff-ui: 21px
  against the outer edge — same edge, one border row apart).
- **Correction to ff-ui's report: the largest empty run is 257px, not 438px.** Measured
  blank runs inside the panel: top padding 131..387 = **257px** (10.7%H), Boss→Build
  1294..1548 = **255px**, No-drafts→Setup 1764..2011 = **248px**, row gaps 145..156px,
  bottom 15px. The 438px figure does not exist in the shipped render — possibly from an
  intermediate build. The actual worst gap is bounded, and it is a top padding, not a
  mid-panel band.
- **Judgement — the spacing reads as a receipt.** Row pitch is a uniform 175px (68
  logical) carrying ~24px glyphs, and section breaks are a consistent second rhythm at
  ~250px. Two stable spacing levels with clear hierarchy: this reads as generous and
  deliberate, not as double-spacing. Against pass 1's continuous 1620px (67.5%) void,
  the largest remaining gap is 257px (10.7%) and the content distributes across the full
  panel.
- **Classic control unchanged.** Panel 50..579, content ends y=238, largest blank 340px
  — byte-for-byte the structure pass 1 measured.

## 4. MEDIUM — settings keymap cut + faint scrollbar: CLEAR

- **The scrollbar now unambiguously says "more below".** At the panel's right edge: a
  6-logical-px thumb (columns x=1039..1044) reading RGB (202,214,214) over the panel
  fill (35,75,79) — exactly the ≈(202,213,214) ff-ui claimed, vs pass 1's ≈(107,…)
  whisper. Thumb extent y=202..1657; a visible track (≈(88,110,105), the 19%-alpha
  band) continues y=1658..2236 — **579px of track below the thumb**, ending ~20px above
  the panel bottom. The thumb stopping at 69% of the panel with track continuing is the
  signal the faint 4px bar never gave.
- **The keymap cut itself is unchanged — as declared.** "Flap: Space, Up" and "Ability:
  X, Shift" draw whole; the third button still enters as a sliver (2199..~2247) before
  the footer (2250..2352) covers it. ff-ui pinned `viewBottom` by contract and delivered
  the scrollbar signal instead — the pass-1 finding listed that as its acceptable
  remedy. With the track visible, the cut now reads as a scroll viewport, not an
  artifact.
- **Classic control gains the same legibility fix (flagged as intentional — confirmed).**
  Bright thumb >150 for 226 rows (y=78..303 at x=405) over a dim track y=304..576 that
  runs to the panel bottom. Everything else in `settings-classic.png` matches pass 1:
  same row ink (480..599 region), same footer 585..623, same bottom margin.

## Classic set — "nothing else moved" audit

- `worldselect-classic`: cell 52 / pitch 58 / panel 353 / Back 585..623 — unchanged
  (delta: none).
- `runsummary-classic`: panel 50..579, content ends 238, blank 340 — unchanged (delta:
  none).
- `statistics-classic`: panel 50..579, list cut mid-list at the same rows; delta:
  scrollbar thumb only (the flagged, mechanism-consistent shortening; exact −6px
  UNCERTAIN against the overwritten prior bytes).
- `settings-classic`: rows and footer unchanged; delta: brighter thumb + track only
  (flagged, confirmed).

## Pass-3 index

| Severity | Screen | Finding | Status |
| --- | --- | --- | --- |
| CRITICAL | statistics | Keeps/Resets overflow | CLEAR |
| HIGH | worldselect | mid-band hole | CLEAR |
| HIGH | runsummary | 68% void | CLEAR (max empty run now 257px; ff-ui's 438px figure not reproducible) |
| MEDIUM | settings | keymap cut + faint scrollbar | CLEAR (cut remains by contract; scrollbar signals it) |

No CRITICAL, HIGH or MEDIUM remains. The four screens are signed off at 1080x2400 and at
the classic 420x640 control.
