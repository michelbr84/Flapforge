package io.github.michelbr84.flapforge.gameplay.stats;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable set of {@link RuleFlag}s (D8). The active rules of a run are the union of every
 * contributing source; {@link #zeroes(StatId)} is the only absolute step of the stat pipeline.
 *
 * <p>The set is carried as a bit mask over {@link RuleFlag#ordinal()}, so {@link #hashCode()} is a
 * <em>specified</em> value rather than {@code EnumSet}'s sum of identity hash codes. That matters
 * because rule sets travel inside records ({@code TierSpec}, {@code WorldSpec}, {@code RunSetup}):
 * the moment one of those becomes a hash key, an unspecified hash makes iteration order
 * JDK-dependent and the cross-OS determinism guarantee unprovable. The mask also makes
 * {@link #contains}, {@link #union} and {@link #equals} plain integer work. It holds up to 64
 * flags, which is where {@link RuleFlag} would have to grow a second word.
 */
public final class RuleSet {

    /** The empty rule set. */
    public static final RuleSet EMPTY = new RuleSet(EnumSet.noneOf(RuleFlag.class));

    private final EnumSet<RuleFlag> flags;
    private final long mask;

    private RuleSet(EnumSet<RuleFlag> flags) {
        this.flags = flags;
        long bits = 0;
        for (RuleFlag flag : flags) {
            bits |= 1L << flag.ordinal();
        }
        this.mask = bits;
    }

    /**
     * Creates a rule set from flags.
     *
     * @param flags the flags
     * @return the set
     */
    public static RuleSet of(RuleFlag... flags) {
        EnumSet<RuleFlag> set = EnumSet.noneOf(RuleFlag.class);
        Collections.addAll(set, flags);
        return set.isEmpty() ? EMPTY : new RuleSet(set);
    }

    /**
     * Creates a rule set from a collection of flags.
     *
     * @param flags the flags
     * @return the set
     */
    public static RuleSet of(Collection<RuleFlag> flags) {
        if (flags.isEmpty()) {
            return EMPTY;
        }
        return new RuleSet(EnumSet.copyOf(flags));
    }

    /**
     * Returns the union of this set with another.
     *
     * @param other the other set
     * @return a set containing the flags of both
     */
    public RuleSet union(RuleSet other) {
        if (other.mask == 0 || (mask | other.mask) == mask) {
            return this;
        }
        if (mask == 0) {
            return other;
        }
        EnumSet<RuleFlag> set = EnumSet.copyOf(flags);
        set.addAll(other.flags);
        return new RuleSet(set);
    }

    /**
     * Returns this set with one more flag.
     *
     * @param flag the flag to add
     * @return the extended set
     */
    public RuleSet with(RuleFlag flag) {
        if (contains(flag)) {
            return this;
        }
        EnumSet<RuleFlag> set = EnumSet.copyOf(flags);
        set.add(flag);
        return new RuleSet(set);
    }

    /**
     * Tells whether a flag is active.
     *
     * @param flag the flag
     * @return {@code true} when active
     */
    public boolean contains(RuleFlag flag) {
        return (mask & (1L << flag.ordinal())) != 0;
    }

    /**
     * Tells whether no flag is active.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return mask == 0;
    }

    /**
     * Read-only view of the flags, in declaration order.
     *
     * @return the flags
     */
    public Set<RuleFlag> flags() {
        return Collections.unmodifiableSet(flags);
    }

    /**
     * Tells whether the active rules force a stat to zero (D8): {@link RuleFlag#NO_REVIVE} zeroes
     * {@link StatId#REVIVES} and {@link RuleFlag#NO_DEFENSIVE_ABILITIES} zeroes
     * {@link StatId#SHIELD_CHARGES}.
     *
     * @param stat the stat
     * @return {@code true} when the stat resolves to zero regardless of modifiers
     */
    public boolean zeroes(StatId stat) {
        switch (stat) {
            case REVIVES:
                return contains(RuleFlag.NO_REVIVE);
            case SHIELD_CHARGES:
                return contains(RuleFlag.NO_DEFENSIVE_ABILITIES);
            default:
                return false;
        }
    }

    /**
     * The flags as a bit mask over {@link RuleFlag#ordinal()} — the value {@link #hashCode()} is
     * derived from, and a stable identity for logs and fixtures.
     *
     * @return the mask
     */
    public long mask() {
        return mask;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof RuleSet other && mask == other.mask;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(mask);
    }

    @Override
    public String toString() {
        return flags.toString();
    }
}
