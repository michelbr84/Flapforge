# Forge / Upgrades screen — visual spec

Written for an agent with no vision. Every number is in the **reference image's own pixel
coordinates** (941 × 1672 px) unless a *logical* value is given; logical values are the image
pixels multiplied by `420 / 941 = 0.4463` (the scale that maps the reference onto the game's
420-px logical width). `UNCERTAIN` marks what I could not verify; `NOT SHOWN` marks what the
images do not show.

---

## 1. Source images and classification

| File | Size | Classification | Evidence |
| --- | --- | --- | --- |
| `docs/design/forge/voo.png` | 941×1672 | **(a) target design** — the Flight ("Voo") tab | Shows a 5-item bottom nav, an illustrated header, an icon+CTA detail panel and pip-bar stat rows — none of which exist in the shipped build (`UpgradeTreeScreen` has a text-only detail panel, a full-width Back button, no bottom nav). Yet every node name, level (`Lv 0/3`), effect wording and price (50 / 90 / 150) matches `data/upgrades.json` and `strings/en.json` exactly, and the stat values (1800 / 405 / 1500 / 1 / 1) are the shipped base constants in `StatId`. So: real Flapforge content, redesigned shell → a target mock, not a screenshot. |
| `docs/design/forge/economia.png` | 941×1672 | **(a) target design** — the Economy ("Economia") tab | Same shell. Content matches `upgrades.json` economy tree (Coin Purse `Lv 0/4`, Scholar `Lv 0/4`, Lodestone `Lv 0/3`, Coin Rain `Lv 0/3`, Trial by Fire `Lv 0/1`, Ability Scholar `Lv 0/1`); tree lock price 120 matches `unlock.purchase.amount: 120`. Balance 50 < 120 so every card is locked and the CTA reads `DESBLOQUEAR ÁRVORE • 120` with a red `Moedas insuficientes` — consistent target-state rendering, not a build screenshot (the build cannot render this screen today). |
| `docs/design/forge/forja.png` | 941×1672 | **(a) target design** — the Forge ("Forja") tab | Same shell. Content matches the forge tree (Tempered Shield `Lv 0/2` … Second Chance `Lv 0/1`); lock price 900 matches `unlock.purchase.amount: 900`. |

None of the three is (b) a screenshot of the current implementation: the shipped screen has no
bottom navigation, no per-node icons, no purchase button, no illustrated header, and no
"Tier-n" pills — all present in every image. None is (c) a content-free conceptual mockup:
the content is verifiably the shipped 18-node content. They are **target designs of the same
content in a new mobile shell**, one per tree tab.

## 2. Canvas and aspect — the critical measurement

- Images are 941 × 1672 = aspect **0.5628 (9:16)**.
- They are **full-bleed UI**: sky colour reaches y=0, the nav bar's foot strip reaches y=1672,
  and edge scans show no letterbox bars on any side (top rows are sky `#46D1E1`, bottom rows
  are the nav foot `#232D2A`, left/right columns are UI at every scanned height).
- Mapped at the game's 420 logical width (scale 0.4463), the reference is a **420 × 746
  logical canvas**. The shipped `Playfield` canvas is 420 × 640 (aspect 0.656; Android surface
  840 × 1280, same aspect). **The reference does not fit the 420×640 canvas at its own
  proportions**: it needs ~106 more logical rows (≈ 17 % taller), or equivalent compression.
- Region budget at 420-width logical scale (image px → logical):

| Region | Image y | Logical y | Logical height |
| --- | --- | --- | --- |
| Header (sky, title, subtitle, coin, illustration) | 0–300 | 0–134 | 134 |
| Category tabs | 308–408 | 137–182 | 45 |
| Tree area (tier pills + cards + connectors) | 430–940 | 192–420 | 228 |
| Detail panel | 945–1466 | 422–654 | 232 |
| Bottom navigation | 1488–1672 | 664–746 | 82 |

Compare the shipped layout budget: tabs 28, node area 300, detail 78, stats 96, footer 40.
The mock's detail panel alone (232) is as tall as the shipped detail + stats + footer
combined. See §21 for the three architecture decisions this forces.

## 3. Overall anatomy (vertical order)

1. Sky/header band with the screen title, per-tree subtitle, coin pill and an illustration.
2. Category tab row: three icon-over-label tabs.
3. Tree area: for each tier — a green tier pill, then the tier's node cards in a 2-column grid,
   with yellow Manhattan connectors routed between rows (under the cards).
4. Detail panel: one dark rounded card holding the selected node's icon, name, level,
   description, status line(s), the purchase CTA, then a divider and the stat summary rows.
5. Bottom navigation: five labelled tiles on a dark band, one active (gold).

Side margin: ~34 px image (≈ 15 logical) on both edges; content spans x ≈ 24–916 for panels,
x ≈ 34–912 for cards/tabs. Corner rounding is generous everywhere: cards/tabs/tiles r≈16–20 px
image (7–9 logical), panels r≈24 px (11 logical), pills fully rounded. Shadows: soft dark
drop shadows under cards, tabs and tiles (offset ~4–6 px down, blur ~10 px, black ~25 %).
UNCERTAIN on exact shadow parameters; they are subtle.

## 4. Header

- **Title**: `Forja` — identical on all three tabs (the *screen* is named Forge, not the tree).
  Position x ≈ 30–265, y ≈ 85–170 (cap height ≈ 85 px → ≈ 38 logical). Very heavy rounded
  display face; fill is dark charcoal-teal, with a thick white outline (~8 px) and a soft dark
  drop shadow. Left-aligned.
- **Subtitle**, left-aligned under the title, two lines, x ≈ 35, y ≈ 185–250, ~30 px
  (≈ 13 logical), regular weight, dark teal ink:
  - voo: `Melhore seus atributos com upgrades permanentes.`
  - economia: `Melhore sua economia com upgrades permanentes.`
  - forja: `Melhore seus escudos e utilidade com upgrades permanentes.`
  (One subtitle per **tree**, not one per screen.)
- **Coin balance**: dark-teal rounded pill, x ≈ 730–915, y ≈ 35–105 (r ≈ full), fill
  `#0E4149`; inside, left: gold coin icon ≈ 44 px diameter (`#FCC239` with darker rim and a
  highlight); right of it the balance `50` in white bold ≈ 40 px. Top-right corner. The
  balance is 50 in all three images.
- **Illustration**: right side, x ≈ 540–941, y ≈ 130–300, sitting on the hill line: a grey
  bird perched on a dark anvil with a light radial glow behind it, flanked by two red banners
  on poles. The banner icon changes with the active tab — voo: white hammer; economia: gold
  coin; forja: white shield. Economia additionally piles gold coins at the anvil's base.
  Finer art details: UNCERTAIN (procedural art will interpret).

## 5. Category tabs

Three tabs, equal width ≈ 283 px (≈ 126 logical), height ≈ 98 px (44 logical), r ≈ 20 px,
row y ≈ 308–408, gaps ≈ 9 px (4 logical), spanning x ≈ 38–910.

- **Active tab**: saturated gold fill (`#FFD03C`–`#FFD338`), dark ink icon and label, no
  visible outline, soft shadow. Icon (≈ 44 px) centred above the label (≈ 30 px bold).
- **Inactive tab**: dark teal fill (`#0C3E44`–`#11474C`), white/light icon and label, no
  strong outline (a slightly darker rim, UNCERTAIN).
- Labels + glyphs: `Voo` = feather; `Economia` = stack of coins; `Forja` = hammer. Order is
  fixed: Voo, Economia, Forja (= trees flight, economy, forge).
- The active tab treatment is a **gold plate** (filled button), not an underline.

## 6. Tier labels

Small fully-rounded pills at the left margin, ≈ 100 × 34 px (≈ 45 × 15 logical), fill mid
green `#54BA62`, dark ink text ≈ 26 px bold: `Tier 1`, `Tier 2`, `Tier 3` (English word
"Tier" kept in the pt-BR UI). Each pill sits in the vertical gap **above its tier's first
card row**, left-aligned at the content margin. A pill appears per tier regardless of how
many rows the tier spans.

## 7. Node cards

Two columns. Left column x ≈ 34–476 (≈ 438 px wide, 195 logical), right column x ≈ 494–912
(≈ 418 px, 187 logical); column gap ≈ 16–22 px (≈ 8 logical). UNCERTAIN on the ~20 px
left/right column width difference — treat columns as equal and measure again at build time.
Side margins ≈ 34 px / 29 px.

- **Height**: ≈ 104–112 px (≈ 46–50 logical) for cards whose effect text fits one line;
  ≈ 130 px (≈ 58 logical) when the effect wraps to two lines. Row gap inside a tier ≈ 6–10 px
  (≈ 3–4 logical). This is very close to the shipped `NODE_H = 46`.
- **Shape/fill**: rounded r ≈ 18 px, dark teal fill `#11474C`–`#13454C` (open cards); locked
  cards read darker, `#0C2A2E`–`#0F3A3F` (a dark veil over the fill). No visible stroke on
  unselected cards (at most a slightly darker rim — UNCERTAIN).
- **Anatomy, left to right**:
  1. Icon, ≈ 55–60 px (≈ 25 logical), vertically centred: white/near-white on open cards,
     grey (desaturated) on locked cards.
  2. Title, white bold ≈ 28 px (≈ 12 logical).
  3. Under it, the state line, light grey-blue ≈ 24 px: `Lv {owned}/{max}` + ` • ` + effect
     phrase (e.g. `Lv 0/3 • -3% Gravidade`), wrapping to a second line when needed.
  4. Right end: **either** a gold coin (≈ 36 px) + price in white bold ≈ 30 px
     (open, purchasable nodes) **or** a light padlock (≈ 30 px) at the top-right
     (locked nodes — no price shown at all).
- **Per-node icons** (voo / economia / forja): feather, wing, t-shirt, stopwatch, wind
  swirl, feather; money bag, graduation cap, horseshoe magnet, coin stack, flame, gear;
  shield, swoosh/chevron, stopwatch, crossed swords, rising arrow, wing. (Icons are
  mockup-quality; procedural equivalents to be chosen by the implementer.)

## 8. Node states (as rendered in the images)

All three images show the profile at Lv 0 everywhere with 50 coins, so:

- **Selected** (Feather Weight, Coin Purse, Tempered Shield — the first node of each tree):
  thick pale-yellow outline ≈ 5–6 px (`#F6EE7E`–`#F9F876`), normal fill, white icon. The
  selected node is also the one the detail panel shows.
- **Affordable** (tree open, prereqs met, balance ≥ price): no outline; white icon; coin +
  price badge on the right (Feather Weight shows this *and* the selection outline).
- **Unaffordable** (Glide 90 > 50, Quick Recharge 150 > 50): same geometry, coin + price
  badge still shown; icon and text look slightly dimmed/grey. UNCERTAIN how much of the
  dimming is state vs. art style — the mock gives no affordable-but-unselected open node to
  compare against.
- **Locked** (missing prereq or locked tree): darker veil over the card, grey icon, light
  padlock at the top-right, **no price badge**. Every economia/forja card is locked (its
  tree is locked); Slim Frame, Updraft, Featherfall are locked by prereqs while their tree
  is open.
- **Maxed**: NOT SHOWN (all nodes at Lv 0).
- **Already-owned / redundant**: NOT SHOWN.

## 9. Prerequisite connectors

- Colour light yellow `#F6DE64`–`#F9E768`; thickness ≈ 6–8 px (≈ 3 logical); sharp 90°
  elbows (Manhattan routing: vertical → horizontal → vertical).
- Routed **under the cards**: long verticals that cross an intervening row disappear behind
  that row's card and resume below it (clearly visible in forja.png, where the Tempered
  Shield vertical passes behind Cooldown Forge).
- Where a node has two prerequisites, the lines meet in a **T / merge junction** before
  entering the target (forja.png: Master Forge and Second Chance both show a horizontal
  joining a vertical).
- Connector x-anchors sit ≈ 15–20 px left of the card centres in the mock (source and target
  alike). UNCERTAIN whether that offset is intentional; true card centres are the sane rule.
- **Important honesty note**: the mock's connector *endpoints* do not reproduce the shipped
  `prereqs` graph edge-for-edge. Examples: a Glide → Quick Recharge edge is drawn although
  `quick_recharge_1` has no prereq; no Glide → Updraft edge is drawn although `updraft_1`
  lists `glide_1`; economia.png's left column reads as a chain
  Coin Purse → Lodestone → Coin Rain → Ability Scholar, which is not the shipped graph. The
  connectors should be read as **styling direction**, not as a data change: keep edges
  data-driven from `prereqs` (as the shipped screen already does) and adopt the mock's
  weight, colour, z-order and merge junctions.

## 10. Detail panel

One dark rounded panel, x ≈ 24–916, y ≈ 945–1466 (≈ 398 × 521 px → 178 × 232 logical),
fill `#134248`/`#103A40`, r ≈ 24 px. Contents, in order:

1. **Hero icon** at the left, centre ≈ (115, 1075), ≈ 150 px (≈ 67 logical), with a gold
   radial glow (ray burst) behind it: white feather (voo), white money bag with `$`
   (economia), white-and-blue shield (forja).
2. **Title**, white bold ≈ 42 px (≈ 19 logical), x ≈ 218: the node name.
3. **Level**, light grey ≈ 30 px: `Lv 0/3`.
4. **Description**, white ≈ 30 px, one line: `Reduz a gravidade em 3% por nível.` (voo) /
   `Moedas ganhas +5% por nível.` (eco) / `Comece cada run com 1 carga de escudo.` (forja).
5. **Status line(s)**, label in accent colour + value in white ≈ 30 px:
   - Tree open: `Próximo nível:` (gold) + ` -3% Gravidade` (white).
   - Tree locked: `Árvore bloqueada:` + `120 moedas` / `900 moedas`. The label is gold in
     economia.png but **red** in forja.png — a mock self-inconsistency (§20).
   - forja.png shows **both** lines at once (`Próximo nível` and `Árvore bloqueada`) while
     economia.png shows only the lock line — also inconsistent between the two images.
6. **CTA button**, right-aligned inside the panel, x ≈ 623–887, y ≈ 1015–1105 (≈ 118 × 40
   logical), r ≈ 16 px (§11).
7. **Insufficient-coins note** under the CTA, red `#D45752` ≈ 28 px:
   `Moedas insuficientes` (eco, forja).
8. **Divider**: thin teal line `#1B626B`, ≈ 4 px, full panel width (inset ≈ 40 px), at
   y ≈ 1161.
9. **Heading**: `Resumo dos atributos`, gold bold ≈ 32 px, x ≈ 65.
10. **Stat rows** (§12).

## 11. Purchase CTA

- **Buy-a-level state** (tree open): bright gold `#FBC538`, dark bold label, gold coin icon
  (≈ 40 px) left of the text: `MELHORAR • 50`.
- **Unlock-the-tree state** (tree locked): label `DESBLOQUEAR ÁRVORE • 120` (eco) /
  `DESBLOQUEAR ÁRVORE • 900` (forja), two lines, coin icon left. Fill: economia renders it
  **muted** `#C2A339`/`#BFA036`; forja renders it **bright** `#FEC639` — inconsistent between
  the two images (§20). Recommended rule: bright gold when affordable, muted gold +
  `Moedas insuficientes` in red when not; both mock trees are unaffordable at balance 50.
- **Maxed / redundant CTA**: NOT SHOWN.
- The CTA lives **inside the detail panel**, top-right area, and acts on the selected node
  (or on the locked tree when the selection's tree is locked).

## 12. Stat summary rows ("Resumo dos atributos")

Row geometry: pitch ≈ 44 px (≈ 20 logical), five rows in voo/forja, four in eco. Per row,
left to right:

1. White icon ≈ 40 px (x ≈ 70–110): per-stat glyph (arrow, wing, gauge, dartboard, lightning;
   coins, cap, coins, magnet; swords, stopwatch, swoosh, shield, heart).
2. Label, white ≈ 28 px (x ≈ 125).
3. **Pip bar**: five rounded segments, each ≈ 48 × 28 px with ≈ 8 px gaps, starting x ≈ 450,
   ending x ≈ 745. Filled segment gold `#FCCB45`; empty segment very dark navy `#0D272C`.
   Fill counts observed: voo 2/2/2/1/1; eco 1/1/1/0; forja 2/2/2/0/0.
4. Value, white ≈ 30 px, right-aligned at x ≈ 880.

Values are the **live resolved stat sheet at Lv 0** (they match the shipped base constants:
GRAVITY 1800, FLAP_VELOCITY 405, MAX_FALL_SPEED 1500, HITBOX_SCALE 1, ABILITY_COOLDOWN_MULT 1;
COIN_MULT 1, XP_MULT 1, COIN_SPAWN_RATE 0.5, MAGNET_RADIUS 0; SHIELD_CHARGES 0, REVIVES 0).
The pip semantics are UNCERTAIN: they do not track owned levels (all levels are 0 yet 0–2 pips
are filled), and the same stat fills differently per tree (Hitbox 1 pip in voo, 2 pips in
forja; Recarga 1 in voo, 2 in forja) — most likely a per-tree normalised magnitude. Decide the
rule before implementing; do not copy the mock's literal counts.

Row **sets** per tab exactly equal the stats the tab's nodes touch (shipped behaviour):
flight GRAVITY / FLAP_VELOCITY / MAX_FALL_SPEED / HITBOX_SCALE / ABILITY_COOLDOWN_MULT;
economy COIN_MULT / XP_MULT / COIN_SPAWN_RATE / MAGNET_RADIUS; forge HITBOX_SCALE /
ABILITY_COOLDOWN_MULT / ABILITY_DURATION_MULT / SHIELD_CHARGES / REVIVES. Row **order**
differs from the shipped content-order rendering: the mock orders flight rows
Gravidade, Força da batida, Velocidade, Hitbox, Recarga (a canonical stat order), while the
shipped screen emits content order (Gravidade, Queda, Hitbox, Recarga, Impulso).

## 13. Bottom navigation

- Dark band y ≈ 1488–1672 (≈ 82 logical tall), background `#133233`; below the tiles a darker
  foot strip y ≈ 1626–1672 `#232D2A` (reads as a home-indicator / safe-area band; UNCERTAIN
  whether it is part of the component or just the mock's phone chrome).
- **Five tiles**, x margins ≈ 16 px, tile width ≈ 166 px (≈ 74 logical), height ≈ 120 px
  (≈ 54 logical), r ≈ 16 px, gaps ≈ 17 px.
- Order and labels (pt): `Loja` (storefront with awning), `Aves` (bird silhouette),
  `Jogar` (play **triangle**), `Forja` (hammer), `Metas` (document/scroll with lines) —
  i.e. shop, birds, play, forge, goals; **Forja is the active item on this screen**.
- **Active tile**: saturated gold fill `#FBC439`, dark icon and label, no light outline.
- **Inactive tiles**: dark teal fill `#0D3D42` with a thin light-teal outline (≈ 2 px,
  ≈ `#4A8C9A` — UNCERTAIN on exact tone), light icon and label (`#B8CBCB`-ish).
- Icon above, label below, both centred; labels are always visible (icon-only is not shown).

## 14. Background / environment

Sky band `#46D1E1` at the top fading toward the horizon; large soft white clouds (one behind
the title, two right). Rolling hills in two green tones — light `#57C893` upper band, deeper
`#3EBA8F`/`#4CB46C` below, with darker bush clusters at the illustration's base. The whole
tree/panel area sits over this green field; the detail panel and nav band overlay it. No
scrolling parallax is observable in stills — UNCERTAIN whether the background animates.

## 15. Typography hierarchy (relative)

1. Title `Forja`: ≈ 100 px, heaviest weight, outlined display face.
2. Panel node title: ≈ 42 px bold.
3. Coin balance, CTA label, stat values: ≈ 30–40 px bold.
4. Card titles: ≈ 28 px bold.
5. Subtitle, descriptions, stat labels, tab labels, nav labels: ≈ 28–30 px regular.
6. Card state line, level line, insufficient note: ≈ 24–28 px regular/semibold.
All ink colours: white `#FDFDFD` (primary), grey-blue `#9FB3B8`/`#B8CBCB` (muted), dark ink
`#1A3A3F`–`#20343A` (on gold/green), dark teal `#0B4A5E` (subtitle on sky). Accent ink gold
`#FBC538`-family for labels like `Próximo nível:`, `Resumo dos atributos`; warning red
`#D45752` for `Moedas insuficientes` / `Árvore bloqueada:` (forja only).

## 16. Colour roles (summary)

| Role | Value(s) seen |
| --- | --- |
| Sky | `#46D1E1` |
| Hills light / deep | `#57C893` / `#3EBA8F`–`#4CB46C` |
| Card fill (open / locked) | `#11474C`–`#13454C` / `#0C2A2E`–`#0F3A3F` |
| Panel fill | `#103A40`–`#134248` |
| Selection outline (pale yellow) | `#F6EE7E`–`#F9F876`, 5–6 px |
| Connector yellow | `#F6DE64`–`#F9E768`, 6–8 px |
| Gold plate (tabs/CTA/coin/nav active) | `#FBC23B`–`#FEC639` |
| Gold plate muted (unaffordable CTA, eco only) | `#BFA036`–`#C2A339` |
| Tier pill green | `#54BA62` |
| Pip filled / empty | `#FCCB45` / `#0D272C` |
| Warning red | `#D45752`–`#D56068` |
| Nav band / tile / foot | `#133233` / `#0D3D42` / `#232D2A` |
| Divider | `#1B626B` |

## 17. Exact pt-BR text visible (transcription)

- Header: `Forja` · `Melhore seus atributos com upgrades permanentes.` ·
  `Melhore sua economia com upgrades permanentes.` ·
  `Melhore seus escudos e utilidade com upgrades permanentes.` · balance `50`
- Tabs: `Voo` · `Economia` · `Forja`; tier pills `Tier 1` `Tier 2` `Tier 3`
- Cards (`Lv {o}/{m}` + ` • ` + effect): `-3% Gravidade` · `-30% vel. máxima de queda` ·
  `-0.03 hitbox` · `×0.92 recarga de habilidade` · `+2% poder de batida` · `-2% Gravidade` ·
  `+5% multiplicaddr de moedas` **[sic — typo for "multiplicador"]** ·
  `+5% multiplicador de XP` · `+20 raio do ímã` · `+0.1 moedas por portão` ·
  `desbloqueia Hard` · `adiciona um slot passivo` · `+1 carga de escudo` ·
  `+5% duração de habilidade` · `×0.93 recarga de habilidade` · `-0.05 hitbox` ·
  `aumenta o limite de nível` · `+1 revive`
- Detail panel: `Próximo nível:` · `Árvore bloqueada: 120 moedas` ·
  `Árvore bloqueada: 900 moedas` · `Reduz a gravidade em 3% por nível.` ·
  `Moedas ganhas +5% por nível.` · `Comece cada run com 1 carga de escudo.`
- CTA: `MELHORAR • 50` · `DESBLOQUEAR ÁRVORE • 120` · `DESBLOQUEAR ÁRVORE • 900` ·
  `Moedas insuficientes`
- Stats: `Resumo dos atributos` · `Gravidade` · `Força da batida` ·
  `Velocidade máx. de queda` · `Hitbox` · `Recarga de habilidade` ·
  `Multiplicador de moedas` · `Multiplicador de XP` · `Moedas por portão` · `Raio do ímã` ·
  `Duração de habilidade` · `Cargas de escudo` · `Revives`
- Bottom nav: `Loja` · `Aves` · `Jogar` · `Forja` · `Metas`
- Node names stay **English** in the pt UI (`Feather Weight`, `Coin Purse`, …) — see §18.

## 18. Differences among the three images

Everything structural is identical (header, tabs, tiers, panel, nav, colours). Only these
change with the tab: subtitle text; illustration banner icon (hammer / coin / shield) plus
coin piles (eco); active tab; card set and tier row counts (flight 2/2/2 cards across tiers
1/2/3; economy 3/2/1 with tier 1 on two rows; forge 3/2/1 with tier 1 on two rows); detail
node, description, status lines and CTA; stat rows (5/4/5); the eco CTA is muted while the
forja CTA is bright (both unaffordable — mock inconsistency); `Árvore bloqueada:` label is
gold in eco, red in forja; forja shows a `Próximo nível` line even though its tree is locked.

## 19. NOT SHOWN

Toast/tooltip styling; focus rings and keyboard/pointer affordances; maxed and
already-owned/redundant card states; maxed CTA; insufficient-prereq copy on the detail panel
(the padlock carries that meaning on cards); scrolling behaviour for trees with more rows;
landscape/desktop layout; text-scale handling; a tree locked AND selected-card interplay
beyond what eco/forja show; the foot strip's interactive role; animation.

## 20. Mock self-inconsistencies (do not copy blindly)

1. `multiplicaddr` typo (eco, Coin Purse card).
2. Eco CTA muted vs forja CTA bright at the same "insufficient" state.
3. `Árvore bloqueada:` gold in eco, red in forja.
4. Forja shows `Próximo nível` while its tree is locked; eco does not.
5. Connector endpoints ≠ shipped `prereqs` graph (§9).
6. English fragments in the pt UI (`Tier`, `Lv`, node names, `Hard`, `Revives`, `Hitbox`)
   where the shipped pt table has translated words (`Camada`, `Nv`, `Peso Pena`, `Difícil`,
   `Ressurreições`, `Área de colisão`). Most likely mock convenience; the shipped localised
   strings should win unless the lead decides otherwise.

## 21. Architecture-level decisions requested by the lead

(a) **Bottom nav instead of the Back button** — the reference unambiguously wants the five-item
`SectionNav` on this screen (gold "you are here" plate on Forja) and shows no Back button.
Recommendation: add `SectionNav` (it already exists with the same five ids/order) and drop the
full-width Back button from the layout, keeping the BACK key/Escape path. Layout cost: the nav
band consumes ≈ 82 logical rows.

(b) **Dedicated purchase CTA instead of tap-to-buy** — the reference has a real CTA in the
detail panel (`MELHORAR • N` / `DESBLOQUEAR ÁRVORE • N`) plus an unlock purchase for locked
trees and a red insufficient-coins note. Today tapping a card buys directly and locked trees
cannot be bought here at all. Recommendation: add the CTA as the primary purchase path (it
also gives the tree-unlock sale a home); keep tap-to-buy on cards or retire it — that sub-choice
is the lead's.

(c) **Canvas proportions** — the reference is full-bleed 9:16 = **420 × 746 logical**, not the
shipped 420 × 640. Options: (1) keep 420×640 and compress/drop regions (shrink the header,
merge the illustration away, slim the panel — the mock's own regions then don't fit without
~17 % vertical scaling); (2) keep 420×640 and make the tree area scrollable (no scrolling
exists in the UI toolkit today); (3) grow the shared canvas to 420×746 (touches every screen,
letterboxing on the 0.656 Android surface, save-neutral but a large blast radius). The
reference itself gives no explicit scroll affordance and no letterbox — it reads as (3), but
(1)/(2) are cheaper. **Decision needed from the lead; the spec's numbers above are the budget
either way.**

---

## 22. Reconciliation against the repository

Legend (one per row): ✔ already implemented correctly · ◐ partially implemented ·
✗ visually wrong · ⨯ functionally wrong · ＋ missing · ~ inconsistent · ⊖ obsolete ·
⚠ conflicting with current data · ? needs clarification from code

| # | Verdict | Finding |
| --- | --- | --- |
| 1 | ✔ already implemented correctly | **Content matches 1:1.** All 3 trees and 18 nodes in the mock match `data/upgrades.json`: ids, tiers, maxLevels, prereq-gated locks, L1 prices (50/90/150), effect wording (−3% Gravidade, ×0.92 recarga, +20 raio do ímã, desbloqueia Hard, adiciona um slot passivo, …), tree unlock prices (120/900) and grant semantics. Node names match `strings/en.json` exactly. Stat values are the shipped base constants (StatId 1800/405/1500). |
| 2 | ~ inconsistent | **Node names in the pt UI.** Mock shows English names in an otherwise pt-BR screen; shipped `pt_BR` translates them (`Peso Pena`, `Bolsa de Moedas`, `Prova de Fogo`…). Same for `Tier`/`Lv`/`Hard`/`Revives`/`Hitbox`. Keep shipped localisation unless the lead wants fixed English names. |
| 3 | ＋ missing | **Bottom section navigation** on this screen (Loja/Aves/Jogar/Forja/Metas). `SectionNav` already implements exactly these five ids in this order — `UpgradeTreeScreen` just doesn't use it. |
| 4 | ⚠ conflicting with current data | **Nav "Play" glyph.** Mock draws a play triangle; `SectionNav.icon("play")` draws crossed hammers. The other four glyphs match (awning, bird, hammer, scroll). |
| 5 | ＋ missing | **Nav labels.** Mock always shows the word under the icon; `SectionNav.build` passes `""` labels (icon-only today). Needs five new string keys. |
| 6 | ⨯ functionally wrong (vs target) | **No purchase CTA.** Mock: `MELHORAR • 50` button in the panel; shipped: tap-to-buy on cards only, no button, no price-in-panel. |
| 7 | ⨯ functionally wrong (vs target) | **No tree-unlock purchase here.** Mock sells the locked tree from the panel (`DESBLOQUEAR ÁRVORE • 120/900` + `Moedas insuficientes`). Shipped `UpgradeManager.buy` refuses `TREE_LOCKED`; the unlock sale lives elsewhere (shop). Needs a new key pair and a purchase route. |
| 8 | ◐ partially implemented | **Detail panel.** Shipped: 3–4 text lines at y=384, 78 tall. Mock: icon + title + level + description + status line(s) + CTA + note, ≈ 232 logical tall, with a divider before the stats. |
| 9 | ＋ missing | **Stat pips.** Mock renders five rounded segments per stat row; shipped stat panel is label+value text only. Values themselves correspond (live `RunLoadout.previewStats` sheet); pip semantics need a decision (§12). |
| 10 | ◐ partially implemented | **Stat row order.** Same sets per tree; mock uses a canonical stat order (flight: Gravidade, Impulso, Queda, Hitbox, Recarga) vs shipped content order. Also the heading: mock `Resumo dos atributos` vs shipped reuse of `birds.breakdown` (`Detalhamento dos atributos`). |
| 11 | ＋ missing | **Card icons** (18 of them) and **per-tree header illustration**; shipped cards are text-only and the header has no scene. |
| 12 | ◐ partially implemented | **Header.** Shipped: small outlined title + `CurrencyDisplay` top-right. Mock: display title `Forja` (shipped key says `Upgrades`/`Melhorias` — ⚠ title conflict), **per-tree subtitle** (no shipped keys), coin pill matches shipped wallet readout (icon + number ✓). |
| 13 | ◐ partially implemented | **Tabs.** 3 tabs, same tree order ✓; mock adds icon-over-label (TabBar supports icons but they are unused here), gold plate for active vs shipped teal plate + optional gold underline, ≈ 44 logical tall vs shipped 28. |
| 14 | ◐ partially implemented | **Tier pills.** Mock: green pills `Tier 1/2/3`; shipped: plain accent-coloured text via `upgrades.tier` — and the shipped pt copy is `Camada {0}`, the mock says `Tier {0}` (⚠ copy conflict). Geometry (≈ 15 logical tall, own band above each tier) matches `TIER_LABEL_H = 16`. |
| 15 | ✔ already implemented correctly | **Tier row packing.** Economy/forge tier 1 = 3 nodes on two rows, flight tiers one row each — exactly what the shipped `rebuild()` produces with `COLUMNS = 2`. |
| 16 | ◐ partially implemented + ⚠ | **Connectors.** Shipped: one translucent (`0x88` alpha) 2 px elbow per real prereq edge. Mock: opaque 6–8 px yellow, merge junctions, drawn under cards — but its endpoints don't match the shipped graph (§9/§20). Adopt the style; keep the data-driven endpoints. |
| 17 | ◐ partially implemented | **Node states.** Shipped already has locked (veil + padlock top-right), dimmed (unaffordable), selected (gold outline), coin+price badge — the mock's versions differ mainly in weight: 5–6 px pale-yellow selection ring, darker locked fill, grey icons, padlock without price. Card geometry (≈ 46 logical tall, 2 columns, 6 px row gap) matches `CardGrid` defaults closely. |
| 18 | ⚠ conflicting with current data | **Canvas.** Full-bleed 9:16 (420×746 logical) vs shipped 420×640. §21(c). |
| 19 | ⊖ obsolete | **Full-width Back button** under the mock (replaced by the nav). Keep the BACK key path. |
| 20 | ⚠ conflicting with current data | **Stale shipped copy exposed by the mock.** `upgrade.glide_1.desc` says "−25% per level" but the M9-retuned effect is −30 % (`levelOverrides` L2 −35 %); the mock's card correctly shows `-30% vel. máxima de queda`. The desc string needs a content fix in the same change that rebuilds the screen. Also mock descriptions are sentence-form (`Reduz a gravidade em 3% por nível.`) vs shipped effect-form (`Gravidade −3% por nível.`) — new/edited desc keys if the panel is rebuilt to the mock. |
| 21 | ? needs clarification from code | **Pip-bar semantics** (per-tree normalised magnitude? per-stat scale?) and the **foot strip** under the nav — no code analogue exists for either. |
| 22 | ? needs clarification from code | **New string keys the mock requires** (none shipped): per-tree subtitle ×3, `MELHORAR • {0}`, `DESBLOQUEAR ÁRVORE • {0}`, `Resumo dos atributos`, nav labels ×5, stat label rewordings (`Força da batida`, `Velocidade máx. de queda`, `Revives`, `Hitbox`) if the mock's pt copy wins over the shipped `stat.*` labels. |
