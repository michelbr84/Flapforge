package io.github.michelbr84.flapforge.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.Random;

/**
 * The render-side particle pool (D18): a flap puff, a crash burst and a UI select sparkle.
 *
 * <p>Design constraints, all of them load-bearing:
 * <ul>
 *   <li><b>Structure of arrays.</b> Position, velocity, life, size and colour live in parallel
 *       primitive arrays, so a particle is never an object and the pool never produces garbage.
 *       </li>
 *   <li><b>Pooled, fixed capacity.</b> The arrays are allocated once; an emit that would exceed
 *       {@link #capacity()} is dropped rather than growing the pool, and a dead particle is
 *       removed by swapping the last live one into its slot. {@link #capacity()} is therefore a
 *       constant a test can watch across ten thousand updates.</li>
 *   <li><b>Its own random source.</b> Particles are decoration, never simulation: they must not
 *       draw from the run's streams or a replay would depend on how many frames were drawn. The
 *       default constructor seeds itself from the platform; a test passes a seeded
 *       {@link Random} and gets identical output every time.</li>
 *   <li><b>Render-side dt.</b> {@link #update(double)} takes elapsed seconds rather than a tick
 *       count, so the caller decides whether particles run on the simulation step (the game does,
 *       for reproducible screenshots) or on real frame time.</li>
 *   <li><b>{@code settings.reduceFlashing}.</b> When it is on, bright particles are damped: the
 *       burst emits fewer of them, none of the white flash sparks are emitted at all and every
 *       particle's peak alpha is capped, so nothing strobes.</li>
 * </ul>
 */
public final class ParticleSystem {

    /** Particles the pool holds by default. */
    public static final int DEFAULT_CAPACITY = 384;
    /** Downward acceleration applied to every particle, in logical px/s². */
    public static final double GRAVITY = 260.0;
    /** Peak alpha a particle may reach while {@code reduceFlashing} is on. */
    public static final double REDUCED_PEAK_ALPHA = 0.45;
    /** Fraction of a burst that is emitted while {@code reduceFlashing} is on. */
    public static final double REDUCED_COUNT_FACTOR = 0.5;
    /** Particles one flap puff emits. */
    public static final int FLAP_PUFF_COUNT = 7;
    /** Particles one crash burst emits. */
    public static final int CRASH_BURST_COUNT = 26;
    /** Particles one UI sparkle emits. */
    public static final int UI_SPARKLE_COUNT = 10;

    private static volatile boolean defaultReduceFlashing = Boolean.TRUE;

    private final Random random;
    private final int capacity;
    private final double[] x;
    private final double[] y;
    private final double[] vx;
    private final double[] vy;
    private final double[] life;
    private final double[] maxLife;
    private final double[] size;
    private final int[] rgb;
    private final double[] peakAlpha;
    private final double[] gravityScale;
    private final Color[] colorCache = new Color[ALPHA_STEPS * COLOR_SLOTS];
    private final int[] colorCacheRgb = new int[COLOR_SLOTS];
    private int count;
    private boolean reduceFlashing = defaultReduceFlashing;

    private static final int ALPHA_STEPS = 16;
    private static final int COLOR_SLOTS = 8;

    /** Creates a pool of {@link #DEFAULT_CAPACITY} particles with its own random source. */
    public ParticleSystem() {
        this(new Random(), DEFAULT_CAPACITY);
    }

    /**
     * Creates a pool of {@link #DEFAULT_CAPACITY} particles with a caller-supplied source.
     *
     * @param random the source (a seeded one makes the output reproducible)
     */
    public ParticleSystem(Random random) {
        this(random, DEFAULT_CAPACITY);
    }

    /**
     * Creates a pool.
     *
     * @param random the random source
     * @param capacity how many particles may be alive at once (at least 1)
     */
    public ParticleSystem(Random random, int capacity) {
        this.random = Objects.requireNonNull(random, "random");
        this.capacity = Math.max(1, capacity);
        this.x = new double[this.capacity];
        this.y = new double[this.capacity];
        this.vx = new double[this.capacity];
        this.vy = new double[this.capacity];
        this.life = new double[this.capacity];
        this.maxLife = new double[this.capacity];
        this.size = new double[this.capacity];
        this.rgb = new int[this.capacity];
        this.peakAlpha = new double[this.capacity];
        this.gravityScale = new double[this.capacity];
    }

    /**
     * The accessibility default new pools adopt, so a pool created after the player changed the
     * setting honours it without being told.
     *
     * @param value {@code settings.reduceFlashing}
     */
    public static void setDefaultReduceFlashing(boolean value) {
        defaultReduceFlashing = value;
    }

    /**
     * The accessibility default new pools adopt.
     *
     * @return {@code true} when bright particles are damped by default
     */
    public static boolean defaultReduceFlashing() {
        return defaultReduceFlashing;
    }

    /**
     * Whether this pool damps bright particles.
     *
     * @return {@code true} when damping
     */
    public boolean isReduceFlashing() {
        return reduceFlashing;
    }

    /**
     * Turns damping on or off for this pool.
     *
     * @param value {@code settings.reduceFlashing}
     */
    public void setReduceFlashing(boolean value) {
        this.reduceFlashing = value;
    }

    /**
     * How many particles the pool can hold; constant for the life of the pool.
     *
     * @return the capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * How many particles are alive.
     *
     * @return the count
     */
    public int count() {
        return count;
    }

    /**
     * Whether nothing is alive.
     *
     * @return {@code true} when the pool is empty
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /** Kills every particle. */
    public void clear() {
        count = 0;
    }

    /**
     * Position of a live particle (tests).
     *
     * @param index the slot in {@code [0, count)}
     * @return the x in logical pixels
     */
    public double x(int index) {
        return x[index];
    }

    /**
     * Position of a live particle (tests).
     *
     * @param index the slot in {@code [0, count)}
     * @return the y in logical pixels
     */
    public double y(int index) {
        return y[index];
    }

    /**
     * Remaining life of a live particle (tests).
     *
     * @param index the slot in {@code [0, count)}
     * @return seconds
     */
    public double life(int index) {
        return life[index];
    }

    /**
     * Current alpha of a live particle: it fades linearly over its life, capped by the
     * accessibility setting.
     *
     * @param index the slot in {@code [0, count)}
     * @return a value in {@code [0, 1]}
     */
    public double alpha(int index) {
        double t = maxLife[index] <= 0 ? 0 : life[index] / maxLife[index];
        return Math.max(0, Math.min(1, t)) * peakAlpha[index];
    }

    /**
     * The brightest particle alive, which is what {@code reduceFlashing} bounds.
     *
     * @return the largest {@link #alpha(int)}, or {@code 0} when the pool is empty
     */
    public double peakAlpha() {
        double peak = 0;
        for (int i = 0; i < count; i++) {
            peak = Math.max(peak, alpha(i));
        }
        return peak;
    }

    /**
     * Emits the small puff a flap leaves behind the bird.
     *
     * @param px the emission x in logical coordinates
     * @param py the emission y in logical coordinates
     */
    public void emitFlapPuff(double px, double py) {
        int n = scaled(FLAP_PUFF_COUNT);
        for (int i = 0; i < n; i++) {
            spawn(px - 6 + random.nextDouble() * 6, py + 4 + random.nextDouble() * 6,
                    -40 - random.nextDouble() * 50, 10 + random.nextDouble() * 40,
                    0.30 + random.nextDouble() * 0.20, 2.5 + random.nextDouble() * 2.0,
                    0xF4F8F8, 0.55, 0.25);
        }
    }

    /**
     * Emits the burst of a crash.
     *
     * @param px the emission x in logical coordinates
     * @param py the emission y in logical coordinates
     * @param tint the debris colour as {@code 0xRRGGBB}
     */
    public void emitCrashBurst(double px, double py, int tint) {
        int n = scaled(CRASH_BURST_COUNT);
        for (int i = 0; i < n; i++) {
            double angle = random.nextDouble() * 2 - 1;
            double speed = 70 + random.nextDouble() * 150;
            spawn(px, py, angle * speed, -60 + (random.nextDouble() * 2 - 1) * speed,
                    0.45 + random.nextDouble() * 0.35, 2.0 + random.nextDouble() * 3.0,
                    tint, 0.9, 1.0);
        }
        if (!reduceFlashing) {
            // The white flash sparks are the part that strobes; they are simply not emitted
            // while reduceFlashing is on, rather than emitted and dimmed.
            for (int i = 0; i < 6; i++) {
                spawn(px, py, (random.nextDouble() * 2 - 1) * 110,
                        (random.nextDouble() * 2 - 1) * 110, 0.16 + random.nextDouble() * 0.10,
                        3.0 + random.nextDouble() * 2.0, 0xFFFFFF, 1.0, 0.2);
            }
        }
    }

    /**
     * Emits the sparkle a UI activation leaves on a button.
     *
     * @param px the emission x in logical coordinates
     * @param py the emission y in logical coordinates
     * @param tint the sparkle colour as {@code 0xRRGGBB}
     */
    public void emitUiSparkle(double px, double py, int tint) {
        int n = scaled(UI_SPARKLE_COUNT);
        for (int i = 0; i < n; i++) {
            double angle = random.nextDouble() * 2 - 1;
            double speed = 30 + random.nextDouble() * 70;
            spawn(px, py, angle * speed, -20 - random.nextDouble() * 60,
                    0.35 + random.nextDouble() * 0.25, 1.5 + random.nextDouble() * 2.0,
                    tint, 0.85, 0.55);
        }
    }

    private int scaled(int wanted) {
        return reduceFlashing ? Math.max(1, (int) Math.round(wanted * REDUCED_COUNT_FACTOR))
                : wanted;
    }

    private void spawn(double px, double py, double dx, double dy, double lifeSeconds,
            double radius, int tint, double peak, double gravity) {
        if (count >= capacity) {
            return;
        }
        int i = count++;
        x[i] = px;
        y[i] = py;
        vx[i] = dx;
        vy[i] = dy;
        life[i] = lifeSeconds;
        maxLife[i] = lifeSeconds;
        size[i] = radius;
        rgb[i] = tint;
        peakAlpha[i] = reduceFlashing ? Math.min(peak, REDUCED_PEAK_ALPHA) : peak;
        gravityScale[i] = gravity;
    }

    /**
     * Integrates every particle and removes the dead ones. Allocation-free.
     *
     * @param dtSeconds elapsed time; values that are not finite or not positive are ignored
     */
    public void update(double dtSeconds) {
        if (!(dtSeconds > 0) || !Double.isFinite(dtSeconds)) {
            return;
        }
        int i = 0;
        while (i < count) {
            life[i] -= dtSeconds;
            if (life[i] <= 0) {
                int last = --count;
                if (i != last) {
                    x[i] = x[last];
                    y[i] = y[last];
                    vx[i] = vx[last];
                    vy[i] = vy[last];
                    life[i] = life[last];
                    maxLife[i] = maxLife[last];
                    size[i] = size[last];
                    rgb[i] = rgb[last];
                    peakAlpha[i] = peakAlpha[last];
                    gravityScale[i] = gravityScale[last];
                }
                continue;
            }
            vy[i] += GRAVITY * gravityScale[i] * dtSeconds;
            x[i] += vx[i] * dtSeconds;
            y[i] += vy[i] * dtSeconds;
            i++;
        }
    }

    /**
     * Draws every live particle as a soft dot. Colours are quantised into a small cache, so a
     * steady frame allocates nothing.
     *
     * @param g the context in logical coordinates
     */
    public void render(Graphics2D g) {
        for (int i = 0; i < count; i++) {
            double a = alpha(i);
            if (a <= 0.01) {
                continue;
            }
            Color color = color(rgb[i], a);
            if (color == null) {
                continue;
            }
            g.setColor(color);
            int d = (int) Math.round(size[i] * 2);
            if (d < 1) {
                d = 1;
            }
            g.fillOval((int) Math.round(x[i] - size[i]), (int) Math.round(y[i] - size[i]), d, d);
        }
    }

    /** Quantised colour cache: {@value #COLOR_SLOTS} tints times {@value #ALPHA_STEPS} levels. */
    private Color color(int tint, double alpha) {
        int step = (int) (alpha * (ALPHA_STEPS - 1) + 0.5);
        if (step <= 0) {
            return null;
        }
        int slot = -1;
        for (int s = 0; s < COLOR_SLOTS; s++) {
            if (colorCacheRgb[s] == tint) {
                slot = s;
                break;
            }
        }
        if (slot < 0) {
            slot = Math.floorMod(tint, COLOR_SLOTS);
            if (colorCacheRgb[slot] != tint) {
                colorCacheRgb[slot] = tint;
                for (int s = 0; s < ALPHA_STEPS; s++) {
                    colorCache[slot * ALPHA_STEPS + s] = null;
                }
            }
        }
        int index = slot * ALPHA_STEPS + step;
        Color cached = colorCache[index];
        if (cached == null) {
            int a = (int) Math.round(255.0 * step / (ALPHA_STEPS - 1));
            cached = new Color((tint & 0xFFFFFF) | (a << 24), true);
            colorCache[index] = cached;
        }
        return cached;
    }

    @Override
    public String toString() {
        return "ParticleSystem[" + count + "/" + capacity
                + (reduceFlashing ? " damped]" : "]");
    }
}
