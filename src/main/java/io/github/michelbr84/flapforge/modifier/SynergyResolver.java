package io.github.michelbr84.flapforge.modifier;

import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.SynergyDef;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Decides which set bonuses a build has earned (D27, E16), and is the single place the rule is
 * written down.
 *
 * <p><b>Entries, not stacks.</b> Each modifier the run has taken is one <em>entry</em> and
 * contributes its tags once, however many stacks of it were taken. Two stacks of
 * {@code tailwind} are still one entry carrying {@code SPEED, GREED}.
 *
 * <p><b>Two distinct entries.</b> A synergy activates when its {@code requiresTags} multiset can
 * be covered by the contributed tags <em>using at least two distinct entries</em>. That second
 * clause is what makes a set bonus a bonus for a <em>set</em>: {@code stormrider} alone carries
 * both {@code SPEED} and {@code RISK} and would otherwise complete {@code daredevil} by itself.
 * With {@code tailwind} beside it the same multiset is covered by two cards, and it activates.
 *
 * <p>The resolver is a pure function of the taken multiset, recomputed from scratch on every
 * change ({@link #update(List)}), so a synergy deactivates as readily as it activates — which is
 * what a test can assert and what a future "swap a card" mechanic would need.
 */
public final class SynergyResolver {

    /** E16: a set bonus needs contributions from at least this many distinct modifiers. */
    public static final int MIN_DISTINCT_ENTRIES = 2;

    /** The most entries the matcher can weigh at once (one bit each). */
    private static final int MAX_ENTRIES = 62;

    private static final int TAG_COUNT = ModifierTag.values().length;

    private final List<SynergyDef> synergies;
    private final List<String> active = new ArrayList<>();
    private List<StatModifier> effects = List.of();
    private RuleSet flags = RuleSet.EMPTY;

    /**
     * Creates a resolver over the run's set bonuses.
     *
     * @param synergies the definitions, in content order
     */
    public SynergyResolver(List<SynergyDef> synergies) {
        this.synergies = List.copyOf(Objects.requireNonNull(synergies, "synergies"));
    }

    /**
     * Recomputes the active set from the modifiers the run has taken.
     *
     * @param entries the distinct modifiers taken, in the order they were taken (a stack is one
     *     entry)
     * @return the synergies that were <em>not</em> active before this call and are now, in content
     *     order; the caller turns them into {@code SynergyActivated} facts
     */
    public List<String> update(List<ModifierDef> entries) {
        Objects.requireNonNull(entries, "entries");
        int[][] capacity = capacityOf(entries);
        List<String> nowActive = new ArrayList<>(synergies.size());
        List<String> added = new ArrayList<>();
        List<StatModifier> layer = new ArrayList<>();
        EnumSet<RuleFlag> ruleFlags = EnumSet.noneOf(RuleFlag.class);
        for (SynergyDef def : synergies) {
            if (!covers(def.requiresTags(), capacity)) {
                continue;
            }
            nowActive.add(def.id());
            layer.addAll(def.toModifiers());
            ruleFlags.addAll(def.flags());
            if (!active.contains(def.id())) {
                added.add(def.id());
            }
        }
        active.clear();
        active.addAll(nowActive);
        effects = Collections.unmodifiableList(layer);
        flags = RuleSet.of(ruleFlags);
        return Collections.unmodifiableList(added);
    }

    /**
     * The synergies active right now, in content order.
     *
     * @return an unmodifiable view
     */
    public List<String> active() {
        return Collections.unmodifiableList(active);
    }

    /**
     * Whether a synergy is active.
     *
     * @param id the synergy id
     * @return {@code true} when it is
     */
    public boolean isActive(String id) {
        return active.contains(id);
    }

    /**
     * The contents of the {@code MOD_SYNERGY} layer for the active set.
     *
     * @return an unmodifiable list, empty when nothing is active
     */
    public List<StatModifier> effects() {
        return effects;
    }

    /**
     * The rule flags the active set turns on.
     *
     * @return the flags, {@link RuleSet#EMPTY} when nothing is active
     */
    public RuleSet flags() {
        return flags;
    }

    /**
     * Whether one synergy would be active for a set of taken modifiers (E16). Static, so the rule
     * can be asserted without building a resolver.
     *
     * @param def the synergy
     * @param entries the distinct modifiers taken
     * @return {@code true} when the tag multiset is covered by at least two distinct entries
     */
    public static boolean matches(SynergyDef def, List<ModifierDef> entries) {
        return covers(def.requiresTags(), capacityOf(entries));
    }

    /**
     * Tag counts per entry: {@code capacity[e][tag]} is how often entry {@code e} carries
     * {@code tag}.
     *
     * @param entries the distinct modifiers taken
     * @return the matrix, truncated at {@link #MAX_ENTRIES} entries
     */
    private static int[][] capacityOf(List<ModifierDef> entries) {
        int n = Math.min(entries.size(), MAX_ENTRIES);
        int[][] capacity = new int[n][TAG_COUNT];
        for (int e = 0; e < n; e++) {
            for (ModifierTag tag : entries.get(e).tags()) {
                capacity[e][tag.ordinal()]++;
            }
        }
        return capacity;
    }

    /**
     * Whether the required multiset can be covered using at least
     * {@link #MIN_DISTINCT_ENTRIES} distinct entries.
     *
     * @param required the tag multiset
     * @param capacity the per-entry tag counts
     * @return {@code true} when such an assignment exists
     */
    private static boolean covers(List<ModifierTag> required, int[][] capacity) {
        if (required.size() < MIN_DISTINCT_ENTRIES || capacity.length < MIN_DISTINCT_ENTRIES) {
            // Fewer required tags than distinct entries needed can never satisfy the E16 clause,
            // and neither can a build with only one card in it.
            return false;
        }
        return assign(required, 0, capacity, 0L);
    }

    /**
     * Backtracking assignment of each required tag to an entry that still carries it.
     *
     * <p>The search is exhaustive on purpose. A greedy pass would answer "is the multiset
     * covered", which is the easy half; the hard half is that the covering assignment must span
     * two entries, and whether one exists depends on which entry each tag is charged to.
     *
     * @param required the tag multiset
     * @param index the tag being placed
     * @param capacity the per-entry tag counts, mutated and restored
     * @param used bit set of the entries charged so far
     * @return {@code true} when a complete, two-entry-wide assignment exists
     */
    private static boolean assign(List<ModifierTag> required, int index, int[][] capacity,
            long used) {
        if (index == required.size()) {
            return Long.bitCount(used) >= MIN_DISTINCT_ENTRIES;
        }
        int tag = required.get(index).ordinal();
        for (int e = 0; e < capacity.length; e++) {
            if (capacity[e][tag] <= 0) {
                continue;
            }
            capacity[e][tag]--;
            boolean ok = assign(required, index + 1, capacity, used | (1L << e));
            capacity[e][tag]++;
            if (ok) {
                return true;
            }
        }
        return false;
    }
}
