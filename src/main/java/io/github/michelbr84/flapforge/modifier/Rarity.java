package io.github.michelbr84.flapforge.modifier;

/**
 * How rare a run modifier is in a draft (§4, D27).
 *
 * <p>The draw weights are deliberately <em>not</em> constants here: they come from
 * {@code modifiers.json.rarityWeights} and reach {@link ModifierPool} through
 * {@link ModifierCatalog}, so rebalancing a draft is a data change and never a code change (D10).
 * The enum only fixes the vocabulary and the order the UI sorts by.
 */
public enum Rarity {
    /** The everyday cards; the bulk of every offer. */
    COMMON,
    /** Noticeably stronger, and rarer for it. */
    RARE,
    /** A build-defining card. */
    EPIC,
    /** The three cards that need their own unlock (§4). */
    LEGENDARY
}
