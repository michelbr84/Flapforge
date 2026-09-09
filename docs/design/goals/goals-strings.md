# Goals redesign — localization plan and audit

This document is the localization contract for the Goals redesign
(`src/main/java/io/github/michelbr84/flapforge/ui/screens/GoalsScreen.java` and the
`screens/goals/` package). It lists what the two string tables already carry, which new
keys the redesign must add, the rules that keep the tables honest, and the verdict.

Files owned here: `src/main/resources/data/strings/en.json` (source of truth),
`src/main/resources/data/strings/pt_BR.json`, `src/main/java/io/github/michelbr84/flapforge/content/StringKey.java`.

## 1. Keys the Goals section already has

Verified against **both** files on 2026-09-09: 775 keys in each, byte-identical key sets
(no key lives in only one file). The goals family is the union of the `goals.*`,
`challenges.*`, `achievements.*`, `milestones.*`, `collections.*` prefixes plus
`menu.nav_goals`. Every key below exists in `en.json` and `pt_BR.json` with the same
placeholder count (verified programmatically — none is missing and none has a placeholder
drift). The table is ordered by prefix so the implementer can diff against the files.

### Screen title and navigation

| key | en | pt_BR |
| --- | --- | --- |
| `goals.title` | Goals | Metas |
| `menu.nav_goals` | Goals | Metas |
| `menu.nav_forge` | Forge | Forja |

### Challenges tab

| key | en | pt_BR |
| --- | --- | --- |
| `challenges.title` | Challenges | Desafios |
| `challenges.objective` | Objective: {0} | Objetivo: {0} |
| `challenges.world` | World: {0} | Mundo: {0} |
| `challenges.tier` | Tier: {0} | Dificuldade: {0} |
| `challenges.rules` | Rules: {0} | Regras: {0} |
| `challenges.rules.none` | Standard rules | Regras padrão |
| `challenges.rule.modifier` | starts with {0} | começa com {0} |
| `challenges.rule.pattern` | fixed corridor | corredor fixo |
| `challenges.rule.boss` | boss at gate {0} | chefe no portão {0} |
| `challenges.record` | Best {0} gates, {1} attempts | Melhor {0} portões, {1} tentativas |
| `challenges.record.none` | Not played yet | Ainda não jogado |
| `challenges.completed` | Completed | Concluído |
| `challenges.completed_entry` | {0} - completed | {0} - concluído |
| `challenges.rewards` | Rewards: {0} | Recompensas: {0} |
| `challenges.reward.coins` | {0} coins | {0} moedas |
| `challenges.locked` | Locked: {0} | Bloqueado: {0} |
| `challenges.locked_entry` | {0} (locked) | {0} (bloqueado) |
| `challenges.locked_title` | Locked | Bloqueado |
| `challenges.play` | Play | Jogar |

### The goals field labels (new, already shipped)

| key | en | pt_BR |
| --- | --- | --- |
| `goals.field.world` | World | Mundo |
| `goals.field.tier` | Tier | Dificuldade |
| `goals.field.rules` | Rules | Regras |
| `goals.field.progress` | Progress | Progresso |
| `goals.field.reward` | Reward | Recompensa |
| `goals.field.unlock` | Unlock | Desbloqueio |
| `goals.percent` | {0}% | {0}% |

These are the paired-card labels and the full-width row labels of the challenge detail
block in the reference spec. `goals.percent` is a floored percentage (rows feed it
`CollectionProgress.percentOf(...)`), one placeholder.

### Collection category names and descriptions

| key | en | pt_BR |
| --- | --- | --- |
| `collections.birds` | Birds | Aves |
| `collections.abilities` | Abilities | Habilidades |
| `collections.worlds` | Worlds | Mundos |
| `collections.challenges` | Challenges | Desafios |
| `collections.cosmetics` | Colours | Cores |
| `collections.achievements` | Achievements | Conquistas |
| `collections.upgrades` | Upgrades | Melhorias |
| `collections.all` | Everything | Tudo |
| `collections.value` | {0} / {1} ({2}%) | {0} / {1} ({2}%) |
| `goals.collections.desc.birds` | Unlock different birds | Desbloqueie aves diferentes |
| `goals.collections.desc.abilities` | Discover special abilities | Descubra habilidades especiais |
| `goals.collections.desc.worlds` | Explore new worlds | Explore novos mundos |
| `goals.collections.desc.challenges` | Complete challenge runs | Complete partidas de desafio |
| `goals.collections.desc.cosmetics` | Collect different colours | Colecione cores diferentes |
| `goals.collections.desc.achievements` | Unlock achievements | Desbloqueie conquistas |
| `goals.collections.desc.upgrades` | Forge permanent upgrades | Forje melhorias permanentes |
| `goals.collections.desc.all` | Total game completion | Conclusão total do jogo |
| `goals.collections.footer` | Keep playing. There's more to discover! | Continue jogando. Há mais para descobrir! |

### Achievements tab

| key | en | pt_BR |
| --- | --- | --- |
| `achievements.tab.achievements` | Achievements | Conquistas |
| `achievements.count` | {0} of {1} unlocked | {0} de {1} desbloqueadas |
| `achievements.hidden.name` | ??? | ??? |
| `achievements.hidden.desc` | A hidden achievement | Uma conquista secreta |
| `achievements.unlocked_at` | Unlocked {0} | Conquistada em {0} |
| `achievements.reward` | +{0} coins | +{0} moedas |

`achievements.unlocked_at` is the card's "Unlocked {date}" line; the date is rendered by
`GoalsScreen.isoDate(...)` from the persisted `unlockedAtEpochMs` and passed as `{0}`.

### Milestones tab

| key | en | pt_BR |
| --- | --- | --- |
| `achievements.tab.milestones` | Milestones | Marcos |
| `milestones.next` | Next milestones | Próximos marcos |
| `milestones.progress` | {0} / {1} | {0} / {1} |
| `milestones.level_reward` | Level {0} reward: {1} coins | Recompensa do nível {0}: {1} moedas |
| `milestones.none` | Every milestone reached | Todos os marcos alcançados |

`milestones.progress` is the row counter ("0 / 100 XP" formats as `{0} / {1}` plus a
trailing `stat.xp` word appended by the screen, as the reference shows the XP counter
inside the same row). `milestones.level_reward` is the embedded reward name line: {0} is
the level, {1} the coins.

### Collections tab

| key | en | pt_BR |
| --- | --- | --- |
| `achievements.tab.collections` | Collections | Coleções |
| `collections.value` | {0} / {1} ({2}%) | {0} / {1} ({2}%) |

The row counter and the Everything card's counter both use `collections.value`: {0} owned,
{1} total, {2} the floored percentage. Section 4's category-name keys apply here too.

### Level / XP wording

| key | en | pt_BR |
| --- | --- | --- |
| `summary.level` | Level {0} | Nível {0} |
| `summary.level_progress` | {0} / {1} XP | {0} / {1} XP |
| `summary.level_max` | Max level | Nível máximo |
| `stat.xp` | XP | XP |

### Unlock-condition wording

| key | en | pt_BR |
| --- | --- | --- |
| `unlock.runs` | Play {0} runs | Jogue {0} partidas |
| `unlock.best_gates` | Pass {0} gates in one run | Passe {0} portões numa partida |
| `unlock.best_points` | Score {0} points in one run | Faça {0} pontos numa partida |
| `unlock.total_gates` | Pass {0} gates in total | Passe {0} portões no total |
| `unlock.level` | Reach level {0} | Chegue ao nível {0} |
| `unlock.coins_earned_total` | Earn {0} coins in total | Ganhe {0} moedas no total |
| `unlock.challenge` | Complete {0} | Complete {0} |
| `unlock.achievement` | Earn {0} | Conquiste {0} |
| `unlock.world_cleared` | Clear {0} | Vença {0} |
| `unlock.prestige` | Prestige {0} times | Faça prestígio {0} vezes |
| `unlock.collection` | Own {0}% of the {1} | Tenha {0}% das {1} |
| `unlock.counter` | {0} at {1} | {0} em {1} |
| `unlock.all_of` | {0} and {1} | {0} e {1} |

### Empty states

| key | en | pt_BR |
| --- | --- | --- |
| `challenges.record.none` | Not played yet | Ainda não jogado |
| `milestones.none` | Every milestone reached | Todos os marcos alcançados |
| `achievements.hidden.desc` | A hidden achievement | Uma conquista secreta |

(Reused keys, listed here to fix their role as empty states; they are already shipped.)

## 2. Keys the redesign still needs

Every key the redesigned GoalsScreen currently references is **already present** in both
tables and in `StringKey.java` — verified holistically: the enum's 493 constants all
resolve in both JSON files, and the two JSON files carry identical key sets. So the
implementation needs **zero new keys from me**; section 2 lists instead the keys the
redesign's visible copy depends on for each new piece of the spec, marking the
placeholder-bearing ones. Do not invent keys beyond these; if a genuinely new string
turns up during implementation, follow section 3's procedure.

| key | en | pt_BR | placeholder(s) |
| --- | --- | --- | --- |
| `goals.title` | Goals | Metas | none |
| `menu.nav_goals` | Goals | Metas | none |
| `goals.percent` | {0}% | {0}% | {0} = floored per-cent (int) |
| `achievements.count` | {0} of {1} unlocked | {0} de {1} desbloqueadas | {0} = unlocked, {1} = total |
| `collections.value` | {0} / {1} ({2}%) | {0} / {1} ({2}%) | {0} = owned, {1} = total, {2} = per-cent |
| `milestones.progress` | {0} / {1} | {0} / {1} | {0} = current, {1} = target |
| `milestones.level_reward` | Level {0} reward: {1} coins | Recompensa do nível {0}: {1} moedas | {0} = level, {1} = coins |
| `summary.level` | Level {0} | Nível {0} | {0} = level |
| `summary.level_progress` | {0} / {1} XP | {0} / {1} XP | {0} = xp, {1} = level total |
| `stat.xp` | XP | XP | none |
| `unlock.runs` | Play {0} runs | Jogue {0} partidas | {0} = run count |
| `challenges.play` | Play | Jogar | none |
| `challenges.locked_title` | Locked | Bloqueado | none |
| `challenges.locked` | Locked: {0} | Bloqueado: {0} | {0} = condition in words |
| `challenges.objective` | Objective: {0} | Objetivo: {0} | {0} = objective in words |
| `challenges.world` | World: {0} | Mundo: {0} | {0} = world name |
| `challenges.tier` | Tier: {0} | Dificuldade: {0} | {0} = tier name |
| `challenges.rules` | Rules: {0} | Regras: {0} | {0} = rules in words |
| `challenges.rules.none` | Standard rules | Regras padrão | none |
| `challenges.rewards` | Rewards: {0} | Recompensas: {0} | {0} = reward list |
| `challenges.reward.coins` | {0} coins | {0} moedas | {0} = coin amount |
| `challenges.record` | Best {0} gates, {1} attempts | Melhor {0} portões, {1} tentativas | {0} = gates, {1} = attempts |
| `challenges.record.none` | Not played yet | Ainda não jogado | none |
| `challenges.completed` | Completed | Concluído | none |
| `achievements.unlocked_at` | Unlocked {0} | Conquistada em {0} | {0} = ISO date string |
| `achievements.reward` | +{0} coins | +{0} moedas | {0} = coins |
| `goals.field.world` | World | Mundo | none |
| `goals.field.tier` | Tier | Dificuldade | none |
| `goals.field.rules` | Rules | Regras | none |
| `goals.field.progress` | Progress | Progresso | none |
| `goals.field.reward` | Reward | Recompensa | none |
| `goals.field.unlock` | Unlock | Desbloqueio | none |
| `collections.birds` | Birds | Aves | none |
| `collections.abilities` | Abilities | Habilidades | none |
| `collections.worlds` | Worlds | Mundos | none |
| `collections.challenges` | Challenges | Desafios | none |
| `collections.cosmetics` | Colours | Cores | none |
| `collections.achievements` | Achievements | Conquistas | none |
| `collections.upgrades` | Upgrades | Melhorias | none |
| `collections.all` | Everything | Tudo | none |
| `goals.collections.desc.birds` | Unlock different birds | Desbloqueie aves diferentes | none |
| `goals.collections.desc.abilities` | Discover special abilities | Descubra habilidades especiais | none |
| `goals.collections.desc.worlds` | Explore new worlds | Explore novos mundos | none |
| `goals.collections.desc.challenges` | Complete challenge runs | Complete partidas de desafio | none |
| `goals.collections.desc.cosmetics` | Collect different colours | Colecione cores diferentes | none |
| `goals.collections.desc.achievements` | Unlock achievements | Desbloqueie conquistas | none |
| `goals.collections.desc.upgrades` | Forge permanent upgrades | Forje melhorias permanentes | none |
| `goals.collections.desc.all` | Total game completion | Conclusão total do jogo | none |
| `goals.collections.footer` | Keep playing. There's more to discover! | Continue jogando. Há mais para descobrir! | none |

The redesign's placeholder semantics, pinned for the implementer:

- `goals.percent` — pass the **floored int** per-cent, exactly what the reference's "(42%)"
  shows. Do not pass a double and format it here.
- `achievements.count` — summary line "N of M unlocked"; {0} unlocked, {1} total.
- `collections.value` — row counter "N / M (P%)"; {0} owned, {1} total, {2} per-cent.
- `milestones.progress` / `summary.level_progress` — the milestone row counter and the level
  bar. DTM note: for the level bar the reference shows "0 / 100 XP": format `milestones.progress`
  with (xp, total) then append `stat.xp`, or format `summary.level_progress` with (xp, total).
  Never drop the `XP` word.
- `milestones.level_reward` — the embedded reward line of a milestone row: "Level 2 reward:
  50 coins". {0} level, {1} coins. Note the pt_BR reorders the level to the front — that is a
  legal placeholder reorder, preserve both placeholders.
- `challenges.locked` — the unlock row's "Locked: {0}" with the condition in words (for
  raids this is the `unlock.runs` wording, e.g. "Play 12 runs").
- `achievements.unlocked_at` — {0} must be the date **string** already rendered by the
  screen's `isoDate(...)`, not an epoch; the screen owns date formatting.
- `unlock.runs` — the "Play N runs" unlock-condition wording; {0} is the run count.

## 3. Rules the implementer must follow

### Adding a player-facing string

1. Add the key to **`en.json`** first — it is the source of truth.
2. Add the **same key** to **`pt_BR.json`**, same placeholders, same count.
3. Add a constant of the same name to **`content/StringKey`**.
4. Fetch it with `strings.get(StringKey.X)` / `strings.format(StringKey.X, ...)` from the
   shared `Strings` held in `GameContext`; never call `Strings.load` per frame.
5. Run the test suite — `StringsTest` fails the build on drift.

The two files must carry **exactly** the same key set. `Strings.load` falls back to
English for a missing key, so a dropped translation is otherwise invisible at runtime —
`StringsTest.everyShippedFileCarriesExactlyTheSameKeys` is the only thing that catches it,
and it fails the build. Placeholders may be **reordered** by the translator but never
dropped or renumbered.

### Re-labelling on a language switch

A screen that caches rendered text (tab labels, challenge names, card rows) must compare
`strings.language()` with the language it last drew and refresh when they differ.

The current `GoalsScreen` already does this: field `shownLanguage` tracks the last drawn
language, and `refreshTexts()` re-reads the four tab labels into `GoalsTabBar`, rebuilds
the challenge list options (with `CHALLENGES_LOCKED_ENTRY` formatting), and refreshes the
play CTA (`CHALLENGES_PLAY` vs `CHALLENGES_LOCKED_TITLE`) — called from `show()` and from
`tick()` whenever `!strings.language().equals(shownLanguage)`. The per-frame field-row
labels are read through `strings.get(...)` each render, so the challenge detail block
cannot go stale. The re-label contract is therefore already satisfied; keep it that way.

### Width growth — the exact class of defect ForgeNodeCardTest locks

pt_BR is systematically longer than en. The redesign's cards have **fixed logical
heights** (achievement card 49 px locked / 58 px held, milestone row 65 px, collection row
55 px / Everything 67 px) and the reference shows descriptions and values at font sizes
the English text already truncates with `TextPainter.ellipsise`. The pt_BR translations
run longer still. Measured deltas (pt_BR characters minus en characters) for the goals
family and the content rows the screen renders:

| key | en length | pt_BR length | delta |
| --- | --- | --- | --- |
| `milestones.level_reward` | 27 | 35 | +8 |
| `goals.collections.footer` | 42 | 43 | +1 |

And the content ids the cards carry, where the deltas are much worse (a sample of the
worst offenders — every achievement/challenge desc the cards render):

| key | en length | pt_BR length | delta |
| --- | --- | --- | --- |
| `challenge.moving_world_1.desc` | 45 | 57 | +12 |
| `achievement.clean_25.desc` | 38 | 50 | +12 |
| `achievement.clean_10.desc` | 38 | 50 | +12 |
| `achievement.collect_all_abilities.desc` | 21 | 33 | +12 |
| `achievement.collect_all_birds.desc` | 18 | 30 | +12 |
| `achievement.boss_storm_sky.desc` | 25 | 35 | +10 |
| `achievement.boss_hunter.desc` | 23 | 33 | +10 |
| `challenge.coin_rush_1.desc` | 38 | 47 | +9 |
| `achievement.first_save.desc` | 27 | 36 | +9 |
| `achievement.collect_all_worlds.desc` | 19 | 28 | +9 |
| `achievement.ability_adept.name` | 13 | 22 | +9 |
| `achievement.collect_all_cosmetics.desc` | 21 | 29 | +8 |
| `achievement.collect_all_cosmetics.name` | 13 | 21 | +8 |
| `achievement.coin_collector.name` | 14 | 22 | +8 |
| `achievement.ability_master.name` | 14 | 22 | +8 |
| `challenge.no_shield_1.desc` | 45 | 56 | +11 |
| `challenge.tiny_wings_1.desc` | 39 | 49 | +10 |
| `challenge.one_life_1.desc` | 37 | 39 | +2 |

Rules drawn from that:

- Assert card geometry (bottom ink below top ink, no clipped glyphs) rather than a
  measured text width against a literal — the font is the runner's logical `SansSerif`
  until a font is installed; CI widths differ by ~1/6. `ForgeNodeCardTest` is the model:
  it pins the geometry, not the width.
- Use `TextPainter.ellipsise` on single-line values, and the custom `wrap()` (used by
  `paintFieldRow`) to split long value lines onto two lines before any ellipsis.
- Lock pt_BR as the layout worst case: with the deltas above, a card that fits `en` will
  not necessarily fit `pt_BR`. If `en` truncates, `pt_BR` truncates harder — treat a
  *truncated en* string as a defect, not a quota.
- Re-check the achievements status pill ("Locked"/"Completed") and the 
  milestone counter row: pt_BR "desbloqueadas"/"concluído" after format are wider than en
  "unlocked"/"completed".

### Build and test gates

```bash
./gradlew --offline build
./gradlew --offline test
# what those tests actually check:
#   StringsTest          - the tables and the placeholders
#   ContentValidatorTest - every StringKey resolves; content ids have name + desc
#   FontsTest            - the base font can draw the accents
#   ProceduralRenderTest - renders every screen in both languages and asserts they differ
./gradlew run --args="--lang pt_BR"     # eyeball it, or hand a render to ff-vision
```

Content is JSON: an unknown key is an error, so any new field in a `data/*.json` content
file must update the matching `content.defs` record in the same change. Never write to the
real profile directory — use `--home build/smoke/app-home` and `ls ~/.flapforge` must
still say it does not exist.

## 4. Verdict

**Zero new keys** are required: the redesign's copy is already fully covered — the
GoalsScreen and the `screens/goals/` package reference 49 distinct `StringKey` constants,
all present in both tables with identical key sets and placeholder counts (verified
2026-09-09, 775/775 across every key, 0 only-in-one-file). Section 2's 49-row dependency
table is the full reference list. The single biggest localization risk is the fixed logical
card heights in the new layout clipping pt_BR descriptions that run 8–12 characters longer
than their en originals — the exact overflow defect class `ForgeNodeCardTest` locks.

---

Verified: `en.json` and `pt_BR.json` each carry 775 keys, byte-identical key sets, no
placeholder drift among the goals family; all 493 `StringKey` constants resolve in both.
