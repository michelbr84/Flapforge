package io.github.michelbr84.flapforge.content;

/**
 * The kinds of content the game knows about (D13, E31.h).
 *
 * <p>A kind carries two names. {@link #key()} is the prefix of its display strings —
 * {@code <kind>.<id>.name} and {@code <kind>.<id>.desc} in {@code data/strings/en.json}, which
 * {@link ContentValidator} requires for every id of every kind that ships. {@link #namespace()}
 * is the prefix of its unlockable ids in {@code profile.unlocked}; the kinds that are not
 * unlockables return {@code null}: an upgrade node is owned through {@code profile.upgrades}
 * (E21) and a synergy activates itself from the modifiers taken in a run.
 */
public enum ContentKind {

    /** Birds ({@code birds.json}). */
    BIRD("bird", "bird:"),
    /** Bird palettes; the id is {@code <bird>.<palette>} for strings, {@code <bird>:<palette>}
     * for unlocks. */
    COSMETIC("cosmetic", "cosmetic:"),
    /** Abilities ({@code abilities.json}). */
    ABILITY("ability", "ability:"),
    /** Upgrade nodes ({@code upgrades.json}); not unlockables (E21). */
    UPGRADE("upgrade", null),
    /** Upgrade trees ({@code upgrades.json}). */
    TREE("tree", "tree:"),
    /** Difficulty tiers ({@code difficulty.json}). */
    TIER("tier", "tier:"),
    /** Features bought in the shop ({@code economy.json}). */
    FEATURE("feature", "feature:"),
    /** Run modifiers ({@code modifiers.json}, M6). */
    MODIFIER("modifier", "modifier:"),
    /** Modifier synergies ({@code modifiers.json}, M6); not unlockables. */
    SYNERGY("synergy", null),
    /** Worlds ({@code worlds.json}). */
    WORLD("world", "world:"),
    /** Challenges ({@code challenges.json}). */
    CHALLENGE("challenge", "challenge:"),
    /** Achievements ({@code achievements.json}). */
    ACHIEVEMENT("achievement", "achievement:");

    private final String key;
    private final String namespace;

    ContentKind(String key, String namespace) {
        this.key = key;
        this.namespace = namespace;
    }

    /**
     * The prefix of the kind's display-string keys (D25).
     *
     * @return the prefix, for example {@code bird}
     */
    public String key() {
        return key;
    }

    /**
     * The prefix of the kind's unlockable ids (D13).
     *
     * @return the prefix, for example {@code bird:}, or {@code null} when the kind is not an
     *     unlockable
     */
    public String namespace() {
        return namespace;
    }

    /**
     * Whether ids of this kind appear in {@code profile.unlocked}.
     *
     * @return {@code true} when the kind has a namespace
     */
    public boolean isUnlockable() {
        return namespace != null;
    }

    /**
     * The unlockable id of one entry of this kind.
     *
     * @param id the entry id
     * @return the namespaced id
     * @throws IllegalStateException when the kind is not an unlockable
     */
    public String unlockableId(String id) {
        if (namespace == null) {
            throw new IllegalStateException(name() + " is not an unlockable kind");
        }
        return namespace + id;
    }

    /**
     * The kind an unlockable id belongs to.
     *
     * @param unlockableId a namespaced id such as {@code bird:classic}
     * @return the kind, or {@code null} when no kind claims the prefix
     */
    public static ContentKind ofUnlockable(String unlockableId) {
        if (unlockableId == null) {
            return null;
        }
        for (ContentKind kind : values()) {
            if (kind.namespace != null && unlockableId.startsWith(kind.namespace)) {
                return kind;
            }
        }
        return null;
    }
}
