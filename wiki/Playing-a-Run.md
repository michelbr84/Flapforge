_Language:_ **English** · [[Português (Brasil)|Jogando-uma-Partida-(pt-BR)]]

# Playing a Run

A run is one flight: from the first flap to the crash, through gates, coins, drafts and a boss.
Everything you earn in it — coins, XP, unlocks, records — is written to your profile the moment
it ends. This page walks through a run from READY to the summary and explains what each number
along the way is worth.

## What START RUN plays

The home hub's plaque shows the world and difficulty the gold **START RUN** will play; the bird,
palette, active ability, passive abilities, tier and mode (Standard, Seeded, Daily) are chosen on
the Birds screen, and challenges are started from the Goals screen. See [[Home Hub]],
[[Birds and Abilities]], [[Worlds and Bosses]] and [[Game Modes and Difficulty]]. A fresh profile
starts with Forgewing, Double Flap, Green Fields and the Normal tier.

## The shape of a run

`READY → FLYING ↔ {BREATHER → CHOOSING_MODIFIER → RESUME_HOLD} ↔ {BOSS_WARNING → BOSS} →
DYING → FINISHED`

| Phase | What happens |
| --- | --- |
| READY | The bird floats at its start position; nothing scrolls or spawns. Your first flap starts the run and counts on that same tick. |
| FLYING | The world scrolls, gates and hazards spawn, coins drift by. Pass gates, collect coins, keep a clean-gate streak. |
| Breather → draft → resume hold | At gates 10, 25, 45, 70, 100 and 140 (once drafts are unlocked) the next obstacle is pushed out, the air ahead empties and the run freezes with three cards on screen. Take one or skip; the bird stays frozen through a 3-2-1 countdown, and the resume grants 30 invulnerability ticks so the first tick back never kills. |
| Boss warning → boss | Each world has a boss at a fixed gate. A banner counts down ("Boss in Ns") while spawning stops, then the boss streams its patterns until its survive timer runs out. Dying during the fight clears nothing; a clear already granted is kept even if you crash later. No draft opens during a boss — one that falls inside it waits until after the clear. |
| Dying → finished | The crash, then the game-over strip. |

Every run is fully deterministic for its seed: obstacles, coins, offers and patterns all draw
from named random streams, so a seeded replay puts the same pipes in the same places.

## Gates, points, coins and streaks

- **Gates.** Every pipe gate has a score line; crossing it counts one gate and awards points
  equal to your score multiplier — one point at base, more with a score modifier, a bird bonus
  or a tier. The HUD counts gates; the summary shows both.
- **Coins.** Coin pickups spawn along the way and are collected on touch; a magnet radius (the
  Coin Magnet passive, the Magnet Burst modifier) pulls them in from further away. Coins you
  pick up are added to the run's payout on top of the reward formula below.
- **Clean-gate streak.** A gate is clean when its column was never grazed, resolved once the
  column has left the bird's hitbox — so a graze after the score line still costs the gate it
  happened on. Every five consecutive clean gates is one streak step and pays 5 coins; the HUD
  shows "Streak N", the summary counts the steps, and Best streak is a lifetime statistic. The
  Streak Bounty modifier adds +10 coins per step.

## The difficulty ramps

Two curves apply per gate passed. Green Fields uses the `classic` curve: only the moving-gate
ramp, a 5 % chance of a moving gate at the start, +5 % per gate, capped at 100 %. Every other
world uses `standard`: the same moving ramp plus scroll speed ×(1 + 0.004 per gate) up to ×1.5
and gap size ×(1 − 0.002 per gate) down to ×0.8. The tier sits on top:

| Tier | Scroll | Gap | Extra rules | Reward multiplier |
| --- | --- | --- | --- | --- |
| Normal | — | — | — | ×1.0 |
| Hard | ×1.10 | ×0.92 | — | ×1.5 |
| Nightmare | ×1.20 | ×0.85 | every obstacle moves, the ceiling kills | ×2.5 |

Hazards, worlds and how each boss behaves are on [[Worlds and Bosses]].

## Pausing

`Esc` pauses. The pause panel has **Resume** and **Menu**; `Esc`, a click or a tap outside the
buttons resumes. Nothing moves while paused, and the run resumes exactly where it stopped.

## The game-over strip

The strip appears over the frozen playfield when the bird dies. It pays the coins and XP of the
run immediately, announces a new best ("That's your best run yet: N points"), a level reached or
a challenge completed, and offers three buttons:

| Button | Key | Does |
| --- | --- | --- |
| **Retry** | `Space` / left click / a tap outside the buttons | starts a new run at once with a fresh seed |
| **Summary** | `Enter` | opens the run summary |
| **Menu** | `Esc` | returns to the home hub |

Because rewards are banked the moment the run ends, a retry never loses them. A daily's retry
keeps its seed and only counts the attempt.

## The run summary

![The run summary](images/run-summary.png)
*The run summary: every term of the reward on its own row, the level bar the XP moved, and the
build.*

The summary reads the profile the run has already been written into; it changes nothing. Its
sections:

| Section | Shows |
| --- | --- |
| Setup | the seed with its mode, the world, tier and bird |
| This run | gates, points, streak steps, the boss |
| Coins | every term of the reward formula on its own row, and the total |
| Experience | the XP earned, the level bar, "N / M XP" or "Max level" |
| Build | every modifier taken with its stacks (×N) and any synergy that fired — or "No drafts: Run Modifiers is still locked in the shop" |
| Achievements | achievements unlocked by this run, and a first challenge completion |

**Retry** and **Menu** sit at the bottom, with the same keys as the strip.

## Coins: the reward formula

```
participation 20 · first-run bonus 25 · 2 per gate · 1 per point
· 5 coins per 5-gate clean streak
+ 150 per world boss cleared + challenge first-clear reward + 100 challenge bonus
× coin multiplier × tier reward multiplier × daily ×1.25   (+ the coins you picked up)
```

Run 1 pays about 50 coins even at 0 gates: 20 for participating, the 25 first-run bonus and
whatever you picked up. The coin multiplier comes from the bird (Jackdaw +30 %, Ironbeak −20 %),
modifiers such as Heavy Wallet or Gold Rush, the Coin Engine synergy and +5 % per prestige. A
world boss also pays its own reward (200 to 800 coins) and opens the next world.

## XP, levels and milestones

XP is never spent. A run earns 15 XP for participating plus 10 per gate, and 200 per boss
cleared. The level curve starts at 100 XP for level 2 and each level-up costs 10 % more than the
one before — level 3 at 210, level 4 at 331 — up to the maximum, level 50. Six levels pay a coin
reward on the spot:

| Level | Milestone reward |
| --- | --- |
| 2 | 50 coins |
| 5 | 150 coins |
| 10 | 500 coins |
| 15 | 800 coins |
| 20 | 1200 coins |
| 25 | 2000 coins |

Levels also open content: Seeded Runs and the daily at level 5, the three legendary modifier
cards at level 8, the Nightmare tier at level 20 and Prestige at level 25 — see
[[Shop and Upgrades]] and [[Challenges and Goals]].

> **Tip:** the strip has already paid you before you read it. Retry with `Space` as often as you
> like; nothing is at stake but the next attempt.
