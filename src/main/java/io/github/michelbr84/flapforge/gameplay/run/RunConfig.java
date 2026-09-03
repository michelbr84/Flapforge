package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything that defines a run before it starts (D11). Immutable; build with
 * {@link #builder(long)} or {@link #classic(long)}. M1 reads {@code seed}, {@code birdId},
 * {@code worldId}, {@code tierId}, {@code mode}, {@code rules} and {@code permanentEffects}; the
 * other fields are carried for later milestones.
 *
 * <p><b>The boss switch (M8).</b> {@link #bossEnabled} says whether the run meets its world's
 * boss (or its challenge's, D11). The builder defaults it to {@code true}: every profile run,
 * every challenge, the balancing tool and the feasibility tests play the encounter. The one
 * configuration that pins it {@code false} is {@link #classic(long)} — the ability-free,
 * draft-free, boss-free run behind the published {@code --headless-run} hash and the golden
 * fixture (D12). That run reaches 36 gates in Green Fields, whose boss is at gate 30, so with
 * the boss on the hash CI compares across OS and JDK would have moved with M8; pinning the boss
 * off there keeps it where M1 left it, and {@code RunFactory} resolves no boss for such a run
 * whatever the content says. Instant retry keeps the flag ({@link #withSeed}), so a retried
 * boss run is still a boss run. See {@code docs/DEVELOPMENT.md}, "--headless-run".
 *
 * @param seed the run seed (every random stream derives from it)
 * @param birdId the bird id
 * @param paletteId the cosmetic palette id
 * @param worldId the world id
 * @param tierId the difficulty tier id
 * @param challengeId the challenge id, or {@code null} outside challenge mode
 * @param mode how the run was started
 * @param activeAbilityId the equipped active ability, or {@code null}
 * @param passiveAbilityIds equipped passive abilities
 * @param passiveSlotBonus extra passive slots the profile earned (E3, {@code
 *     profile.passiveSlotBonus}); the loadout keeps {@code BirdDef.passiveSlots + this} passives
 * @param permanentEffects snapshot of upgrade effects (layer {@code UPGRADES})
 * @param abilityLevels owned level per ability id
 * @param upgradeLevelsTotal total of owned upgrade levels (bird synergies)
 * @param forcedModifiers modifiers pre-taken at start (challenge/daily)
 * @param availableModifiers the modifier ids the profile may be <em>offered</em> (M6): every
 *     {@code unlock: default} card is available anyway, so this list only has to carry the
 *     earned ones ({@code gold_rush}, {@code phoenix}, {@code stormrider})
 * @param rules rules from the run source (challenge/daily/config)
 * @param allowOffers whether modifier offers open during the run
 * @param bossEnabled whether the run meets its boss (M8); {@code false} only for the pinned
 *     {@link #classic(long)} configuration, see the class comment
 */
public record RunConfig(long seed, String birdId, String paletteId, String worldId, String tierId,
        String challengeId, RunMode mode, String activeAbilityId, List<String> passiveAbilityIds,
        int passiveSlotBonus, List<StatModifier> permanentEffects,
        Map<String, Integer> abilityLevels, int upgradeLevelsTotal, List<String> forcedModifiers,
        List<String> availableModifiers, RuleSet rules, boolean allowOffers,
        boolean bossEnabled) {

    /** Default bird id. */
    public static final String DEFAULT_BIRD = "classic";
    /** Default palette id. */
    public static final String DEFAULT_PALETTE = "default";
    /** Default world id. */
    public static final String DEFAULT_WORLD = "green_fields";
    /** Default tier id. */
    public static final String DEFAULT_TIER = "normal";

    /**
     * Copies the collections into deterministic, unmodifiable ones.
     *
     * @param seed the run seed
     * @param birdId the bird id
     * @param paletteId the palette id
     * @param worldId the world id
     * @param tierId the tier id
     * @param challengeId the challenge id or {@code null}
     * @param mode the run mode
     * @param activeAbilityId the active ability or {@code null}
     * @param passiveAbilityIds passive abilities
     * @param passiveSlotBonus extra passive slots
     * @param permanentEffects upgrade effects
     * @param abilityLevels ability levels
     * @param upgradeLevelsTotal total upgrade levels
     * @param forcedModifiers forced modifiers
     * @param availableModifiers modifier ids the profile may be offered
     * @param rules rules
     * @param allowOffers whether offers open
     * @param bossEnabled whether the run meets its boss
     */
    public RunConfig {
        Objects.requireNonNull(birdId, "birdId");
        Objects.requireNonNull(paletteId, "paletteId");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(tierId, "tierId");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(rules, "rules");
        passiveAbilityIds = List.copyOf(passiveAbilityIds);
        permanentEffects = List.copyOf(permanentEffects);
        abilityLevels = Collections.unmodifiableMap(new LinkedHashMap<>(abilityLevels));
        forcedModifiers = List.copyOf(forcedModifiers);
        availableModifiers = List.copyOf(availableModifiers);
    }

    /**
     * Starts a builder with defaults (classic bird, Green Fields, normal tier, standard mode).
     *
     * @param seed the run seed
     * @return the builder
     */
    public static Builder builder(long seed) {
        return new Builder(seed);
    }

    /**
     * The default configuration for a seed: the pinned classic run (D12). Classic bird, Green
     * Fields, normal tier, no abilities, no drafts and — since M8 — no boss, so the published
     * {@code --headless-run} hash and the golden fixture stay where M1 recorded them (see the
     * class comment). Everything else the builder defaults to.
     *
     * @param seed the run seed
     * @return the config
     */
    public static RunConfig classic(long seed) {
        return new Builder(seed).bossEnabled(false).build();
    }

    /**
     * Builder pre-filled with this configuration.
     *
     * @return the builder
     */
    public Builder toBuilder() {
        Builder b = new Builder(seed);
        b.birdId = birdId;
        b.paletteId = paletteId;
        b.worldId = worldId;
        b.tierId = tierId;
        b.challengeId = challengeId;
        b.mode = mode;
        b.activeAbilityId = activeAbilityId;
        b.passiveAbilityIds = new ArrayList<>(passiveAbilityIds);
        b.passiveSlotBonus = passiveSlotBonus;
        b.permanentEffects = new ArrayList<>(permanentEffects);
        b.abilityLevels = new LinkedHashMap<>(abilityLevels);
        b.upgradeLevelsTotal = upgradeLevelsTotal;
        b.forcedModifiers = new ArrayList<>(forcedModifiers);
        b.availableModifiers = new ArrayList<>(availableModifiers);
        b.rules = rules;
        b.allowOffers = allowOffers;
        b.bossEnabled = bossEnabled;
        return b;
    }

    /**
     * Copy with another seed (instant retry keeps the config, D29).
     *
     * @param newSeed the seed
     * @return the copy
     */
    public RunConfig withSeed(long newSeed) {
        return toBuilder().seed(newSeed).build();
    }

    /** Mutable builder for {@link RunConfig}. */
    public static final class Builder {
        private long seed;
        private String birdId = DEFAULT_BIRD;
        private String paletteId = DEFAULT_PALETTE;
        private String worldId = DEFAULT_WORLD;
        private String tierId = DEFAULT_TIER;
        private String challengeId;
        private RunMode mode = RunMode.STANDARD;
        private String activeAbilityId;
        private List<String> passiveAbilityIds = new ArrayList<>();
        private int passiveSlotBonus;
        private List<StatModifier> permanentEffects = new ArrayList<>();
        private Map<String, Integer> abilityLevels = new LinkedHashMap<>();
        private int upgradeLevelsTotal;
        private List<String> forcedModifiers = new ArrayList<>();
        private List<String> availableModifiers = new ArrayList<>();
        private RuleSet rules = RuleSet.EMPTY;
        private boolean allowOffers;
        private boolean bossEnabled = true;

        private Builder(long seed) {
            this.seed = seed;
        }

        /**
         * Sets the seed.
         *
         * @param value the seed
         * @return this builder
         */
        public Builder seed(long value) {
            seed = value;
            return this;
        }

        /**
         * Sets the bird id.
         *
         * @param value the id
         * @return this builder
         */
        public Builder birdId(String value) {
            birdId = value;
            return this;
        }

        /**
         * Sets the palette id.
         *
         * @param value the id
         * @return this builder
         */
        public Builder paletteId(String value) {
            paletteId = value;
            return this;
        }

        /**
         * Sets the world id.
         *
         * @param value the id
         * @return this builder
         */
        public Builder worldId(String value) {
            worldId = value;
            return this;
        }

        /**
         * Sets the tier id.
         *
         * @param value the id
         * @return this builder
         */
        public Builder tierId(String value) {
            tierId = value;
            return this;
        }

        /**
         * Sets the challenge id.
         *
         * @param value the id or {@code null}
         * @return this builder
         */
        public Builder challengeId(String value) {
            challengeId = value;
            return this;
        }

        /**
         * Sets the mode.
         *
         * @param value the mode
         * @return this builder
         */
        public Builder mode(RunMode value) {
            mode = value;
            return this;
        }

        /**
         * Sets the active ability.
         *
         * @param value the id or {@code null}
         * @return this builder
         */
        public Builder activeAbilityId(String value) {
            activeAbilityId = value;
            return this;
        }

        /**
         * Sets the passive abilities.
         *
         * @param value the ids
         * @return this builder
         */
        public Builder passiveAbilityIds(List<String> value) {
            passiveAbilityIds = new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the extra passive slots the profile earned (E3).
         *
         * @param value the bonus
         * @return this builder
         */
        public Builder passiveSlotBonus(int value) {
            passiveSlotBonus = value;
            return this;
        }

        /**
         * Sets the permanent (upgrade) effects.
         *
         * @param value the modifiers
         * @return this builder
         */
        public Builder permanentEffects(List<StatModifier> value) {
            permanentEffects = new ArrayList<>(value);
            return this;
        }

        /**
         * Adds one permanent effect.
         *
         * @param value the modifier
         * @return this builder
         */
        public Builder addPermanentEffect(StatModifier value) {
            permanentEffects.add(value);
            return this;
        }

        /**
         * Sets the ability levels.
         *
         * @param value level per ability id
         * @return this builder
         */
        public Builder abilityLevels(Map<String, Integer> value) {
            abilityLevels = new LinkedHashMap<>(value);
            return this;
        }

        /**
         * Sets the total of owned upgrade levels.
         *
         * @param value the total
         * @return this builder
         */
        public Builder upgradeLevelsTotal(int value) {
            upgradeLevelsTotal = value;
            return this;
        }

        /**
         * Sets the forced modifiers.
         *
         * @param value the ids
         * @return this builder
         */
        public Builder forcedModifiers(List<String> value) {
            forcedModifiers = new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the modifier ids the profile may be offered (M6).
         *
         * @param value the ids
         * @return this builder
         */
        public Builder availableModifiers(List<String> value) {
            availableModifiers = new ArrayList<>(value);
            return this;
        }

        /**
         * Sets the rules.
         *
         * @param value the rules
         * @return this builder
         */
        public Builder rules(RuleSet value) {
            rules = value;
            return this;
        }

        /**
         * Sets whether offers open.
         *
         * @param value {@code true} to allow offers
         * @return this builder
         */
        public Builder allowOffers(boolean value) {
            allowOffers = value;
            return this;
        }

        /**
         * Sets whether the run meets its boss (M8). Defaults to {@code true}; only the pinned
         * classic configuration turns it off (see the class comment).
         *
         * @param value {@code true} to play the encounter
         * @return this builder
         */
        public Builder bossEnabled(boolean value) {
            bossEnabled = value;
            return this;
        }

        /**
         * Builds the config.
         *
         * @return the config
         */
        public RunConfig build() {
            return new RunConfig(seed, birdId, paletteId, worldId, tierId, challengeId, mode,
                    activeAbilityId, passiveAbilityIds, passiveSlotBonus, permanentEffects,
                    abilityLevels, upgradeLevelsTotal, forcedModifiers, availableModifiers, rules,
                    allowOffers, bossEnabled);
        }
    }
}
