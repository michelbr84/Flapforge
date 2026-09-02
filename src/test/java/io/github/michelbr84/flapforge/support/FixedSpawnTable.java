package io.github.michelbr84.flapforge.support;

import io.github.michelbr84.flapforge.core.Playfield;
import io.github.michelbr84.flapforge.gameplay.obstacle.ObstacleKind;
import io.github.michelbr84.flapforge.gameplay.obstacle.PipeGate;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnDecision;
import io.github.michelbr84.flapforge.gameplay.obstacle.SpawnTable;
import java.util.Map;
import java.util.Random;

/**
 * A world with no surprises (E17): every spawn is a static standard gate whose gap sits at the
 * same height, so a scripted pilot flies a straight line and the only thing a test can be
 * measuring is what it set out to measure.
 *
 * <p>It draws nothing at all — neither the {@code spawn} stream nor the {@code obstacle} one — so
 * a run driven by it is the same run for every seed. That is deliberate: {@code
 * ModifierDirectorTest} is about the breather, the freeze and the countdown, and a gate that
 * happens to spawn floating would turn a timing assertion into a flake.
 *
 * <p>{@code patterns.json} and {@code PatternStreamer} are M7, so this is the M6 way of getting a
 * predictable corridor; {@code test_flat_corridor.json} arrives with them (E17).
 */
public final class FixedSpawnTable extends SpawnTable {

    /** Gap top that puts the gap centre exactly on {@link Playfield#BIRD_START_Y}. */
    public static final double CENTRED_TOP = Playfield.BIRD_START_Y - 64;

    private final double top;

    /** Creates a table whose gaps are centred on the bird's start height. */
    public FixedSpawnTable() {
        this(CENTRED_TOP);
    }

    /**
     * Creates a table with a chosen gap height.
     *
     * @param top the top of every gap, in px
     */
    public FixedSpawnTable(double top) {
        super(Map.of(ObstacleKind.PIPE_GATE, 100));
        this.top = top;
    }

    @Override
    public SpawnDecision rollFirst(Random obstacle, boolean forceMoving) {
        return decision();
    }

    @Override
    public SpawnDecision roll(Random spawn, Random obstacle, double movingChance,
            boolean forceMoving) {
        return decision();
    }

    private SpawnDecision decision() {
        return new SpawnDecision(ObstacleKind.PIPE_GATE, PipeGate.Layout.STANDARD, false, top, 0,
                0);
    }

    /**
     * The gap top every gate uses.
     *
     * @return the y
     */
    public double top() {
        return top;
    }
}
