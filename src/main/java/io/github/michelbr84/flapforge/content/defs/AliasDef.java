package io.github.michelbr84.flapforge.content.defs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The id reconciliation table, {@code aliases.json} (E21). It is applied on load, between the
 * save migration and {@code PlayerProfile.normalize()}: parse → migrate → <b>aliases</b> →
 * normalize → bind.
 *
 * <p>The table is <em>per field</em>, because the same bare word means different things in
 * different places: {@code "feather"} is an upgrade node, {@code "bird:starter"} an unlockable id
 * and {@code "starter"} a value of {@code selected.birdId}. One flat rename map would rewrite all
 * three or none.
 *
 * <p>Every map is {@code old id -> new id}. {@link #removedUpgrades} drops nodes that no longer
 * exist and {@link #refunds} credits the coins they cost, once — {@code profile.reconciled}
 * records which entries have already been applied so a refund is never paid twice.
 *
 * @param version the table version, bumped when entries are added
 * @param unlocked renames applied to {@code profile.unlocked} (namespaced ids)
 * @param upgrades renames applied to the keys of {@code profile.upgrades} (bare node ids)
 * @param abilityLevels renames applied to the keys of {@code profile.abilityLevels}
 * @param selected renames applied to {@code profile.selected}, keyed by field name
 * @param removedUpgrades node ids to drop from {@code profile.upgrades}
 * @param refunds coins to credit once for a dropped node
 */
public record AliasDef(int version, Map<String, String> unlocked, Map<String, String> upgrades,
        Map<String, String> abilityLevels, Map<String, Map<String, String>> selected,
        List<String> removedUpgrades, Map<String, Long> refunds) {

    /** An empty table: nothing to reconcile. */
    public static final AliasDef EMPTY =
            new AliasDef(1, Map.of(), Map.of(), Map.of(), Map.of(), List.of(), Map.of());

    /**
     * Copies every map and list so the table is immutable and keeps file order.
     *
     * @throws IllegalArgumentException when the version is below 1
     */
    public AliasDef {
        if (version < 1) {
            throw new IllegalArgumentException("aliases.version must be at least 1: " + version);
        }
        unlocked = Collections.unmodifiableMap(new LinkedHashMap<>(unlocked));
        upgrades = Collections.unmodifiableMap(new LinkedHashMap<>(upgrades));
        abilityLevels = Collections.unmodifiableMap(new LinkedHashMap<>(abilityLevels));
        Map<String, Map<String, String>> fields = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> e : selected.entrySet()) {
            fields.put(e.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(e.getValue())));
        }
        selected = Collections.unmodifiableMap(fields);
        removedUpgrades = List.copyOf(removedUpgrades);
        refunds = Collections.unmodifiableMap(new LinkedHashMap<>(refunds));
    }

    /**
     * Whether the table would change anything at all.
     *
     * @return {@code true} when every map and list is empty
     */
    public boolean isEmpty() {
        return unlocked.isEmpty() && upgrades.isEmpty() && abilityLevels.isEmpty()
                && selected.isEmpty() && removedUpgrades.isEmpty() && refunds.isEmpty();
    }
}
