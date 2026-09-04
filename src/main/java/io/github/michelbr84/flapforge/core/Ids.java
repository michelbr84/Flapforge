package io.github.michelbr84.flapforge.core;

import java.util.regex.Pattern;

/**
 * Content id conventions (D10, D13): bare ids match {@code ^[a-z][a-z0-9_]*$} and unlockable ids
 * are namespaced as {@code <namespace>:<id>} (cosmetics use {@code cosmetic:<bird>:<palette>}).
 */
public final class Ids {

    /** Regular expression every content id must match. */
    public static final Pattern ID_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** Namespace of bird unlocks. */
    public static final String BIRD = "bird";
    /** Namespace of ability unlocks. */
    public static final String ABILITY = "ability";
    /** Namespace of world unlocks. */
    public static final String WORLD = "world";
    /** Namespace of challenge unlocks. */
    public static final String CHALLENGE = "challenge";
    /** Namespace of modifier unlocks. */
    public static final String MODIFIER = "modifier";
    /** Namespace of difficulty tier unlocks. */
    public static final String TIER = "tier";
    /** Namespace of upgrade tree unlocks. */
    public static final String TREE = "tree";
    /** Namespace of cosmetic (palette) unlocks. */
    public static final String COSMETIC = "cosmetic";
    /** Namespace of feature unlocks. */
    public static final String FEATURE = "feature";

    private static final char SEPARATOR = ':';

    private Ids() {
    }

    /**
     * Tells whether {@code id} is a valid bare id.
     *
     * @param id the candidate id
     * @return {@code true} when it matches {@link #ID_PATTERN}
     */
    public static boolean isValid(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    /**
     * Validates a bare id.
     *
     * @param id the candidate id
     * @return the id itself
     * @throws IllegalArgumentException when the id is invalid
     */
    public static String requireValid(String id) {
        if (!isValid(id)) {
            throw new IllegalArgumentException("Invalid id: " + id);
        }
        return id;
    }

    /**
     * Builds a namespaced unlock id.
     *
     * @param namespace the namespace (for example {@link #BIRD})
     * @param id the bare id
     * @return {@code namespace + ":" + id}
     */
    public static String qualified(String namespace, String id) {
        return namespace + SEPARATOR + id;
    }

    /**
     * Builds a bird unlock id.
     *
     * @param id the bird id
     * @return the namespaced id
     */
    public static String bird(String id) {
        return qualified(BIRD, id);
    }

    /**
     * Builds an ability unlock id.
     *
     * @param id the ability id
     * @return the namespaced id
     */
    public static String ability(String id) {
        return qualified(ABILITY, id);
    }

    /**
     * Builds a world unlock id.
     *
     * @param id the world id
     * @return the namespaced id
     */
    public static String world(String id) {
        return qualified(WORLD, id);
    }

    /**
     * Builds a challenge unlock id.
     *
     * @param id the challenge id
     * @return the namespaced id
     */
    public static String challenge(String id) {
        return qualified(CHALLENGE, id);
    }

    /**
     * Builds a modifier unlock id.
     *
     * @param id the modifier id
     * @return the namespaced id
     */
    public static String modifier(String id) {
        return qualified(MODIFIER, id);
    }

    /**
     * Builds a tier unlock id.
     *
     * @param id the tier id
     * @return the namespaced id
     */
    public static String tier(String id) {
        return qualified(TIER, id);
    }

    /**
     * Builds an upgrade tree unlock id.
     *
     * @param id the tree id
     * @return the namespaced id
     */
    public static String tree(String id) {
        return qualified(TREE, id);
    }

    /**
     * Builds a feature unlock id.
     *
     * @param id the feature id
     * @return the namespaced id
     */
    public static String feature(String id) {
        return qualified(FEATURE, id);
    }

    /**
     * Builds a cosmetic unlock id.
     *
     * @param birdId the bird id
     * @param paletteId the palette id
     * @return {@code cosmetic:<bird>:<palette>}
     */
    public static String cosmetic(String birdId, String paletteId) {
        return COSMETIC + SEPARATOR + birdId + SEPARATOR + paletteId;
    }

    /**
     * Returns the namespace of a namespaced id.
     *
     * @param unlockId the namespaced id
     * @return the text before the first separator
     * @throws IllegalArgumentException when the id has no namespace
     */
    public static String namespaceOf(String unlockId) {
        int i = unlockId.indexOf(SEPARATOR);
        if (i <= 0) {
            throw new IllegalArgumentException("Not a namespaced id: " + unlockId);
        }
        return unlockId.substring(0, i);
    }

    /**
     * Returns the part after the namespace of a namespaced id.
     *
     * @param unlockId the namespaced id
     * @return the text after the first separator
     * @throws IllegalArgumentException when the id has no namespace
     */
    public static String localOf(String unlockId) {
        int i = unlockId.indexOf(SEPARATOR);
        if (i <= 0 || i == unlockId.length() - 1) {
            throw new IllegalArgumentException("Not a namespaced id: " + unlockId);
        }
        return unlockId.substring(i + 1);
    }

    /**
     * Tells whether {@code unlockId} belongs to {@code namespace}.
     *
     * @param unlockId the namespaced id
     * @param namespace the namespace to test
     * @return {@code true} when the id starts with {@code namespace + ":"}
     */
    public static boolean inNamespace(String unlockId, String namespace) {
        return unlockId.startsWith(namespace + SEPARATOR);
    }
}
