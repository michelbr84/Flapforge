package io.github.michelbr84.flapforge.modifier;

import static io.github.michelbr84.flapforge.modifier.ModifierTestData.WEIGHTS;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.card;
import static io.github.michelbr84.flapforge.modifier.ModifierTestData.synergy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.UnknownIdException;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.RandomProvider;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.TickReport;
import io.github.michelbr84.flapforge.gameplay.run.DraftWorld;
import io.github.michelbr84.flapforge.gameplay.run.ModifierDirector;
import io.github.michelbr84.flapforge.gameplay.run.Run;
import io.github.michelbr84.flapforge.gameplay.run.RunConfig;
import io.github.michelbr84.flapforge.gameplay.run.RunInput;
import io.github.michelbr84.flapforge.gameplay.run.RunPhase;
import io.github.michelbr84.flapforge.gameplay.run.RunSetup;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import io.github.michelbr84.flapforge.support.FixedSpawnTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The draft as a run sees it (D11, E7, E17): a scripted pilot flies a fixed corridor of standard
 * gates, and every timing the milestone promises is measured on it — the breather at the
 * scheduled gate, the freeze when the air is clear, the 45-tick hold, the invulnerability on
 * resume, the skip, the forced modifiers and the feature gate.
 *
 * <p>The world comes from {@link FixedSpawnTable}: gates at one height, no random layout, no
 * patterns (those are M7). What is being measured is the director, so nothing else is allowed to
 * vary.
 */
class ModifierDirectorTest {

    /** Gate the test schedule opens its only draft at. */
    private static final int OFFER_GATE = 3;

    /** A pilot that keeps the bird inside a gap centred on its start height. */
    private static RunInput fly(Run run) {
        if (run.phase() == RunPhase.READY) {
            return RunInput.FLAP;
        }
        return run.simulation().bird().y() > Playfield.BIRD_START_Y + 10
                ? RunInput.FLAP : RunInput.NONE;
    }

    private static ModifierCatalog catalog(int... schedule) {
        List<Integer> gates = new ArrayList<>();
        for (int gate : schedule) {
            gates.add(gate);
        }
        return new ModifierCatalog(gates, 3, WEIGHTS,
                List.of(card("alpha", Rarity.COMMON, 2, ModifierTag.SPEED),
                        card("beta", Rarity.COMMON, 2, ModifierTag.RISK),
                        card("gamma", Rarity.RARE, 2, ModifierTag.ECONOMY),
                        card("delta", Rarity.RARE, 2, ModifierTag.ECONOMY)),
                List.of(synergy("rush", ModifierTag.SPEED, ModifierTag.RISK),
                        synergy("purse", ModifierTag.ECONOMY, ModifierTag.ECONOMY)));
    }

    private static Run run(ModifierCatalog catalog, boolean allowOffers, List<String> forced) {
        return run(catalog, allowOffers, forced, RunConfig.builder(42));
    }

    private static Run run(ModifierCatalog catalog, boolean allowOffers, List<String> forced,
            RunConfig.Builder builder) {
        RunConfig config = builder
                .allowOffers(allowOffers)
                .forcedModifiers(forced)
                .build();
        return new Run(config, RunSetup.CLASSIC.withModifiers(catalog), new FixedSpawnTable());
    }

    /** Ticks until the phase changes or the budget runs out, returning the last report. */
    private static TickReport flyUntil(Run run, RunPhase target, int budget) {
        TickReport last = null;
        for (int i = 0; i < budget && run.phase() != target; i++) {
            last = run.tick(fly(run));
        }
        assertEquals(target, run.phase(), "the run never reached " + target);
        return last;
    }

    /**
     * D11's own numbers, against the literals the plan writes down rather than against the
     * constants that carry them: a retune is then a deliberate edit of this test and not a silent
     * one.
     */
    @Test
    void theTimingsAreTheOnesTheDecisionRecordFixes() {
        assertEquals(45, ModifierDirector.RESUME_HOLD_TICKS, "D11: a 45-tick resume hold");
        assertEquals(30, ModifierDirector.RESUME_IFRAMES, "D11: 30 i-frames on the resume");
        assertEquals(3, ModifierDirector.COUNTDOWN_STEPS, "D11/D17: the hold is a 3-2-1");
        assertEquals(1.5, ModifierDirector.BREATHER_INTERVALS, 0.0,
                "D11: the breather defers the next spawn by 1.5 gate intervals");
    }

    @Test
    void theBreatherOpensAtTheScheduledGateAndPushesTheNextSpawnOut() {
        Run run = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(run, RunPhase.BREATHER, 2000);
        assertEquals(OFFER_GATE, run.stats().gatesPassed(),
                "the breather starts on the tick the scheduled gate is passed");
        assertEquals(ModifierDirector.BREATHER_INTERVALS,
                run.simulation().spawner().deferredIntervals(), 1e-9,
                "the next obstacle is pushed out by 1.5 intervals");
        assertEquals(ModifierDirector.State.BREATHER, run.simulation().modifiers().state());
    }

    @Test
    void theDraftFreezesTheRunOnceTheAirIsClearAndTheHoldLastsFortyFiveTicks() {
        Run run = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(run, RunPhase.BREATHER, 2000);
        TickReport opening = flyUntil(run, RunPhase.CHOOSING_MODIFIER, 2000);

        TickFact.ModifierOffered offered =
                opening.first(TickFact.ModifierOffered.class).orElseThrow();
        assertEquals(0, offered.offerIndex());
        assertEquals(3, offered.cardIds().size(), "choicesPerOffer cards");
        assertTrue(run.simulation().isDraftPathClear(),
                "the freeze only happens with nothing on screen ahead of the bird");
        assertNotNull(run.simulation().modifiers().offer());

        // Frozen: the simulation does not tick, so nothing moves and nothing is counted.
        int simTick = run.simulation().tick();
        double y = run.simulation().bird().y();
        int alive = run.stats().ticksAlive();
        double gateX = run.simulation().obstacles().last().x();
        for (int i = 0; i < 20; i++) {
            run.tick(RunInput.NONE);
        }
        assertEquals(RunPhase.CHOOSING_MODIFIER, run.phase(), "no answer, no resume");
        assertEquals(simTick, run.simulation().tick());
        assertEquals(y, run.simulation().bird().y(), 0.0);
        assertEquals(alive, run.stats().ticksAlive());
        assertEquals(gateX, run.simulation().obstacles().last().x(), 0.0);

        String taken = run.simulation().modifiers().offer().cardAt(0).id();
        TickReport chosen = run.tick(RunInput.choose(0));
        assertEquals(RunPhase.RESUME_HOLD, run.phase());
        TickFact.ModifierChosen fact = chosen.first(TickFact.ModifierChosen.class).orElseThrow();
        assertEquals(taken, fact.modifierId());
        assertEquals(1, fact.stacks());
        assertEquals(List.of(taken), run.stats().modifiersTaken());
        assertEquals(1, run.simulation().effects().layer(Layer.MODIFIERS).size(),
                "the card is in the MODIFIERS layer");

        for (int i = 1; i < ModifierDirector.RESUME_HOLD_TICKS; i++) {
            run.tick(RunInput.NONE);
            assertEquals(RunPhase.RESUME_HOLD, run.phase(), "hold tick " + i);
            assertEquals(ModifierDirector.RESUME_HOLD_TICKS - i,
                    run.simulation().modifiers().holdTicksRemaining());
            assertTrue(run.simulation().modifiers().countdown() >= 1);
            assertTrue(run.simulation().modifiers().countdown() <= 3);
        }
        run.tick(RunInput.NONE);
        assertEquals(RunPhase.FLYING, run.phase(), "the 45th hold tick hands the run back");
        assertEquals(ModifierDirector.RESUME_IFRAMES, run.simulation().invulnerableTicks(),
                "and the resume comes with 30 i-frames");
        assertEquals(simTick, run.simulation().tick(), "the whole draft cost the world no tick");
        assertEquals(y, run.simulation().bird().y(), 0.0);
    }

    /**
     * The draft is labelled with the gate it was scheduled for. The spawner has two or three
     * obstacles queued when the breather starts, so the freeze lands a few gates later; the offer
     * still belongs to the schedule entry, which is the number §6 promises and the overlay prints.
     */
    @Test
    void theOfferCarriesTheScheduledGateAndNotTheGateItOpenedAt() {
        Run run = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        assertEquals(OFFER_GATE, run.simulation().modifiers().offer().gate(),
                "the card table names the scheduled gate");
        assertTrue(run.stats().gatesPassed() > OFFER_GATE,
                "and the run really did pass more gates before the air was clear");
    }

    /**
     * An index the table does not have is not an answer (D11): only {@code RunInput.SKIP} means
     * "take nothing", so a stale index from a script or a controller mapping leaves the cards up
     * instead of burning the draft.
     */
    @Test
    void anIndexTheTableDoesNotHaveIsIgnored() {
        Run run = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        int cards = run.simulation().modifiers().offer().size();

        run.tick(RunInput.choose(cards + 7));
        assertEquals(RunPhase.CHOOSING_MODIFIER, run.phase(), "the draft is still waiting");
        assertEquals(0, run.simulation().modifiers().offersSkipped());
        run.tick(RunInput.choose(-3));
        assertEquals(RunPhase.CHOOSING_MODIFIER, run.phase(), "and a negative index is not SKIP");
        assertEquals(0, run.simulation().modifiers().offersSkipped());

        run.tick(RunInput.choose(0));
        assertEquals(RunPhase.RESUME_HOLD, run.phase(), "a real index still answers");
        assertEquals(1, run.stats().modifiersTaken().size());
    }

    /**
     * The breather has to leave a window {@code isDraftPathClear()} can actually see. It widens
     * one spacing, so a tighter {@code GATE_INTERVAL} closes the window: at 128 px the fixed 1.5
     * intervals leave the next obstacle on screen before the last one has cleared the bird, and
     * the run would wait in {@code BREATHER} for the rest of its life with every draft lost.
     */
    @Test
    void aTightCorridorStillOpensItsDraft() {
        RunConfig.Builder tight = RunConfig.builder(42).addPermanentEffect(
                new StatModifier(StatId.GATE_INTERVAL, StatOp.MULTIPLY, 0.8, "test"));
        Run run = run(catalog(OFFER_GATE), true, List.of(), tight);
        assertEquals(128.0, run.simulation().stats().resolve(StatId.GATE_INTERVAL), 1e-9,
                "the corridor really is tighter than the 1.5-interval breather covers");

        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        assertEquals(1, run.simulation().modifiers().offersOpened(),
                "the breather asked for as much clearance as the corridor needed");
    }

    /**
     * The bounded fallback: a breather that never sees clear air widens the spacing again rather
     * than waiting forever.
     */
    @Test
    void aBreatherThatNeverClearsWidensTheSpacingAgain() {
        FakeWorld world = new FakeWorld();
        ModifierDirector director = new ModifierDirector(catalog(OFFER_GATE), true, List.of(),
                new EffectStack(), world, new RandomProvider(1).stream(RandomProvider.OFFERS));
        List<TickFact> facts = new ArrayList<>();
        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.BREATHER_INTERVALS, world.deferred, 1e-9);

        for (int i = 0; i < ModifierDirector.BREATHER_RETRY_TICKS; i++) {
            director.afterTick(OFFER_GATE, false, facts);
        }
        assertEquals(2 * ModifierDirector.BREATHER_INTERVALS, world.deferred, 1e-9,
                "the run asked the world for another window");
        assertEquals(ModifierDirector.State.BREATHER, director.state());
        assertTrue(facts.isEmpty(), "and nothing was offered in air that is not clear");
    }

    @Test
    void skippingTakesNothingAndStillRunsTheHold() {
        Run run = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        TickReport skipped = run.tick(RunInput.skip());
        assertEquals(RunPhase.RESUME_HOLD, run.phase());
        assertTrue(skipped.has(TickFact.ModifierSkipped.class));
        assertFalse(skipped.has(TickFact.ModifierChosen.class));
        assertEquals(List.of(), run.stats().modifiersTaken());
        assertEquals(List.of(), run.simulation().effects().layer(Layer.MODIFIERS));
        assertEquals(1, run.simulation().modifiers().offersSkipped());
        assertNull(run.simulation().modifiers().offer());
        for (int i = 1; i <= ModifierDirector.RESUME_HOLD_TICKS; i++) {
            run.tick(RunInput.NONE);
        }
        assertEquals(RunPhase.FLYING, run.phase());
    }

    @Test
    void twoScheduledGatesOpenTwoDrafts() {
        Run run = run(catalog(OFFER_GATE, OFFER_GATE + 2), true, List.of());
        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        run.tick(RunInput.choose(0));
        for (int i = 1; i <= ModifierDirector.RESUME_HOLD_TICKS; i++) {
            run.tick(RunInput.NONE);
        }
        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        assertEquals(1, run.simulation().modifiers().offer().index());
        assertTrue(run.stats().gatesPassed() >= OFFER_GATE + 2);
        run.tick(RunInput.choose(0));
        assertEquals(2, run.stats().modifiersTaken().size());
    }

    @Test
    void aLockedFeatureNeverOpensADraft() {
        Run run = run(catalog(OFFER_GATE), false, List.of());
        for (int i = 0; i < 3000 && !run.isFinished(); i++) {
            run.tick(fly(run));
            assertTrue(run.phase() == RunPhase.FLYING || run.phase() == RunPhase.READY,
                    () -> "a run without feature:modifiers stayed in " + run.phase());
        }
        assertTrue(run.stats().gatesPassed() > OFFER_GATE, "and it flew well past the schedule");
        assertEquals(0, run.simulation().modifiers().offersOpened());
        assertFalse(run.simulation().modifiers().isActive(),
                "nothing about the draft is part of the run, so nothing is folded into its hash");
    }

    @Test
    void forcedModifiersArePreTakenAndCountForSynergies() {
        Run run = run(catalog(OFFER_GATE), false, List.of("alpha", "beta"));
        assertEquals(List.of("alpha", "beta"), run.stats().modifiersTaken(),
                "a challenge's forced cards are taken before the first tick");
        assertEquals(List.of("rush"), run.stats().synergiesActivated(),
                "SPEED + RISK across two entries completes the set bonus at run start");
        assertEquals(List.of("rush"), run.simulation().modifiers().activeSynergies());
        assertEquals(2, run.simulation().effects().layer(Layer.MODIFIERS).size());
        assertEquals(1, run.simulation().effects().layer(Layer.MOD_SYNERGY).size());
        assertTrue(run.simulation().modifiers().isActive(),
                "a run that took something is a run the draft state belongs to");
    }

    @Test
    void stacksAddUpAndDriveTheStreakBonus() {
        ModifierDef bounty = ModifierTestData.bounty("bounty", 10);
        ModifierCatalog catalog = new ModifierCatalog(List.of(OFFER_GATE), 1, WEIGHTS,
                List.of(bounty), List.of());
        Run run = run(catalog, false, List.of("bounty"));
        assertEquals(10, run.stats().modifierStreakCoins(),
                "the streak term of the coin formula is fed by the taken cards (E32.a)");
    }

    /**
     * E7: no breather while a boss is pending or active. Bosses are M8, so the gate is exercised
     * against the seam the director asks — which is exactly how M8 will switch it on.
     */
    @Test
    void aPendingBossHoldsTheDraftBack() {
        FakeWorld world = new FakeWorld();
        world.bossPending = true;
        ModifierDirector director = new ModifierDirector(catalog(OFFER_GATE), true, List.of(),
                new EffectStack(), world, new RandomProvider(1).stream(RandomProvider.OFFERS));
        List<TickFact> facts = new ArrayList<>();
        director.afterTick(OFFER_GATE + 5, false, facts);
        assertEquals(ModifierDirector.State.IDLE, director.state(), "no breather during a boss");
        assertEquals(0, world.deferred, 1e-9);

        world.bossPending = false;
        director.afterTick(OFFER_GATE + 5, false, facts);
        assertEquals(ModifierDirector.State.BREATHER, director.state(),
                "the schedule entry was not consumed, so it fires the moment the boss is gone");
        assertEquals(ModifierDirector.BREATHER_INTERVALS, world.deferred, 1e-9);
    }

    /** D11: the offer never opens on a tick that reported a collision. */
    @Test
    void aCollisionTickNeverOpensTheOffer() {
        FakeWorld world = new FakeWorld();
        ModifierDirector director = new ModifierDirector(catalog(OFFER_GATE), true, List.of(),
                new EffectStack(), world, new RandomProvider(1).stream(RandomProvider.OFFERS));
        List<TickFact> facts = new ArrayList<>();
        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.State.BREATHER, director.state());

        world.pathClear = true;
        director.afterTick(OFFER_GATE, true, facts);
        assertEquals(ModifierDirector.State.BREATHER, director.state(),
                "a shield absorb and a draft must not land on the same frame");
        assertTrue(facts.isEmpty());

        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.State.CHOOSING, director.state());
        assertEquals(1, facts.size());
    }

    /**
     * E7 again, on the other side of the breather: a boss that starts while the run is already
     * waiting suppresses spawning (M8), which clears the air. Freezing the run inside the warning
     * banner is exactly what the errata forbids, and the schedule entry stays unconsumed.
     */
    @Test
    void aBossThatStartsDuringTheBreatherHoldsTheOfferBack() {
        FakeWorld world = new FakeWorld();
        ModifierDirector director = new ModifierDirector(catalog(OFFER_GATE), true, List.of(),
                new EffectStack(), world, new RandomProvider(1).stream(RandomProvider.OFFERS));
        List<TickFact> facts = new ArrayList<>();
        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.State.BREATHER, director.state());

        world.pathClear = true;
        world.bossActive = true;
        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.State.BREATHER, director.state(),
                "the suppressed spawning of a boss warning is not clear air");
        assertTrue(facts.isEmpty());
        assertEquals(0, director.nextOfferIndex(), "the schedule entry is untouched");

        world.bossActive = false;
        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.State.CHOOSING, director.state(),
                "and the draft opens once the encounter is over");
    }

    // ------------------------------------------------------- forced modifiers (D11, challenges)

    /** A forced id that resolves to nothing is a broken reference, not a locked card. */
    @Test
    void anUnknownForcedModifierIsRejected() {
        UnknownIdException e = assertThrows(UnknownIdException.class,
                () -> run(catalog(OFFER_GATE), false, List.of("alpha", "no_such_card")));
        assertEquals("modifier", e.kind());
        assertEquals("no_such_card", e.id());
    }

    /** {@code maxStacks} caps the whole run, and the forced path is not a way around it. */
    @Test
    void forcedModifiersRespectMaxStacks() {
        Run run = run(catalog(OFFER_GATE), false, List.of("alpha", "alpha", "alpha", "alpha"));
        assertEquals(Map.of("alpha", 2), run.simulation().modifiers().stacks(),
                "alpha caps at two stacks however often it is forced");
        assertEquals(2, run.simulation().effects().layer(Layer.MODIFIERS).size(),
                "and the layer carries two copies of its effects, not four");
    }

    /** Two cards that exclude each other are never held together, forced or drafted. */
    @Test
    void forcedModifiersRespectExcludes() {
        ModifierCatalog catalog = new ModifierCatalog(List.of(OFFER_GATE), 3, WEIGHTS,
                List.of(ModifierTestData.excluding("light", Rarity.COMMON, 1, "glass"),
                        card("glass", Rarity.COMMON, 1, ModifierTag.RISK)),
                List.of());
        Run run = run(catalog, false, List.of("light", "glass"));
        assertEquals(List.of("light"), run.stats().modifiersTaken(),
                "the second half of an exclusion is dropped, not held beside the first");
    }

    /** A card the run's rules forbid is not forced onto it either (E12's authored half). */
    @Test
    void forcedModifiersRespectTheRulesTheRunStartsWith() {
        ModifierDef bounty = ModifierTestData.bounty("bounty", 10);
        ModifierCatalog catalog = new ModifierCatalog(List.of(OFFER_GATE), 1, WEIGHTS,
                List.of(bounty, card("plain", Rarity.COMMON, 1, ModifierTag.GREED)), List.of());
        FakeWorld world = new FakeWorld();
        world.rules = RuleSet.of(RuleFlag.NO_COINS);
        ModifierDirector director = new ModifierDirector(catalog, false,
                List.of("bounty", "plain"), new EffectStack(), world,
                new RandomProvider(1).stream(RandomProvider.OFFERS));
        assertEquals(List.of("plain"), director.taken(),
                "a coin bounty is not forced onto a run that has no coins");
        assertEquals(0, director.streakBonusCoins());
    }

    /** A draft with nothing eligible is skipped outright rather than frozen on an empty table. */
    @Test
    void anEmptyOfferIsSkippedWithoutFreezing() {
        FakeWorld world = new FakeWorld();
        world.pathClear = true;
        ModifierCatalog catalog = new ModifierCatalog(List.of(OFFER_GATE), 3, WEIGHTS,
                List.of(card("only", Rarity.COMMON, 1, ModifierTag.GREED)), List.of());
        ModifierDirector director = new ModifierDirector(catalog, true, List.of("only"),
                new EffectStack(), world, new RandomProvider(1).stream(RandomProvider.OFFERS));
        List<TickFact> facts = new ArrayList<>();
        director.afterTick(OFFER_GATE, false, facts);
        director.afterTick(OFFER_GATE, false, facts);
        assertEquals(ModifierDirector.State.IDLE, director.state());
        assertEquals(1, director.offersSkipped());
        assertEquals(0, director.offersOpened());
        assertEquals(1, director.nextOfferIndex(), "the schedule entry is used up all the same");
    }

    /** The draft state is part of the run hash, so two answers are two different runs (D12). */
    @Test
    void theDraftStateIsFoldedIntoTheStateHash() {
        Run picked = run(catalog(OFFER_GATE), true, List.of());
        Run skipped = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(picked, RunPhase.CHOOSING_MODIFIER, 4000);
        flyUntil(skipped, RunPhase.CHOOSING_MODIFIER, 4000);
        assertEquals(picked.simulation().stateHash(), skipped.simulation().stateHash(),
                "same seed, same corridor, same cards on the table");
        picked.tick(RunInput.choose(0));
        skipped.tick(RunInput.skip());
        assertTrue(picked.simulation().stateHash() != skipped.simulation().stateHash(),
                "and the answer is visible in the hash");
    }

    /**
     * <em>Which</em> card was taken is part of the hash, and not merely <em>whether</em> one was.
     * Two runs that both answer the same draft, on the same tick, differing only in the card they
     * point at, have to hash differently — otherwise a refactor could drop the taken multiset from
     * the fold and every test would stay green.
     */
    @Test
    void twoDifferentCardsFromOneTableAreTwoDifferentHashes() {
        Run first = run(catalog(OFFER_GATE), true, List.of());
        Run second = run(catalog(OFFER_GATE), true, List.of());
        flyUntil(first, RunPhase.CHOOSING_MODIFIER, 4000);
        flyUntil(second, RunPhase.CHOOSING_MODIFIER, 4000);
        first.tick(RunInput.choose(0));
        second.tick(RunInput.choose(1));
        assertNotEquals(first.stats().modifiersTaken(), second.stats().modifiersTaken(),
                "the two runs really did take different cards");
        assertNotEquals(first.simulation().stateHash(), second.simulation().stateHash(),
                "and the card taken is what tells the two runs apart");
    }

    /**
     * The active set bonuses are folded too. The two directors hold exactly the same cards, so
     * only the synergy list can separate them.
     */
    @Test
    void theActiveSynergiesAreFoldedIntoTheStateHash() {
        List<ModifierDef> cards = List.of(card("gamma", Rarity.RARE, 2, ModifierTag.ECONOMY),
                card("delta", Rarity.RARE, 2, ModifierTag.ECONOMY));
        List<String> pair = List.of("gamma", "delta");
        ModifierDirector withBonus = new ModifierDirector(
                new ModifierCatalog(List.of(OFFER_GATE), 2, WEIGHTS, cards,
                        List.of(synergy("purse", ModifierTag.ECONOMY, ModifierTag.ECONOMY))),
                false, pair, new EffectStack(), new FakeWorld(),
                new RandomProvider(1).stream(RandomProvider.OFFERS));
        ModifierDirector without = new ModifierDirector(
                new ModifierCatalog(List.of(OFFER_GATE), 2, WEIGHTS, cards, List.of()),
                false, pair, new EffectStack(), new FakeWorld(),
                new RandomProvider(1).stream(RandomProvider.OFFERS));

        assertEquals(List.of("purse"), withBonus.activeSynergies());
        assertEquals(List.of(), without.activeSynergies());
        assertEquals(withBonus.stacks(), without.stacks(), "the builds are identical");
        assertNotEquals(withBonus.hashState(0L), without.hashState(0L),
                "so the set bonus is the only thing the hash can be separating");
    }

    /** A card that raises SHIELD_CHARGES mid-run hands the charge over (the M5 limit, closed). */
    @Test
    void aDraftedShieldIsAShieldTheBirdActuallyHas() {
        ModifierDef shieldCard = ModifierTestData.touching("shieldy", Rarity.RARE,
                StatId.SHIELD_CHARGES);
        ModifierCatalog catalog = new ModifierCatalog(List.of(OFFER_GATE), 1, WEIGHTS,
                List.of(shieldCard), List.of());
        Run run = run(catalog, true, List.of());
        assertEquals(0, run.simulation().shield().maxCharges());
        flyUntil(run, RunPhase.CHOOSING_MODIFIER, 4000);
        run.tick(RunInput.choose(0));
        assertEquals(1, run.simulation().shield().maxCharges());
        assertEquals(1, run.simulation().shield().charges());
        assertEquals(1.0, run.simulation().stats().resolve(StatId.SHIELD_CHARGES), 1e-9);
    }

    /** A {@link DraftWorld} that answers whatever the test needs it to. */
    private static final class FakeWorld implements DraftWorld {
        private boolean pathClear;
        private boolean bossPending;
        private boolean bossActive;
        private double deferred;
        private int iFrames;
        private RuleSet rules = RuleSet.EMPTY;

        @Override
        public RuleSet rules() {
            return rules;
        }

        @Override
        public boolean isDraftPathClear() {
            return pathClear;
        }

        @Override
        public void deferSpawn(double intervals) {
            deferred += intervals;
        }

        @Override
        public void grantIFrames(int ticks) {
            iFrames = ticks;
        }

        @Override
        public void addRules(RuleSet extra) {
            rules = rules.union(extra);
        }

        @Override
        public void refreshDefensiveCharges() {
            // The fake has no charges to refresh.
        }

        @Override
        public boolean bossPending() {
            return bossPending;
        }

        @Override
        public boolean bossActive() {
            return bossActive;
        }
    }
}
