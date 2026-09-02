package io.github.michelbr84.flapforge.gameplay.run;

import io.github.michelbr84.flapforge.content.UnknownIdException;
import io.github.michelbr84.flapforge.content.defs.ModifierDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import io.github.michelbr84.flapforge.modifier.ModifierCatalog;
import io.github.michelbr84.flapforge.modifier.ModifierOffer;
import io.github.michelbr84.flapforge.modifier.ModifierPool;
import io.github.michelbr84.flapforge.modifier.SynergyResolver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * The mid-run draft (D11, D27): the breather, the offer, the choice, the countdown, and the two
 * stat layers a build lives in.
 *
 * <p>The sequence for one scheduled gate is:
 * <ol>
 *   <li><b>BREATHER</b> — {@code gatesPassed} reached the next entry of {@code offerSchedule},
 *       offers are allowed and no boss is pending or active (E7). The next obstacle is pushed out
 *       by {@value #BREATHER_INTERVALS} gate intervals — or by whatever more the world says a
 *       clear window needs ({@link DraftWorld#clearanceIntervals()}) — and the run keeps
 *       flying;</li>
 *   <li><b>CHOOSING</b> — the air ahead of the bird is empty
 *       ({@link DraftWorld#isDraftPathClear()}), so the run freezes and the cards go up. Never on
 *       a tick that reported a collision: a shield absorb and a draft opening on the same frame
 *       would be two overlays fighting over one moment; and never while a boss is pending or
 *       active either, because a boss warning suppresses spawning and the empty air it leaves is
 *       not the breather's (E7). The draft is labelled with the schedule entry rather than with
 *       the gate the corridor cleared at, which is two or three gates later;</li>
 *   <li><b>HOLD</b> — a card was taken or the draft was skipped. The bird and the obstacles stay
 *       frozen for {@value #RESUME_HOLD_TICKS} ticks while the UI counts 3-2-1, and the resume
 *       grants {@value #RESUME_IFRAMES} invulnerability ticks so the first tick back is never the
 *       one that kills.</li>
 * </ol>
 *
 * <p>An offer with nothing eligible in it is skipped outright (E12): the schedule entry is
 * consumed, no freeze happens and the run carries on.
 *
 * <p>Forced modifiers (a challenge, the daily) are taken before the first tick and are entries
 * like any other, so they count for synergies (D11).
 *
 * <p><b>Determinism.</b> The draw is the only thing here that touches randomness and it uses the
 * {@code offers} stream alone, so however the player picks, the spawn decisions of the run are
 * the same (E32.d). A run whose catalogue is empty and whose offers are off never touches the
 * stream, never changes a layer and folds nothing into the state hash, which is what keeps the
 * published {@code --headless-run} hash where M5 left it.
 */
public final class ModifierDirector {

    /** Gate intervals the breather pushes the next obstacle out by (D11). */
    public static final double BREATHER_INTERVALS = 1.5;

    /** Frozen ticks between the choice and the resume (D11). */
    public static final int RESUME_HOLD_TICKS = 45;

    /** Invulnerability ticks granted when the run resumes (D11). */
    public static final int RESUME_IFRAMES = 30;

    /** Steps of the 3-2-1 the hold is rendered as. */
    public static final int COUNTDOWN_STEPS = 3;

    /**
     * Ticks a breather may wait for clear air before it widens the spacing again.
     *
     * <p>The deferral opens one window; if the run misses it — a collision on exactly those ticks,
     * a world that moves obstacles into the gap — nothing would ever reopen it and the run would
     * stay in {@code BREATHER} with every remaining draft lost. Ten seconds is several times the
     * few hundred ticks a shipped breather takes to clear, so this only ever fires when something
     * went wrong.
     */
    public static final int BREATHER_RETRY_TICKS = 600;

    private static final long HASH_SEED = MathUtil.fnv1a64("flapforge-draft");

    /** Where the draft is between two offers. */
    public enum State {
        /** Nothing is happening; the run flies normally. */
        IDLE,
        /** The next obstacle was pushed out and the run is waiting for clear air. */
        BREATHER,
        /** The cards are on the table and the simulation is frozen. */
        CHOOSING,
        /** The countdown before the run resumes. */
        HOLD
    }

    private final ModifierCatalog catalog;
    private final ModifierPool pool;
    private final SynergyResolver synergies;
    private final EffectStack stack;
    private final DraftWorld world;
    private final boolean allowOffers;
    private final Map<String, Integer> stacks = new LinkedHashMap<>();
    private final List<ModifierDef> entries = new ArrayList<>();
    private State state = State.IDLE;
    private int nextOfferIndex;
    private ModifierOffer offer;
    private int holdTicks;
    private int breatherTicks;
    private int offersOpened;
    private int offersSkipped;
    private long streakBonusCoins;

    /**
     * Creates the director of one run.
     *
     * @param catalog the cards and set bonuses this run may see
     * @param allowOffers whether drafts open at all (D11: {@code feature:modifiers} is unlocked
     *     and the challenge does not forbid them)
     * @param forcedModifiers ids pre-taken before the first tick (challenge, daily)
     * @param stack the run's effect stack; the director owns {@code MODIFIERS} and
     *     {@code MOD_SYNERGY}
     * @param world the run being drafted in
     * @param offers the run's {@code offers} random stream
     */
    public ModifierDirector(ModifierCatalog catalog, boolean allowOffers,
            List<String> forcedModifiers, EffectStack stack, DraftWorld world, Random offers) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.stack = Objects.requireNonNull(stack, "stack");
        this.world = Objects.requireNonNull(world, "world");
        this.allowOffers = allowOffers;
        this.pool = new ModifierPool(catalog, world, offers);
        this.synergies = new SynergyResolver(catalog.synergies());
        start(forcedModifiers);
    }

    /**
     * Takes the forced modifiers of the run before it starts (D11). No facts: nothing has ticked
     * yet, so {@code Run} reads {@link #taken()} and {@link #activeSynergies()} to seed its stats
     * instead.
     *
     * <p>Forced cards come from a challenge or the daily rather than from a draft, so nothing has
     * filtered them: the same authored rules the pool enforces are enforced here, one card at a
     * time and in list order. {@code maxStacks} is a cap on the whole run and not only on the
     * draft; a card the run's rules forbid ({@code requiresFlagsAbsent}) is not taken, because
     * taking it would push effects the pipeline zeroes anyway; and a card excluded by one already
     * taken is dropped rather than held beside it. The validator rejects all three in
     * {@code challenges.json}, so a card dropped here means content that never passed
     * {@code contentCheck} — but a run is not the place to find that out, so it drops quietly.
     *
     * @param forcedModifiers the ids
     * @throws UnknownIdException when an id is in no catalogue this run can see; the run source
     *     decides what is forced and {@code RunFactory} puts every forced id into the catalogue
     *     whether the profile owns it or not, so an id that resolves to nothing here is a broken
     *     reference and not a locked card
     */
    private void start(List<String> forcedModifiers) {
        if (forcedModifiers == null || forcedModifiers.isEmpty()) {
            return;
        }
        for (String id : forcedModifiers) {
            ModifierDef def = catalog.get(id);
            if (def == null) {
                throw new UnknownIdException("modifier", id);
            }
            if (canForce(def)) {
                take(def, null);
            }
        }
    }

    /**
     * Whether a forced card can still be taken: it is under its cap, its forbidden flags are
     * absent and nothing already taken excludes it.
     *
     * @param def the card
     * @return {@code true} when taking it is legal
     */
    private boolean canForce(ModifierDef def) {
        if (stacks.getOrDefault(def.id(), 0) >= Math.max(1, def.maxStacks())) {
            return false;
        }
        for (RuleFlag flag : def.requiresFlagsAbsent()) {
            if (world.rules().contains(flag)) {
                return false;
            }
        }
        for (ModifierDef held : entries) {
            if (held.excludes().contains(def.id()) || def.excludes().contains(held.id())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Advances the draft on a tick the simulation ran.
     *
     * @param gatesPassed gates passed so far
     * @param collided whether a lethal hit was reported this tick (absorbed or not)
     * @param facts where {@code ModifierOffered} is appended
     */
    public void afterTick(int gatesPassed, boolean collided, List<TickFact> facts) {
        if (!enabled()) {
            return;
        }
        if (state == State.IDLE) {
            maybeStartBreather(gatesPassed, collided);
        } else if (state == State.BREATHER) {
            maybeOpenOffer(gatesPassed, collided, facts);
        }
    }

    /**
     * Advances the draft on a frozen tick (the simulation did not run).
     *
     * @param choice the card index the player picked, {@link RunInput#NO_CHOICE} while the overlay
     *     is still waiting, or {@link RunInput#SKIP} to take nothing
     * @param facts where {@code ModifierChosen} / {@code ModifierSkipped} /
     *     {@code SynergyActivated} are appended
     * @return {@code true} when this tick handed the run back to {@code FLYING}
     */
    public boolean tickFrozen(int choice, List<TickFact> facts) {
        if (state == State.CHOOSING) {
            if (choice == RunInput.NO_CHOICE) {
                return false;
            }
            ModifierOffer.Card card = offer == null ? null : offer.cardAt(choice);
            if (card == null && choice != RunInput.SKIP && offer != null) {
                // An index the table does not have is not an answer. Only RunInput.SKIP means
                // "take nothing"; a stale index from a script or a controller mapping would
                // otherwise burn the draft silently, so the overlay simply keeps waiting.
                return false;
            }
            if (card == null) {
                facts.add(new TickFact.ModifierSkipped(offer == null ? -1 : offer.index()));
                offersSkipped++;
            } else {
                take(card.modifier(), facts);
            }
            offer = null;
            state = State.HOLD;
            holdTicks = RESUME_HOLD_TICKS;
            return false;
        }
        if (state == State.HOLD) {
            holdTicks--;
            if (holdTicks <= 0) {
                holdTicks = 0;
                state = State.IDLE;
                world.grantIFrames(RESUME_IFRAMES);
                return true;
            }
            return false;
        }
        return true;
    }

    /**
     * Takes one card: one more stack, the {@code MODIFIERS} layer rebuilt, the defensive charges
     * re-resolved, the rule flags added and the synergies recomputed (D27).
     *
     * @param def the card
     * @param facts where the facts go, or {@code null} for the forced modifiers of run start
     */
    private void take(ModifierDef def, List<TickFact> facts) {
        if (stacks.getOrDefault(def.id(), 0) >= Math.max(1, def.maxStacks())) {
            // Unreachable through either caller today — the pool never offers a capped card and
            // canForce() checks the same thing — and deliberately kept: the cap is a property of
            // the run rather than of the draft, and a caller that got past both would put the
            // effects in the layer twice while RunStats, which counts distinct entries, still
            // showed 'x1' on the summary.
            return;
        }
        int count = stacks.merge(def.id(), 1, Integer::sum);
        if (count == 1) {
            entries.add(def);
        }
        streakBonusCoins += def.streakBonusCoins();
        rebuildLayer();
        if (!def.flags().isEmpty()) {
            world.addRules(RuleSet.of(def.flags()));
        }
        world.refreshDefensiveCharges();
        if (facts != null) {
            facts.add(new TickFact.ModifierChosen(def.id(), count));
        }
        resolveSynergies(facts);
    }

    private void rebuildLayer() {
        List<StatModifier> layer = new ArrayList<>();
        for (ModifierDef def : entries) {
            int count = stacks.getOrDefault(def.id(), 0);
            for (int i = 0; i < count; i++) {
                // One stack contributes its effects once: FLAT_ADD and PERCENT_ADD add up and
                // MULTIPLY compounds, which is what stacking a card is supposed to mean (D8).
                layer.addAll(def.toModifiers());
            }
        }
        stack.setLayer(Layer.MODIFIERS, layer);
    }

    private void resolveSynergies(List<TickFact> facts) {
        List<String> activated = synergies.update(entries);
        stack.setLayer(Layer.MOD_SYNERGY, synergies.effects());
        if (!synergies.flags().isEmpty()) {
            world.addRules(synergies.flags());
        }
        world.refreshDefensiveCharges();
        if (facts == null) {
            return;
        }
        for (String id : activated) {
            facts.add(new TickFact.SynergyActivated(id));
        }
    }

    private void maybeStartBreather(int gatesPassed, boolean collided) {
        if (collided || nextOfferIndex >= catalog.offerSchedule().size()) {
            return;
        }
        if (gatesPassed < catalog.offerSchedule().get(nextOfferIndex)) {
            return;
        }
        if (world.bossPending() || world.bossActive()) {
            // E7: the schedule entry is not consumed, so the breather opens on the first tick
            // after the encounter clears.
            return;
        }
        state = State.BREATHER;
        breatherTicks = 0;
        world.deferSpawn(breatherIntervals());
    }

    /**
     * How far the breather pushes the next obstacle out: D11's 1.5 intervals, or whatever more
     * the world says a clear window needs ({@link DraftWorld#clearanceIntervals()}).
     *
     * @return the extra intervals
     */
    private double breatherIntervals() {
        return Math.max(BREATHER_INTERVALS, world.clearanceIntervals());
    }

    private void maybeOpenOffer(int gatesPassed, boolean collided, List<TickFact> facts) {
        if (collided || world.bossPending() || world.bossActive()) {
            // E7 again: a boss that starts while the run is already waiting suppresses spawning,
            // which would clear the air and freeze the run inside the warning banner. The
            // schedule entry is still unconsumed, so the draft opens after the encounter.
            return;
        }
        if (!world.isDraftPathClear()) {
            if (++breatherTicks >= BREATHER_RETRY_TICKS) {
                breatherTicks = 0;
                world.deferSpawn(breatherIntervals());
            }
            return;
        }
        int index = nextOfferIndex++;
        // The draft belongs to the gate it was scheduled for, not to the gate the corridor
        // happened to clear at: the spawner has two or three obstacles queued when the breather
        // starts, so the freeze lands about three gates later and the overlay would otherwise
        // read "gate 13" for the entry §6 calls gate 10.
        int scheduled = catalog.offerSchedule().get(index);
        ModifierOffer drawn = pool.draw(index, scheduled, stacks);
        if (drawn.isEmpty()) {
            // Nothing is eligible: skipping outright beats freezing the run on an empty table.
            state = State.IDLE;
            offersSkipped++;
            return;
        }
        offer = drawn;
        state = State.CHOOSING;
        offersOpened++;
        facts.add(new TickFact.ModifierOffered(index, gatesPassed, drawn.ids()));
    }

    private boolean enabled() {
        return allowOffers && !catalog.isEmpty();
    }

    /**
     * Whether the director is part of this run at all: it can still open a draft, or it already
     * took something. A run that answers {@code false} folds nothing into the state hash.
     *
     * @return {@code true} when the roguelite layer is doing something
     */
    public boolean isActive() {
        return enabled() || !stacks.isEmpty();
    }

    /**
     * Where the draft is.
     *
     * @return the state
     */
    public State state() {
        return state;
    }

    /**
     * Whether the simulation must not tick right now.
     *
     * @return {@code true} while choosing or holding
     */
    public boolean isFrozen() {
        return state == State.CHOOSING || state == State.HOLD;
    }

    /**
     * The cards on the table.
     *
     * @return the offer, or {@code null} when none is open
     */
    public ModifierOffer offer() {
        return offer;
    }

    /**
     * Frozen ticks left in the countdown.
     *
     * @return the ticks, 0 outside the hold
     */
    public int holdTicksRemaining() {
        return holdTicks;
    }

    /**
     * The number the 3-2-1 shows right now (D17).
     *
     * @return 3, 2 or 1 during the hold, 0 outside it
     */
    public int countdown() {
        if (state != State.HOLD || holdTicks <= 0) {
            return 0;
        }
        int step = RESUME_HOLD_TICKS / COUNTDOWN_STEPS;
        return Math.min(COUNTDOWN_STEPS, (holdTicks + step - 1) / step);
    }

    /**
     * The modifiers taken, distinct, in the order they were taken (forced ones first).
     *
     * @return an unmodifiable view
     */
    public List<String> taken() {
        List<String> ids = new ArrayList<>(entries.size());
        for (ModifierDef def : entries) {
            ids.add(def.id());
        }
        return Collections.unmodifiableList(ids);
    }

    /**
     * How many stacks of each modifier the run holds.
     *
     * @return an unmodifiable view in take order
     */
    public Map<String, Integer> stacks() {
        return Collections.unmodifiableMap(stacks);
    }

    /**
     * The set bonuses active right now.
     *
     * @return an unmodifiable view, in content order
     */
    public List<String> activeSynergies() {
        return synergies.active();
    }

    /**
     * The extra coins one clean-gate streak step pays because of the taken modifiers (E32.a).
     *
     * @return the coins per step
     */
    public long streakBonusCoins() {
        return streakBonusCoins;
    }

    /**
     * Drafts that actually opened.
     *
     * @return the count
     */
    public int offersOpened() {
        return offersOpened;
    }

    /**
     * Drafts that were skipped — by the player, or because nothing was eligible.
     *
     * @return the count
     */
    public int offersSkipped() {
        return offersSkipped;
    }

    /**
     * The next entry of {@code offerSchedule} the run is waiting for.
     *
     * @return the index, {@code offerSchedule().size()} when they are all used
     */
    public int nextOfferIndex() {
        return nextOfferIndex;
    }

    /**
     * The catalogue in use.
     *
     * @return the catalogue
     */
    public ModifierCatalog catalog() {
        return catalog;
    }

    /**
     * The pool the drafts are drawn from.
     *
     * @return the pool
     */
    public ModifierPool pool() {
        return pool;
    }

    /**
     * Folds the draft state into the run's state hash (D12): the phase, the countdown, the
     * schedule cursor, what is on the table and what has been taken. Two runs that diverge only in
     * which card was picked are therefore different runs to the hash, which is what makes the
     * choice observable at all.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, HASH_SEED);
        h = MathUtil.fold(h, state.ordinal());
        h = MathUtil.fold(h, nextOfferIndex);
        h = MathUtil.fold(h, holdTicks);
        h = MathUtil.fold(h, offersOpened);
        h = MathUtil.fold(h, offersSkipped);
        h = MathUtil.fold(h, streakBonusCoins);
        for (Map.Entry<String, Integer> entry : stacks.entrySet()) {
            h = MathUtil.fold(h, MathUtil.fnv1a64(entry.getKey()));
            h = MathUtil.fold(h, entry.getValue());
        }
        for (String id : synergies.active()) {
            h = MathUtil.fold(h, MathUtil.fnv1a64(id));
        }
        if (offer != null) {
            for (String id : offer.ids()) {
                h = MathUtil.fold(h, MathUtil.fnv1a64(id));
            }
        }
        return h;
    }
}
