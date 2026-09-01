package io.github.michelbr84.flapforge.persistence;

import com.google.gson.JsonObject;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * One step from one save schema version to the next (D15).
 *
 * <p>A migration is a pure function on the parsed tree: it takes the {@code save.json} object as
 * version {@link #from()} and returns it as version {@link #to()}. It works on the tree rather
 * than on {@link io.github.michelbr84.flapforge.progression.PlayerProfile} on purpose — the POJO
 * is the <em>current</em> shape, so binding an old file to it would drop exactly the fields the
 * migration needs to read.
 *
 * <p>Rules a migration must follow:
 * <ul>
 *   <li>it never mutates its argument ({@link #apply(JsonObject)} hands it a deep copy);</li>
 *   <li>it touches only what changed, so unknown keys written by other builds survive (E22);</li>
 *   <li>it is total: a field the old file did not have is a default, not a failure.</li>
 * </ul>
 *
 * <p>{@link SaveMigrator} sets the {@code version} key after each step, so a migration does not
 * have to.
 *
 * @param from the version the step reads
 * @param to the version the step produces; strictly greater than {@code from}
 * @param transform the transformation
 */
public record Migration(int from, int to, UnaryOperator<JsonObject> transform) {

    /**
     * Validates the step.
     *
     * @param from the source version
     * @param to the target version
     * @param transform the transformation
     */
    public Migration {
        Objects.requireNonNull(transform, "transform");
        if (to <= from) {
            throw new IllegalArgumentException(
                    "a migration must move forward: " + from + " -> " + to);
        }
    }

    /**
     * Runs the step on a copy of the tree.
     *
     * @param tree the tree at version {@link #from()}
     * @return the tree at version {@link #to()}; never the argument itself
     */
    public JsonObject apply(JsonObject tree) {
        JsonObject copy = tree.deepCopy();
        JsonObject migrated = transform.apply(copy);
        return migrated == null ? copy : migrated;
    }

    /**
     * A human-readable name for logs and for the pre-migration backup notes.
     *
     * @return {@code "1 -> 2"}
     */
    public String label() {
        return from + " -> " + to;
    }
}
