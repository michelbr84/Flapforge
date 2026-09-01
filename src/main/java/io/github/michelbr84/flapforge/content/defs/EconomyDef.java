package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
import java.util.Objects;

/**
 * The whole of {@code economy.json} (§4, E1, E4, E32.a): the currencies a wallet may hold, the
 * run reward terms, the XP block with its curve and level rewards, the feature unlocks, the daily
 * block and the prestige block.
 *
 * <p>The file is the only source of the reward numbers: {@code RunRewardCalculator} reads this
 * record and nothing else, so a balance change is a data change.
 *
 * @param currencies the currency ids a {@code Wallet} may hold, in file order
 * @param rewards the coin terms of the run reward formula
 * @param xp the XP terms, the level curve and the level rewards
 * @param features the feature unlockables ({@code feature:<id>})
 * @param daily the daily challenge block
 * @param prestige the prestige block
 */
public record EconomyDef(List<String> currencies, RewardsDef rewards, XpDef xp,
        List<FeatureDef> features, DailyDef daily, PrestigeDef prestige) {

    /** The currency every reward is paid in (D13). */
    public static final String COINS = "coins";

    /**
     * Copies the lists.
     *
     * @param currencies the currency ids
     * @param rewards the coin rewards
     * @param xp the XP block
     * @param features the features
     * @param daily the daily block
     * @param prestige the prestige block
     */
    public EconomyDef {
        Objects.requireNonNull(rewards, "rewards");
        Objects.requireNonNull(xp, "xp");
        Objects.requireNonNull(daily, "daily");
        Objects.requireNonNull(prestige, "prestige");
        currencies = List.copyOf(currencies);
        features = List.copyOf(features);
    }

    /**
     * The currency run rewards are paid in: the first declared one.
     *
     * @return the currency id, or {@code null} when none is declared (the validator rejects that)
     */
    public String primaryCurrency() {
        return currencies.isEmpty() ? null : currencies.get(0);
    }

    /**
     * Looks a feature up.
     *
     * @param id the feature id
     * @return the feature, or {@code null} when the economy declares no such feature
     */
    public FeatureDef feature(String id) {
        for (FeatureDef def : features) {
            if (def.id().equals(id)) {
                return def;
            }
        }
        return null;
    }
}
