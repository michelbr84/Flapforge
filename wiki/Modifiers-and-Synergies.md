_Language:_ **English** · [[Português (Brasil)|Modificadores-e-Sinergias-(pt-BR)]]

# Modifiers and Synergies

Modifiers are the roguelite half of a run: cards drafted mid-flight that change the rules of this
run and this run only — more score, more coins, a smaller hitbox, another shield charge, a faster
world. Take two that share a tag and a set-bonus synergy fires on top. Everything here disappears
when the run ends; the permanent upgrades are on [[Shop and Upgrades]].

## Unlocking drafts

Drafts are the **Run Modifiers** feature (`feature:modifiers`): it unlocks after 7 runs played,
or earlier for 150 coins in Shop › Features. Until then runs have no drafts and the run summary's
Build section says "No drafts: Run Modifiers is still locked in the shop". A challenge may switch
drafts off for its own run; the daily always has them.

Fourteen of the seventeen cards are in the pool from your first draft. The three legendary cards
— Gold Rush, Phoenix and Stormrider — are earned at level 8 or bought in the Shop for 300 coins
each.

## When a draft opens

| Draft | Gate |
| --- | --- |
| 1 | 10 |
| 2 | 25 |
| 3 | 45 |
| 4 | 70 |
| 5 | 100 |
| 6 | 140 |

When you pass one of those gates the next obstacle is pushed out to make a clear window (the
breather), and once the air ahead is empty the run freezes and three cards go up. The cards are
drawn without replacement from the run's own `offers` random stream, so however you pick, the
obstacles of the run stay the same. A draft never opens on a tick that reported a collision, and
never while a boss is pending or active: a schedule gate that falls inside a boss waits until
after the clear. After your answer the bird stays frozen through a 3-2-1 countdown and gets 30
invulnerability ticks on resume.

![The modifier draft](images/draft.png)
*Draft 1 of 6 at gate 10: three cards, the stack each would be, and the synergy a card would
complete.*

## Reading a card

Each card shows its name, its rarity (in its own colour and word), its tags, the effect in words
and the same effect in numbers, the stack it would be ("Stack 1 of 2") and — when taking it would
complete a set bonus — "Completes Coin Engine" or the like. Arrows compare, `Enter` takes, `Esc`
skips, exactly as the hint under the cards says; the mouse and touch work on the cards and on the
**Skip this draft** button too. While the cards are up no flap, ability or pause reaches the run.

## Rarity, stacks and exclusions

| Rarity | Weight | Cards |
| --- | --- | --- |
| Common | 60 | 5 |
| Rare | 28 | 4 |
| Epic | 10 | 5 |
| Legendary | 2 | 3 |

The weights are per draw, so a common is thirty times as likely as a legendary; measured over
5000 sampled drafts, a legendary shows up on 1.5 % of tables.

- **Stacks.** Each card has a stack cap; taking it again adds a stack up to that cap, and a card
  at its cap is no longer offered.
- **Exclusions.** Heavy Air and Stormrider exclude each other, as do Light Frame and Glass Wings:
  once you hold one, the other leaves the pool.
- **Rules.** A card whose effect the run's rules would cancel is not offered: Field Shield needs
  defensive abilities allowed, Second Wind and Phoenix need revives allowed, and the coin cards
  (Coin Drops, Magnet Burst, Streak Bounty) need coins allowed. A draft with nothing eligible is
  skipped outright, without freezing the run.

## The seventeen modifiers

| Id | Name | Rarity | Tags | Effect | Max stacks |
| --- | --- | --- | --- | --- | --- |
| `tailwind` | Tailwind | Common | Speed, Greed | +10% score, and the world scrolls 2% faster. | 2 |
| `score_plus` | Sharper Eye | Common | Greed | +10% score. | 3 |
| `coin_drops` | Coin Drops | Common | Economy | +0.15 coins per gate. | 3 |
| `slower_obstacles` | Heavy Air | Common | Tempo | The world scrolls 8% slower. | 2 |
| `quick_hands` | Quick Hands | Common | Tempo | Ability cooldowns are 15% shorter. | 2 |
| `light_frame` | Light Frame | Rare | Precision | A smaller hitbox: -0.08 scale. | 2 |
| `streak_bounty` | Streak Bounty | Rare | Economy, Precision | +10 coins for every clean-gate streak step. | 1 |
| `magnet_burst` | Magnet Burst | Rare | Economy | Coins are pulled in from 60 px away and pay 15% more. | 2 |
| `wide_gaps` | Wide Gaps | Rare | Precision | Gaps are 8% taller. | 2 |
| `temp_shield` | Field Shield | Epic | Defence | One more shield charge for this run. | 1 |
| `heavy_wallet` | Heavy Wallet | Epic | Economy, Greed | Coins pay 30% more, and the bird falls 8% harder. | 1 |
| `glass_wings` | Glass Wings | Epic | Risk, Greed | Score x1.5, and a hitbox 0.15 larger. | 1 |
| `second_wind` | Second Wind | Epic | Defence | One revive for this run. | 1 |
| `long_fuse` | Long Fuse | Epic | Tempo | Abilities last 30% longer. | 1 |
| `gold_rush` | Gold Rush | Legendary | Economy, Risk | Coins pay double, and the world scrolls 5% faster. | 1 |
| `phoenix` | Phoenix | Legendary | Defence, Risk | Two revives, but coins pay 30% less. | 1 |
| `stormrider` | Stormrider | Legendary | Speed, Risk | Score x1.6 at 5% more speed. | 1 |

Tags are the only thing the synergies read; Greed and Tempo carry no synergy of their own.

## Forced cards

Some runs start with cards already taken. They are entries like any other — they show in the
HUD, count for synergies and appear in the summary's Build section — and they are taken before
the first tick, so they do not use up a draft.

- **Daily.** Two forced compatible modifiers, drawn deterministically from the content you have
  unlocked and frozen for the date.
- **Challenges.** Coin Rush I forces Coin Drops on top of its ×3 coin spawn rate; the other
  challenges set rules (and, for Corridor Boss, a forced pattern) rather than cards. See
  [[Challenges and Goals]] and [[Game Modes and Difficulty]].

## Synergies

A synergy is a set bonus: it activates when two *distinct* taken cards together cover the tags it
requires, so a single card never completes one by itself — two stacks of Coin Drops do not make
a Coin Engine; Coin Drops plus Magnet Burst do.

| Id | Name | Needs | Effect |
| --- | --- | --- | --- |
| `coin_engine` | Coin Engine | Economy + Economy | Two economy cards: +25% coins. |
| `bulwark` | Bulwark | Defence + Defence | Two defence cards: one more shield charge. |
| `needle_threader` | Needle Threader | Precision + Precision | Two precision cards: -0.10 hitbox scale. |
| `daredevil` | Daredevil | Speed + Risk | Speed and risk together: +35% score. |

The draft tells you when a card would complete one, the summary lists the synergies that fired,
and "Synergies activated" is a lifetime statistic on the Profile. A bot that reaches its third
draft activates a synergy in 69.9 % of runs, so a build that aims for one is not a long shot.

Some pairs to aim for:

| Take | Then | Result |
| --- | --- | --- |
| Coin Drops | Magnet Burst, Streak Bounty, Heavy Wallet or Gold Rush | Coin Engine |
| Light Frame | Wide Gaps or Streak Bounty | Needle Threader |
| Field Shield | Second Wind or Phoenix | Bulwark |
| Tailwind or Stormrider | Glass Wings, Gold Rush or Phoenix | Daredevil |

Stormrider carries both Speed and Risk, but on its own it completes nothing: the two tags have to
come from two different cards.

## Skipping a draft

Skipping is always allowed: `Esc` or the **Skip this draft** button takes nothing, the countdown
runs and the run carries on. The draft is consumed — it does not come back later — and the
summary simply lists fewer cards. Skip when every card on the table would hurt the build you are
flying: Glass Wings on a precision build, or Gold Rush and its extra speed in a world you are
barely surviving.

> **Tip:** the card that completes a synergy is usually worth more than a rarer card that does
> not — Bulwark's extra shield charge or Needle Threader's −0.10 hitbox is a whole card's worth
> of effect for free.
