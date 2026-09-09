# Goals hub — visual spec (canonical)

Source references (real files, read in full, never downscaled):

| file | size | aspect |
|---|---|---|
| `docs/design/goals/challenges.png` | 941x1672 | 16:9 (0.5628) |
| `docs/design/goals/achievements.png` | 941x1672 | 16:9 |
| `docs/design/goals/milestones.png` | 941x1672 | 16:9 |
| `docs/design/goals/collections.png` | 852x1846 | ~19.5:9 (0.4615) |

Method: pixel-level sampling with PIL (dominant colours of regions plus 1px edge
scans). Hex values are therefore **measured**, not eyeballed, unless marked
UNCERTAIN. All reference px are in each image's own coordinate space; every value
is also given as a ratio of that image's width (W) or height (H) so the spec
holds at 16:9 and 19.5:9 and scales to the 420x640 logical surface.

---

## Section 1 — challenges.png (also defines the SHARED FRAME)

All four screens share the same skeleton; this section defines it once.
Sections 2-4 only record deltas.

### 1.1 Band anatomy (y-ranges in 941x1672 reference space)

| band | y-range | height | ratio | notes |
|---|---|---|---|---|
| top safe area (bare sky) | 0 .. 85 | 85 | 5.1%H | empty sky, nothing interactive |
| header (title "Goals") | 85 .. 178 | ~93 | 5.6%H | left-aligned, no subtitle, no coin readout |
| gap | 178 .. 198 | 20 | 1.2%H | |
| tab row | 198 .. 263 | 65 | 3.9%H (6.9%W) | 4 pill tabs, ~5px sky gaps between them |
| gap | 263 .. 288 | 25 | 1.5%H | |
| content panel | 288 .. 1372 | 1084 | **64.8%H** | one rounded panel, x 40..905 |
| background reveal (hills) | 1372 .. 1488 | ~116 | 6.9%H | decorative only, no controls |
| bottom nav | 1488 .. 1672 | ~184 | 11.0%H | dark band + 5 buttons |

- Content panel x-inset: 40px left / 36px right => 4.2%W each side, panel width
  865px = 91.9%W. Corner radius ~28px (3.0%W). Border ~3px light teal #347C84
  (measured on the top-edge scan). Panel fill #174046 (samples #184549 top,
  #163F43 lower — a subtle vertical darkening or decorative-cloud variance,
  <=4% luminance; treat as one base #174046 with faint darker cloud silhouettes
  inside, ~#1E4A50, decorative watermark only).
- Nav buttons: y 1508..1645 (h ~137 = 8.2%H), five buttons x 22..920, each
  ~165-173 wide with ~10-13px gaps, corner radius ~20. Band behind them reads
  dark green-tinted (#1C3F2E at the right edge, #29382F below the buttons):
  likely a translucent dark scrim over the hills rather than an opaque bar —
  UNCERTAIN on compositing; buttons themselves are opaque.
- Sky gradient behind everything: #48CCDE at the top -> #68DCEB by mid-screen.
  Hills #67BB4E / #62B74B (two close green layers), white clouds #FEFEFE.

### 1.2 Header

- "Goals" single line, left-aligned at x~55. Rounded heavy display face,
  ~96px (10.2%W), fill light gold #FCE68A, dark outline near-black #000207
  (~6-8px stroke) plus a soft drop shadow. Uppercase-only in look but written
  "Goals" (mixed case).
- No subtitle, no currency readout on any of the four screens.

### 1.3 Category tabs

Four pills spanning x 40..906, roughly equal widths (~212px, 22.5%W each),
separated by ~5px gaps through which the sky shows (they are separate pills,
not one segmented bar).

- Active ("Challenges"): gold fill with a subtle vertical gradient,
  #FCD054 mid / #FFD862 toward the top edge (measured); near-black text
  #000000-ish; radius ~14px (half-height, pill look); no visible outer stroke.
- Inactive: dark teal fill #1F5257 (same family as the panel), light text
  ~#DCEDEF (UNCERTAIN, not directly sampled), thin lighter-teal stroke
  ~1-2px #3E7C88.
- Selected = gold, exactly one at a time; the four labels are
  Challenges | Achievements | Milestones | Collections. Tab label ~32px
  (3.4%W) semibold.

### 1.4 Content structure (Challenges = carousel, NOT a list)

One challenge visible at a time inside the panel; no scrolling on this tab.

Order inside the panel, top to bottom (all centred unless said otherwise):

1. Carousel arrows: two rounded-square buttons ~92x90 (9.8%W), radius ~20,
   fill ~#174046 with a thin pale stroke, white chevron ~30px stroke. Left
   arrow at x 85..177, right arrow at x 775..867, y 335..425.
2. Title "No Shield I": gold #FCCB4E, bold, ~54px (5.7%W), centred, y~345..400.
3. Status pill "Locked": centred, ~258x60 (27.5%W x 3.6%H), full radius,
   fill #23474C, thin #3E7C88 stroke, padlock glyph + text ~30px in pale
   blue-gray #C9D8DE. (Reads "Locked" here; an unlocked challenge presumably
   shows no pill or an "Unlocked"/"New" pill — UNCERTAIN, not shown.)
4. Objective line: "Survive 30 gates without defensive abilities." white
   #FFFFFF regular ~34px (3.6%W), centred, y~508..540.
5. Field rows — a 2-column pair first, then four full-width rows:
   - Pair: World (x 82..462) and Tier (x 482..862), y 565..695, each 380 wide
     (40.4%W) x 130 high (7.8%H), 20px gap, radius ~16, fill #1F5257 (measured
     — lighter than the full rows below), thin stroke ~#3E7C88. Left icon tile
     ~72x72 radius ~12 (World: mini landscape, sky #54D7E9 + green; Tier: dark
     tile with gold bar-chart glyph), then a label over a value: label
     ("World"/"Tier") muted gray-teal ~#7E9EA6 (UNCERTAIN) ~28px (3.0%W);
     value ("Green Fields"/"Normal") white #FFFFFF semibold ~34px.
   - Full-width rows, x 82..862 (82.9%W), height ~110 (6.6%H), radius ~16,
     fill #164044 (measured, slightly darker than the panel base), stroke
     ~1.5px #3E7C88 (measured on the left-edge scan). Top edges at y=713, 840,
     965, 1090 => row pitch ~126px, inter-row gap ~16px (1.0%H). Icon column
     starts ~24px from the row's left edge (glyphs ~44-56px, pale gray-teal);
     text column starts ~140px from the row's left edge; label (muted, ~28px)
     stacked over value (white, ~34px).
     - Rules -> "No defensive abilities"
     - Progress -> "Not played yet" (text only; NO progress bar on a locked
       challenge)
     - Reward -> "200 coins + Ember" — the value is gold #FCCB4E semibold, and
       the icon is a gold coin (~56px, #F4BE3B with a lighter rim)
     - Unlock -> "Play 12 runs"
6. Primary action area: full-width button x 72..870 (84.8%W), y 1218..1332,
   height ~114 (6.8%H), radius ~24, fill slate blue-gray #4E6F7C (measured),
   border ~2px pale #ACC8D8 (measured), label "Locked" ~40px (4.2%W) with a
   padlock glyph, pale #C9D8DE. This is the disabled state; the available
   state is expected to read "Start Challenge" on the gold accent — UNCERTAIN
   (not shown in any reference).

### 1.5 States (locked shown)

- Locked challenge: NO veil/dim over the content — everything stays fully
  readable. Lock is communicated by (a) the pill under the title, (b) the
  padlock in the Unlock row, (c) the disabled slate CTA. Desaturation: none.
- Progress bars: none on this tab in the locked state.
- Rewards: gold text + coin icon only, in the Reward row.

### 1.6 Bottom navigation

Dark band (see 1.1) with five rounded-rect buttons, order Shop | Birds | Play
| Forge | Goals. Inactive: opaque very dark teal #183A3A (measured), white
icon (~52px) over a white label ~26px (2.8%W). Active (Goals): gold #FBCC4F
(measured), near-black icon + label, and a thin lighter outer stroke
(UNCERTAIN, reads as a pale/white 2-3px rim).

---

## Section 2 — achievements.png (scrolling list tab)

Shared frame identical to Section 1 (bands, header, tabs with "Achievements"
active, bottom nav with Goals active). Deltas and structure below.

### 2.1 Panel deltas

- The content panel is TALLER than on the carousel tab: y 288..1436
  (height 1148 = 68.7%H) versus 288..1372 on challenges. The panel grows to
  serve a scrolling list and the background reveal shrinks from ~116px to
  ~60px (3.6%H). The panel is elastic; the background reveal is the slack.
- The panel fill on this screen samples darker (#0F3A3E..#123F43) than on
  challenges (#174046). Best explanation: the panel is a translucent dark
  teal and the underlying artwork/clouds differ — treat the panel base as
  #143F45 with allowed variance rather than two hard-coded colours.
- A scrollbar appears on the panel's right inner edge: thumb ~20px wide
  (x 862..884), rounded, light teal #5BBDCE (measured), y ~335..445 for the
  top-of-list position; rail barely visible. Cards shift left ~12px compared
  with the challenge rows to clear it.

### 2.2 Summary block (top of panel, left-aligned)

1. "1 of 41 unlocked" — gold #FDD14C (bar-fill measurement; text stroke is the
   same accent family), bold, ~48px (5.1%W), left-aligned at x~82, y~318..368.
2. Progress bar: x 82..760 (width 678 = 72%W), y ~388..424, height ~36
   (2.2%H), full radius (~18). Track #184A50 (measured, lighter than card
   fill, no visible border); fill #FDD14C from the left, here ~2% (~28px).
3. "2%" — light gray ~#CFE0E4 (UNCERTAIN), ~34px (3.6%W), vertically centred
   on the bar, left edge ~x 785 (i.e. ~25px right of the track's end).

### 2.3 Achievement cards

Card geometry: x 70..850 (width 780 = 82.9%W, same width as the challenge
rows but 12px further left for the scrollbar), radius ~16.

- Unlocked card ("First Flight"): height ~131 (y 452..583). Fill green-teal
  #1B6154 (measured #195E53..#1D6355), border ~2px bright green #4DCCA2
  (measured) — the complete state is a green glow, not gold. Icon: green
  circle ~72px #5DBB46 (measured) with a darker green ring and a white check
  mark, left inset ~28px. Three text lines: name white semibold ~36px,
  description muted gray-teal ~30px (~#9FB8BC, UNCERTAIN), footer
  "Unlocked 2026-09-09" ~28px muted — a real calendar date, ISO-like format.
- Locked cards: height ~110 (6.6%H), tops at y = 597, 720, 843, 966, 1089,
  1212, 1335 => pitch ~123px (7.4%H), gap ~13px (0.8%H). Fill = card base
  #123F44, stroke ~1.5px #3E7C88 (measured). Left icon tile: rounded square
  ~72x72 radius ~14, fill #1E535B (measured, one step lighter than the card),
  containing a pale gray padlock ~40px. Name white semibold ~34px at x~225
  (~155px from card left); description muted ~30px beneath it.
- Reward display on locked cards: gold coin ~52px (#F4BE3B family) plus
  "+100 coins" gold #FDD14C bold ~32px, right-aligned as a group ending
  ~20px from the card's right edge; vertically centred. This is the footer
  role: unlocked cards show "Unlocked <date>" in that slot instead of a
  reward.
- 8 cards fully visible before scrolling (1 unlocked + 7 locked). List
  continues below the fold (41 total per the summary — MOCK VALUE, see the
  reconciliation section).

### 2.4 States summary (this tab shows both)

| state | fill | border | icon | dimming |
|---|---|---|---|---|
| unlocked/complete | green-teal #1B6154 | 2px #4DCCA2 | green circle + white check | none, brightest card |
| locked | #123F44 | 1.5px #3E7C88 | pale padlock in lighter tile | none — text fully readable |

No veil is applied to locked cards; readability is preserved, only the icon
and the footer slot distinguish state.

---

## Section 3 — milestones.png (summary + metric rows tab)

Shared frame identical to Section 1 (tabs with "Milestones" active, nav with
Goals active). Structure and deltas below.

### 3.1 Panel deltas and a correction

- Panel spans y 288..1445 (height 1157 = 69.2%H) — same elastic tall panel as
  achievements. Background reveal ~50-60px.
- CORRECTION to 1.1/2.1: the panel is a VERTICAL GRADIENT, not a flat fill.
  Measured ~#184549 near the top and ~#0D393D near the bottom on every tab
  (the challenges samples only saw the upper half). Spec value: gradient
  #184549 -> #0D393D, top to bottom. The "two panel colours" of 2.1 are this
  gradient.
- No scrollbar on this tab: rows use the full inner width, x 69..868
  (width ~799 = 84.9%W), i.e. ~18px wider on each side than the achievements
  cards which clear a scrollbar.

### 3.2 Heading

"Next milestones" — gold #FDD14C, bold, ~48px (5.1%W), left-aligned at x~82,
y ~318..368. Title case. Same type role as the achievements summary line
(same size, same colour, same x), just no bar/percentage under it.

### 3.3 Milestone rows

Six rows visible, no scrolling needed, and this is the only tab with
prominent progress bars on every row.

- Card geometry: height ~146 (8.7%H), tops at y = 385, 545, 700, 866, 1026,
  1186 => pitch ~157-160px (9.5%H), gap ~14px (0.8%H), radius ~16.
- Card fill = the panel tone itself (#0D393D..#0F3B3F measured — the cards
  are effectively outline-only here), stroke ~2px light teal #276F79
  (measured; same family as the #3E7C88 strokes elsewhere, slightly dimmer).
- Internals, top to bottom:
  1. Title row: name left-aligned at ~30px from the card's left edge, white
     semibold ~34px (3.6%W); counter right-aligned ending ~28px from the
     card's right edge, ~34px, pale blue-gray ~#B8CDD2 (UNCERTAIN — thinner
     strokes deflected the sampler). Formats seen: "0 / 100 XP", "1 / 2",
     "0 / 1" — spaces around the slash.
  2. Progress bar: inset ~30px from both card edges (x ~100..840 inside an
     868-wide card), height ~36 (2.2%H), full radius. Track #184A50 with a
     barely-visible lighter stroke; fill #FDD14C from the left with a rounded
     cap — "Level 2" shows a 50% fill for 1/2; the empty rows show track
     only. No percentage text on this tab (the counter IS the percentage
     carrier).
- Reward display: embedded in the row name — "Level 2 reward: 50 coins" is a
  single white title, NOT a separate gold line. Gold on this tab appears only
  in the heading and the bar fills.

### 3.4 Content shown (mock values, see reconciliation)

Rows: "Level 1" (0 / 100 XP), "Level 2 reward: 50 coins" (1 / 2), "Saved"
(0 / 1), "Field Marshal" (0 / 1), "Windbreaker" (0 / 1), "Forgebreaker"
(0 / 1).

### 3.5 Honest waste note

The last row ends at y~1332 but the panel runs to y~1445: ~110px (6.6%H) of
empty panel at the bottom. The reference uses a fixed tall scroll viewport
even when content is short — a flaw worth not copying verbatim if the
implementation can size the panel to content (min-height, not fixed).

---

## Section 4 — collections.png (19.5:9 outlier; several one-off choices)

Size 852x1846 — the only non-16:9 reference, and proof the skeleton survives
a 21:9-class screen. Ratios below use W=852, H=1846.

### 4.1 Band anatomy (deltas from Section 1)

| band | y-range | height | ratio |
|---|---|---|---|
| top safe area | 0 .. ~190 | 190 | 10.3%H (double the 16:9 share) |
| header (trophy + "Goals") | 190 .. 258 | ~68 | 3.7%H |
| tab row | 272 .. 325 | 53 | 2.9%H |
| content panel | 340 .. 1578 | 1238 | 67.1%H |
| background reveal | 1578 .. 1620 | ~42 | 2.3%H |
| bottom nav | 1620 .. 1846 | 226 | 12.2%H |

- Header gains a gold trophy icon ~72px at x 35..105 before the title; the
  title itself is proportionally SMALLER here (~60px = 7.0%W vs 10.2%W on
  16:9) — a mock inconsistency, not a deliberate scale rule.
- Panel x-inset is tighter: x 25..830 (94.5%W) vs 91.9%W at 16:9.
- Panel fill is LIGHTER and bluer than the 16:9 panels: ~#2D4E53 at the top
  drifting greenish to ~#2A5243 at the bottom (measured). Likely a
  translucent panel over a different background (this screen's backdrop adds
  a dark mountain silhouette and a sand path). Do not copy the lighter value;
  keep the Section 3 gradient and let translucency do the rest.
- Background reveal is nearly gone (42px) — at 19.5:9 this mock spends almost
  everything on chrome + panel.
- Bottom nav: band 1620..1846, buttons y 1642..1775 (h 133), and ~70px of
  dark band below the buttons (3.8%H) — this mock's stand-in for a bottom
  safe-area inset.

### 4.2 Tab treatment INCONSISTENCY

The active "Collections" tab is NOT a filled gold pill. Measured: fill
#39858E (lighter teal than inactive #174E5B), border ~3-4px bright gold
#F3D66C, white text. The other three screens fill the active tab with gold
and use near-black text. **Canonical choice for implementation: the filled
gold pill (3 of 4 references, and it matches the active nav button and the
Forge/Shop accent language).** Recorded here so the delta is a decision, not
an accident.

### 4.3 Collection rows (8 rows, no scroll)

Seven category rows + a stronger "Everything" card, all visible without
scrolling — this tab is the density benchmark.

- Category row: x 33..820 (92.1%W — only ~8px inside the panel edge; tighter
  than any 16:9 screen), height ~120-128 (6.5-6.9%H), tops at y = 370, 505,
  640, 785, 925, 1070, 1215 => pitch ~135-145 (avg ~140 = 7.6%H), radius ~16,
  fill #1A4146 (measured, darker than this panel), stroke ~1.5px #34737B
  (measured).
- Internals: large colourful art icon ~80-100px at x 55..160 (bird, ice
  diamond, island, flag, palette, star, hammer — these are the only
  multi-colour content icons in the whole set); name white bold ~32px at
  x~180; description muted ~24px beneath ("Unlock different birds",
  "Discover special abilities", "Explore new worlds", "Complete challenge
  runs", "Collect different colours", "Unlock achievements", "Forge
  permanent upgrades"); counter "3 / 7  (42%)" right-aligned ending ~x 725,
  ~30px, pale (count + percentage with a wider gap before the parenthesised
  percentage); white chevron ">" ~28px at x 765..790 (rows are tappable).
- Progress bar inside every row: x 180..727 (under the text column only, not
  the icon), height ~22 (1.2%H — thinner than milestones' 36), full radius,
  track #0E272B (measured — much darker than the 16:9 track #184A50), fill
  #FDCE4D (measured) from the left, no label on the bar itself.
- EVERYTHING card: y 1355..1505 (height ~150 — taller than category rows),
  fill dark OLIVE #333C2A (measured), border ~3px bright yellow #FFF060
  (measured), gold crown icon ~90px, name "Everything", description "Total
  game completion", counter "9 / 133  (6%)", same bar anatomy with a 6% fill.
  This is the single strongest card on any Goals screen: yellow outline +
  olive fill + crown + extra height.
- Footer line, centred under the card: "Keep playing. There's more to
  discover!" — pale gray ~26px (2.9%W), y ~1530..1555. A motivation/
  empty-state line; it should come from the string tables, not a literal.

### 4.4 Bottom nav delta

Goals' nav icon on this screen is a TROPHY, while the 16:9 screens draw a
list/document glyph. Pick one (trophy reads better as "Goals/achievements
hub"; but any choice must be consistent). Mock inconsistency, same class as
4.2.

---

## Section 5 — consolidated palette (measured vs the Forge's shipped constants)

The Forge (`ForgeArt`, `ProceduralArt`) already ships most of these roles. The
references were generated from the same design language — several values match
the shipped code to within 1-2 RGB points. Where the two agree, keep the code's
constant; where they disagree, the table says which one to adopt.

| role | measured in references | Forge constant today | recommendation |
|---|---|---|---|
| gold accent (tabs, bars, active nav) | #FCCB4E / #FDD14C / #FDCE4D / #FCD054 / #FBCC4F | `COIN_GOLD` #F5C542, `PIP_FILLED` #FCCB45 | keep the code constants; the ref spread is one gradient family |
| gold fill, light stop (display title) | #FCE68A with near-black #000207 outline | none (display face) | new constant, reference-only |
| content panel fill | gradient #184549 -> #0D393D | `PANEL_FILL` #134248 | same family; keep #134248 flat, or adopt the measured gradient — either is faithful |
| card fill | #164044 / #123F44 / #1A4146 | (cards use `ProceduralArt.button`) | a single GOALS card fill ~#123F44 |
| card stroke | #3E7C88 (16:9), #34737B, #276F79 | `PANEL_BORDER` white @25% | adopt measured teal ~#3E7C88 as CARD_STROKE for Goals cards |
| inset icon tile | #1E535B / #1F5257 | `COIN_PILL` #0E4149 (same family) | adopt #1E535B as TILE_FILL |
| progress-bar track | #0E272B (collections) — EXACTLY `PIP_EMPTY` #0D272C; #184A50 on 16:9 tabs | `PIP_EMPTY` #0D272C | keep `PIP_EMPTY`; unify the 16:9 tabs' #184A50 down to it |
| text light | #FFFFFF | `TEXT_LIGHT` #F4F8F8 | keep #F4F8F8 (reference is pure white; 4-point delta is invisible) |
| text muted | ~#7E9EA6..#9FB8BC (UNCERTAIN, thin strokes) | `TEXT_MUTED` #A9BABC | keep #A9BABC |
| pale ink on disabled CTA / pill | ~#C9D8DE | `PADLOCK` #D8E2E4 | reuse `PADLOCK` |
| complete/complete-border | fill #1B6154, border #4DCCA2, check circle #5DBB46 | `TIER_PILL` #54BA62 (same green family) | new COMPLETE_FILL #1B6154, COMPLETE_BORDER #4DCCA2 |
| disabled CTA | fill #4E6F7C, border #ACC8D8 | none | new DISABLED_CTA pair |
| scrollbar thumb | #5BBDCE (solid light teal) | Shop `SCROLLBAR` #F4F8F8 @31% | adopt the shop's translucent white (more subtle, already shipped) |
| Everything card | fill #333C2A olive, border #FFF060 | none | new EVERYTHING_FILL / EVERYTHING_BORDER |
| sky / hills / clouds | #48CCDE -> #68DCEB sky, #67BB4E / #62B74B hills, #FEFEFE clouds | `WorldPalette` (Green Fields) | reuse the existing world palette; do not add a parallel one |
| nav band / buttons | band reads ~#1C3F2E..#293533 (translucent over bg, UNCERTAIN), buttons #183A3A / #173336, active #FBCC4F | `SectionNav`'s current treatment | keep the shipped nav; only its ANCHOR must move (Section 7) |
| sky gap between tabs | the ~5px tab gaps show sky through | n/a | tabs are separate pills, not one segmented control |

## Section 6 — typography scale

Font sizes in reference px with ratios; the game maps them through `Fonts`
point sizes at the 420-wide logical surface (16:9 refs scale by ~0.446,
collections by ~0.493 — e.g. the 96px title is ~43 logical px, a card name
~15).

| role | size (16:9) | ratio | weight | colour |
|---|---|---|---|---|
| H1 "Goals" | 96 (collections ~60) | 10.2%W / 7.0%W | heavy rounded display | #FCE68A on #000207 outline + soft shadow |
| screen heading / summary | 48 | 5.1%W | bold | gold |
| challenge title | 54 | 5.7%W | bold | gold |
| card name / field value / objective / counter | 34-36 | 3.6-3.8%W | semibold | white (value), pale #B8CDD2 (counter, UNCERTAIN) |
| tab label | 32 | 3.4%W | semibold | near-black on gold; #DCEDEF (UNCERTAIN) inactive |
| description / pill / reward | 30-32 | 3.2-3.4%W | regular / bold for reward | muted / gold |
| footer, date, collections footer | 26-28 | 2.8-3.0%W | regular | muted |
| nav label | 26 | 2.8%W | medium | white / near-black on gold |
| CTA label | 40 | 4.2%W | semibold | #C9D8DE (disabled state) |

No all-caps convention anywhere; strings are title case. The mock draws the
date as ISO `2026-09-09`.

## Section 7 — screen utilization (the vertical-space argument)

Reference screens devote, of FULL screen height:

| screen | top inset | header+gap | tabs+gap | content panel | bg reveal | nav | dead |
|---|---|---|---|---|---|---|---|
| challenges.png | 5.1% | 6.8% | 5.4% | **64.8%** | 6.9% | 11.0% | 0% |
| achievements.png | 5.1% | 6.8% | 5.4% | **68.7%** | 3.6% | 11.0% | 0% |
| milestones.png | 5.1% | 6.8% | 5.4% | **69.2%** (of which ~6.6% empty) | 3.0% | 11.0% | 0% |
| collections.png | 10.3% | 6.6% | 5.2% | **67.1%** | 2.3% | 12.2% | 0% |

The game today (from `layout-diagnosis.md`, numbers for a 1080x2400 phone):

- The fixed 420x640 surface letterboxes to a 1080x1646 band = 68.6% of the
  screen, with 377px dead bands top AND bottom = **31.4% of the physical
  screen spent on nothing**.
- Inside the band, the current Goals spends tabs 48..76 (4.4%), view
  86..578 (**76.9% of the band**), nav 582..640 (9.1%).
- Effective content share of the physical screen: 492/640 x 1646/2400 =
  **52.7%**. Effective nav position: its bottom sits at y=2023 of 2400 —
  **377px above the physical bottom**, over a dead band.

**Headline: the references give the content panel 64.8-69.2% of the physical
screen; the game gives it 52.7% — a loss of 12 to 16.5 points, all of it the
letterbox.** The chrome shares themselves are comparable (reference top+tabs
~10.5-22% vs game's 11.9%-of-band; reference nav 11-12.2% of screen vs game's
9.1%-of-band = 6.2% of screen). The references are not leaner — they are
edge-pinned: header at the physical top, nav at the physical bottom, safe-area
insets instead of centring.

Honest waste in the references (do not copy):
- milestones.png: ~110px of empty panel under the last row = 6.6% of the
  screen — a fixed-height scroll viewport with short content.
- challenges.png: 6.9% background reveal under the panel (decorative, but it
  is the reason the panel is the smallest of the four).
- collections.png: 10.3% top inset — generous even for a cutout-safe layout.
- The nav band at 11-12.2% of screen height is heavy (large touch targets,
  but the game's shipped nav band is proportionally leaner).

Implementation consequence (matches `layout-diagnosis.md`): pin the header to
the safe top, pin `SectionNav` to the safe bottom, make the content panel
elastic between them, and keep every card metric as a ratio of width, not a
constant of 640.

## Section 8 — mock vs canonical content (reconciliation)

Verified against `src/main/resources/data/*.json` on 2026-09-09. A
`goals-data-sources.md` does NOT exist in this directory yet, so binding
targets marked UNVERIFIED are the best candidate in code, not a documented
contract.

| displayed value | mock | repo | verdict |
|---|---|---|---|
| "1 of 41 unlocked" | 41 achievements | `achievements.json` = 41 | MATCH — bind live |
| "+100/+300/+800/+50/+250/+600 coins" | achievement rewards | `achievements.json` rewards: 100, 300, 800, 50, 250, 600 | MATCH exactly (frequent_flyer..gates_100) |
| Birds "3 / 7" | 7 | `birds.json` = 7 | MATCH |
| Abilities "1 / 8" | 8 | `abilities.json` = 8 | MATCH |
| Worlds "1 / 5" | 5 | `worlds.json` = 5 | MATCH |
| Challenges "0 / 7" | 7 | `challenges.json` = 7 | MATCH |
| Colours "3 / 22" | 22 | 22 palettes across `birds.json` (classic 4, others 3) | MATCH — colours = bird palettes |
| Upgrades "0 / 43" | 43 | `upgrades.json` = **18** nodes / 3 trees | **CONFLICT** — mock value is stale or forward-looking; bind live |
| Everything "9 / 133" | 133 = 7+8+5+7+22+41+43 | same sum with the real 18 = **108** | **CONFLICT** (cascades from Upgrades) — must be computed, never a literal |
| "Level 1 / 0 / 100 XP" | 100 XP to level | `economy.json.xp.curve.base` = 100 | MATCH |
| "Level 2 reward: 50 coins" | 50 | `economy.json.xp.levelRewards["2"] = {coins: 50}` | MATCH |
| "200 coins + Ember" | challenge reward | `no_shield_1.rewards = {coins: 200, unlocks: ["cosmetic:classic:ember"]}` | MATCH — Ember is the classic-bird `ember` palette |
| "Survive 30 gates without defensive abilities", Green Fields, Normal, "No defensive abilities", Unlock "Play 12 runs" | challenge fields | `no_shield_1`: objective SURVIVE_GATES 30, world green_fields, tier normal, flag NO_DEFENSIVE_ABILITIES | MATCH; the "Play 12 runs" unlock condition has no obvious field in the def — UNVERIFIED |
| "Unlocked 2026-09-09" | today's date (mock generation date) | profile achievement timestamps | bind profile data; format ISO yyyy-MM-dd |
| "Saved 0 / 1", "Field Marshal 0 / 1", "Windbreaker 0 / 1", "Forgebreaker 0 / 1" | milestone rows | no `milestones.json`; nearest sources: `AchievementEvaluator`/`Statistics` lifetime stats | UNVERIFIED — needs a lead decision (are milestones derived stats or new content?) |
| Level 2 shows 1/2 while Level 1 shows 0/100 XP | internally inconsistent mock state | — | MOCK ARTIFACT — ignore; the real screen shows one consistent profile |

Binding map (authoritative sources in code):
- Collection rows and percentages -> `progression.CollectionProgress.of(content).all(profile)` / `.of(category, profile)` (`Entry.owned/total/percent()`); the EVERYTHING card is category `ALL = "all"`.
- Achievements tab -> `progression.AchievementEvaluator.definitions()` + `PlayerProfile` unlocked set (+ timestamps).
- Level/XP milestones -> `progression.PlayerLevel` thresholds + `economy.json.xp.levelRewards`.
- Challenge fields -> `challenges.json` defs (`objective`, `world`, `tier`, `flags`, `rewards`) + `PlayerProfile.challenges` records; the run-count unlock gate is UNVERIFIED.
- Coins reward text -> `progression.Wallet` is unrelated here; reward strings come from the defs. All player-visible strings via `content.StringKey` + `strings/en.json` + `strings/pt_BR.json`.

## Section 9 — deltas the implementation must decide (summary for the lead)

1. Active-tab treatment: filled gold pill (3 of 4 refs) vs gold-outlined teal
   (collections ref) — pick the filled pill.
2. Goals nav icon: list glyph (16:9 refs) vs trophy (collections ref) — pick
   one; the trophy matches the header trophy.
3. Locked cards: references apply NO veil and keep full readability; the Forge
   ships `CARD_VEIL` + padlock. For Goals lists follow the references (no
   veil); the padlock tile alone carries the state.
4. Scrollbar: references show a visible light-teal thumb; the shop already
   ships a subtler translucent one — reuse the shop's.
5. Milestone rows are not shipped content — the tab needs either derived
   stat milestones or new content before it can exist.
6. Panel height must be elastic (achievements/collections tall, challenges
   shorter) with the background reveal as slack — never the fixed 640 band.

---
