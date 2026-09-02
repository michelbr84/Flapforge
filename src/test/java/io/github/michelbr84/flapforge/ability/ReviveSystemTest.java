package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.run.ReviveSystem;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * D9: the revive is stat-driven. A bare {@code REVIVES > 0} — the forge node
 * {@code second_chance_1} — absorbs one lethal hit, zeroes the velocity and grants 60
 * invulnerability ticks with no ability equipped; {@code emergency_recovery} adds the auto-flap
 * kick and the longer window on top.
 */
class ReviveSystemTest {

    /** A run with the {@code second_chance_1} effect and no ability at all. */
    private static Run upgradeReviveOnly() {
        return AbilityRuns.factory().newRun(AbilityRuns.config(13, null, List.of())
                .permanentEffects(List.of(StatModifier.flat(StatId.REVIVES, 1,
                        "upgrade:second_chance_1")))
                .build());
    }

    @Test
    void aBareReviveAbsorbsOneHitZeroesTheVelocityAndGrantsSixtyIFrames() {
        Run run = AbilityRuns.started(upgradeReviveOnly());
        assertTrue(run.simulation().abilities().isEmpty(), "no ability, only the node");
        assertEquals(1, run.simulation().revive().maxCharges());
        assertEquals(ReviveSystem.DEFAULT_INVULN_TICKS, run.simulation().revive().invulnTicks());
        assertEquals(0.0, run.simulation().revive().kickMultiplier(), 0.0);

        AbilityRuns.wall(run);
        TickReport report = run.tick(RunInput.NONE);
        assertTrue(report.has(TickFact.Revived.class));
        assertFalse(report.has(TickFact.Crashed.class));
        assertEquals(RunPhase.FLYING, run.phase());
        assertEquals(0.0, run.simulation().bird().vy(), 0.0, "the fall is zeroed");
        assertEquals(60, run.simulation().invulnerableTicks());
        assertEquals(1, run.stats().revives());
        assertEquals(0, run.simulation().revive().charges());
    }

    @Test
    void aShieldIsSpentBeforeARevive() {
        Run run = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(13, null, List.of())
                        .permanentEffects(List.of(
                                StatModifier.flat(StatId.SHIELD_CHARGES, 1, "upgrade:shield"),
                                StatModifier.flat(StatId.REVIVES, 1, "upgrade:second_chance_1")))
                        .build()));
        AbilityRuns.wall(run);
        TickReport report = run.tick(RunInput.NONE);
        assertTrue(report.has(TickFact.ShieldAbsorbed.class));
        assertFalse(report.has(TickFact.Revived.class), "the cheaper resource goes first");
        assertEquals(1, run.simulation().revive().charges());
    }

    @Test
    void aReviveOnTheGroundLeavesTheBirdAliveAboveTheGroundLine() {
        Run run = AbilityRuns.started(upgradeReviveOnly());
        TickReport revived = null;
        for (int i = 0; i < 200 && revived == null; i++) {
            TickReport report = run.tick(RunInput.NONE);
            if (report.has(TickFact.Revived.class)) {
                revived = report;
            }
            assertFalse(report.has(TickFact.Crashed.class), "the fall must be survived once");
        }
        assertTrue(revived != null, "the bird fell into the ground and was revived");
        assertTrue(run.simulation().bird().y() < Playfield.GROUND_DEATH_Y,
                "M1 kills anything at or below the ground line at the start of the next tick");
        // The contract, not the constant: the clearance has to buy a window the player can fly
        // out of. An assertion written as GROUND_DEATH_Y - GROUND_CLEARANCE_PX would pass with a
        // clearance of one pixel, which is worth two ticks of free fall.
        assertTrue(Playfield.GROUND_DEATH_Y - run.simulation().bird().y() >= 60,
                "the lift must be worth flying out of, was "
                        + (Playfield.GROUND_DEATH_Y - run.simulation().bird().y()) + " px");
        assertEquals(RunPhase.FLYING, run.phase());

        // And it is really alive: a dozen ticks of doing nothing at all neither crash the bird
        // nor freeze the run (a bare revive gets no kick, so this is pure free fall).
        for (int i = 0; i < 12; i++) {
            assertFalse(run.tick(RunInput.NONE).has(TickFact.Crashed.class),
                    "the revive lasted only " + i + " ticks");
        }
        assertEquals(RunPhase.FLYING, run.phase());
        assertEquals(80.0, ReviveSystem.GROUND_CLEARANCE_PX, 0.0,
                "and the authored clearance is what buys that window");
    }

    /**
     * The lift belongs to the ground, not to every revive: a bird saved in mid-air stays in the
     * column it was in and gets only its velocity kick, exactly like a shield absorb there.
     */
    @Test
    void aReviveInTheAirDoesNotTeleportTheBird() {
        Run run = AbilityRuns.started(AbilityRuns.passive("emergency_recovery", 1));
        run.simulation().bird().setY(550);
        run.simulation().bird().setVy(0);
        AbilityRuns.wall(run);
        TickReport report = run.tick(RunInput.NONE);

        assertTrue(report.has(TickFact.Revived.class));
        assertEquals(550 + 30.0 / 60, run.simulation().bird().y(), 1e-9,
                "where it was hit, plus this tick's gravity step — not lifted to the safe band");
        assertEquals(-405.0, run.simulation().bird().vy(), 1e-9);
    }

    @Test
    void theSecondFallKillsWhenTheReviveIsSpent() {
        Run run = AbilityRuns.started(upgradeReviveOnly());
        boolean crashed = false;
        for (int i = 0; i < 600 && !crashed; i++) {
            crashed = run.tick(RunInput.NONE).has(TickFact.Crashed.class);
        }
        assertTrue(crashed, "one revive, one save");
        assertEquals(1, run.stats().revives());
    }

    @Test
    void theRecoveryAbilityAddsTheKickAndTheLongerWindow() {
        Run run = AbilityRuns.started(AbilityRuns.passive("emergency_recovery", 1));
        assertEquals(1, run.simulation().revive().maxCharges(), "effects: REVIVES +1");
        assertEquals(90, run.simulation().revive().invulnTicks());
        assertEquals(1.0, run.simulation().revive().kickMultiplier(), 0.0);

        AbilityRuns.wall(run);
        TickReport report = run.tick(RunInput.NONE);
        assertTrue(report.has(TickFact.Revived.class));
        assertEquals(-405.0, run.simulation().bird().vy(), 1e-9,
                "the auto-flap kick, not a zeroed velocity");
        assertEquals(90, run.simulation().invulnerableTicks());

        Run maxed = AbilityRuns.started(AbilityRuns.passive("emergency_recovery", 3));
        assertEquals(120, maxed.simulation().revive().invulnTicks());
        assertEquals(1.3, maxed.simulation().revive().kickMultiplier(), 1e-9);
    }

    @Test
    void noReviveZeroesTheStatAndStripsTheAbility() {
        Run run = AbilityRuns.factory().newRun(
                AbilityRuns.config(13, null, List.of("emergency_recovery"))
                        .permanentEffects(List.of(StatModifier.flat(StatId.REVIVES, 1,
                                "upgrade:second_chance_1")))
                        .rules(RuleSet.of(RuleFlag.NO_REVIVE)).build());
        assertEquals(0.0, run.simulation().stats().resolve(StatId.REVIVES), 0.0);
        assertEquals(0, run.simulation().revive().maxCharges());
        assertTrue(run.simulation().abilities().isEmpty());
        assertEquals(List.of("emergency_recovery"), run.simulation().abilities().strippedIds());

        Run started = AbilityRuns.started(run);
        AbilityRuns.wall(started);
        assertTrue(started.tick(RunInput.NONE).has(TickFact.Crashed.class));
    }

    @Test
    void theSafeYOnlyLiftsABirdInsideTheGroundBand() {
        assertEquals(120.0, ReviveSystem.safeY(120), 0.0, "a revive in the air keeps its place");
        assertEquals(Playfield.GROUND_DEATH_Y - ReviveSystem.GROUND_CLEARANCE_PX,
                ReviveSystem.safeY(Playfield.GROUND_DEATH_Y + 10), 1e-9);
    }
}
