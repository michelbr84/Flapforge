package io.github.michelbr84.flapforge.core;

/**
 * Logical playfield geometry and timing constants shared by simulation, rendering and UI.
 *
 * <p>All values are expressed in logical pixels of the fixed 420x640 playfield (D3) and in
 * simulation ticks at {@link #TICK_RATE} Hz. They are the single source of truth for every
 * subsystem; nothing derives geometry from image sizes.
 */
public final class Playfield {

    /** Logical width of the playfield in pixels. */
    public static final int WIDTH = 420;
    /** Logical height of the playfield in pixels. */
    public static final int HEIGHT = 640;
    /** Height of the scrolling ground strip at the bottom of the playfield. */
    public static final int GROUND_HEIGHT = 42;
    /** Y coordinate of the top edge of the ground strip. */
    public static final int GROUND_Y = 598;
    /** Fixed x coordinate of the bird sprite origin. */
    public static final int BIRD_X = 105;
    /** Y coordinate of the bird sprite origin at the start of a run. */
    public static final int BIRD_START_Y = 320;
    /** Width of the bird sprite. */
    public static final int SPRITE_W = 39;
    /** Height of the bird sprite. */
    public static final int SPRITE_H = 33;
    /** The bird dies when its y coordinate reaches this value (sprite bottom touches the ground). */
    public static final double GROUND_DEATH_Y = 581.5;
    /** Above this y coordinate a flap is not allowed (upstream ceiling gate). */
    public static final int CEILING_FLAP_Y = 32;
    /** Vertical size of the gap between the two halves of a pipe gate. */
    public static final int GAP = 128;
    /** Horizontal distance between consecutive gates. */
    public static final int GATE_INTERVAL = 160;
    /** Width of a pipe body (the lethal hitbox width). */
    public static final int PIPE_BODY_W = 40;
    /** Width of the decorative pipe cap. */
    public static final int PIPE_CAP_W = 44;
    /** Height of the decorative pipe cap. */
    public static final int PIPE_CAP_H = 25;
    /** Extra height of the top pipe above the playfield so it never shows its end. */
    public static final int TOP_PIPE_EXTRA = 100;
    /** Ticks between synthetic flaps while hold-to-flap is engaged. */
    public static final int AUTO_FLAP_PERIOD_TICKS = 24;
    /** Inflation of the bird hitbox used to detect near misses. */
    public static final int NEAR_MISS_INFLATE_PX = 6;
    /** Clouds scroll at the ground speed divided by this factor. */
    public static final int CLOUD_SPEED_FACTOR = 2;
    /** Maximum number of clouds alive at once. */
    public static final int CLOUD_MAX = 7;
    /** Per-tick percentage chance of spawning a cloud. */
    public static final int CLOUD_SPAWN_PCT = 6;
    /** Simulation ticks per second. */
    public static final int TICK_RATE = 60;
    /** Duration of one simulation tick in nanoseconds. */
    public static final long TICK_NS = 16_666_667L;
    /** Duration of one simulation tick in seconds. */
    public static final double TICK_SECONDS = 1.0 / TICK_RATE;

    private Playfield() {
    }
}
