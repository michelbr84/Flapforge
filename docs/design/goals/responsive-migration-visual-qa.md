# Responsive migration — visual QA

Read-only review of the four screens migrated to the elastic-band layout. No reference
mockups exist for these screens; the classic 420x640 renders are the control group and the
question is space usage and breakage at 1080x2400 (20:9).

Sources: `build/responsive-qa/{worldselect,runsummary,statistics,settings}-{phone,classic}.png`.
All numbers below are pixels in each picture's own coordinate space (phone 1080x2400,
classic 420x640). Measured programmatically (pixel-column scans), not eyeballed.

## Global checks (all four phone renders)

- **Uniform scale, no distortion.** World-card pitch 58 -> 149 (ratio 2.571 = 1080/420,
  exact), panel width 395 -> 1016 (2.572), Back-button height 40 -> 103 (2.575), panel
  side margins 12/13 -> 31/32. Elements scale uniformly and stay centred; the freed height
  goes into the content region, not into stretched glyphs. PASS.
- **Footer pinned and healthy.** Buttons occupy y=2256-2358 (settings 2250-2356) on all
  four screens; bottom margin 40-44px (classic: 15 design px, same at scale). Not floating,
  not clipped, not on the edge. PASS.
- **Header pinned and healthy.** Title glyph band starts at y=60 (classic y=23, same at
  scale). No dead strip above the header. PASS.
- **No horizontal letterbox.** Panels span x=32-1048 with equal margins; content is
  full-bleed. PASS.

## Per-screen findings

### World picker (worldselect)

- **HIGH — 782px empty band between the last world card and the difficulty panel.**
  Card 5 ends at y=874; the difficulty/detail panel starts at y=1656. The gap is 782px =
  32.6% of the picture height, sitting in the middle of the screen. Within it, y=881-1506
  (625px, 26%) is featureless sky gradient with two small decorative clouds; hills only
  start at y=1506. It reads as a hole, not breathing room: dense UI above and below, and
  the gap severs the card list from the difficulty panel that belongs to the same
  selection flow. In the classic render the panel sits 7px below the last card (adjacency
  would be ~18px at scale); the migration anchored the panel to the footer and spent all
  freed height in this one gap. A further 398px (16.6%) of empty ground sits between the
  panel and the Back button, so ~49% of the picture carries no UI.
- Everything else is clean: cards, lock badges, prices, coin counter, Back button.

### Run summary (runsummary)

- **HIGH — 68% of the picture is empty panel interior.** The summary panel spans
  y=129-2242 (88% of the picture height); its content ends at "Seed 42 (Seeded)" around
  y=620. The remaining 1620px (67.5% of the picture) is dark semi-transparent panel with
  only faint clouds/hills showing through — it reads as a void. The classic control
  already had this shape (panel y=51-578, content ends y=238, 53% empty), so the
  migration amplified a pre-existing layout rather than creating it: the empty span grew
  from 340 to 1620 real px (53% -> 68% of the picture). Needs a layout decision: cap the
  panel height, bottom-anchor the content, or let the panel end after the content instead
  of stretching to the footer.
- Panel bottom edge ends at y=2242, 14px above the Retry/Menu row — no collision.
  Retry/Menu pinned correctly with the selection outline on Retry.

### Statistics / Profile (statistics)

- **CRITICAL — "Keeps" value wrap overprints and clips.** In the Prestige section, the
  Keeps value ("birds, achievements, cosmetics, lifetime statistics, ability levels,
  passive slots, challenge records and the daily pick") wraps onto a second line
  (line 1 at y=1785-1805, line 2 at y=1832-1851). The second line is drawn starting left
  of the panel border (panel edge x=32, inner text margin x=64): it begins at the screen's
  left edge, mid-word, showing "s, ability levels, ..." with the leading characters cut
  off — and it overlaps a dim grey word (consistent with the row label "Keeps" redrawn on
  the same baseline) around x=100-230, leaving roughly six garbled, unreadable glyphs.
  The split also appears to duplicate the trailing "s" of "statistics" (line 1 already
  ends "...statistics"; line 2 begins "s,"). Exact wrap mechanism UNCERTAIN; the visible
  breakage is certain. On the classic control this row is below the fold and never
  renders, so the bug is phone-only.
- Otherwise the best space usage of the four: player card y=129-334, main panel y=345-2043
  (the freed height buys the full list — the classic control cuts off mid-list at "Hits
  absorbed"), Last runs bar y=2055-2155, hint toast y=2158-2240, Back y=2256-2358. No
  wasted band anywhere.

### Settings (settings)

- **MEDIUM — third keymap button clipped to a sliver at the viewport bottom.** The
  Controls keymap list shows "Flap: Space, Up" (y=2032-2104) and "Ability: X, Shift"
  (y=2124-2196) in full; a third button appears only as a y=2199-2250 sliver (~51 of ~92px)
  before the footer row (y=2250-2356) covers it. The list is scrollable — a scrollbar
  track/thumb exists at the panel's right edge in both the classic and phone renders — so
  this is the viewport cut, and the classic control cuts content mid-section too
  ("Game" header at its panel bottom). But cutting mid-button directly above the footer
  reads as a render artifact at a glance, and the scrollbar is faint enough that nothing
  signals "more content". Prefer cutting at a row boundary, or making the scrollbar
  visible when content overflows.
- **Not broken:** the keymap buttons are NOT mis-centred — all three span x=68-1012
  identically, footer pair (Restore defaults x=68-602, Back x=629-1012) is centred with
  equal 68px margins.

## Findings index

| Severity | Screen | Finding |
| --- | --- | --- |
| CRITICAL | statistics | Keeps value wrap line clipped at screen edge and overprints label glyphs (y=1832-1851) |
| HIGH | worldselect | 782px (33%) empty mid-band between last card and difficulty panel; total empty backdrop ~49% |
| HIGH | runsummary | 68% of picture is empty panel interior below "Seed 42 (Seeded)" (classic: 53%) |
| MEDIUM | settings | Third keymap button cut mid-body at viewport bottom, flush above footer; scrollbar too faint to explain it |

## Verdicts

- World picker: **NEEDS WORK** — the 33% mid-gap must be spent (anchor panel to the list,
  grow the cards, or scroll the list region).
- Run summary: **NEEDS WORK** — the stretched panel turns the freed height into a 68% void;
  cap, bottom-anchor or fill it.
- Statistics: **NEEDS WORK** — space usage itself is the best of the four and would be
  ACCEPTABLE, but the Keeps wrap garble is CRITICAL and must be fixed first.
- Settings: **ACCEPTABLE** — dense, correct, pinned; one polish item on the keymap
  viewport cut.
