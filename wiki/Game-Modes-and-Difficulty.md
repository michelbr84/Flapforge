_Language:_ **English** · [[Português (Brasil)|Modos-de-Jogo-e-Dificuldade-(pt-BR)]]

# Game Modes, Difficulty and Prestige

A run is defined by three choices: its mode (where the seed comes from), its world
([[Worlds and Bosses]]) and its difficulty tier. This page covers the four modes, the three
tiers and what happens at level 25, when the Profile screen offers a prestige. Attract mode, the
demo that plays when you leave the hub alone, is at the end.

## Where the mode is chosen

The mode row sits on the Birds screen, beside the world row and the tier row
([[Birds and Abilities]]). Standard and Seeded are always listed; Daily is listed when the game
has a clock, and while `feature:seeded_runs` is still locked both Seeded and Daily say so with
their unlock condition. The line under the mode names what Play would start: "A fresh seed
every run", "Replays seed 1234", or the world, tier and cards of today's daily. Challenges are
not on this row: they are played from the Challenges tab of the Goals screen.

START RUN on the [[Home Hub]] plays the selected bird, world, tier and mode, and shows
"world • tier" under the button so you always know what it will launch.

## Standard

A fresh random seed every run. This is the default mode and the one the whole meta-progression
is tuned around: every unlock condition, price and reward multiplier assumes standard runs on the
Normal tier.

## Seeded

Replays the seed of the last run your profile finished, so a run can be retried on exactly the
obstacles that killed you, or shared as "beat my 63 gates on this seed". The HUD shows
"seed 1234" during the run and the summary repeats it. Seeded and Daily open together with the
Seeded Runs feature (`feature:seeded_runs`): reach level 5, or buy it for 100 coins in the
Features tab of the Shop ([[Shop and Upgrades]]). While it is locked the picker says so and Play
falls back to a standard run. On desktop, `--seed N` starts one launch on a chosen seed.

## Daily

One run per UTC day for the whole planet, with no server involved:

* **The date is the whole seed.** The seed is `fnv1a("daily:" + yyyy-MM-dd)`, so two players
  on opposite sides of the planet get the same challenge on the same UTC day.
* **The pick comes from what you own.** From that seed the game draws one world you have
  unlocked, one tier from Normal and Hard among the tiers you have unlocked, and two forced
  modifiers compatible with each other and with the world and tier. The draw is uniform, not
  rarity-weighted: a daily is a fixed configuration, not an offer. Forced daily modifiers do not
  need the Run Modifiers feature; only drafting mid-run does.
* **The pick is frozen for the date.** The first time the daily is viewed or played, the
  profile records the date, seed, world, tier and cards. Unlocking a new world at lunchtime
  cannot change the run you practised in the morning; only the attempt counter and the best gate
  count move.
* **It pays more.** A daily run earns ×1.25 coins on top of the normal formula, shown as the
  "Daily multiplier" line of the run summary.
* **Retry keeps the seed.** The instant retry on the game-over strip replays the same daily and
  only counts another attempt. The Profile's statistics count your daily runs, and the mode line
  reads "best 12 gates, attempt 3" or "not flown today yet".

The Daily Flyer and Seven Days achievements reward the first and the seventh daily played.

## Challenge

The seven special runs of the Challenges tab of the Goals screen. A challenge brings its own
world, tier, rules, forced cards and, for the Corridor Boss, its own boss; it is playable whether
or not its world is unlocked, and it uses the bird, palette and loadout of your profile under
those rules. Clearing one for the first time pays coins and, for most of them, an unlock: three
bird palettes, the Invulnerability ability (One Life I) and the Nightmare tier (Corridor Boss).
The full list, with objectives and rewards, is on [[Challenges and Goals]].

## Difficulty tiers

A tier stacks on top of every mode, except where a challenge fixes its own. It is chosen on the
tier row of the Birds screen or on the Difficulty row of the World Select screen (the screenshot
is on [[Worlds and Bosses]]); on desktop `--tier <id>` picks it for one launch.

| `id` | Name | Effects | Flags | Reward multiplier | Unlocked by |
| --- | --- | --- | --- | --- | --- |
| `normal` | Normal | — | — | ×1.0 | available from the start |
| `hard` | Hard | scroll speed ×1.10, gap size ×0.92 | — | ×1.5 | 40 gates in one run, 400 gates across the profile, or the Trial by Fire node of the Economy tree |
| `nightmare` | Nightmare | scroll speed ×1.20, gap size ×0.85 | every obstacle moves, lethal ceiling | ×2.5 | the Corridor Boss challenge or level 20 |

In the game's words, Hard is "Faster scrolling, tighter gaps, richer rewards" and Nightmare is
"Everything moves, the ceiling kills, the payout doubles". The multiplier is applied to the whole
coin reward of the run and shows up as the "Difficulty multiplier" line of the summary. The tier
effects multiply with the world's own curve and effects: Storm Sky on Nightmare scrolls at
×1.15 × ×1.20 before the curve even starts. The Hard Ten and Nightmare Ten achievements reward
10 gates on each tier.

> **Tip:** Trial by Fire costs 400 coins and does nothing but unlock Hard. If you have already
> passed 40 gates in a run, the upgrade screen marks it "Already unlocked" and refuses the
> purchase, so nothing is wasted.

## Prestige

At level 25 the **Profile** screen, behind the player card of the home hub, offers a prestige.
The panel lists what the profile has banked, what a prestige would reset and what it keeps, and
the **Prestige** button needs two presses: the first arms it with "Confirm prestige?", the second
performs it. Below level 25 the panel reads "Prestige opens at level 25"; after the fifth
prestige it reads "The prestige cap is reached".

| Resets | Keeps |
| --- | --- |
| coins, XP and the level (back to 1) | every bird you own |
| every upgrade node and the ability levels | every palette (cosmetic) you own |
| the ability level cap (back to 2) and the passive slot bonus (back to 0) | every achievement, with its date |
| worlds, tiers, trees, abilities, challenges and features: all earned again | the lifetime statistics |
| challenge records and the daily pick | the prestige count and its bonus |

What you gain, and keep for ever:

* **+5 % coins per prestige** on every later run, stacking up to +25 % at the fifth. The Profile
  shows it as "Permanent bonus: +5% coins".
* **The golden Prestige palette** of the bird you prestige with. Every bird ships one; prestige
  with a different bird each time to collect them.
* **The badge.** The player card on the hub and the Profile header carry "Prestige ×1" (or
  higher), and the forge scene on the hub shows gold trim on the anvil from the first prestige on.

The profile also freezes a baseline of your lifetime totals at that moment. Unlock conditions
that count runs, gates, coins earned or bosses cleared read "since prestige" afterwards, so the
climb is real but nothing already earned is granted twice, and conditions on achievements or
best scores keep working. The toast "Prestige 1 performed — the climb starts over" confirms the
write, and the selection falls back to Forgewing, Green Fields and Normal.

## Attract mode

Leave the home hub alone for twenty seconds and a bot plays a demo run behind it, dimmed under
the hub's chrome, on a fixed attract seed. It runs without your profile, so it can never earn,
spend or change anything. Any input (a key, a click, a focus change) takes the game back and
resets the idle timer.

Related pages: [[Playing a Run]] for the HUD and the run summary, [[Challenges and Goals]] for
the Goals screen, [[FAQ]] for the questions people ask about the daily and the prestige.
