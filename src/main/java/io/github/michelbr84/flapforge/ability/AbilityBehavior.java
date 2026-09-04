package io.github.michelbr84.flapforge.ability;

import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.pickup.Coin;

/**
 * The behaviour half of an ability (D9). The stat half is authored data — {@code effects} pushed
 * into the {@code ABILITY} layer — and needs no code; a behaviour exists only for what the stat
 * pipeline cannot express: setting the bird's velocity, holding it, configuring the shield and
 * revive systems, or cancelling a lethal hit.
 *
 * <p>Every hook has a default that does nothing, so a behaviour implements only what it needs.
 * The instance is created once per equipped ability at run start ({@link BehaviorRegistry}) and
 * may keep per-run state in fields; it must never keep static state, draw from an unseeded random
 * source or read a clock — a run has to replay tick for tick from its seed (D12).
 *
 * <p><b>When a hook may write the bird.</b> {@link #onActivate}, {@link #onTick} and
 * {@link #onLethalHit} may set the bird's velocity and position directly (E24: the dash holds
 * {@code vy = 0} for its burst, the double flap zeroes the fall, the revive kick sets an upward
 * velocity). That is deliberate and is the <em>only</em> sanctioned way to move the bird from an
 * ability: gravity, flap strength and scroll speed are stats and must go through the layer, never
 * through a {@code SET} on the sheet (D8). {@link #onEquip}, {@link #onFlap} and
 * {@link #onCoinNear} must not move the bird.
 */
public interface AbilityBehavior {

    /**
     * Called once at run start, after the ability's {@code effects} are in the {@code ABILITY}
     * layer and after the shield and revive systems have read their charges.
     *
     * <p>This is where a passive configures a run system ({@code shield} sets the absorb
     * invulnerability and the regeneration cadence, {@code emergency_recovery} sets the revive
     * kick) or contributes a level-scaled modifier with
     * {@link AbilityContext#addRunEffect}. It must not add {@code SHIELD_CHARGES} or
     * {@code REVIVES}: those are read from the sheet before this call, so a charge added here
     * would never be seen.
     *
     * @param ctx the ability's view of the run
     */
    default void onEquip(AbilityContext ctx) {
    }

    /**
     * Asked before the charge and cooldown bookkeeping runs: {@code false} refuses the activation
     * outright, so nothing is spent and the tick reports no {@code AbilityActivated} (the HUD's
     * refusal beat answers instead).
     *
     * <p>This is where a behaviour declines a press its effect could not honour — the double flap
     * refuses above the ceiling gate, the way the ordinary flap it amplifies does. It must not
     * change anything: it is a question, and the manager may ask it without activating.
     *
     * @param ctx the ability's view of the run
     * @return {@code true} when the activation may proceed
     */
    default boolean canActivate(AbilityContext ctx) {
        return true;
    }

    /**
     * Whether this behaviour pins the bird's position while its duration runs (the dash).
     *
     * <p>The simulation asks before it applies a flap: a control the ability is about to overwrite
     * must not answer with a wing beat, a sound and a counter (D9). It is a property of the
     * behaviour, not of the tick — the manager combines it with "the duration is running".
     *
     * @return {@code true} when the bird's y is held by this behaviour
     */
    default boolean holdsBird() {
        return false;
    }

    /**
     * Whether this behaviour wants {@link #onCoinNear}. Opting in is explicit because the routing
     * walks every live coin every tick: a manager whose behaviours all say {@code false} skips the
     * walk entirely, so the hook costs nothing until something uses it.
     *
     * @return {@code true} when {@link #onCoinNear} is implemented
     */
    default boolean routesCoins() {
        return false;
    }

    /**
     * Called on the tick the player activated the ability, after the charge and cooldown
     * bookkeeping accepted it and after the flap of the same tick was applied — so a behaviour
     * that writes the velocity wins over a simultaneous flap rather than being overwritten by
     * it.
     *
     * <p>May set the bird's velocity and may ask for invulnerability ticks.
     *
     * @param ctx the ability's view of the run
     */
    default void onActivate(AbilityContext ctx) {
    }

    /**
     * Called once per tick while the ability is equipped, after the bird integrated and before
     * the world scrolls.
     *
     * <p>After the integration on purpose: a behaviour that pins the bird (the dash) has to undo
     * the gravity step of this very tick, and one that reads the position sees the position the
     * collision test will use.
     *
     * @param ctx the ability's view of the run
     */
    default void onTick(AbilityContext ctx) {
    }

    /**
     * Called when a lethal hit was detected and neither invulnerability ticks nor the ghost state
     * already cancelled it, before the shield and the revive get their turn.
     *
     * <p>Returning {@code true} cancels the hit outright — the bird survives without spending a
     * shield charge or a revive. None of the eight shipped abilities does that (the defensive
     * ones work through charges and invulnerability ticks, which is what the run systems and the
     * HUD can count); the hook exists so a future ability can, and so a behaviour can react to
     * the hit it is about to survive.
     *
     * @param ctx the ability's view of the run
     * @param obstacle the obstacle that was hit, or {@code null} for the ground and the ceiling
     * @return {@code true} when the hit is cancelled by this behaviour
     */
    default boolean onLethalHit(AbilityContext ctx, Obstacle obstacle) {
        return false;
    }

    /**
     * Called when a flap is accepted, with the {@code FLAP_VELOCITY} the sheet resolved; the
     * returned value is the upward speed actually applied.
     *
     * <p>Read-only on the bird: return the modified velocity instead of writing it, so several
     * equipped abilities compose in a defined order.
     *
     * @param ctx the ability's view of the run
     * @param velocity the upward speed in px/s the flap would apply
     * @return the upward speed to apply
     */
    default double onFlap(AbilityContext ctx, double velocity) {
        return velocity;
    }

    /**
     * Called once per tick for every live coin inside the resolved {@code MAGNET_RADIUS} of the
     * bird, after the pickups moved and before the bird collects them.
     *
     * <p>May adjust the coin; must not collect it and must not move the bird. A behaviour that
     * implements it must also return {@code true} from {@link #routesCoins()}, otherwise the
     * manager never routes anything to it.
     *
     * @param ctx the ability's view of the run
     * @param coin the coin near the bird
     */
    default void onCoinNear(AbilityContext ctx, Coin coin) {
    }
}
