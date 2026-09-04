package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.content.defs.AbilityDef;
import io.github.michelbr84.flapforge.content.defs.AbilityKind;
import io.github.michelbr84.flapforge.content.defs.AbilityTag;
import io.github.michelbr84.flapforge.content.defs.StatModifierDef;
import io.github.michelbr84.flapforge.core.MathUtil;
import io.github.michelbr84.flapforge.gameplay.SimContext;
import io.github.michelbr84.flapforge.gameplay.TickFact;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;
import io.github.michelbr84.flapforge.gameplay.stats.EffectStack;
import io.github.michelbr84.flapforge.gameplay.stats.Layer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;
import io.github.michelbr84.flapforge.gameplay.stats.StatModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * The abilities of one run (D9): the equipped active plus the passives, their timers, the
 * {@code ABILITY} layer they push and the routing of every {@link AbilityBehavior} hook.
 *
 * <p><b>Loadout.</b> {@link #selectLoadout} picks the ids — one active, then the equipped passives
 * up to {@code BirdDef.passiveSlots + profile.passiveSlotBonus}, then the bird's innate passives,
 * which need no unlock and cannot be unequipped (D9, E3). {@link #create} then strips what the
 * rules forbid — {@code NO_DEFENSIVE_ABILITIES} removes every {@link AbilityTag#DEFENSIVE} one
 * (an innate passive included: Ironbeak's shield is disabled under that flag) and
 * {@code NO_REVIVE} every {@link AbilityTag#REVIVE} one — and builds an
 * {@link AbilityInstance} per survivor at the level the profile owns.
 *
 * <p><b>Layer.</b> A passive contributes its {@code effects} for the whole run; an active
 * contributes them only while its duration is running, which is what makes the dash a
 * {@code SCROLL_SPEED × 2.5} burst and slow time a {@code TIME_SCALE × 0.5} window instead of a
 * permanent stat change. The layer is rebuilt only when the set of contributions changes, so a
 * run whose abilities are idle never invalidates the stat cache.
 *
 * <p><b>Routing order.</b> The active ability first, then the passives in equip order (equipped
 * before innate). The order is fixed and documented because a hook may cancel a lethal hit or
 * change a flap velocity, and two abilities must compose the same way in every run.
 */
public final class AbilityManager {

    /** Prefix of the label ability modifiers carry in a stat breakdown. */
    public static final String SOURCE_PREFIX = "ability:";

    /** An empty manager: no ability is equipped, every hook is a no-op. */
    private static final List<AbilityInstance> NONE = List.of();

    private final AbilityHost host;
    private final EffectStack stack;
    private final List<AbilityInstance> instances;
    private final List<String> strippedIds;
    private final AbilityInstance active;
    private final AbilityContext ctx;
    private final List<StatModifier> passiveEffects = new ArrayList<>();
    private final List<StatModifier> extraEffects = new ArrayList<>();
    private final boolean routesCoins;
    private boolean layerDirty;

    private AbilityManager(AbilityHost host, EffectStack stack, List<AbilityInstance> instances,
            List<String> strippedIds) {
        this.host = Objects.requireNonNull(host, "host");
        this.stack = Objects.requireNonNull(stack, "stack");
        this.instances = instances;
        this.strippedIds = strippedIds;
        AbilityInstance found = null;
        for (AbilityInstance instance : instances) {
            if (instance.kind() == AbilityKind.ACTIVE) {
                found = instance;
                break;
            }
        }
        this.active = found;
        this.ctx = new AbilityContext(host, this);
        boolean coins = false;
        for (AbilityInstance instance : instances) {
            if (instance.kind() == AbilityKind.PASSIVE) {
                addEffects(passiveEffects, instance);
            }
            coins |= instance.behavior().routesCoins();
        }
        // Decided once: the coin routing walks every live coin every tick, and none of the eight
        // shipped behaviours wants it, so a run with abilities must not pay for a hook nothing
        // listens to (the magnet is stat-driven, in Coin.update).
        this.routesCoins = coins;
        // Published before anything reads the sheet: the shield and revive systems resolve their
        // charges from SHIELD_CHARGES / REVIVES right after the manager is built (D9).
        publishLayer();
    }

    /**
     * An empty manager, for a run without abilities.
     *
     * @param host the run
     * @param stack the effect stack
     * @return the manager
     */
    public static AbilityManager empty(AbilityHost host, EffectStack stack) {
        return new AbilityManager(host, stack, NONE, List.of());
    }

    /**
     * Builds the manager of a run: strips what the rules forbid and instantiates the rest.
     *
     * @param equipped the loadout, active first (see {@link #selectLoadout})
     * @param levels the owned level per ability id; a missing id counts as level 1
     * @param rules the active rules of the run
     * @param host the run
     * @param stack the effect stack the {@code ABILITY} layer lives in
     * @param registry the behaviour implementations
     * @return the manager
     * @throws IllegalStateException when a definition names a behaviour the registry does not
     *     know (the content validator rejects that, so it can only happen with hand-built data)
     */
    public static AbilityManager create(List<AbilityDef> equipped, Map<String, Integer> levels,
            RuleSet rules, AbilityHost host, EffectStack stack, BehaviorRegistry registry) {
        Objects.requireNonNull(equipped, "equipped");
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(registry, "registry");
        if (equipped.isEmpty()) {
            return empty(host, stack);
        }
        List<AbilityInstance> instances = new ArrayList<>(equipped.size());
        List<String> stripped = new ArrayList<>(2);
        boolean activeTaken = false;
        for (AbilityDef def : equipped) {
            if (isForbidden(def, rules)) {
                stripped.add(def.id());
                continue;
            }
            if (def.kind() == AbilityKind.ACTIVE) {
                if (activeTaken) {
                    // A loadout carries one active (D9); a second one is data corruption, not a
                    // second slot, so it is dropped rather than silently doubling the offence.
                    stripped.add(def.id());
                    continue;
                }
                activeTaken = true;
            }
            Integer level = levels.get(def.id());
            instances.add(new AbilityInstance(def, level == null ? 1 : level,
                    registry.create(def.behavior())));
        }
        if (instances.isEmpty()) {
            return new AbilityManager(host, stack, NONE, List.copyOf(stripped));
        }
        instances.sort((a, b) -> Integer.compare(order(a), order(b)));
        return new AbilityManager(host, stack, List.copyOf(instances), List.copyOf(stripped));
    }

    private static int order(AbilityInstance instance) {
        return instance.kind() == AbilityKind.ACTIVE ? 0 : 1;
    }

    /**
     * Whether the rules strip an ability (D9): {@code NO_DEFENSIVE_ABILITIES} removes the
     * defensive ones, {@code NO_REVIVE} the revive ones.
     *
     * @param def the definition
     * @param rules the active rules
     * @return {@code true} when the ability must not be equipped
     */
    public static boolean isForbidden(AbilityDef def, RuleSet rules) {
        return (rules.contains(RuleFlag.NO_DEFENSIVE_ABILITIES) && def.has(AbilityTag.DEFENSIVE))
                || (rules.contains(RuleFlag.NO_REVIVE) && def.has(AbilityTag.REVIVE));
    }

    /**
     * Picks the loadout of a run from what the player selected and what the bird grants (D9, E3):
     * the active ability, then the equipped passives up to {@code passiveSlots}, then the bird's
     * innate passives, which do not occupy a slot, need no unlock and cannot be unequipped.
     * Unknown ids and duplicates are dropped, and a passive selected in the active slot (or the
     * reverse) is ignored rather than played in the wrong role.
     *
     * @param lookup resolves an ability id to its definition, {@code null} when unknown
     * @param activeId the selected active ability id, or {@code null}
     * @param passiveIds the selected passive ability ids, in slot order
     * @param innateIds the bird's innate passive ability ids
     * @param passiveSlots how many selected passives the bird can carry
     * @return the definitions, active first, then the passives in order
     */
    public static List<AbilityDef> selectLoadout(Function<String, AbilityDef> lookup,
            String activeId, List<String> passiveIds, List<String> innateIds, int passiveSlots) {
        Objects.requireNonNull(lookup, "lookup");
        List<AbilityDef> out = new ArrayList<>(4);
        LinkedHashSet<String> taken = new LinkedHashSet<>();
        if (activeId != null && !activeId.isBlank()) {
            AbilityDef def = lookup.apply(activeId);
            if (def != null && def.kind() == AbilityKind.ACTIVE) {
                out.add(def);
                taken.add(def.id());
            }
        }
        int slots = Math.max(0, passiveSlots);
        int used = 0;
        for (String id : passiveIds) {
            if (used >= slots) {
                break;
            }
            AbilityDef def = lookup.apply(id);
            if (def == null || def.kind() != AbilityKind.PASSIVE || !taken.add(def.id())) {
                continue;
            }
            out.add(def);
            used++;
        }
        for (String id : innateIds) {
            AbilityDef def = lookup.apply(id);
            if (def == null || def.kind() != AbilityKind.PASSIVE || !taken.add(def.id())) {
                continue;
            }
            out.add(def);
        }
        return List.copyOf(out);
    }

    /**
     * Runs {@link AbilityBehavior#onEquip} on every instance and publishes the {@code ABILITY}
     * layer. Called once, at run start, after the shield and revive systems read their charges.
     */
    public void equip() {
        if (instances.isEmpty()) {
            return;
        }
        for (AbilityInstance instance : instances) {
            ctx.bind(instance, null, null);
            instance.behavior().onEquip(ctx);
        }
        if (layerDirty) {
            publishLayer();
        }
    }

    /**
     * Advances every timer by one tick and reports what changed. Called at the start of a tick,
     * before the activation edge is read.
     *
     * @param facts where {@code AbilityReady} is appended
     */
    public void beginTick(List<TickFact> facts) {
        for (AbilityInstance instance : instances) {
            AbilityInstance.Tick changed = instance.advance();
            if (changed.expired()) {
                layerDirty = true;
            }
            if (changed.ready()) {
                facts.add(new TickFact.AbilityReady(instance.id()));
            }
        }
        if (layerDirty) {
            publishLayer();
        }
    }

    /**
     * Activates the equipped active ability when it is ready and the behaviour accepts the press.
     *
     * <p>{@link AbilityBehavior#canActivate} is asked <em>before</em> the bookkeeping, so a
     * refused press costs neither a charge nor a cooldown and the screen can explain it exactly
     * like an ability that is not ready yet.
     *
     * @param sim the tick context
     * @param facts where {@code AbilityActivated} is appended
     * @return {@code true} when the ability was activated this tick
     */
    public boolean activate(SimContext sim, List<TickFact> facts) {
        if (active == null || !active.isReady()) {
            return false;
        }
        ctx.bind(active, sim, facts);
        if (!active.behavior().canActivate(ctx)) {
            return false;
        }
        if (!active.activate(host.stats())) {
            return false;
        }
        layerDirty = true;
        publishLayer();
        ctx.bind(active, sim, facts);
        active.behavior().onActivate(ctx);
        facts.add(new TickFact.AbilityActivated(active.id()));
        if (layerDirty) {
            publishLayer();
        }
        return true;
    }

    /**
     * Runs {@link AbilityBehavior#onTick} on every instance, after the bird integrated.
     *
     * @param sim the tick context
     * @param facts where facts are appended
     */
    public void onTick(SimContext sim, List<TickFact> facts) {
        for (AbilityInstance instance : instances) {
            ctx.bind(instance, sim, facts);
            instance.behavior().onTick(ctx);
        }
        if (layerDirty) {
            publishLayer();
        }
    }

    /**
     * Routes an accepted flap through every behaviour, in routing order.
     *
     * @param sim the tick context
     * @param velocity the {@code FLAP_VELOCITY} the sheet resolved
     * @return the upward speed to apply
     */
    public double onFlap(SimContext sim, double velocity) {
        double v = velocity;
        for (AbilityInstance instance : instances) {
            ctx.bind(instance, sim, null);
            v = instance.behavior().onFlap(ctx, v);
        }
        return v;
    }

    /**
     * Routes a coin inside the magnet radius through every behaviour.
     *
     * @param sim the tick context
     * @param coin the coin
     */
    public void onCoinNear(SimContext sim, Coin coin) {
        for (AbilityInstance instance : instances) {
            ctx.bind(instance, sim, null);
            instance.behavior().onCoinNear(ctx, coin);
        }
    }

    /**
     * Offers a lethal hit to every behaviour, in routing order; the first one that takes it
     * cancels the hit.
     *
     * @param sim the tick context
     * @param obstacle the obstacle hit, or {@code null} for the ground and the ceiling
     * @param facts where facts are appended
     * @return {@code true} when a behaviour cancelled the hit
     */
    public boolean onLethalHit(SimContext sim, Obstacle obstacle, List<TickFact> facts) {
        for (AbilityInstance instance : instances) {
            ctx.bind(instance, sim, facts);
            if (instance.behavior().onLethalHit(ctx, obstacle)) {
                if (layerDirty) {
                    publishLayer();
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Restores charges on the recharge cadence of every charge-gated ability.
     *
     * @param gatesPassed the gates passed so far
     * @param facts where {@code AbilityReady} is appended when a restored charge makes an ability
     *     usable again (a charge that only tops it up is silent)
     */
    public void onGatePassed(int gatesPassed, List<TickFact> facts) {
        for (AbilityInstance instance : instances) {
            boolean wasReady = instance.isReady();
            if (instance.recharge(gatesPassed) && !wasReady && instance.isReady()) {
                facts.add(new TickFact.AbilityReady(instance.id()));
            }
        }
    }

    /**
     * Adds a modifier to the {@code ABILITY} layer for the rest of the run (level scaling of a
     * passive, {@link AbilityContext#addRunEffect}).
     *
     * @param modifier the modifier
     */
    void addRunEffect(StatModifier modifier) {
        extraEffects.add(Objects.requireNonNull(modifier, "modifier"));
        layerDirty = true;
    }

    private void publishLayer() {
        layerDirty = false;
        if (instances.isEmpty() && extraEffects.isEmpty()) {
            return;
        }
        List<StatModifier> layer = new ArrayList<>(
                passiveEffects.size() + extraEffects.size() + 2);
        layer.addAll(passiveEffects);
        layer.addAll(extraEffects);
        for (AbilityInstance instance : instances) {
            if (instance.kind() == AbilityKind.ACTIVE && instance.isActive()) {
                addEffects(layer, instance);
            }
        }
        stack.setLayer(Layer.ABILITY, layer);
    }

    private static void addEffects(List<StatModifier> out, AbilityInstance instance) {
        String source = SOURCE_PREFIX + instance.id();
        for (StatModifierDef def : instance.def().effects()) {
            out.add(def.toModifier(source));
        }
    }

    /**
     * Whether the run has no ability at all (the simulation skips every hook then, so a run
     * without abilities behaves exactly as it did before they existed).
     *
     * @return {@code true} when nothing is equipped
     */
    public boolean isEmpty() {
        return instances.isEmpty();
    }

    /**
     * The equipped abilities, active first (HUD order).
     *
     * @return an unmodifiable list
     */
    public List<AbilityInstance> instances() {
        return instances;
    }

    /**
     * The equipped active ability.
     *
     * @return the instance, or {@code null} when none is equipped
     */
    public AbilityInstance active() {
        return active;
    }

    /**
     * The ids the rules stripped from the loadout, in loadout order — what the selection screen
     * greys out and the HUD explains (D9: "Ironbeak's innate shield is disabled and the UI says
     * so").
     *
     * @return an unmodifiable list
     */
    public List<String> strippedIds() {
        return strippedIds;
    }

    /**
     * Looks an equipped ability up.
     *
     * @param id the ability id
     * @return the instance, or {@code null} when it is not equipped
     */
    public AbilityInstance instance(String id) {
        for (AbilityInstance instance : instances) {
            if (instance.id().equals(id)) {
                return instance;
            }
        }
        return null;
    }

    /**
     * Whether an active ability is equipped and ready right now (the bot asks before spending a
     * prediction on it, D21).
     *
     * @return {@code true} when {@link #activate} would succeed
     */
    public boolean hasReadyActive() {
        return active != null && active.isReady();
    }

    /**
     * Whether a running ability is pinning the bird's position this tick (the dash).
     *
     * <p>The simulation asks before it applies a flap: a flap that is about to be undone by
     * {@link AbilityBehavior#onTick} must not be accepted, because an accepted flap restarts the
     * wing animation, plays the flap sound and counts in the run statistics for a bird that does
     * not move.
     *
     * @return {@code true} while the bird's y is held by an ability
     */
    public boolean holdsBird() {
        return active != null && active.isActive() && active.behavior().holdsBird();
    }

    /**
     * Whether any equipped behaviour asked for {@link AbilityBehavior#onCoinNear}; when none does,
     * the simulation skips the per-tick walk over the coins entirely.
     *
     * @return {@code true} when a coin near the bird must be routed
     */
    public boolean routesCoins() {
        return routesCoins;
    }

    /**
     * Folds the ability state into a hash (D12).
     *
     * @param hash the running hash
     * @return the updated hash
     */
    public long hashState(long hash) {
        long h = MathUtil.fold(hash, instances.size());
        for (AbilityInstance instance : instances) {
            h = instance.hashState(h);
        }
        return h;
    }

    @Override
    public String toString() {
        List<String> ids = new ArrayList<>(instances.size());
        for (AbilityInstance instance : instances) {
            ids.add(instance.id() + "@" + instance.level());
        }
        return "AbilityManager" + Collections.unmodifiableList(ids);
    }
}
