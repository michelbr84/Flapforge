package io.github.michelbr84.flapforge.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The id tables {@link PlayerProfile#normalize(ProfileSchema)} needs to decide whether a saved id
 * still means something (E21): which birds, palettes, worlds, tiers and abilities the build ships,
 * which tree each upgrade node belongs to, what a selection falls back to when its id is gone, and
 * the E3 ceiling of {@code abilityLevelCap}.
 *
 * <p>It exists because {@code progression} must not depend on {@code content}: the save system is
 * pure and testable on its own, while the milestone that owns a registry (M4 birds and upgrades,
 * M5 abilities, M7 worlds) fills the corresponding table in through {@link #builder()}. Until a
 * registry exists, {@link #permissive()} keeps every id: a build that does not yet know about
 * worlds must not "repair" a world selection away.
 */
public final class ProfileSchema {

    private static final ProfileSchema PERMISSIVE = new Builder().build();

    private final boolean permissiveBirds;
    private final boolean permissiveWorlds;
    private final boolean permissiveTiers;
    private final boolean permissiveAbilities;
    private final Set<String> birds;
    private final Map<String, Set<String>> palettes;
    private final Set<String> worlds;
    private final Set<String> tiers;
    private final Set<String> abilities;
    private final Map<String, String> upgradeTrees;
    private final boolean permissiveUpgrades;
    private final int maxAbilityLevelCap;
    private final String defaultBirdId;
    private final String defaultPaletteId;
    private final String defaultWorldId;
    private final String defaultTierId;
    private final String defaultActiveAbilityId;

    private ProfileSchema(Builder b) {
        this.birds = Set.copyOf(b.birds);
        this.palettes = copyPalettes(b.palettes);
        this.worlds = Set.copyOf(b.worlds);
        this.tiers = Set.copyOf(b.tiers);
        this.abilities = Set.copyOf(b.abilities);
        this.upgradeTrees = Map.copyOf(b.upgradeTrees);
        this.permissiveUpgrades = upgradeTrees.isEmpty();
        this.maxAbilityLevelCap = b.maxAbilityLevelCap;
        this.permissiveBirds = birds.isEmpty();
        this.permissiveWorlds = worlds.isEmpty();
        this.permissiveTiers = tiers.isEmpty();
        this.permissiveAbilities = abilities.isEmpty();
        this.defaultBirdId = b.defaultBirdId;
        this.defaultPaletteId = b.defaultPaletteId;
        this.defaultWorldId = b.defaultWorldId;
        this.defaultTierId = b.defaultTierId;
        this.defaultActiveAbilityId = b.defaultActiveAbilityId;
    }

    private static Map<String, Set<String>> copyPalettes(Map<String, Set<String>> source) {
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            out.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        return Map.copyOf(out);
    }

    /**
     * A schema that knows no registry and therefore accepts every id, mapping no upgrade node to a
     * tree. This is what M3 uses: the save system is complete, the content that would populate the
     * tables is not.
     *
     * @return the shared instance
     */
    public static ProfileSchema permissive() {
        return PERMISSIVE;
    }

    /**
     * A builder pre-filled with the default selection.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Whether the build knows a bird.
     *
     * @param id the bird id
     * @return {@code true} when the id is known, or when no bird table was given
     */
    public boolean knowsBird(String id) {
        return id != null && (permissiveBirds || birds.contains(id));
    }

    /**
     * Whether the build knows a palette of a bird.
     *
     * @param birdId the bird id
     * @param paletteId the palette id
     * @return {@code true} when the pair is known, or when no palette table was given for the bird
     */
    public boolean knowsPalette(String birdId, String paletteId) {
        if (paletteId == null) {
            return false;
        }
        Set<String> owned = palettes.get(birdId);
        return owned == null || owned.contains(paletteId);
    }

    /**
     * Whether the build knows a world.
     *
     * @param id the world id
     * @return {@code true} when the id is known, or when no world table was given
     */
    public boolean knowsWorld(String id) {
        return id != null && (permissiveWorlds || worlds.contains(id));
    }

    /**
     * Whether the build knows a difficulty tier.
     *
     * @param id the tier id
     * @return {@code true} when the id is known, or when no tier table was given
     */
    public boolean knowsTier(String id) {
        return id != null && (permissiveTiers || tiers.contains(id));
    }

    /**
     * Whether the build knows an ability.
     *
     * @param id the ability id
     * @return {@code true} when the id is known, or when no ability table was given
     */
    public boolean knowsAbility(String id) {
        return id != null && (permissiveAbilities || abilities.contains(id));
    }

    /**
     * The upgrade tree an owned node belongs to (E21: owning a node implies owning its tree).
     *
     * @param upgradeNodeId the bare node id
     * @return the tree id, or {@code null} when the node is unknown
     */
    public String treeOf(String upgradeNodeId) {
        return upgradeTrees.get(upgradeNodeId);
    }

    /**
     * Whether the build knows an upgrade node.
     *
     * @param upgradeNodeId the bare node id
     * @return {@code true} when the id is known, or when no upgrade table was given
     */
    public boolean knowsUpgrade(String upgradeNodeId) {
        return upgradeNodeId != null
                && (permissiveUpgrades || upgradeTrees.containsKey(upgradeNodeId));
    }

    /**
     * The highest {@code abilityLevelCap} the content can produce (E3): the base cap plus every
     * {@code ability_cap} grant the trees ship, capped by the number of levels the thinnest
     * ability has.
     *
     * <p>{@code normalize} clamps to it from above as well as from below, so a hand-edited
     * {@code "abilityLevelCap": 99} cannot survive a load and offer ability levels that do not
     * exist. A schema built without content ({@link #permissive()}) returns 0, which means
     * "unknown, do not clamp from above".
     *
     * @return the ceiling, or {@code 0} when no content declared one
     */
    public int maxAbilityLevelCap() {
        return maxAbilityLevelCap;
    }

    /**
     * Bird a broken selection falls back to.
     *
     * @return the id
     */
    public String defaultBirdId() {
        return defaultBirdId;
    }

    /**
     * Palette a broken selection falls back to.
     *
     * @return the id
     */
    public String defaultPaletteId() {
        return defaultPaletteId;
    }

    /**
     * World a broken selection falls back to.
     *
     * @return the id
     */
    public String defaultWorldId() {
        return defaultWorldId;
    }

    /**
     * Tier a broken selection falls back to.
     *
     * @return the id
     */
    public String defaultTierId() {
        return defaultTierId;
    }

    /**
     * Active ability a broken selection falls back to.
     *
     * @return the id, possibly {@code null}
     */
    public String defaultActiveAbilityId() {
        return defaultActiveAbilityId;
    }

    /** Mutable builder for {@link ProfileSchema}. An empty table means "accept every id". */
    public static final class Builder {
        private final Set<String> birds = new LinkedHashSet<>();
        private final Map<String, Set<String>> palettes = new LinkedHashMap<>();
        private final Set<String> worlds = new LinkedHashSet<>();
        private final Set<String> tiers = new LinkedHashSet<>();
        private final Set<String> abilities = new LinkedHashSet<>();
        private final Map<String, String> upgradeTrees = new LinkedHashMap<>();
        private int maxAbilityLevelCap;
        private String defaultBirdId = PlayerProfile.DEFAULT_BIRD;
        private String defaultPaletteId = PlayerProfile.DEFAULT_PALETTE;
        private String defaultWorldId = PlayerProfile.DEFAULT_WORLD;
        private String defaultTierId = PlayerProfile.DEFAULT_TIER;
        private String defaultActiveAbilityId = PlayerProfile.DEFAULT_ACTIVE_ABILITY;

        private Builder() {
        }

        /**
         * Declares a bird and the palettes it ships with.
         *
         * @param birdId the bird id
         * @param paletteIds its palette ids
         * @return this builder
         */
        public Builder bird(String birdId, List<String> paletteIds) {
            birds.add(birdId);
            palettes.put(birdId, new LinkedHashSet<>(new ArrayList<>(paletteIds)));
            return this;
        }

        /**
         * Declares the worlds the build ships.
         *
         * @param ids the world ids
         * @return this builder
         */
        public Builder worlds(List<String> ids) {
            worlds.addAll(ids);
            return this;
        }

        /**
         * Declares the difficulty tiers the build ships.
         *
         * @param ids the tier ids
         * @return this builder
         */
        public Builder tiers(List<String> ids) {
            tiers.addAll(ids);
            return this;
        }

        /**
         * Declares the abilities the build ships.
         *
         * @param ids the ability ids
         * @return this builder
         */
        public Builder abilities(List<String> ids) {
            abilities.addAll(ids);
            return this;
        }

        /**
         * Declares which tree an upgrade node belongs to.
         *
         * @param nodeId the bare node id
         * @param treeId the tree id
         * @return this builder
         */
        public Builder upgradeNode(String nodeId, String treeId) {
            upgradeTrees.put(nodeId, treeId);
            return this;
        }

        /**
         * Declares the E3 ceiling of {@code profile.abilityLevelCap}.
         *
         * @param cap the ceiling; values below 1 mean "unknown, do not clamp from above"
         * @return this builder
         */
        public Builder abilityLevelCap(int cap) {
            maxAbilityLevelCap = Math.max(0, cap);
            return this;
        }

        /**
         * Sets the fallback selection.
         *
         * @param birdId the bird id
         * @param paletteId the palette id
         * @param worldId the world id
         * @param tierId the tier id
         * @param activeAbilityId the active ability id, or {@code null}
         * @return this builder
         */
        public Builder defaults(String birdId, String paletteId, String worldId, String tierId,
                String activeAbilityId) {
            defaultBirdId = birdId;
            defaultPaletteId = paletteId;
            defaultWorldId = worldId;
            defaultTierId = tierId;
            defaultActiveAbilityId = activeAbilityId;
            return this;
        }

        /**
         * Builds the schema.
         *
         * @return the schema
         */
        public ProfileSchema build() {
            return new ProfileSchema(this);
        }
    }
}
