package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.run.ReviveSystem;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.ShieldSystem;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * D9: the shield is stat-driven. {@code SHIELD_CHARGES > 0} is the whole condition, so the forge
 * node {@code tempered_shield_1} works with no ability equipped; the {@code shield} ability only
 * configures the absorb.
 */
class ShieldSystemTest {

    /** A run with the {@code tempered_shield_1} effect and no ability at all. */
    private static Run upgradeShieldOnly(int charges) {
        return AbilityRuns.factory().newRun(AbilityRuns.config(11, null, List.of())
                .permanentEffects(List.of(StatModifier.flat(StatId.SHIELD_CHARGES, charges,
                        "upgrade:tempered_shield_1")))
                .build());
    }

    @Test
    void anUpgradeChargeAbsorbsALethalHitWithNoAbilityEquipped() {
        Run run = AbilityRuns.started(upgradeShieldOnly(1));
        assertTrue(run.simulation().abilities().isEmpty(), "no ability, only the node");
        assertEquals(1, run.simulation().shield().maxCharges());
        assertEquals(ShieldSystem.DEFAULT_INVULN_TICKS, run.simulation().shield().invulnTicks());

        AbilityRuns.wall(run);
        TickReport report = run.tick(RunInput.NONE);
        assertTrue(report.has(TickFact.ShieldAbsorbed.class), "the charge absorbed the hit");
        assertFalse(report.has(TickFact.Crashed.class));
        assertEquals(RunPhase.FLYING, run.phase());
        assertEquals(0, run.simulation().shield().charges());
        assertEquals(1, run.simulation().shield().absorbs());
        assertEquals(1, run.stats().shieldAbsorbs(), "and RunStats counts it");
        assertEquals(45, run.simulation().invulnerableTicks());
        assertTrue(run.simulation().isGhosting());
    }

    @Test
    void theSecondHitKillsWhenTheChargesAreSpent() {
        Run run = AbilityRuns.started(upgradeShieldOnly(1));
        AbilityRuns.wall(run);
        run.tick(RunInput.NONE);
        // Fly the invulnerability out well away from the wall, then come back into it.
        run.simulation().obstacles().clear();
        for (int i = 0; i < 60; i++) {
            run.simulation().bird().setY(320);
            run.simulation().bird().setVy(0);
            run.tick(RunInput.NONE);
        }
        assertEquals(0, run.simulation().invulnerableTicks());
        assertFalse(run.simulation().isGhosting(), "clear of everything, solid again");

        AbilityRuns.wall(run);
        TickReport second = run.tick(RunInput.NONE);
        assertTrue(second.has(TickFact.Crashed.class), "one charge, one absorb");
        assertEquals(1, run.stats().shieldAbsorbs());
    }

    @Test
    void theShieldAbilityBringsItsOwnChargeAndConfiguresTheAbsorb() {
        Run level1 = AbilityRuns.started(AbilityRuns.passive("shield", 1));
        assertEquals(1.0, level1.simulation().stats().resolve(StatId.SHIELD_CHARGES), 0.0);
        assertEquals(1, level1.simulation().shield().maxCharges());
        assertEquals(45, level1.simulation().shield().invulnTicks());
        assertEquals(0, level1.simulation().shield().regenEveryGates(), "level 1 never regrows");

        Run level3 = AbilityRuns.started(AbilityRuns.passive("shield", 3));
        assertEquals(60, level3.simulation().shield().invulnTicks());
        assertEquals(10, level3.simulation().shield().regenEveryGates());
    }

    @Test
    void anAbilityChargeAndAnUpgradeChargeStack() {
        Run run = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(11, null, List.of("shield"))
                        .permanentEffects(List.of(StatModifier.flat(StatId.SHIELD_CHARGES, 1,
                                "upgrade:tempered_shield_1")))
                        .build()));
        assertEquals(2, run.simulation().shield().maxCharges(),
                "the ABILITY layer adds to the UPGRADES layer like any other effect");
    }

    @Test
    void regenerationRestoresOneChargePerCadenceAndNeverMore() {
        ShieldSystem shield = new ShieldSystem(2);
        shield.configure(60, 10);
        assertTrue(shield.absorb());
        assertTrue(shield.absorb());
        assertFalse(shield.absorb(), "no charge left");
        assertFalse(shield.onGatePassed(9));
        assertTrue(shield.onGatePassed(10));
        assertEquals(1, shield.charges());
        assertTrue(shield.onGatePassed(20));
        assertEquals(2, shield.charges());
        assertFalse(shield.onGatePassed(30), "never above the charges the run started with");
        assertEquals(2, shield.charges());
        assertEquals(2, shield.regenerated());
    }

    /**
     * The wiring, not the counter: {@code shield.onGatePassed} is called from the simulation on a
     * real passed gate, and a restored charge is announced like any other "ready" (D17), so the
     * HUD pip and the shield cue fire on the one defensive state change that matters most.
     */
    @Test
    void aRealPassedGateRegeneratesTheShieldAndSaysSo() {
        Run run = AbilityRuns.factory().newRun(AbilityRuns.config(7, null, List.of("shield"))
                .abilityLevels(Map.of("shield", 3)).build());
        run.tick(RunInput.FLAP);
        assertEquals(10, run.simulation().shield().regenEveryGates());
        assertTrue(run.simulation().shield().absorb(), "spend the charge the run started with");
        assertEquals(0, run.simulation().shield().charges());

        BotPilot pilot = new BotPilot(BotPilot.Preset.PERFECT, 7);
        boolean announced = false;
        while (run.stats().gatesPassed() < 10 && run.phase() == RunPhase.FLYING) {
            TickReport report = run.tick(pilot.decide(run));
            for (TickFact fact : report.facts()) {
                announced |= fact instanceof TickFact.AbilityReady ready
                        && ShieldSystem.ABILITY_ID.equals(ready.abilityId());
            }
        }
        assertEquals(10, run.stats().gatesPassed(), "the bot had to actually pass ten gates");
        assertEquals(1, run.simulation().shield().charges(), "gate 10 is the cadence");
        assertEquals(1, run.simulation().shield().regenerated());
        assertTrue(announced, "the regenerated charge reached the tick report");
    }

    /**
     * A shield charge does save a bird that dives into the ground, and the lift is what makes the
     * charge worth more than one tick (see {@code Simulation.absorbLethalHit} and the ground-save
     * row in docs/BALANCING.md). It is a deliberate decision, so it is pinned here.
     */
    @Test
    void aChargeAlsoAbsorbsTheGroundAndLiftsTheBirdClearOfIt() {
        Run run = AbilityRuns.started(upgradeShieldOnly(1));
        run.simulation().bird().setY(Playfield.GROUND_DEATH_Y - 1);
        run.simulation().bird().setVy(600);
        TickReport report = run.tick(RunInput.NONE);

        assertTrue(report.has(TickFact.ShieldAbsorbed.class));
        assertFalse(report.has(TickFact.Crashed.class));
        assertEquals(Playfield.GROUND_DEATH_Y - ReviveSystem.GROUND_CLEARANCE_PX,
                run.simulation().bird().y(), 0.0);
        assertEquals(0.0, run.simulation().bird().vy(), 0.0);
        // The lift is the point: the bird has to survive the ground rule of the next ticks too.
        for (int t = 0; t < 15; t++) {
            assertFalse(run.tick(RunInput.NONE).has(TickFact.Crashed.class), "tick " + t);
        }
        assertEquals(RunPhase.FLYING, run.phase());
    }

    @Test
    void aShieldThatNeverRegeneratesIgnoresGates() {
        ShieldSystem shield = new ShieldSystem(1);
        assertTrue(shield.absorb());
        for (int gate = 1; gate <= 100; gate++) {
            assertFalse(shield.onGatePassed(gate));
        }
        assertEquals(0, shield.charges());
    }

    @Test
    void noDefensiveAbilitiesZeroesTheChargesAndStripsTheAbility() {
        Run run = AbilityRuns.factory().newRun(AbilityRuns.config(11, null, List.of("shield"))
                .permanentEffects(List.of(StatModifier.flat(StatId.SHIELD_CHARGES, 2,
                        "upgrade:tempered_shield_1")))
                .rules(RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES))
                .build());
        assertEquals(0.0, run.simulation().stats().resolve(StatId.SHIELD_CHARGES), 0.0,
                "D8: the flag zeroes the stat, upgrade charges included");
        assertEquals(0, run.simulation().shield().maxCharges());
        assertTrue(run.simulation().abilities().isEmpty());
        assertEquals(List.of("shield"), run.simulation().abilities().strippedIds());

        Run started = AbilityRuns.started(run);
        AbilityRuns.wall(started);
        assertTrue(started.tick(RunInput.NONE).has(TickFact.Crashed.class),
                "nothing absorbs under the flag");
    }

    @Test
    void anInnateShieldIsStrippedToo() {
        Run run = AbilityRuns.factory().newRun(AbilityRuns.config(11, null, List.of())
                .birdId("guardian")
                .rules(RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES)).build());
        assertTrue(run.simulation().abilities().isEmpty(),
                "D9: Ironbeak's innate shield is disabled and the UI says so");
        assertEquals(List.of("shield"), run.simulation().abilities().strippedIds());
        assertEquals(0, run.simulation().shield().maxCharges());

        Run allowed = AbilityRuns.factory().newRun(
                AbilityRuns.config(11, null, List.of()).birdId("guardian").build());
        assertEquals(1, allowed.simulation().shield().maxCharges(), "and works without the flag");
        assertEquals(Map.of(), allowed.config().abilityLevels());
        assertEquals(1, allowed.simulation().abilities().instances().size());
    }
}
