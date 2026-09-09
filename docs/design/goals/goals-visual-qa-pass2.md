# Goals — visual QA pass 2 (verification)

(render set: `build/goals-qa/`, regenerated 2026-09-09 12:01 after the pass-1
fixes; pass-1 findings: `docs/design/goals/goals-visual-qa.md`)

Method: frames judged in the implementer's endorsed order, one `##` section
appended as it was judged. Measurements in logical px (renders are 1:1 with the
420xH logical surface), taken with a pixel classifier plus visual reading.
X.1 is recorded as an intentional divergence and is not re-raised; X.2 is
deferred to the lead.

## 3.1 — Collections caption  -> FIXED

Frame `goals-en-933-tab3.png` (420x933). Measured:

- Caption ink run **y 759..770 (12 glyph rows)**, x 110..309 (199px wide),
  centred on the panel centre (x 209.5) — the full string
  "Keep playing. There's more to discover!" including descenders.
- Panel bottom edge at y 787 — the caption clears it by **17px**. Nothing is
  cut. Pass 1 measured a 2-row ghost at y 785-786; the same string (identical
  x extent) now draws whole, higher up inside the panel.
- Row maxima shaved as reported: category-row pitch 71px, card ~65px
  (stroke-to-stroke, 7 rows measured 171→236→307→378→449→520→591→662);
  Everything card 668..746 = **78px** (pass 1: ~87px).

Cramp check — the question the lead asked. Inside a shaved 65px category row
(Birds): 8px above the title, title 10 rows, 7px gap, subtitle 8 rows, 12px
gap, bar 10 rows, **12px below the bar to the bottom border**. The Everything
card: 8px above title, 11-row subtitle, 16px below the bar. Padding is
top-lighter/bottom-heavier but within ~4px of symmetric — the rows do **not**
read as cramped; the bar-to-border gap is visibly the largest white space in
the row. Verdict: the shave cost nothing legible.

pt twin (`goals-pt_BR-933-tab3.png`): caption ink y 759..770 (12 rows), x
100..318 — the longer pt string "Continue jogando. Há mais para descobrir!",
still centred (centre x 209), same 17px clearance to the panel bottom at
y 787. The fix is language-independent.

**FIXED.**

## 1.1 — card-boundary crop  -> FIXED

`goals-en-640-tab1.png` (420x640, the hardest case — 6 achievement cards fit to
the fold). Measured:

- Last visible card (Twenty-Five Gates): top stroke y 498, bottom stroke
  **y 546**. Panel bottom edge **y 558**. The strip 547..557 between them
  contains **zero ink rows** against the panel background — no partial card,
  no half-height glyphs. The crop lands exactly on the card's own bottom
  stroke.
- Card text inside the last card ends at y 535, 11px above its own bottom
  border — the title/subtitle draw whole.
- Card geometry at 640: pitch ~82px, card ~78px, 4px gaps (boundaries at
  171/253, 259/308, 314/363, 369/427, 433/491, 497/546).
- Pass 1's defect was the Marathon card sliced mid-text at y=803 on 933; the
  equivalent check at 640 now shows a clean boundary crop.

**FIXED** (933 spot-check to follow with the track sections).

pt_BR twin (`goals-pt_BR-640-tab1.png`): same geometry — strip 547..557 has
zero ink; last card ("Vinte e Cinco Canos") text ends y 532 against its 546
border. The clean crop is language-independent.

## 1.4 — unlocked-card date line  -> FIXED

`goals-en-640-tab1.png` + `goals-pt_BR-640-tab1.png`: every unlocked card now
carries **"Unlocked <date>" / "Conquistada em <date>" as a third left-aligned
line** in the text stack, directly under the subtitle at the same left edge
(First Flight x≈74: "Finish a run." then "Unlocked 2023-11-14"; Centurion,
Ten Gates identical; pt: "Terminou uma corrida." then "Conquistada em
2023-11-14"). The pass-1 arrangement — right-aligned date on its own row at
the card's bottom-right — is gone. 933 spot-check follows with the track
sections.

**FIXED.**

## 1.5 — locked-card coin group  -> FIXED

`goals-en-640-tab1.png` + `goals-pt_BR-640-tab1.png`: locked cards now draw a
**gold coin icon plus the "+N coins"/"+N moedas" text as one right-aligned
group** (Frequent Flyer "+100 coins", Veteran "+300 coins", Twenty-Five Gates
"+100 coins"; pt "+100 moedas"/"+300 moedas"). Measured on Twenty-Five Gates:
the group sits in the card's upper-right, ink from x≈300 to the card's right
padding, vertically aligned with the title row. The pass-1 text-only reward is
gone. 933 spot-check follows with the track sections.

**FIXED.**

933 spot-checks for the three sections above, from `goals-en-933-tab1.png`:
date is the third left line on all three unlocked cards; coin groups
"+100/+300/+250/+600 coins" right-aligned on all locked cards; the last
visible card (A Hundred Gates) ends at its own bottom stroke **y 761**, panel
bottom edge y 802, and the strip 762..801 (x 25..384) holds **zero ink** —
1.1 FIXED at both 640 and 933, both languages.

## 2.1 — track colours (per tab)  -> FIXED

Measured fill/stroke pairs (render, logical px):

| tab | track | fill | stroke | mock relation |
| --- | ----- | ---- | ------ | ------------- |
| Milestones (en+pt, 933) | empty cards 3-5 | (23,74,80) | (49,123,131) | fill LIGHTER than card (18,63,68), stroked — matches mock (23,74,80) vs card (14,58,62) + light stroke |
| Achievements 933 summary bar | unfilled right part | (23,74,80) | (49,123,131) | same stroked pill, 201..219 (~18px tall) |
| Collections 933 | category-row slots | (14,39,43) | none | deliberately dark unstroked #0E272B, matches its own mock |

- The pass-1 defect (near-black (13,39,44) slots, darker than the card, no
  outline) is gone on Milestones and on the Achievements summary bar. The
  empty tracks now read as unfilled meters, not holes.
- **Gold fill inside the stroke is legible**: on Milestones card 2 (4/5) the
  gold (253,209,76) runs x 42..309 inside a pill spanning x 39..380, with the
  light stroke visible around it (vertical order: stroke → ~1px track fill →
  gold → track fill → stroke). The gold does not bleed over the stroke.
- Fill fractions stay consistent with the counters (~78% drawn for 4/5 after
  pill-end rounding; ~70% for 94/133 in pass 1 — unchanged).
- Collections keeps the dark unstroked slot (no light stroke found across the
  Birds row bar, direct card→bar→card transition) — the per-tab split the fix
  promised.

**FIXED.**

## 2.2 — pt_BR reward value  -> FIXED

`goals-pt_BR-933-tab2.png` (Milestones, Portuguese). Measured:

- Card 2 title reads **"Recompensa do nível 5: 150 moedas" whole** — title ink
  ends at x 270, counter "4 / 5" starts at x 358, an 88px gap. No ellipsis
  anywhere. Pass 1 cut it to "Recompensa do nível 5: 15…".
- **The shrink did not need to fire and no other label changed size**: card 1
  "Nível 4" and card 2's long title both measure exactly **10 glyph rows**
  (y 220..229 vs y 321..330) — same bold size, no shrunken odd-one-out among
  the five card titles ("Salvo", "Marechal dos Campos", "Quebra-vento" also
  uniform). The measure-first/label-second fix means the long pt string fits
  at normal weight; 13→10 shrink stays a fallback that never triggers here.
- Card geometry: pitch ~101px, card ~95px, nothing clipped (last card
  Quebra-vento ends y 604+, panel bottom far below).

**FIXED.**

## 1.2 / 3.3 — the two LOWs  -> both FIXED

- **1.2 scrollbar thumb**: measured **9px wide** (x 390..398, colour ~(91,123,
  126)) on `goals-pt_BR-933-tab2.png`, `goals-en-933-tab1.png` and
  `goals-en-933-tab2.png` — up from 4px in pass 1, and matching the ~9px the
  reference scales to. FIXED.
- **3.3 Collections row glyphs**: on `goals-en-933-tab3.png` the Challenges row
  now shows a **red flag** and the Achievements row a **gold star**, matching
  the mock. The other five rows were already correct (bird, diamond, world,
  palette, hammer, crown). FIXED.

## Regressions — anything that got worse

**Challenges (tab 0), the do-not-touch tab: no regression.** Measured on
`goals-en-933-tab0.png` and `goals-pt_BR-933-tab0.png`: panel bottom edge
y 766 — identical to pass 1's 162..766; reveal band 768..~875; gold active
pill; carousel chevrons flanking the gold title on one row; "Locked" status
pill; the six field rows (World, Tier, Rules, Progress, Reward, Unlock) with
gold coin + reward value; full-width disabled slate CTA (fill (78,111,124),
light padlock/label) — all unchanged. pt strings ("Campos Verdes", "Sem
habilidades defensivas", "Passe 20 portões numa partida", "200 moedas, Brasa")
all fit their containers.

**Elsewhere: nothing measured worse.** Three expected consequences of the
fixes, for the record:

- The last visible Achievements card at 933 changed from pass 1's "Marathon"
  to "A Hundred Gates" — the direct result of unlocked cards growing a third
  line (the date) plus the boundary crop. Nothing is half-drawn; Marathon is
  simply below the fold.
- The shaved Collections rows redistributed space into the bottom padding
  (12px below the bar vs 8px above the title) — slightly bottom-heavy but not
  visibly unbalanced (see 3.1).
- 980 Milestones gains a sixth card (Forgebreaker, ends y 819 against panel
  bottom 847, 28px slack) — the elastic panel behaving as specified.

Deferred to the lead (unchanged from pass 1, owned by the lead, not
re-measured as a fix): **X.2** — nav buttons still flush with the screen's
bottom edge on every surface (active gold button's last row = screen's last
row; the reference keeps a ~15px margin at 933).

Intentional divergence, recorded once and not re-raised: **X.1** — the bottom
nav stays icon-only, matching `SectionNav` game-wide (lead's decision).

## Verdict — counts by severity for whatever REMAINS

All eight verified fixes: **8 of 8 FIXED, none partial, none worse.**
(3.1 caption, 1.1 boundary crop, 1.4 date line, 1.5 coin group, 2.1 track
colours, 2.2 pt reward value, 1.2 thumb width, 3.3 flag/star glyphs.)

Remaining open items carried from pass 1, with today's status:

- **CRITICAL: 0**
- **HIGH: 0** (X.1 resolved as an intentional divergence)
- **MEDIUM: 1** — X.2 nav flush with the bottom edge (deferred to the lead)
- **LOW: 5** — 0.1b reward separator/copy ("200 coins, Ember" comma and the
  shorter description — string-content decision for the lead); 0.2 Challenges
  reveal slack (re-measured today: panel bottom 766, reveal 768..~875, ~107px
  = 11.5%H, unchanged); 0.3/X.4 flat backdrop (no sky gradient/clouds/hill
  layers — still true on every frame read today); X.3 active nav glyph still
  hard to identify at 420px (UNCERTAIN, unchanged); X.5 640 Collections
  Everything card + caption below the fold (re-confirmed: 6 category rows
  visible, crop clean, rest scrollable). The 640 Challenges panel share was
  re-measured today at 132..537 = 63.3%H — unchanged, 0.6% under target,
  recorded with the LOWs to keep the count honest.

**The goal is met: zero CRITICAL, zero HIGH, zero MEDIUM defects owned by the
Goals screen.** The single remaining MEDIUM (X.2) sits on the lead's desk.
