package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.content.GameContent;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.content.defs.ModifiersDef;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunMode;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.ui.screens.SeededRunSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs that draft, for the screen tests (M6).
 *
 * <p>Two things a UI test needs and the shipped configuration will not give it quickly: a draft
 * that opens within a few hundred ticks instead of at gate 10, and a corridor the bird survives
 * long enough to reach it, which is {@link FixedSpawnTable} (E17). Everything else is the shipped
 * content — the real cards, the real rarities, the real set bonuses and therefore the real strings
 * — so what a test asserts on the cards is what a player reads on them.
 */
public final class DraftRuns {

    private DraftRuns() {
    }

    /**
     * A catalogue of shipped cards whose only draft opens at one gate.
     *
     * @param content the loaded content
     * @param gate the gate count the draft opens at
     * @param choices how many cards it shows
     * @param ids the modifier ids to keep, in the order given; empty keeps the whole file
     * @return the catalogue, carrying the shipped rarity weights and synergies
     */
    public static ModifierCatalog catalog(GameContent content, int gate, int choices,
            String... ids) {
        ModifiersDef defs = content.modifierBlock();
        List<String> wanted = Arrays.asList(ids);
        List<ModifierDef> kept = new ArrayList<>();
        if (wanted.isEmpty()) {
            kept.addAll(defs.modifiers());
        } else {
            for (String id : wanted) {
                for (ModifierDef def : defs.modifiers()) {
                    if (def.id().equals(id)) {
                        kept.add(def);
                    }
                }
            }
        }
        return new ModifierCatalog(List.of(gate), choices, defs.rarityWeights(), kept,
                defs.synergies());
    }

    /**
     * A run source whose runs draft from a catalogue on a flat corridor.
     *
     * @param catalog the roguelite content of the run
     * @param forced modifier ids taken before the first tick (a challenge's cards, D11)
     * @return the source a {@code GameScreen} can be built with
     */
    public static SeededRunSource source(ModifierCatalog catalog, List<String> forced) {
        return source(catalog, forced, true);
    }

    /**
     * A run source whose runs carry a catalogue on a flat corridor, with the feature gate either
     * way.
     *
     * @param catalog the roguelite content of the run
     * @param forced modifier ids taken before the first tick
     * @param allowOffers whether drafts open at all (D11: {@code feature:modifiers})
     * @return the source
     */
    public static SeededRunSource source(ModifierCatalog catalog, List<String> forced,
            boolean allowOffers) {
        return seed -> new Run(RunConfig.builder(seed)
                .mode(RunMode.SEEDED)
                .allowOffers(allowOffers)
                .forcedModifiers(forced)
                .build(), RunSetup.CLASSIC.withModifiers(catalog), new FixedSpawnTable());
    }

    /**
     * A run source with no forced cards.
     *
     * @param catalog the roguelite content of the run
     * @return the source
     */
    public static SeededRunSource source(ModifierCatalog catalog) {
        return source(catalog, List.of());
    }
}
