package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * A feature toggle earned like any other unlockable (D13, §4). The unlockable id is
 * {@code feature:<id>}; {@code modifiers} and {@code seeded_runs} ship in {@code economy.json}.
 *
 * @param id the feature id
 * @param unlock how the feature is earned
 */
public record FeatureDef(String id, UnlockConditionDef unlock) {

    /** Namespace prefix of a feature unlockable id (D13). */
    public static final String NAMESPACE = "feature:";

    /**
     * Validates the components.
     *
     * @param id the feature id
     * @param unlock the unlock condition
     */
    public FeatureDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unlock, "unlock");
    }

    /**
     * The namespaced unlockable id.
     *
     * @return {@code feature:<id>}
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }
}
