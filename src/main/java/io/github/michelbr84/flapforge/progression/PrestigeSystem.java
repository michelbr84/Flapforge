package io.github.michelbr84.flapforge.progression;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PrestigeDef;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * The prestige of a profile (D13, E4, E23). There are no prestige shards: the only permanent
 * gain is {@code economy.json.prestige.bonusPerPrestige × prestigeCount}, which travels into the
 * {@code PRESTIGE} stat layer of every later run (D8) through {@link #effectsOf}.
 *
 * <p>{@link #prestige(PlayerProfile, GameContent)} implements E23 to the letter. It requires
 * {@code level ≥ prestige.requiredLevel} and {@code prestigeCount < prestige.maxPrestige}, then:
 * <ol>
 *   <li>snapshots {@code profile.prestigeBaseline} = the lifetime {@code totalRuns},
 *       {@code totalGates}, {@code coinsEarned} and {@code bossesCleared[]} of the statistics —
 *       the frozen "before" every cumulative unlock condition reads "since prestige" against;</li>
 *   <li>resets the wallet, the experience and the level to 1, the upgrades, the ability levels,
 *       the ability level cap back to 2, the passive-slot bonus back to 0, the challenge records
 *       and the daily record; the selection falls back to the defaults (E23 names no reset for
 *       {@code selected}, but E15 makes one unavoidable: the implied-unlock repair of
 *       {@code PlayerProfile.normalize} re-grants whatever {@code selected} points at, so a kept
 *       selection would hand back the world, the tier or the ability the reset just took away —
 *       on every load, forever);</li>
 *   <li>rebuilds {@code unlocked} as the defaults union the kept ids — every {@code bird:*} and
 *       every {@code cosmetic:*} the profile owned — dropping worlds, tiers, trees, abilities,
 *       challenges and features, which are all earned again;</li>
 *   <li>keeps the achievements and the lifetime statistics untouched;</li>
 *   <li>raises {@code prestigeCount} (capped at {@code maxPrestige}) and grants
 *       {@code cosmetic:<selectedBird>:prestige}, the golden palette every bird ships.</li>
 * </ol>
 *
 * <p>The cumulative conditions themselves do not live here: {@link UnlockEvaluator} subtracts
 * {@link PlayerProfile#prestigeBaseline} when it reads {@code runs}, {@code total_gates},
 * {@code coins_earned_total} and {@code world_cleared}, and reads {@code level} as the reset
 * level. {@code best_gates}, {@code best_points} and {@code achievement} conditions read lifetime
 * values and keep working, because achievements and statistics survive a prestige (E20, E23).
 *
 * <p>Pure: a function of the profile, the content and nothing else. The caller marks the profile
 * dirty ({@code ProgressionManager.markChanged}) and flushes the save.
 */
public final class PrestigeSystem {

    /** The level a prestige asks for when no content supplies {@code economy.json} (E23). */
    public static final int DEFAULT_REQUIRED_LEVEL = 25;
    /** Source label of the prestige modifiers in a stat breakdown (D8, D17). */
    public static final String SOURCE = "prestige";
    /** Palette id of the cosmetic a prestige grants (E4: one golden palette per bird). */
    public static final String PRESTIGE_PALETTE = "prestige";

    /** Why a prestige did or did not happen. */
    public enum Status {
        /** The profile may prestige. */
        ELIGIBLE,
        /** {@code level} is below {@code prestige.requiredLevel}. */
        LEVEL_TOO_LOW,
        /** {@code prestigeCount} has reached {@code prestige.maxPrestige}. */
        MAX_REACHED
    }

    /**
     * What one call did.
     *
     * @param status why the call did or did not change the profile
     * @param prestigeCount the count after the call
     * @param cosmeticGranted the cosmetic id granted, or {@code null} when the call was refused
     */
    public record Result(Status status, int prestigeCount, String cosmeticGranted) {

        /**
         * Whether the profile was actually changed.
         *
         * @return {@code true} only for {@link Status#ELIGIBLE}
         */
        public boolean ok() {
            return status == Status.ELIGIBLE;
        }
    }

    private PrestigeSystem() {
    }

    /**
     * The prestige block to use: the content's, or the defaults a content-less caller falls back
     * to (the shipped {@code economy.json} values).
     *
     * @param content the loaded content, or {@code null}
     * @return the block
     */
    public static PrestigeDef defOf(GameContent content) {
        if (content != null) {
            return content.economy().prestige();
        }
        return new PrestigeDef(DEFAULT_REQUIRED_LEVEL, PrestigeDef.KEEPS, List.of(
                new StatModifierDef(StatId.COIN_MULT, StatOp.PERCENT_ADD, 0.05)),
                PlayerProfile.MAX_PRESTIGE_COUNT);
    }

    /**
     * Whether the profile may prestige right now, without changing anything (the preview the
     * statistics panel shows before the two-step confirm).
     *
     * @param profile the profile to read
     * @param content the loaded content, or {@code null} for the shipped defaults
     * @return the verdict
     */
    public static Status check(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        PrestigeDef def = defOf(content);
        if (profile.level < def.requiredLevel()) {
            return Status.LEVEL_TOO_LOW;
        }
        // The real cap enforcement: this refusal is the only path that observes the cap, so the
        // clamp in prestige() below is unreachable defence-in-depth — do not remove this guard
        // on the belief that the clamp alone enforces it.
        if (profile.prestigeCount >= def.maxPrestige()) {
            return Status.MAX_REACHED;
        }
        return Status.ELIGIBLE;
    }

    /**
     * Prestiges the profile in place (E23). A refused call changes nothing.
     *
     * @param profile the profile to reset
     * @param content the loaded content, or {@code null} for the shipped defaults
     * @return what happened
     */
    public static Result prestige(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        PrestigeDef def = defOf(content);
        Status status = check(profile, content);
        if (status != Status.ELIGIBLE) {
            return new Result(status, profile.prestigeCount, null);
        }

        // The badge cosmetic names the bird the player stands on — read before the selection
        // falls back to the defaults below.
        String badgeBirdId = profile.selected.birdId;

        // 1. The baseline: the lifetime totals the unlock evaluator reads "since prestige"
        // against. Taken before anything is reset — the statistics themselves are kept.
        PlayerProfile.PrestigeBaseline baseline = new PlayerProfile.PrestigeBaseline();
        Statistics stats = profile.statistics == null ? new Statistics() : profile.statistics;
        baseline.totalRuns = stats.totalRuns;
        baseline.totalGates = stats.totalGates;
        baseline.coinsEarned = stats.coinsEarned;
        baseline.bossesCleared = new ArrayList<>(stats.bossesCleared);
        baseline.normalize();
        profile.prestigeBaseline = baseline;

        // 2. The resets, exactly the list E23 names.
        Wallet wallet = Wallet.of(profile);
        for (String currency : new ArrayList<>(wallet.balances().keySet())) {
            wallet.set(currency, 0);
        }
        profile.xp = 0;
        profile.level = 1;
        profile.upgrades = new LinkedHashMap<>();
        profile.abilityLevels = new LinkedHashMap<>();
        profile.abilityLevelCap = PlayerProfile.DEFAULT_ABILITY_LEVEL_CAP;
        profile.passiveSlotBonus = 0;
        profile.challenges = new LinkedHashMap<>();
        profile.daily = new PlayerProfile.DailyRecord();
        profile.selected = new PlayerProfile.Selection();

        // 3. unlocked := defaults ∪ kept (bird:*, cosmetic:*), defaults first, kept in the order
        // they were owned. Worlds, tiers, trees, abilities, challenges, features and modifiers —
        // everything condition-derived — are dropped and earned again.
        LinkedHashSet<String> next = new LinkedHashSet<>(PlayerProfile.DEFAULT_UNLOCKED);
        for (String id : profile.unlocked) {
            if (id != null && (id.startsWith(BirdDef.NAMESPACE)
                    || id.startsWith(BirdDef.COSMETIC_NAMESPACE))) {
                next.add(id);
            }
        }
        profile.unlocked = new ArrayList<>(next);

        // 4. The count (capped again defensively — check() already refused the cap) and the badge
        // cosmetic of the bird the player stands on.
        profile.prestigeCount = Math.min(profile.prestigeCount + 1, def.maxPrestige());
        String cosmetic = BirdDef.COSMETIC_NAMESPACE + badgeBirdId + ":"
                + PRESTIGE_PALETTE;
        profile.unlock(cosmetic);
        return new Result(Status.ELIGIBLE, profile.prestigeCount, cosmetic);
    }

    /**
     * The {@code PRESTIGE} layer of a run (E23, D8): {@code bonusPerPrestige} once per prestige
     * performed, sourced {@code prestige}. Zero prestiges carry no layer at all, which is what
     * keeps the published hash where it is.
     *
     * @param profile the profile to read
     * @param content the loaded content, or {@code null} for the shipped defaults
     * @return the modifiers, empty when the profile has never prestiged
     */
    public static List<StatModifier> effectsOf(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        if (profile.prestigeCount <= 0) {
            return List.of();
        }
        List<StatModifierDef> bonus = defOf(content).bonusPerPrestige();
        List<StatModifier> out = new ArrayList<>(bonus.size() * profile.prestigeCount);
        for (int i = 0; i < profile.prestigeCount; i++) {
            for (StatModifierDef def : bonus) {
                out.add(def.toModifier(SOURCE));
            }
        }
        return List.copyOf(out);
    }

    /**
     * The permanent bonus a profile has banked, as a percentage of {@code COIN_MULT} (the
     * {@code PERCENT_ADD} entries of {@code bonusPerPrestige} summed over the prestiges). This is
     * the number the statistics panel shows in words.
     *
     * @param profile the profile to read
     * @param content the loaded content, or {@code null} for the shipped defaults
     * @return the percentage, 0 for a profile that never prestiged
     */
    public static double bonusPercent(PlayerProfile profile, GameContent content) {
        Objects.requireNonNull(profile, "profile");
        double percent = 0;
        for (StatModifierDef def : defOf(content).bonusPerPrestige()) {
            if (def.stat() == StatId.COIN_MULT && def.op() == StatOp.PERCENT_ADD) {
                percent += def.value();
            }
        }
        return percent * Math.max(0, profile.prestigeCount);
    }
}
