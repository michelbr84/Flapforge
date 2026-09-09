# Goals — visual QA pass 1

(render set: `build/goals-qa/`, built 2026-09-09; references: `docs/design/goals/{challenges,achievements,milestones,collections}.png`)

Method: renders judged in the order en-933 (tabs 0-3), pt_BR-933 (tabs 0-3), the four
references, then the 640/980 spot checks. One `##` section appended per tab as it was
judged. Severities: CRITICAL / HIGH / MEDIUM / LOW.


Measurements in logical px (renders are 1:1 with the 420xH logical surface),
taken with a pixel classifier (`build/goals-qa/edges.py`) plus visual reading.

## Tab 0 — Challenges

Render `goals-en-933-tab0.png` (420x933). Frame skeleton (identical on all four
tabs): sky 0..111, header ink ~57..88 (~42px em, matches the ~43px spec target),
tab row y 112..147 (36px = 3.9%H, spec 3.9%H — exact), panel y 162..766
(h 604 = 64.7%H, target 64.8-69.2% — at the low edge), background reveal
768..~872 (105px = 11.3%H), nav band ~876..933.

| # | Sev | Finding |
| - | --- | ------- |
| 0.1 | OK (CORRECTED after reference read) | Carousel arrows flanking the gold title on one row is what the MOCK shows: at 941px the chevron buttons and "No Shield I" share the band y~340..415, title centred between them. The render matches the mock; the spec's itemized "top to bottom: 1. arrows, 2. title" wording was ambiguous and should be fixed, not the render. |
| 0.1b | LOW | Mock's Reward value reads "200 coins **+** Ember"; the render's string uses a comma ("200 coins, Ember"). Also the mock description is the longer "Survive 30 gates without defensive abilities." while the render shows a shorter "Survive 30 gates". Both are string-content choices, not layout defects — flagging so the lead can decide whether the copy should match the mock. |
| 0.2 | LOW | Background reveal under the panel is 11.3%H (105px) — the largest slack of the four tabs; reference 16:9 shows 6.9%, the 19.5:9 mock 2.3-3.6%. Panel height itself is on target, so this is breathing room, not a letterbox. |
| 0.3 | LOW | Sky and hill bands are flat fills (sky (75,196,207), fields (222,216,149)); reference has a vertical sky gradient and layered hill silhouettes in the reveal. Decorative only. |

On-spec and confirmed by measurement: four equal-width tab pills (x 19-112,
115-208, 211-304, 307-400 — 94px each, ~3px gaps; reference scales to the same);
panel x 19..400 (inset 19px ≈ reference 4.2%W); locked status pill under the
gold title; World/Tier tile pair then Rules, Progress, Reward, Unlock tiles;
Reward value gold with coin icon; full-width disabled slate CTA labelled
"Locked" with padlock (the unlocked gold "Start Challenge" state is not
exercisable in this render set — UNCERTAIN); no dim/veil over locked content, as
the reference demands. No clipped or colliding text on this tab in EN.

pt_BR (`goals-pt_BR-933-tab0.png`): header "Metas", tabs "Desafios / Conquistas /
Marcos / Coleções", title "Sem Escudo I", "Bloqueado" pill, "Sobreviva a 30
portões", "Campos Verdes", "Sem habilidades defensivas", "Ainda não jogado",
"200 moedas, Brasa", "Passe 20 portões numa partida" — every string fits its
container; no pt_BR-only overflow. "Conquistas" fills its 94px pill tightly but
stays inside it.


## Tab 1 — Achievements

Render `goals-en-933-tab1.png` (420x933). Panel y 162..803 (h 642 = **68.8%H**;
the reference's achievements panel is 68.7%H — essentially exact). Background
reveal 804..~875 (72px = 7.7%H). Active gold tab = "Achievements" (x 115..208).

| # | Sev | Finding |
| - | --- | ------- |
| 1.1 | MEDIUM | The last visible card ("Marathon / Pass 1000 gates in total") is sliced mid-text by the panel's bottom edge: the subtitle's glyph row is cut through at y=803, leaving half-height letters. The reference crops its list at a card boundary; clipping through a text line reads as a bug, not a scroll affordance. Likely cause: the scroll viewport's bottom = panel bottom with no per-card clip or fade. |
| 1.2 | LOW | Scrollbar thumb is present (x ~392-395, y 175..312) but only ~4px wide; the reference thumb is ~20px at 941px width → ~9px at this scale, so the render's thumb is about half the intended weight. |
| 1.3 | OK (resolved after reference read) | Summary bar arrangement matches the mock: full-ish track with the gold fill at its left and the percentage label to the RIGHT of the track (mock track x 82..760 = 72%W with "2%" at its right; render track x 30..310 = 66.7%W with "7%" at its right — the render's track is proportionally ~5%W shorter, negligible). |
| 1.4 | MEDIUM | Unlocked-card date placement: the mock renders "Unlocked <date>" as a THIRD LEFT-ALIGNED line under the subtitle inside the text stack ("First Flight / Finish a run / Unlocked 2026-09-09"). The render right-aligns "Unlocked 2023-11-14" on its own row at the card's bottom-right. Same information, visibly different arrangement. |
| 1.5 | MEDIUM | Locked-card reward is text-only: the mock right-aligns a gold COIN icon (~52px) together with the gold "+N coins" text as one group; the render draws only the "+N coins" text with no coin. The spec pinned this coin explicitly ("gold coin ~52px plus '+100 coins', right-aligned as a group"). |

On-spec: gold "3 of 41 unlocked" summary line top-left (count is data, reference
shows "1 of 41"); unlocked cards carry a green-tinted fill/border with a green
check medallion and a gray "Unlocked <date>" bottom-right (reference's complete
state is a green glow, not gold — matched); locked cards are dark teal with a
white padlock chip and a right-aligned gold "+N coins" (100/300/100/250/600/400 —
sensible ladder); white bold title over a smaller gray subtitle; no text
overflow inside cards in EN. Reinforced by the reference read: the mock's
visible list ends CLEANLY at the panel bottom (last card fully drawn, no
mid-text slice), so 1.1 is a genuine divergence, not a mock trait. Mock reward
values differ from the render's (+800/+50 vs +100/+400 etc.) — content-spec
territory, not visual QA.

pt_BR (`goals-pt_BR-933-tab1.png`): "3 de 41 desbloqueadas", "+N moedas",
"Conquistada em <date>", "Vinte e Cinco Canos" all fit. The bottom card
"Maratona / Passe 1000 canos no total." is sliced mid-text exactly as in EN —
defect 1.1 is language-independent. No pt_BR-only overflow.


## Tab 2 — Milestones

Render `goals-en-933-tab2.png` (420x933). Panel y 162..808 (h 647 = **69.2%H** —
the reference's milestones panel is 69.2%H, exact). Background reveal
809..~875 (66px = 7.1%H). Active gold tab = "Milestones" (x 211..304).

| # | Sev | Finding |
| - | --- | --- |
| 2.1 | MEDIUM (upgraded after reference read) | Empty tracks are near-black slots; the mock's are stroked teal pills. Measured: render track fill (13,39,44) — DARKER than its card (18,63,68), no outline; mock track fill (23,74,80) — LIGHTER than its card (14,58,62) — plus a light stroke (58,139,148) and rounded pill ends. Four 0-progress cards therefore read as four empty holes in the render where the mock reads as four unfilled meters. Root cause: the spec's colour table advised unifying the 16:9 track down to `PIP_EMPTY` #0D272C; the mock actually draws track = #174A50-ish + stroke. The dark track remains correct on Collections (its own mock measures #0E272B) — the fix is per-tab: restore the lighter stroked track on Challenges/Achievements/Milestones. |
| 2.2 | MEDIUM | pt_BR only (`goals-pt_BR-933-tab2.png`): the second card's title is ellipsis-truncated to "Recompensa do nível 5: 15…" where EN fits "Level 5 reward: 150 coins" on one line. The cut does not just shorten flavour text — it eats the reward value ("150 moedas" becomes "15…"), so the player cannot read what the milestone pays. Likely cause: fixed single-line title width with no pt_BR-aware shrink/wrap; the pt string is ~30% longer. |

On-spec: gold "Next milestones" heading, left-aligned, only gold element besides
the bar fills (reference: gold on this tab appears only in the heading —
matched); cards with a single white title, right-aligned counter ("94 / 133 XP",
"4 / 5", "0 / 1") and no percentage text (the counter is the percentage —
matched); full-card-width bars under each title with fills consistent with their
counters (94/133 ≈ 70% drawn ≈ 70%; 4/5 = 80% drawn ≈ 80%); last card ends
~18px above the panel bottom — nothing is clipped mid-text on this tab (the
contrast with tab 1's sliced "Marathon" is visible). The tab's silhouette
(heading + plain counter cards, no icons) is clearly distinct from tab 1's
(icon cards + summary block) — the references' per-screen identities survive.


## Tab 3 — Collections

Render `goals-en-933-tab3.png` (420x933). Panel y 162..788 (h 627 = **67.2%H**;
the collections reference measures 67.1%H — exact). Background reveal
789..~875 (87px = 9.3%H) in a muted gray-green (95,147,136) — a different
backdrop tone than tabs 0-2 (light field 222,216,149), which matches the
references' per-screen backdrops. Active gold tab = "Collections" (x 307..400).

| # | Sev | Finding |
| - | --- | --- |
| 3.1 | HIGH | The mock's closing caption — "Keep playing. There's more to discover!", centred under the Everything card — is clipped to its top 2 glyph rows in the render (dim gray pixels y 785-786, x 110..309, then nothing): a ghost smudge. The caption exists in the render's draw list but the viewport ends ~18px too high to show it. Fix: reclaim ~20px (rows are ~68px here vs ~59-63px proportional to the mock; shaving 3px per row frees 21px) or clip cleanly. As-is it reads as a rendering artifact. |
| 3.3 | LOW | Row icon glyphs diverge from the mock on two rows: Challenges shows a white document where the mock has a red flag, and Achievements a gold diamond where the mock has a gold star. Birds/Abilities/Worlds/Colours/Upgrades/Everything match (bird, diamond, world, palette, hammer, crown). Decorative, but the flag and star are recognisable identity choices in the mock. |
| 3.2 | OK (recorded) | The active tab here is a filled gold pill. The collections mock alone outlines the active tab in gold over teal; the spec resolved this mock inconsistency in favour of the gold pill (3 of 4 references). The render follows the spec decision — deliberate, not drift. |

On-spec: seven category rows (Birds 1/7 14%, Abilities 1/8 12%, Worlds 1/5 20%,
Challenges 0/7 0%, Colours 1/22 4%, Achievements 3/41 7%, Upgrades 0/43 0%) each
with icon, white title, gray subtitle, right-aligned "n / m (pct%)" counter and a
white chevron; counter arithmetic checks out on every row; bars sit under the
titles with fills consistent with the counters; the "Everything" card is taller
than the rows (~87px vs ~68px), gold-bordered, with a gold crown and "7 / 133
(5%)" — matching the reference's stronger final card. Rows are slightly darker
than the panel with a thin stroke, as measured in the reference.

pt_BR (`goals-pt_BR-933-tab3.png`): "Aves / Habilidades / Mundos / Desafios /
Cores / Conquistas / Melhorias", "Tudo / Conclusão total do jogo" — all fit.
The ghost caption line below the "Tudo" card is present exactly as in EN —
defect 3.1 is language-independent. No pt_BR-only overflow.


## Cross-cutting (frame, nav, utilization, both languages)

Panel utilisation, measured (all within or at the 64.8-69.2%H target; no
letterbox anywhere):

| surface | tab | panel y | %H | reveal | nav band |
| ------- | --- | ------- | -- | ------ | -------- |
| 640  | Challenges  | 133..537 | 63.3% | 44px = 6.9%H (mock share, exact) | ~585..640 |
| 640  | Collections | 133..560 | 66.9% | ~21px = 3.3%H | same |
| 933  | Challenges  | 162..766 | 64.7% | 105px = 11.3%H | ~876..933 |
| 933  | Achievements| 162..803 | 68.8% | 72px = 7.7%H | same |
| 933  | Milestones  | 162..808 | 69.2% | 66px = 7.1%H | same |
| 933  | Collections | 162..788 | 67.2% | 87px = 9.3%H | same |
| 980  | Milestones  | 170..847 | 69.2% | 74px = 7.6%H | ~923..980 |
| 980  | Collections | 170..828 | 67.2% | 94px = 9.6%H | same |

| # | Sev | Finding |
| - | --- | ------- |
| X.1 | HIGH | The bottom nav is icon-only on every one of the 40 renders: a scan of the nav band found zero rows with more than 15 bright pixels — no text labels exist. All four references label every button (Shop, Birds, Play, Forge, Goals). The parent's frame anatomy pins "nav showing Shop/Birds/Play/Forge/Goals"; icons alone do carry the items, but identity rests on glyph recognition and the reference is unambiguous. UNCERTAIN: whether the shipped `SectionNav` ever draws labels (if it is icon-only game-wide, this is a conscious global divergence and the lead should decide whether Goals re-introduces labels or stays consistent). |
| X.2 | MEDIUM | Nav buttons are flush with the screen's bottom edge on every surface: the active gold button's last row is the screen's last row (y=639/640, 932/933, 979/980). The reference keeps a ~27px margin below the buttons (1.6%H → ~15px at 933). Likely cause: the nav band is bottom-pinned with its padding above the buttons, not below. |
| X.3 | UNCERTAIN | The active Goals glyph in the gold nav button is hard to identify at 420px (reads as a dark rounded shape with internal lines; the 16:9 mocks use a document/list glyph, the collections mock a trophy). Worth a look at 2x zoom before calling it wrong. |
| X.4 | LOW | Backdrop is flat: no sky gradient, no clouds, single-tone hill band (sky (75,196,207), fields (222,216,149), tab-3 reveal (95,147,136)). Mocks carry a sky gradient, clouds and layered hill silhouettes. Decorative; panel tone contrast is unaffected. |
| X.5 | LOW | At 640 Collections, the "Everything" card and caption sit below the fold (7 rows fill the panel); they are reachable by scroll and nothing clips — noted because the mock presents all eight elements + caption as one composition. |
| X.6 | OK | Frame skeleton is identical across all tabs, languages and heights: no Back button anywhere; four equal 94px tab pills with ~3px gaps; header ink ~57..88 at 933 (same absolute size at 640/980 — width-scaled, correct); panel top 162/133/170 tracks the tab row + 15px gap; panel inset 19px ≈ the mock's 4.2%W. |
| X.7 | OK | Utilisation story: the elastic panel absorbs the extra height (933→980 adds ~60-80px of content room — the Collections caption that clips at 933 fits fully at 980), the background reveal takes the slack, and the nav anchor stays pinned. This is exactly the Section 8 behaviour the spec asked for. |
| X.8 | OK | pt_BR: no overflow anywhere except 2.2; "Metas" header, "Coleções"/"Conquistas" pills, "Conquistada em <date>", "Recompensa do nível 5" (truncation aside) all hold. The icon-only nav (X.1) is incidentally language-invariant. |
| X.9 | OK | The four tabs keep four distinct silhouettes (carousel with flanking arrows; summary block + icon cards; gold heading + counter cards; category rows + gold Everything card), matching the references — not one list with different data. |

## Verdict — counts by severity, and the single worst defect

Severity counts over all findings recorded above (OK/CORRECTED entries excluded):

- **CRITICAL: 0**
- **HIGH: 2** — 3.1 (Collections caption clipped to a 2px ghost line at 933), X.1 (nav has no button labels vs the labelled mock nav)
- **MEDIUM: 6** — 1.1 (Marathon card sliced mid-text, both languages), 1.4 (unlocked-card date right-aligned instead of a third left line), 1.5 (missing coin icon on locked-card rewards), 2.1 (milestone empty tracks are near-black slots instead of the mock's stroked teal pills), 2.2 (pt_BR "Recompensa do nível 5: 15…" truncation eats the reward value), X.2 (nav buttons flush with the screen bottom)
- **LOW: 8** — 0.1b (reward separator/copy), 0.2 (reveal slack at 933 Challenges), 0.3/X.4 (flat backdrop), 1.2 (scrollbar thumb half weight), 3.3 (flag/star icon glyphs), X.3 (nav glyph UNCERTAIN), X.5 (640 Collections fold), plus the 640 Challenges panel at 63.3% (0.6% under target, recorded here to keep the count honest)

**Single worst defect: X.1 — the unlabelled bottom nav.** It touches all 40
renders, both languages and every Goals tab, and it is the one place the build
diverges from the reference's explicit, information-bearing anatomy (five
labelled buttons). Runner-up: 3.1, the clipped "Keep playing…" caption, which is
the most bug-looking artifact on a single screen. Note for `ff-ui`: 1.1 and 3.1
share one root cause (scroll viewport clips mid-element at the panel bottom);
2.1 is caused by the spec's own track-colour advice and needs the lighter
stroked track restored on the three 16:9-style tabs only.
