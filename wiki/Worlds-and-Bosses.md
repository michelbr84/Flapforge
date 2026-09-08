_Language:_ **English** · [[Português (Brasil)|Mundos-e-Chefes-(pt-BR)]]

# Worlds and Bosses

Flapforge has five worlds. Each one brings its own palette, hazard mix, difficulty curve and
music, and a boss waiting at a fixed gate. Clearing that boss opens the next world; buying the
next world in the Shop is the other way in, so nobody is ever locked out of the progression. The
world of your next run is picked on the World Select screen, behind the plaque of the
[[Home Hub]].

## The five worlds

| # | `id` | Name | Style | Hazard mix (spawn weights) | World-wide effects | Boss: gate / survive / first-clear reward |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `green_fields` | Green Fields | hills | Pipes 100 | the classic curve: only the moving-gate ramp | 30 / 1200 ticks / 200 coins, opens Wind Valley |
| 2 | `wind_valley` | Wind Valley | canyon | Pipes 60, Wind 40 | a constant headwind of −20 px/s | 30 / 1500 ticks / 300 coins, opens Iron Forge and the Forge upgrade tree |
| 3 | `iron_forge` | Iron Forge | factory | Pipes 40, Gears 30, Pistons 30 | scroll speed ×1.1 | 30 / 1800 ticks / 400 coins, opens Storm Sky |
| 4 | `storm_sky` | Storm Sky | storm | Pipes 55, Lightning 25, Wind 20 | scroll speed ×1.15, darkness 0.5, a sky flash every 3 gates | 35 / 1800 ticks / 500 coins, opens the Void |
| 5 | `void` | The Void | void | Pipes 40, Gears 20, Pistons 20, Wind 10, Lightning 10 | the rules change every 5 gates | 40 / 2100 ticks / 800 coins, grants the Voidglass palette |

In the game's own words (`world.<id>.desc`):

* **Green Fields** — "Open sky, steady pipes. Where every run begins."
* **Wind Valley** — "A canyon that pushes back, gate after gate."
* **Iron Forge** — "Gears and pistons, and everything ten percent faster."
* **Storm Sky** — "Half darkness, half lightning."
* **The Void** — "The rules themselves change every few gates."

Green Fields runs on the `classic` difficulty curve: the chance of a moving gate starts at 5 %
and grows by 5 % per gate passed, up to 100 %, and nothing else changes. Every other world runs
on the `standard` curve, which adds scroll speed ×(1 + 0.004 per gate) up to ×1.5 and gap size
×(1 − 0.002 per gate) down to ×0.8. The difficulty tier stacks on top of the curve; see
[[Game Modes and Difficulty]].

## The obstacle families

Obstacles come in five families. Pipes appear in every world; the other four are the hazards a
world mixes in, in the proportions of the table above. The names are the ones the world row and
World Select show under "Hazards".

| Family | `id` | What it does |
| --- | --- | --- |
| Pipes | `pipe_gate` | The gate every world spawns: a standard or floating pair with an optional oscillator that makes it move |
| Gears | `gear` | Rotating circles, some riding a vertical rail |
| Pistons | `piston` | A telegraph, then extend, hold and retract: the telegraph is your cue |
| Wind | `wind_zone` | A constant force that bends your trajectory; never lethal by itself |
| Lightning | `lightning` | A partial-height bolt with a guaranteed safe band and a warning before the strike |

![Iron Forge hazards](images/iron-forge-hazards.png)
*Iron Forge: gears and a piston row between the pipes, ten percent faster than Green Fields.*

Each world also draws authored set pieces: Wind Valley's updraft run and crosswind, Iron Forge's
gear corridor and piston row, Storm Sky's bolt lane and squall, and the Void's mixer and gauntlet.
Every pattern is checked to be clearable in at least 30 % of seeds by the game's expert bot, so
no set piece is a guaranteed death.

## The Void's rule cycles

Every 5 gates the Void shifts its rules. A **Rule shift** banner counts down 90 ticks ahead
("… in …s", then "… now", then "… in effect") before one of four rules takes over:

| Rule shift | Effect |
| --- | --- |
| Every obstacle moves | the `ALL_OBSTACLES_MOVE` flag |
| Tighter gaps | gap size ×0.85 |
| Heavier gravity | gravity ×1.3 |
| Lethal ceiling | the `LETHAL_CEILING` flag: touching the top of the screen kills |

The same option is never drawn twice in a row, so a rule you have just survived is off the table
for the next shift.

## Storm Sky's lightning

Storm Sky is dark (ambient darkness 0.5) and flashes its sky every 3 gates. That flash, with its
thunder, is cosmetic: it has no hitbox. The bolts that kill are spawned as obstacles, in Storm
Sky and, more rarely, in the Void:

* A bolt comes down from the top or up from the bottom of the screen and covers 40 to 60 % of
  the playfield height, so a safe band always remains on the other side.
* A warning marker shows the side and extent of the bolt 45 ticks (three quarters of a second)
  before it strikes; it is visible from its first tick and brightens as the strike approaches.
* The strike itself lasts 10 to 12 ticks. Be in the safe band when it lands.

![Storm Sky lightning warning](images/storm-sky-warning.png)
*Storm Sky: the warning band marks where the bolt will land; the rest of the column is safe.*

> **Tip:** the Reduce flashing option in [[Settings and Accessibility]] dims the lightning glow
> without changing where the bolts land.

## Bosses

Every world ends in a boss encounter at its boss gate (30, 30, 30, 35 and 40). The HUD warns
120 ticks ahead (150 in the Void) with "Boss in …s", then counts the fight down with "BOSS …s"
while the world streams the boss's authored patterns: two per world, three in the Void. Survive
the whole countdown (1200 to 2100 ticks, that is 20 to 35 seconds) and the banner says
"… cleared!". The music crossfades to a faster variant of the world's loop while the fight lasts,
15 % faster and capped at 170 BPM.

* **The first clear** grants the boss reward of the table above: the coins and the next world,
  plus the Forge upgrade tree after Wind Valley and the Voidglass palette of Forgewing after the
  Void. It is written as `world_cleared`, which other unlocks read too: Cinder, the Moving World I
  and Corridor Boss challenges and the Forge tree all open on a Green Fields or Wind Valley clear.
* **Every clear** also pays the run's boss bonus of 150 coins and 200 XP, listed on the run
  summary under Bosses, and clears one of the boss achievements (Field Marshal, Windbreaker,
  Forgebreaker, Stormcaller, Void Walker, and Boss Hunter for all five). See
  [[Challenges and Goals]].
* Only a **world** boss counts. The Corridor Boss challenge loops its own boss pattern and pays
  its own reward, but never clears a world.

## Unlocking a world

Green Fields is yours from the start. Every other world opens either by clearing the previous
world's boss or by buying it in the Worlds tab of the Shop ([[Shop and Upgrades]]):

| World | Earn it by | Or buy for |
| --- | --- | --- |
| Wind Valley | clearing the Green Fields boss | 350 coins |
| Iron Forge | clearing the Wind Valley boss | 700 coins |
| Storm Sky | clearing the Iron Forge boss | 1200 coins |
| The Void | clearing the Storm Sky boss | 2000 coins |

A challenge is always played in its own world, owned or not, so the seven challenges of the Goals
screen can take you into Wind Valley or Iron Forge before you own them.

## The World Select screen

![World Select](images/world-select.png)
*World Select: one card per world, the difficulty row under the cards and the description line.*

The plaque on the home hub ("World 1 · Green Fields") opens the screen titled **Choose a world**:

* One card per world, in order, with the world's palette as its art. An owned card lists the
  hazards it spawns; a locked card shows the cheapest way in as its subtitle and the price as a
  badge.
* Activating an owned card writes your selection and takes you straight back to the hub, whose
  plaque, backdrop and START RUN subtitle follow the change.
* Activating a locked card is refused with a toast ("Purchase refused: Locked"): worlds are
  bought in the Shop, not here.
* The **Difficulty** row under the cards steps through Normal, Hard and Nightmare, with the
  locked tiers marked; stepping onto a locked tier snaps the row back with the same toast.
* A description line under the row describes the focused world.

The Birds screen sets the same world from the other side of the hub, in the panel behind its
run-setup bar ([[Birds and Abilities]]).

## Picking a world from the command line

On desktop, `--world <id>` selects an owned world for one launch, exactly as the picker would:
`green_fields`, `wind_valley`, `iron_forge`, `storm_sky` or `void`. The selection is written to
the profile, so the hub's plaque and START RUN play it. A locked world cannot be selected: the hub
keeps the owned selection and a line on the log says so (only a `--headless-run` plays the flag's
world regardless). `--tier <id>` picks the difficulty the same way. See [[Getting Started]].

## Music

Each world plays its own procedurally rendered chiptune loop: a deterministic eight-bar sequence
generated at run start from the world's music block, and crossfaded to a faster variant while a
boss fight runs. The menu plays the Green Fields loop. No audio files ship; the sequencer, the
synthesiser and the software mixer generate everything.

| World | Tempo | Scale | Layers |
| --- | --- | --- | --- |
| Green Fields | 112 BPM | major pentatonic | bass, lead, drums |
| Wind Valley | 96 BPM | dorian | bass, lead, arp, pad |
| Iron Forge | 126 BPM | phrygian | bass, lead, drums |
| Storm Sky | 134 BPM | minor pentatonic | bass, lead, arp, drums |
| The Void | 104 BPM | whole tone | bass, pad, arp |
