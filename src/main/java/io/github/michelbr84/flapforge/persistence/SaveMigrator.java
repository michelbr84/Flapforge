package io.github.michelbr84.flapforge.persistence;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runs the {@link Migration} chain that carries an old {@code save.json} up to the version this
 * build reads (D15).
 *
 * <p>The chain is ordered and gapless: from version {@code n} there is at most one migration, and
 * the migrator walks {@code n → n+1 → …} until it reaches the target. A missing step stops the
 * walk and is reported rather than guessed at — a save that cannot be carried forward is a bug in
 * the release, not something to improvise on a player's file.
 *
 * <p>Migrating is idempotent: a tree already at the target version comes back untouched with an
 * empty step list, so a double load or a re-save can never run a step twice.
 *
 * <p>Version 1 ships no migrations. The chain exists, is wired into the load path and is proved by
 * a synthetic 1 → 2 pair in {@code SaveMigrationTest}, so the first real schema change is a data
 * change rather than a rewrite of the loader (D15's policy: from {@code v0.1.0} on, every schema
 * change ships a migration).
 */
public final class SaveMigrator {

    private final List<Migration> migrations;
    private final Map<Integer, Migration> byFrom;

    /**
     * Creates a migrator.
     *
     * @param migrations the steps, in any order; at most one may start at a given version
     */
    public SaveMigrator(List<Migration> migrations) {
        Objects.requireNonNull(migrations, "migrations");
        List<Migration> sorted = new ArrayList<>(migrations);
        sorted.sort((a, b) -> Integer.compare(a.from(), b.from()));
        Map<Integer, Migration> index = new LinkedHashMap<>();
        for (Migration migration : sorted) {
            Migration clash = index.put(migration.from(), migration);
            if (clash != null) {
                throw new IllegalArgumentException(
                        "two migrations start at version " + migration.from());
            }
        }
        this.migrations = List.copyOf(sorted);
        this.byFrom = Map.copyOf(index);
    }

    /**
     * The migrator this build ships.
     *
     * @return a migrator with no steps (schema version 1 is the first)
     */
    public static SaveMigrator standard() {
        return new SaveMigrator(List.of());
    }

    /**
     * The steps, ordered by source version.
     *
     * @return the steps
     */
    public List<Migration> migrations() {
        return migrations;
    }

    /**
     * Whether the chain can carry a version up to the target.
     *
     * @param fromVersion the version on disk
     * @param toVersion the version this build reads
     * @return {@code true} when every intermediate step exists
     */
    public boolean canMigrate(int fromVersion, int toVersion) {
        int version = fromVersion;
        while (version < toVersion) {
            Migration step = byFrom.get(version);
            if (step == null) {
                return false;
            }
            version = step.to();
        }
        return true;
    }

    /**
     * Carries a tree up to the target version.
     *
     * @param tree the parsed save; not modified
     * @param fromVersion the version the tree is at
     * @param toVersion the version to reach
     * @return the result; when a step is missing the tree comes back at the version the walk
     *     reached, and {@link Result#complete()} is {@code false}
     */
    public Result migrate(JsonObject tree, int fromVersion, int toVersion) {
        Objects.requireNonNull(tree, "tree");
        JsonObject current = tree;
        int version = fromVersion;
        List<String> applied = new ArrayList<>();
        while (version < toVersion) {
            Migration step = byFrom.get(version);
            if (step == null) {
                return new Result(current, version, applied, false);
            }
            current = step.apply(current);
            version = step.to();
            current.addProperty(SaveFile.KEY_VERSION, version);
            applied.add(step.label());
        }
        return new Result(current, version, applied, version >= toVersion);
    }

    /**
     * The outcome of a migration walk.
     *
     * @param tree the migrated tree (the argument itself when nothing ran)
     * @param version the version the tree is at now
     * @param applied the labels of the steps that ran, in order
     * @param complete whether the target version was reached
     */
    public record Result(JsonObject tree, int version, List<String> applied, boolean complete) {

        /**
         * Copies the step list.
         *
         * @param tree the migrated tree
         * @param version the reached version
         * @param applied the applied steps
         * @param complete whether the target was reached
         */
        public Result {
            Objects.requireNonNull(tree, "tree");
            applied = List.copyOf(applied);
        }

        /**
         * Whether any step ran.
         *
         * @return {@code true} when at least one migration was applied
         */
        public boolean migrated() {
            return !applied.isEmpty();
        }
    }
}
