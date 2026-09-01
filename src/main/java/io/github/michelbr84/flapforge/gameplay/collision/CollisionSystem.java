package io.github.michelbr84.flapforge.gameplay.collision;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.core.geom.Aabb;
import io.github.michelbr84.flapforge.core.geom.Hitbox;
import io.github.michelbr84.flapforge.gameplay.bird.Bird;
import io.github.michelbr84.flapforge.gameplay.obstacle.Obstacle;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleLayer;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.RuleSet;

/**
 * Bird-versus-world collision test (D7).
 *
 * <ul>
 *   <li>Ground: lethal when {@code y ≥ GROUND_DEATH_Y (581.5)} — the sprite bottom touches the
 *       ground line (cause {@link CollisionCause#GROUND}).</li>
 *   <li>Ceiling: lethal only under {@link RuleFlag#LETHAL_CEILING} when the hitbox top rises above
 *       {@code y = 0} (cause {@link CollisionCause#CEILING}).</li>
 *   <li>Obstacles: the bird box (scaled by {@code HITBOX_SCALE} about its centre) against every
 *       lethal hitbox using strict inequalities, the semantics of the AWT rectangle test the
 *       original relied on (cause {@link CollisionCause#OBSTACLE}).</li>
 *   <li>Near miss: when nothing lethal was hit, the same test with the bird box inflated by
 *       {@code inflatePx}.</li>
 *   <li>Tunnelling guard: when the bird's vertical displacement or an obstacle's displacement in
 *       the tick exceeds {@link #SUBSTEP_PX}, the pair is tested {@code ceil(disp / 12)} times
 *       along the linear path between the previous and the current state.</li>
 * </ul>
 */
public final class CollisionSystem {

    /** Displacement per tick above which the test is sub-stepped. */
    public static final double SUBSTEP_PX = 12;

    /**
     * Runs the full test.
     *
     * @param bird the bird (its {@code prevY} must be the tick-start position)
     * @param layer the obstacles
     * @param inflatePx inflation of the bird box for the near-miss test (0 disables it)
     * @param hitboxScale the {@code HITBOX_SCALE} factor
     * @param rules the active rules
     * @return the report
     */
    public CollisionReport test(Bird bird, ObstacleLayer layer, double inflatePx,
            double hitboxScale, RuleSet rules) {
        if (bird.y() >= Playfield.GROUND_DEATH_Y) {
            return CollisionReport.lethal(CollisionCause.GROUND, null);
        }
        Aabb box = bird.hitbox(hitboxScale);
        if (rules.contains(RuleFlag.LETHAL_CEILING) && box.y() < 0) {
            return CollisionReport.lethal(CollisionCause.CEILING, null);
        }
        double birdDy = Math.abs(bird.y() - bird.prevY());
        for (Obstacle o : layer.obstacles()) {
            if (!o.lethal()) {
                continue;
            }
            int steps = substeps(birdDy, o.maxDisplacement());
            for (int k = 1; k <= steps; k++) {
                double t = (double) k / steps;
                Aabb b = k == steps ? box
                        : bird.hitboxAt(bird.prevY() + (bird.y() - bird.prevY()) * t, hitboxScale);
                for (Hitbox h : o.hitboxesAt(t)) {
                    if (h.intersects(b)) {
                        return CollisionReport.lethal(CollisionCause.OBSTACLE, o);
                    }
                }
            }
        }
        if (inflatePx > 0) {
            Aabb inflated = box.inflated(inflatePx);
            for (Obstacle o : layer.obstacles()) {
                if (!o.lethal()) {
                    continue;
                }
                for (Hitbox h : o.hitboxes()) {
                    if (h.intersects(inflated)) {
                        return CollisionReport.nearMiss(o);
                    }
                }
            }
        }
        return CollisionReport.NONE;
    }

    /**
     * Test with an unscaled bird box and no rules.
     *
     * @param bird the bird
     * @param layer the obstacles
     * @param inflatePx inflation for the near-miss test
     * @return the report
     */
    public CollisionReport test(Bird bird, ObstacleLayer layer, double inflatePx) {
        return test(bird, layer, inflatePx, 1.0, RuleSet.EMPTY);
    }

    /**
     * Number of sub-steps for the given per-tick displacements.
     *
     * @param birdDisplacement the bird's vertical displacement
     * @param obstacleDisplacement the obstacle's displacement
     * @return {@code ceil(max / 12)}, at least 1
     */
    public static int substeps(double birdDisplacement, double obstacleDisplacement) {
        double d = Math.max(birdDisplacement, obstacleDisplacement);
        return d > SUBSTEP_PX ? (int) Math.ceil(d / SUBSTEP_PX) : 1;
    }
}
