package io.github.michelbr84.flapforge.gameplay;

import java.util.List;
import java.util.Optional;

/**
 * Immutable list of {@link TickFact}s produced by one tick (D5).
 *
 * @param tick the tick number the facts belong to
 * @param facts the facts in production order
 */
public record TickReport(int tick, List<TickFact> facts) {

    /**
     * Copies the fact list.
     *
     * @param tick the tick number
     * @param facts the facts
     */
    public TickReport {
        facts = List.copyOf(facts);
    }

    /**
     * A report with no facts.
     *
     * @param tick the tick number
     * @return the report
     */
    public static TickReport empty(int tick) {
        return new TickReport(tick, List.of());
    }

    /**
     * Tells whether a fact of the given type is present.
     *
     * @param type the fact class
     * @return {@code true} when present
     */
    public boolean has(Class<? extends TickFact> type) {
        for (TickFact f : facts) {
            if (type.isInstance(f)) {
                return true;
            }
        }
        return false;
    }

    /**
     * First fact of the given type.
     *
     * @param <T> the fact type
     * @param type the fact class
     * @return the fact when present
     */
    public <T extends TickFact> Optional<T> first(Class<T> type) {
        for (TickFact f : facts) {
            if (type.isInstance(f)) {
                return Optional.of(type.cast(f));
            }
        }
        return Optional.empty();
    }

    /**
     * Number of facts of the given type.
     *
     * @param type the fact class
     * @return the count
     */
    public int count(Class<? extends TickFact> type) {
        int n = 0;
        for (TickFact f : facts) {
            if (type.isInstance(f)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Tells whether no fact was produced.
     *
     * @return {@code true} when empty
     */
    public boolean isEmpty() {
        return facts.isEmpty();
    }
}
