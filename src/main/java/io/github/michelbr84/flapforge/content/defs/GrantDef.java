package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;

/**
 * One thing an upgrade node grants when it is bought (E31.f):
 * {@code {type: UNLOCK | ABILITY_CAP | PASSIVE_SLOT, id?, amount?}}.
 *
 * <p>{@link GrantType#UNLOCK} carries the namespaced {@code id} it adds to
 * {@code profile.unlocked} and is the only shape that becomes an unlock-graph edge; the other two
 * carry an {@code amount} that {@code UpgradeManager.buy} adds to {@code profile.abilityLevelCap}
 * / {@code profile.passiveSlotBonus}. A grant is applied once, when the node reaches level 1.
 *
 * @param type what kind of grant this is
 * @param id the unlockable id for {@link GrantType#UNLOCK}, otherwise {@code null}
 * @param amount how much to add for the counter grants, otherwise ignored
 */
public record GrantDef(GrantType type, String id, long amount) {

    /**
     * Checks the shape of the grant.
     *
     * @throws NullPointerException when the type is missing, or an {@code UNLOCK} has no id
     * @throws IllegalArgumentException when a counter grant has a non-positive amount
     */
    public GrantDef {
        Objects.requireNonNull(type, "type");
        if (type == GrantType.UNLOCK) {
            Objects.requireNonNull(id, "grant.id is required for an UNLOCK grant");
        } else if (amount <= 0) {
            throw new IllegalArgumentException(
                    "grant.amount must be positive for " + type + ": " + amount);
        }
    }

    /**
     * Whether this grant adds an unlockable id (and therefore an unlock-graph edge).
     *
     * @return {@code true} for {@link GrantType#UNLOCK}
     */
    public boolean isUnlock() {
        return type == GrantType.UNLOCK;
    }
}
