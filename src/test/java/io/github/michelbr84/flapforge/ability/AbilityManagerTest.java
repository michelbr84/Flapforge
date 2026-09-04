package io.github.michelbr84.flapforge.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityLevelDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.harness.BotPilot;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Timers, charges, the loadout and the E3 caps (D9, E3). */
class AbilityManagerTest {

    private static AbilityInstance activeOf(Run run) {
        return run.simulation().abilities().active();
    }

    @Test
    void cooldownAndDurationAreScaledByTheAbilityMultipliers() {
        Run plain = AbilityRuns.started(AbilityRuns.active("dash"));
        plain.tick(AbilityRuns.useAbility());
        AbilityInstance dash = activeOf(plain);
        assertEquals(20, dash.durationRemaining(), "the authored duration, mult 1.0");
        assertEquals(600, dash.cooldownRemaining(), "the authored cooldown, mult 1.0");

        // Oracle: ABILITY_DURATION_MULT x1.3, ABILITY_COOLDOWN_MULT x1.4 (birds.json).
        Run mystic = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(7, "dash", List.of()).birdId("mystic").build()));
        mystic.tick(AbilityRuns.useAbility());
        AbilityInstance scaled = activeOf(mystic);
        assertEquals(26, scaled.durationRemaining(), "20 x 1.3");
        assertEquals(840, scaled.cooldownRemaining(), "600 x 1.4");
    }

    @Test
    void aScaledTickCountNeverRoundsDownToNothing() {
        assertEquals(0, AbilityInstance.scale(0, 3), "a passive stays timerless");
        assertEquals(1, AbilityInstance.scale(1, 0.25), "0.25 ticks would delete the burst");
        assertEquals(5, AbilityInstance.scale(20, 0.25));
        assertEquals(60, AbilityInstance.scale(20, 3));
    }

    @Test
    void activationIsRefusedWhileTheCooldownRuns() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash"));
        TickReport first = run.tick(AbilityRuns.useAbility());
        assertEquals(1, first.count(TickFact.AbilityActivated.class));
        AbilityInstance dash = activeOf(run);
        assertFalse(dash.isReady(), "the burst is running");

        TickReport second = run.tick(AbilityRuns.useAbility());
        assertEquals(0, second.count(TickFact.AbilityActivated.class), "no double activation");
        assertEquals(1, dash.activations());

        AbilityRuns.idle(run, 30);
        assertFalse(dash.isActive(), "the 20-tick burst is over");
        assertFalse(dash.isReady(), "but the 600-tick cooldown is not");
        assertEquals(0, run.tick(AbilityRuns.useAbility())
                .count(TickFact.AbilityActivated.class));
        assertEquals(1, dash.activations(), "a refused activation costs nothing");
        assertEquals(1, run.stats().abilitiesUsed().getOrDefault("dash", 0),
                "and RunStats counts one use");
    }

    @Test
    void aCooldownThatElapsesReportsTheAbilityReady() {
        Run run = AbilityRuns.started(AbilityRuns.active("dash"));
        run.tick(AbilityRuns.useAbility());
        AbilityInstance dash = activeOf(run);
        boolean ready = false;
        for (int i = 0; i < 700 && !ready; i++) {
            ready = run.tick(RunInput.NONE).has(TickFact.AbilityReady.class);
            if (!ready) {
                // the bird must survive long enough to see the cooldown out
                run.simulation().bird().setY(320);
                run.simulation().bird().setVy(0);
            }
        }
        assertTrue(ready, "the cooldown must report the ability ready exactly once");
        assertTrue(dash.isReady());
    }

    @Test
    void chargesGateTheDoubleFlapAndComeBackWithTheGates() {
        Run run = AbilityRuns.started(AbilityRuns.active("double_flap"));
        AbilityInstance flap = activeOf(run);
        assertEquals(2, flap.maxCharges());
        assertEquals(5, flap.rechargeEveryGates());

        assertEquals(1, run.tick(AbilityRuns.useAbility())
                .count(TickFact.AbilityActivated.class));
        assertEquals(1, flap.charges());
        assertEquals(1, run.tick(AbilityRuns.useAbility())
                .count(TickFact.AbilityActivated.class));
        assertEquals(0, flap.charges());
        assertFalse(flap.isReady(), "no charge left");
        assertEquals(0, run.tick(AbilityRuns.useAbility())
                .count(TickFact.AbilityActivated.class));

        assertFalse(flap.recharge(4), "not on the cadence");
        assertTrue(flap.recharge(5), "one charge back every five gates");
        assertEquals(1, flap.charges());
        assertFalse(flap.recharge(6));
        assertTrue(flap.recharge(10));
        assertEquals(2, flap.charges());
        assertFalse(flap.recharge(15), "never above the maximum");
        assertEquals(2, flap.charges());
    }

    /**
     * The wiring, not the counter: every other charge test calls {@code recharge(n)} by hand, so
     * cutting the {@code abilities.onGatePassed} call out of {@code Simulation.tick} used to leave
     * the whole suite green while the double flap silently never got a charge back.
     */
    @Test
    void aRealPassedGateIsWhatGivesTheChargeBack() {
        Run run = AbilityRuns.factory().newRun(AbilityRuns.config(7, "double_flap", List.of())
                .build());
        run.tick(RunInput.FLAP);
        AbilityInstance flap = activeOf(run);
        run.tick(AbilityRuns.useAbility());
        run.tick(AbilityRuns.useAbility());
        assertEquals(0, flap.charges(), "both charges spent");

        BotPilot pilot = new BotPilot(BotPilot.Preset.PERFECT, 7);
        while (run.stats().gatesPassed() < 5 && run.phase() == RunPhase.FLYING) {
            run.tick(pilot.decide(run));
        }
        assertEquals(5, run.stats().gatesPassed(), "the bot had to actually pass five gates");
        assertEquals(1, flap.charges(), "gate 5 is the cadence");
    }

    /**
     * "Ready" is a transition of {@link AbilityInstance#isReady()}, not the cooldown edge: no
     * shipped level has a duration longer than its cooldown, but one that did would become usable
     * when the duration ended, on a tick the cooldown branch no longer runs — and the HUD ring and
     * the audio cue would never fire.
     */
    @Test
    void anAbilityWhoseDurationOutlastsItsCooldownStillReportsItselfReady() {
        AbilityDef def = new AbilityDef("long_window", AbilityKind.ACTIVE, "invulnerability",
                List.of(), List.of(new AbilityLevelDef(2, 5, Map.of(), 0)), List.of(),
                UnlockConditionDef.DEFAULT);
        AbilityInstance instance = new AbilityInstance(def, 1,
                BehaviorRegistry.DEFAULT.create("invulnerability"));
        assertTrue(instance.activate(AbilityRuns.started(AbilityRuns.active("dash"))
                .simulation().stats()));

        assertFalse(instance.advance().ready(), "duration 4, cooldown 1");
        assertFalse(instance.advance().ready(), "duration 3, cooldown 0 — still running");
        assertFalse(instance.advance().ready());
        assertFalse(instance.advance().ready());
        assertTrue(instance.advance().ready(), "the duration ran out: usable again");
        assertTrue(instance.isReady());
        assertFalse(instance.advance().ready(), "and it is announced exactly once");
    }

    @Test
    void theLoadoutTakesOneActiveAndTheBirdsPassiveSlots() {
        List<AbilityDef> two = AbilityRuns.factory().loadout(
                AbilityRuns.config(1, "dash", List.of("shield", "coin_magnet",
                        "emergency_recovery")).build());
        assertEquals(List.of("dash", "shield", "coin_magnet"), ids(two),
                "the classic bird has two passive slots");

        List<AbilityDef> three = AbilityRuns.factory().loadout(
                AbilityRuns.config(1, "dash", List.of("shield", "coin_magnet",
                        "emergency_recovery")).passiveSlotBonus(1).build());
        assertEquals(List.of("dash", "shield", "coin_magnet", "emergency_recovery"), ids(three),
                "E3: ability_scholar_1 grants one more slot");
    }

    @Test
    void aPassiveInTheActiveSlotIsIgnoredAndSoIsAnUnknownId() {
        List<AbilityDef> loadout = AbilityRuns.factory().loadout(
                AbilityRuns.config(1, "shield", List.of("nope", "dash", "coin_magnet")).build());
        assertEquals(List.of("coin_magnet"), ids(loadout),
                "a passive is not an active, an active is not a passive, and 'nope' is nothing");
    }

    @Test
    void innatePassivesAreEquippedBesideTheChosenOnes() {
        List<AbilityDef> loadout = AbilityRuns.factory().loadout(
                AbilityRuns.config(1, "dash", List.of("coin_magnet", "emergency_recovery"))
                        .birdId("guardian").build());
        assertEquals(List.of("dash", "coin_magnet", "emergency_recovery", "shield"), ids(loadout),
                "Ironbeak's innate shield needs no unlock and costs no slot");
    }

    @Test
    void theOwnedLevelIsClampedToTheLevelsTheAbilityShips() {
        Run run = AbilityRuns.factory().newRun(AbilityRuns.config(3, "dash", List.of())
                .abilityLevels(Map.of("dash", 9)).build());
        assertEquals(3, activeOf(run).level(), "E3: never past the levels abilities.json ships");
        assertEquals(32, activeOf(run).levelDef().durationTicks());

        Run zero = AbilityRuns.factory().newRun(AbilityRuns.config(3, "dash", List.of())
                .abilityLevels(Map.of("dash", 0)).build());
        assertEquals(1, activeOf(zero).level(), "level 1 comes with the unlock");
    }

    @Test
    void theAbilityLayerCarriesPassivesAlwaysAndActivesOnlyWhileTheyRun() {
        Run run = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(5, "dash", List.of("coin_magnet")).build()));
        assertEquals(90.0, run.simulation().stats()
                .resolve(StatId.MAGNET_RADIUS), 0.0,
                "the passive is on from the first tick");
        assertEquals(120.0, run.simulation().stats()
                .resolve(StatId.SCROLL_SPEED), 0.0);

        run.tick(AbilityRuns.useAbility());
        assertEquals(300.0, run.simulation().stats()
                .resolve(StatId.SCROLL_SPEED), 0.0,
                "120 x 2.5 while the burst runs");
        AbilityRuns.idle(run, 25);
        assertEquals(120.0, run.simulation().stats()
                .resolve(StatId.SCROLL_SPEED), 0.0,
                "and back to normal when it ends");
    }

    @Test
    void anEmptyLoadoutRoutesNothing() {
        Run run = AbilityRuns.started(AbilityRuns.factory().newRun(
                AbilityRuns.config(5, null, List.of()).build()));
        AbilityManager abilities = run.simulation().abilities();
        assertTrue(abilities.isEmpty());
        assertNull(abilities.active());
        assertFalse(abilities.hasReadyActive());
        assertFalse(run.simulation().hasRunSystems(),
                "no ability, no shield charge and no revive: nothing to fold into the hash");
        assertTrue(run.tick(AbilityRuns.useAbility()).facts().stream()
                .noneMatch(f -> f instanceof TickFact.AbilityActivated));
    }

    @Test
    void rulesStripTaggedAbilitiesFromTheLoadout() {
        Run noDefence = AbilityRuns.factory().newRun(
                AbilityRuns.config(5, "invulnerability", List.of("shield", "coin_magnet"))
                        .rules(RuleSet.of(RuleFlag.NO_DEFENSIVE_ABILITIES)).build());
        AbilityManager abilities = noDefence.simulation().abilities();
        assertEquals(List.of("coin_magnet"), instanceIds(abilities));
        assertEquals(List.of("invulnerability", "shield"), abilities.strippedIds(),
                "the UI needs to know what the run took away");
        assertNull(abilities.active());

        Run noRevive = AbilityRuns.factory().newRun(
                AbilityRuns.config(5, "dash", List.of("emergency_recovery"))
                        .rules(RuleSet.of(RuleFlag.NO_REVIVE)).build());
        assertEquals(List.of("dash"), instanceIds(noRevive.simulation().abilities()));
        assertEquals(List.of("emergency_recovery"),
                noRevive.simulation().abilities().strippedIds());
    }

    @Test
    void everyShippedBehaviorIdIsImplemented() {
        assertEquals(8, BehaviorRegistry.DEFAULT.size());
        for (AbilityDef def : AbilityRuns.content().abilities()) {
            assertTrue(BehaviorRegistry.DEFAULT.contains(def.behavior()), def.behavior());
            assertNotNull(BehaviorRegistry.DEFAULT.create(def.behavior()));
        }
    }

    private static List<String> ids(List<AbilityDef> defs) {
        return defs.stream().map(AbilityDef::id).toList();
    }

    private static List<String> instanceIds(AbilityManager manager) {
        return manager.instances().stream().map(AbilityInstance::id).toList();
    }
}
