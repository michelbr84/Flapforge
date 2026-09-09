# Goals screen economy binding

## 1. Reward values the Goals screens display, and their authoritative source

| Displayed reward | Authoritative source |
| --- | --- |
| Next-unlock counter / gold track (NextUnlockCard) | `UnlockManager.nearestUnlockable(...)` (UNVERIFIED exact signature) + unlock cost from `src/main/resources/data/economy.json` |
| Coin rewards | `economy.json` `rewards.coin.*` base values, x `COIN_MULT` stat (owned by ff-gameplay) at grant time |
| XP rewards | `economy.json` `rewards.xp.*` base values, x `XP_MULT` stat (owned by ff-gameplay) at grant time |
| Run participation reward | `RunRewardCalculator` participation reward (E32.a amendment) |
| Level-up rewards | `economy.json` `levelRewards` table keyed by level |

Rule: the UI reads the same numbers the grant pipeline reads (economy.json parsed once by GameContent); nothing is re-hardcoded.

## 2. Coin totals: what "coins" means on this screen

- Wallet balance = spendable coins now; exact accessor UNVERIFIED (candidate `Wallet.balance()`).
- Lifetime earned = cumulative granted coins; exact accessor UNVERIFIED (candidate `Wallet.coinsEarned()`).
- Goals shows the wallet balance (spendable), not lifetime: the card's gold track is a cost-to-unlock gauge, so only spendable coins are meaningful. Lifetime is a stats screen number, not a Goals number.
- Confirm the exact accessor against the Wallet class before coding (UNVERIFIED).

## 3. XP / level row: the authoritative numbers

- PlayerLevel threshold: threshold table consulted by `PlayerLevel.currentLevel()` (UNVERIFIED exact name).
- Current XP: `PlayerLevel.currentXp()` (UNVERIFIED exact name).
- levelRewards lookup: `GameContent` level-reward accessor keyed by level (UNVERIFIED exact name).
- A reward for the NEXT level is NOT a stored field — it must be derived by looking up `levelRewards` at `currentLevel + 1`, or computed from the next threshold. Never hardcode it.

## 4. Economy risks in the redesign

- Hardcoded totals (the mock's "43 upgrades" / "133 everything") drift from `economy.json`; the UI must count/price only from GameContent data.
- Cost display must be gated by `UnlockManager` / the purchase-outcome closed set (`NOT_FOR_SALE`, `TREE_LOCKED`, `MISSING_PREREQ`, `LEVEL_CAPPED`) — showing a price for an unsellable node is a lie.
- `COIN_MULT`/`XP_MULT` (ff-gameplay) must be applied consistently at display-or-grant; showing base values without the multiplier disagrees with the grant.
- Unknown JSON keys are errors — any new reward/pricing key must be added to the `content.defs` record in both languages.

## 5. Verdict

Yes — safe to implement as specified, provided every displayed number is read from GameContent/economy.json (never hardcoded) and costs are gated by UnlockManager; the single biggest caveat is the UNVERIFIED accessor names in sections 1-3 must be confirmed against the actual classes before coding.
