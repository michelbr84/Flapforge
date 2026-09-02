package io.github.michelbr84.flapforge.ability.behaviors;

import io.github.michelbr84.flapforge.ability.AbilityBehavior;
import io.github.michelbr84.flapforge.ability.AbilityContext;
import io.github.michelbr84.flapforge.ability.ParamSpec;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import java.util.List;

/**
 * {@code dash} (ACTIVE, MOVEMENT) — D9: a 20-tick burst at {@code SCROLL_SPEED × 2.5} during
 * which the bird holds its line and cannot be killed.
 *
 * <p><b>The speed is a stat, the held line is not (E24).</b> {@code SCROLL_SPEED × 2.5} is
 * authored in {@code effects} and goes through the {@code ABILITY} layer, so it composes with
 * everything else and clamps at the stat's ceiling of 360 px/s — a nightmare tier that already
 * multiplies the scroll cannot stack past it. Holding the bird is done here instead, by writing
 * {@code vy = 0} and restoring the y after the integration of every tick of the burst: gravity is
 * <em>not</em> zeroed through the sheet, because a stat pipeline with no absolute set (D8) cannot
 * express "no gravity for 20 ticks" without a modifier that a second source could cancel.
 *
 * <p>Running after the integration is what makes the line exactly flat: the tick's gravity step
 * has already moved the bird when {@link #onTick} undoes it, so the bird's y is bit-identical for
 * the whole burst and the collision test sees the same y the renderer draws.
 *
 * <p><b>The burst ends clear of what it flew into.</b> The invulnerability is granted at
 * activation for the length of the burst plus the level's extra ticks, but a burst that ends
 * inside a column would otherwise kill the bird on the very tick the hold releases — the world
 * has moved 100 px meanwhile and the bird is still between the pipes it entered. So the tick the
 * duration runs out also asks for "ghost until clear", the same rule D9 gives a shield absorb:
 * the overlap the burst created is free, anything that arrives afterwards is not. Without it the
 * level-1 dash measured 13.9 gates against the 79.6 of a bird with no ability at all.
 *
 * <p>Both the i-frames and the ghost are suppressed — the burst is not — under
 * {@link RuleFlag#NO_DEFENSIVE_ABILITIES}: the dash is a movement ability, so the flag does not
 * strip it, but a run that forbids defensive abilities must not hand out invulnerability through
 * one.
 */
public final class DashBehavior implements AbilityBehavior {

    /** Level parameter: invulnerability ticks that outlast the burst. */
    public static final String INVULN_EXTRA_TICKS = "invulnExtraTicks";

    /** What the behaviour reads from {@code abilities.json}. */
    public static final List<ParamSpec> PARAMS = List.of(
            ParamSpec.up(INVULN_EXTRA_TICKS, 0, 60));

    private double heldY;
    private boolean holding;

    @Override
    public boolean holdsBird() {
        return true;
    }

    @Override
    public void onActivate(AbilityContext ctx) {
        Bird bird = ctx.bird();
        heldY = bird.y();
        holding = true;
        bird.setVy(0);
        if (!ctx.rules().contains(RuleFlag.NO_DEFENSIVE_ABILITIES)) {
            ctx.grantIFrames(ctx.ability().durationRemaining()
                    + ctx.intParam(INVULN_EXTRA_TICKS, 0));
        }
    }

    @Override
    public void onTick(AbilityContext ctx) {
        if (ctx.ability().isActive()) {
            Bird bird = ctx.bird();
            bird.setVy(0);
            bird.setY(heldY);
            return;
        }
        if (holding) {
            // The burst ended this tick, before the collision test of this very tick: whatever the
            // bird was flown into is left behind instead of killing it on the release frame.
            holding = false;
            if (!ctx.rules().contains(RuleFlag.NO_DEFENSIVE_ABILITIES)) {
                ctx.ghostUntilClear();
            }
        }
    }

    /**
     * The y the burst is holding, for tests and debug overlays.
     *
     * @return the y
     */
    public double heldY() {
        return heldY;
    }
}
