package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
import java.util.Objects;

/**
 * One unlock condition (D13, E20). The record is flat with a {@code type} discriminator, so a
 * condition tree is expressed by {@code conditions} on {@code ALL_OF}/{@code ANY_OF} entries.
 *
 * <p>Which fields matter depends on {@code type}: {@code value} for the threshold kinds
 * ({@code runs}, {@code level}, {@code best_gates}, {@code prestige}, {@code counter}),
 * {@code id} for the named kinds ({@code challenge}, {@code achievement}, {@code world_cleared}),
 * {@code amount} for {@code purchase}, {@code counter} for {@code counter}, and
 * {@code conditions} for the composites. Unused fields stay at their defaults.
 *
 * @param type the condition kind
 * @param value the threshold, when the kind uses one
 * @param id the referenced content id, when the kind uses one
 * @param amount the shop price in coins, for {@code purchase}
 * @param counter the counter name, for {@code counter}
 * @param conditions nested conditions, for {@code all_of} and {@code any_of}
 */
public record UnlockConditionDef(UnlockType type, double value, String id, double amount,
        String counter, List<UnlockConditionDef> conditions) {

    /** The condition every default-owned entry uses. */
    public static final UnlockConditionDef DEFAULT =
            new UnlockConditionDef(UnlockType.DEFAULT, 0, null, 0, null, List.of());

    /**
     * Copies the nested conditions.
     *
     * @param type the condition kind
     * @param value the threshold
     * @param id the referenced id
     * @param amount the shop price
     * @param counter the counter name
     * @param conditions nested conditions
     */
    public UnlockConditionDef {
        Objects.requireNonNull(type, "type");
        conditions = List.copyOf(conditions);
    }
}
