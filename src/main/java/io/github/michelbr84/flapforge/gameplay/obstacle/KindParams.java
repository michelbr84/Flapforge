package io.github.michelbr84.flapforge.gameplay.obstacle;

import io.github.michelbr84.flapforge.core.MathUtil;
import java.util.Objects;

/**
 * The typed geometry of one spawn, per obstacle family (M7, E32.d). A {@link SpawnDecision}
 * carries one of these for every kind but the classic stream gate, and folds it into the decision
 * hash, so a change to any parameter of any kind is a different decision.
 *
 * <p>Values are in playfield pixels and ticks: the {@code 0..1} fractions of {@code patterns.json}
 * are resolved by {@link ObstacleParams} before they get here.
 */
public sealed interface KindParams {

    /**
     * Folds every field into a hash.
     *
     * @param hash the running hash
     * @return the updated hash
     */
    long fold(long hash);

    /**
     * Extras of a pattern gate on top of the layout and gap position the decision itself carries.
     *
     * @param layout the segment arrangement
     * @param gapCenter the gap centre as a fraction of the playable height, or {@code NaN} for a
     *     random position (rolled by {@link SpawnTable#decisionFor})
     * @param gapSize the gap height in px, or {@code 0} to use the resolved {@code GAP_SIZE}
     * @param oscillate whether the pair moves
     * @param amplitude the oscillation amplitude in px (used when {@code oscillate})
     * @param speed the oscillation speed in px/s, or {@code 0} for the {@code OSCILLATION_SPEED}
     *     stat
     */
    record GateSpec(PipeGate.Layout layout, double gapCenter, double gapSize, boolean oscillate,
            double amplitude, double speed) implements KindParams {

        /**
         * Validates the components.
         *
         * @param layout the layout
         * @param gapCenter the gap centre fraction or {@code NaN}
         * @param gapSize the gap height or 0
         * @param oscillate whether the pair moves
         * @param amplitude the amplitude
         * @param speed the speed or 0
         */
        public GateSpec {
            Objects.requireNonNull(layout, "layout");
            if (gapSize < 0 || amplitude <= 0 || speed < 0) {
                throw new IllegalArgumentException("Invalid gate spec: gapSize " + gapSize
                        + ", amplitude " + amplitude + ", speed " + speed);
            }
        }

        /**
         * Tells whether the gap position is left to the stream.
         *
         * @return {@code true} for {@code "random"}
         */
        public boolean randomGapCenter() {
            return Double.isNaN(gapCenter);
        }

        @Override
        public long fold(long hash) {
            long h = MathUtil.fold(hash, layout.ordinal());
            h = MathUtil.fold(h, Double.doubleToLongBits(gapCenter));
            h = MathUtil.fold(h, Double.doubleToLongBits(gapSize));
            h = MathUtil.fold(h, oscillate ? 1 : 0);
            h = MathUtil.fold(h, Double.doubleToLongBits(amplitude));
            return MathUtil.fold(h, Double.doubleToLongBits(speed));
        }
    }

    /**
     * A gear (D6): a circle, optionally sweeping a vertical rail.
     *
     * @param cy the centre y in px (the sweep centre when a rail is present)
     * @param radius the radius in px
     * @param railAmplitude the rail travel in px, or {@code 0} for a fixed gear
     * @param railSpeed the rail speed in px/s (ignored without a rail)
     */
    record GearSpec(double cy, double radius, double railAmplitude, double railSpeed)
            implements KindParams {

        /**
         * Validates the components.
         *
         * @param cy the centre y
         * @param radius the radius
         * @param railAmplitude the rail travel or 0
         * @param railSpeed the rail speed
         */
        public GearSpec {
            if (radius <= 0 || railAmplitude < 0 || railSpeed < 0) {
                throw new IllegalArgumentException("Invalid gear spec: radius " + radius
                        + ", rail " + railAmplitude + "/" + railSpeed);
            }
        }

        /**
         * Tells whether the gear rides a rail.
         *
         * @return {@code true} when the amplitude is positive
         */
        public boolean hasRail() {
            return railAmplitude > 0;
        }

        /**
         * Copy with the default rail forced on ({@code ALL_OBSTACLES_MOVE}).
         *
         * @return this spec when it already has a rail, else one with the default rail
         */
        public GearSpec withRail() {
            return hasRail() ? this
                    : new GearSpec(cy, radius, Gear.DEFAULT_RAIL_AMPLITUDE, Gear.DEFAULT_RAIL_SPEED);
        }

        @Override
        public long fold(long hash) {
            long h = MathUtil.fold(hash, Double.doubleToLongBits(cy));
            h = MathUtil.fold(h, Double.doubleToLongBits(radius));
            h = MathUtil.fold(h, Double.doubleToLongBits(railAmplitude));
            return MathUtil.fold(h, Double.doubleToLongBits(railSpeed));
        }
    }

    /**
     * A piston (D6).
     *
     * @param side the anchoring edge
     * @param length the full extension in px
     * @param telegraphTicks ticks of warning before the head extends
     * @param extendTicks ticks the head takes to extend
     * @param holdTicks ticks the head stays extended
     * @param retractTicks ticks the head takes to retract
     * @param phaseOffset where in the cycle the piston starts, in ticks
     */
    record PistonSpec(Side side, double length, int telegraphTicks, int extendTicks, int holdTicks,
            int retractTicks, int phaseOffset) implements KindParams {

        /**
         * Validates the components.
         *
         * @param side the side
         * @param length the extension
         * @param telegraphTicks the telegraph length
         * @param extendTicks the extend length
         * @param holdTicks the hold length
         * @param retractTicks the retract length
         * @param phaseOffset the start offset
         */
        public PistonSpec {
            Objects.requireNonNull(side, "side");
            if (length <= 0 || telegraphTicks < 1 || extendTicks < 1 || holdTicks < 0
                    || retractTicks < 1 || phaseOffset < 0) {
                throw new IllegalArgumentException("Invalid piston spec: length " + length
                        + ", phases " + telegraphTicks + "/" + extendTicks + "/" + holdTicks + "/"
                        + retractTicks + ", offset " + phaseOffset);
            }
        }

        /**
         * Copy with the telegraph shortened to the {@code ALL_OBSTACLES_MOVE} value when it is
         * longer.
         *
         * @return the spec with {@code min(telegraphTicks, FORCED_TELEGRAPH_TICKS)}
         */
        public PistonSpec withForcedTelegraph() {
            int forced = Math.min(telegraphTicks, Piston.FORCED_TELEGRAPH_TICKS);
            return forced == telegraphTicks ? this
                    : new PistonSpec(side, length, forced, extendTicks, holdTicks, retractTicks,
                            phaseOffset);
        }

        @Override
        public long fold(long hash) {
            long h = MathUtil.fold(hash, side.ordinal());
            h = MathUtil.fold(h, Double.doubleToLongBits(length));
            h = MathUtil.fold(h, telegraphTicks);
            h = MathUtil.fold(h, extendTicks);
            h = MathUtil.fold(h, holdTicks);
            h = MathUtil.fold(h, retractTicks);
            return MathUtil.fold(h, phaseOffset);
        }
    }

    /**
     * A wind zone (D6).
     *
     * @param width the zone width in px
     * @param cy the zone centre y in px
     * @param height the zone height in px
     * @param accelY the vertical acceleration applied to the bird in px/s² (positive = down)
     * @param scrollDelta the change of the relative scroll speed in px/s while the bird is inside
     */
    record WindSpec(double width, double cy, double height, double accelY, double scrollDelta)
            implements KindParams {

        /**
         * Validates the components.
         *
         * @param width the width
         * @param cy the centre y
         * @param height the height
         * @param accelY the vertical acceleration
         * @param scrollDelta the scroll change
         */
        public WindSpec {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Invalid wind spec: " + width + "x" + height);
            }
        }

        @Override
        public long fold(long hash) {
            long h = MathUtil.fold(hash, Double.doubleToLongBits(width));
            h = MathUtil.fold(h, Double.doubleToLongBits(cy));
            h = MathUtil.fold(h, Double.doubleToLongBits(height));
            h = MathUtil.fold(h, Double.doubleToLongBits(accelY));
            return MathUtil.fold(h, Double.doubleToLongBits(scrollDelta));
        }
    }

    /**
     * A partial-height lightning bolt (D6).
     *
     * @param side the edge the bolt hangs from
     * @param lengthFrac the lit fraction of the playable height
     * @param warningTicks ticks of warning before the strike (at the current scroll)
     * @param strikeTicks ticks the bolt stays lethal
     */
    record LightningSpec(Side side, double lengthFrac, int warningTicks, int strikeTicks)
            implements KindParams {

        /**
         * Validates the components.
         *
         * @param side the side
         * @param lengthFrac the lit fraction
         * @param warningTicks the warning length
         * @param strikeTicks the strike length
         */
        public LightningSpec {
            Objects.requireNonNull(side, "side");
            if (lengthFrac <= 0 || lengthFrac >= 1 || warningTicks < 0 || strikeTicks < 1) {
                throw new IllegalArgumentException("Invalid lightning spec: frac " + lengthFrac
                        + ", warning " + warningTicks + ", strike " + strikeTicks);
            }
        }

        @Override
        public long fold(long hash) {
            long h = MathUtil.fold(hash, side.ordinal());
            h = MathUtil.fold(h, Double.doubleToLongBits(lengthFrac));
            h = MathUtil.fold(h, warningTicks);
            return MathUtil.fold(h, strikeTicks);
        }
    }
}
