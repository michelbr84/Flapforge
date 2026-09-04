package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * One upgrade tree of {@code upgrades.json} (D13): an id and the condition that opens it.
 *
 * <p>{@code tree:<id>} must be unlocked before any of the tree's nodes can be bought, which makes
 * the tree — not the node — the unlockable (E21: {@code profile.unlocked} never holds an
 * {@code upgrade:} id). The three shipped trees are {@code flight} (default), {@code economy} and
 * {@code forge}.
 *
 * @param id the tree id
 * @param unlock the condition that opens the tree
 */
public record TreeDef(String id, UnlockConditionDef unlock) {

    /** Namespace of the unlockable id of a tree. */
    public static final String NAMESPACE = "tree:";

    /**
     * Checks the required fields.
     *
     * @throws NullPointerException when the id or the unlock block is missing
     */
    public TreeDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unlock, "unlock");
    }

    /**
     * The namespaced unlockable id, {@code tree:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }
}
