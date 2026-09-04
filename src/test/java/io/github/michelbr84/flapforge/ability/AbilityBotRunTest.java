package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityTag;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.harness.HeadlessRunner;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * One headless bot run per ability (M5): each one is survivable and each one changes what the run
 * does. The bot's ability rule (D21) is what drives the actives.
 */
class AbilityBotRunTest {

    private static final int TICKS = 1800;
    private static final long SEED = 7;

    /** Seeds of the baseline comparison sweep, and its tick budget. */
    private static final int SWEEP_SEEDS = 24;
    private static final int SWEEP_TICKS = 6000;
    /**
     * How far below the ability-free baseline an ability's mean gates may sit. Slow time is the
     * one that legitimately drops: halving {@code TIME_SCALE} passes fewer gates inside a fixed
     * tick budget (measured 0.87 of the baseline). A net-negative ability — the level-1 dash
     * before it learned to leave its burst clear of the column it flew into — measured 0.17.
     */
    private static final double TOLERANCE = 0.75;

    private static RunConfig config(String abilityId) {
        if (abilityId == null) {
            return AbilityRuns.config(SEED, null, List.of()).build();
        }
        AbilityDef def = AbilityRuns.def(abilityId);
        return def.kind() == AbilityKind.ACTIVE
                ? AbilityRuns.config(SEED, abilityId, List.of()).build()
                : AbilityRuns.config(SEED, null, List.of(abilityId)).build();
    }

    private static HeadlessRunner.Outcome fly(String abilityId, BotPilot.Preset preset) {
        Run run = AbilityRuns.factory().newRun(config(abilityId));
        return HeadlessRunner.run(run, new BotPilot(preset, SEED), TICKS, true);
    }

    private static double meanGates(String abilityId) {
        long total = 0;
        for (int i = 0; i < SWEEP_SEEDS; i++) {
            long seed = 1 + i;
            Run run = AbilityRuns.factory().newRun(abilityId == null
                    ? AbilityRuns.config(seed, null, List.of()).build()
                    : equip(seed, abilityId));
            total += HeadlessRunner.run(run, new BotPilot(BotPilot.Preset.AVERAGE, seed),
                    SWEEP_TICKS, false).result().gatesPassed();
        }
        return (double) total / SWEEP_SEEDS;
    }

    private static RunConfig equip(long seed, String abilityId) {
        AbilityDef def = AbilityRuns.def(abilityId);
        return def.kind() == AbilityKind.ACTIVE
                ? AbilityRuns.config(seed, abilityId, List.of()).build()
                : AbilityRuns.config(seed, null, List.of(abilityId)).build();
    }

    /**
     * The M5 gate the plan calls "bot with each ability": no ability may make the bot materially
     * worse than flying with nothing, and a defensive passive has to make it better. Asserting
     * "gates >= 4 on one seed" let a level-1 dash through that cost 83 % of the bot's survival.
     */
    @Test
    void noAbilityMakesTheBotWorseThanFlyingWithNone() {
        double baseline = meanGates(null);
        assertTrue(baseline > 20, "the baseline sweep must fly: " + baseline);
        StringBuilder table = new StringBuilder("mean gates over " + SWEEP_SEEDS + " seeds, "
                + SWEEP_TICKS + " ticks, AVERAGE preset\n  none " + baseline);
        for (String id : AbilityRuns.content().abilities().ids()) {
            double mean = meanGates(id);
            table.append("\n  ").append(id).append(' ').append(mean)
                    .append(" (").append(Math.round(100 * mean / baseline)).append(" %)");
            assertTrue(mean >= TOLERANCE * baseline,
                    id + " costs the bot too much survival to be worth equipping. " + table);
            if (AbilityRuns.def(id).kind() == AbilityKind.PASSIVE
                    && AbilityRuns.def(id).has(AbilityTag.DEFENSIVE)) {
                // A defensive passive costs nothing to carry and absorbs a hit the bot would have
                // died from, so it must show up as survival. An active defensive one only pays
                // when the bot spends it, which the ability rule does not always get to do.
                assertTrue(mean > baseline,
                        id + " is a defensive passive and must beat the baseline. " + table);
            }
        }
    }

    @Test
    void everyAbilityIsSurvivableAndChangesTheRun() {
        HeadlessRunner.Outcome baseline = fly(null, BotPilot.Preset.AVERAGE);
        assertTrue(baseline.result().gatesPassed() > 0, "the baseline run must fly");
        for (String id : AbilityRuns.content().abilities().ids()) {
            HeadlessRunner.Outcome outcome = fly(id, BotPilot.Preset.AVERAGE);
            RunResult result = outcome.result();
            assertTrue(result.stats().ticksAlive() > 500,
                    id + " must be flyable, ticksAlive=" + result.stats().ticksAlive());
            assertTrue(result.gatesPassed() >= 4,
                    id + " passed only " + result.gatesPassed() + " gates");
            assertNotEquals(baseline.hashes(), outcome.hashes(),
                    id + " changed nothing in the run");
        }
    }

    @Test
    void theBotSpendsAnActiveAbilityWhenItPredictsALethalHit() {
        for (String id : AbilityRuns.content().abilities().ids()) {
            AbilityDef def = AbilityRuns.def(id);
            HeadlessRunner.Outcome outcome = fly(id, BotPilot.Preset.NOVICE);
            int uses = outcome.result().stats().abilitiesUsed().getOrDefault(id, 0);
            if (def.kind() == AbilityKind.ACTIVE) {
                assertTrue(uses > 0, id + " was never used by the bot");
            } else {
                assertEquals(0, uses, id + " is a passive and needs no input");
            }
        }
    }

    @Test
    void aDefensivePassiveKeepsAWeakPilotFlyingLonger() {
        HeadlessRunner.Outcome bare = fly(null, BotPilot.Preset.NOVICE);
        HeadlessRunner.Outcome shielded = fly("shield", BotPilot.Preset.NOVICE);
        assertTrue(shielded.result().stats().shieldAbsorbs() > 0, "the shield was spent");
        assertTrue(shielded.result().stats().ticksAlive() > bare.result().stats().ticksAlive(),
                "shielded " + shielded.result().stats().ticksAlive() + " vs bare "
                        + bare.result().stats().ticksAlive());

        HeadlessRunner.Outcome revived = fly("emergency_recovery", BotPilot.Preset.NOVICE);
        assertTrue(revived.result().stats().revives() > 0, "the revive was spent");
        assertTrue(revived.result().stats().ticksAlive() > bare.result().stats().ticksAlive());
    }

    @Test
    void anAbilityRunIsReproducibleTickForTick() {
        for (String id : List.of("dash", "slow_time", "double_flap", "shield")) {
            HeadlessRunner.Outcome a = fly(id, BotPilot.Preset.AVERAGE);
            HeadlessRunner.Outcome b = fly(id, BotPilot.Preset.AVERAGE);
            assertEquals(a.hashes(), b.hashes(), id + " is not reproducible");
            assertEquals(a.result().counters(), b.result().counters());
        }
    }
}
