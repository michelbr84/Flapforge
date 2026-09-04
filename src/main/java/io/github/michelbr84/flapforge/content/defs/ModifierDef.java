package io.github.michelbr84.flapforge.content.defs;

import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.modifier.ModifierTag;
import io.github.michelbr84.flapforge.modifier.Rarity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One draftable run modifier of {@code modifiers.json} (D27, §4).
 *
 * <p>Two independent mechanisms keep a card out of an offer, and they are not interchangeable
 * (E12): {@link #requiresFlagsAbsent()} is an <em>authored</em> exclusion the designer writes
 * down, while the pool <em>derives</em> a second one from the effects — a card whose whole effect
 * list is a no-op under the active rules is never shown. {@code phoenix} carries both, because a
 * card that also pays coins is not derivably inert under {@code NO_REVIVE}.
 *
 * @param id the modifier id, unique across the file
 * @param rarity the draw class; the weights live in {@code rarityWeights}
 * @param tags what the card is about, the only input of the synergy rules (D27)
 * @param maxStacks how often the same card may be taken in one run (at least 1)
 * @param excludes modifier ids that may not be taken together with this one (symmetric)
 * @param requiresFlagsAbsent rule flags that keep the card out of the pool
 * @param effects the stat modifiers one stack contributes to the {@code MODIFIERS} layer
 * @param flags rule flags the card turns on for the rest of the run
 * @param streakBonus extra coins per clean-gate streak step, or {@code null}
 * @param unlock the condition that unlocks {@code modifier:<id>}
 */
public record ModifierDef(String id, Rarity rarity, List<ModifierTag> tags, int maxStacks,
        List<String> excludes, List<RuleFlag> requiresFlagsAbsent, List<StatModifierDef> effects,
        List<RuleFlag> flags, StreakBonusDef streakBonus, UnlockConditionDef unlock) {

    /** Namespace of the unlockable id of a modifier. */
    public static final String NAMESPACE = "modifier:";

    /** Prefix of the label a modifier's effects carry in a stat breakdown. */
    public static final String SOURCE_PREFIX = "modifier:";

    /**
     * Copies the lists and checks the required fields.
     *
     * @throws NullPointerException when the id, the rarity or the unlock block is missing
     */
    public ModifierDef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rarity, "rarity");
        Objects.requireNonNull(unlock, "unlock");
        tags = List.copyOf(tags);
        excludes = List.copyOf(excludes);
        requiresFlagsAbsent = List.copyOf(requiresFlagsAbsent);
        effects = List.copyOf(effects);
        flags = List.copyOf(flags);
    }

    /**
     * The namespaced unlockable id, {@code modifier:<id>}.
     *
     * @return the id
     */
    public String unlockableId() {
        return NAMESPACE + id;
    }

    /**
     * Whether the modifier carries a tag.
     *
     * @param tag the tag to look for
     * @return {@code true} when it is present
     */
    public boolean has(ModifierTag tag) {
        return tags.contains(tag);
    }

    /**
     * Whether the modifier forbids a rule flag by hand (E12).
     *
     * @param flag the flag
     * @return {@code true} when the flag keeps the card out of the pool
     */
    public boolean forbids(RuleFlag flag) {
        return requiresFlagsAbsent.contains(flag);
    }

    /**
     * The coins one streak step pays because of this modifier.
     *
     * @return the coins, or 0 when it has no streak bonus
     */
    public long streakBonusCoins() {
        return streakBonus == null ? 0 : streakBonus.coins();
    }

    /**
     * The simulation seam records of one stack, labelled {@code modifier:<id>}.
     *
     * @return the modifiers, in authoring order
     */
    public List<StatModifier> toModifiers() {
        List<StatModifier> out = new ArrayList<>(effects.size());
        String source = SOURCE_PREFIX + id;
        for (StatModifierDef effect : effects) {
            out.add(effect.toModifier(source));
        }
        return out;
    }
}
