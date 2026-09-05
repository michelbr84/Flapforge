package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.Playfield;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vector drawing helpers for everything the game shows without shipped images (D18): the app
 * icon, the world backdrop, UI panels and buttons, and a stylised bird.
 *
 * <p>Every routine is deterministic — animation phases are parameters, never wall-clock time —
 * and the per-frame paths allocate nothing: colours and paints derived from a
 * {@link WorldPalette} are resolved once and cached, unit shapes are shared and drawn through the
 * context transform, strokes are constants. Only {@link #icon(int)} builds images, at start-up.
 * Callers pass a context already in logical coordinates (D3) unless stated otherwise.
 */
public final class ProceduralArt {

    /** Pose of a drawn bird (§5 cosmetic row: upstream has an "up" and a "dead" sprite). */
    public enum BirdPose {
        /** Level flight; the wing animates with the phase. */
        NORMAL,
        /** Rising ({@code vy < 0}): nose up, wing held at the top of its stroke. */
        UP,
        /** Dead: nose down, wing folded, eye closed. */
        DEAD
    }

    /**
     * A colour derived from a {@link WorldPalette}. Values are resolved and cached per palette so
     * per-frame lookups allocate nothing.
     */
    public enum Tone {
        /** Sky colour at the top of the playfield. */
        SKY_TOP,
        /** Sky colour just above the ground. */
        SKY_BOTTOM,
        /** Ground strip fill. */
        GROUND,
        /** Darker line along the top of the ground strip. */
        GROUND_EDGE,
        /** Distant hill band. */
        HILL_FAR,
        /** Near hill band. */
        HILL_NEAR,
        /** Cloud fill (translucent). */
        CLOUD,
        /** Obstacle body. */
        PIPE,
        /** Obstacle outline and shading. */
        PIPE_EDGE,
        /** Obstacle highlight stripe. */
        PIPE_LIGHT,
        /** Highlight colour (bird body, title, focus ring). */
        ACCENT,
        /** Darker accent (bird wing and tail). */
        ACCENT_DARK,
        /** Bird belly. */
        BELLY,
        /** Letterbox tone, also used for outlines. */
        LETTERBOX
    }

    /** Visual state of a button. */
    public enum ButtonState {
        /** Idle. */
        NORMAL,
        /** Pointer over the button but focus elsewhere. */
        HOVER,
        /** Keyboard focus (also set when the pointer moved onto it). */
        FOCUSED,
        /** Not interactive. */
        DISABLED;

        /**
         * Picks the state for a node's flags (disabled wins, then focus, then hover).
         *
         * @param enabled whether the node is enabled
         * @param focused whether the node has focus
         * @param hovered whether the pointer is over the node
         * @return the state
         */
        public static ButtonState of(boolean enabled, boolean focused, boolean hovered) {
            if (!enabled) {
                return DISABLED;
            }
            if (focused) {
                return FOCUSED;
            }
            return hovered ? HOVER : NORMAL;
        }
    }

    /** Icon sizes handed to the window (D4). */
    public static final List<Integer> ICON_SIZES = List.of(16, 32, 64, 128);
    /** Corner radius of panels. */
    public static final int PANEL_RADIUS = 14;
    /** Corner radius of buttons. */
    public static final int BUTTON_RADIUS = 10;

    /** Light text on dark UI. */
    public static final Color TEXT_LIGHT = new Color(0xF4F8F8);
    /** Dark text on the accent colour. */
    public static final Color TEXT_DARK = new Color(0x1C3A3E);
    /** Secondary text. */
    public static final Color TEXT_MUTED = new Color(0xA9BABC);
    /** Ticks one coin needs for a full turn (D18). */
    public static final int COIN_SPIN_TICKS = 48;
    /** The gold a coin is drawn in; the HUD and the currency display share it. */
    public static final Color COIN_GOLD = new Color(0xF5C542);

    private static volatile boolean smoothing = true;

    /** Narrowest a spinning coin gets, as a fraction of its radius. */
    private static final double COIN_MIN_SQUASH = 0.12;
    /** Below this visible width no highlight is drawn: the coin is seen almost edge on. */
    private static final double COIN_SHINE_MIN_WIDTH = 0.35;
    private static final Color COIN_BODY = COIN_GOLD;
    private static final Color COIN_RIM = new Color(0xB8860B);
    private static final Color COIN_SHINE = new Color(0xFFF3C4);
    /**
     * The squash of a coin at every phase of its turn, tabulated once. Building it here costs one
     * array of {@value #COIN_SPIN_TICKS} doubles and removes {@link Math#cos} from every frame.
     */
    private static final double[] COIN_SPIN = coinSpinTable();

    private static double[] coinSpinTable() {
        double[] table = new double[COIN_SPIN_TICKS];
        for (int i = 0; i < COIN_SPIN_TICKS; i++) {
            table[i] = Math.cos(2 * Math.PI * i / COIN_SPIN_TICKS);
        }
        return table;
    }

    private static final Color PANEL_FILL = new Color(0x1C, 0x3A, 0x3E, 0xD2);
    private static final Color PANEL_BORDER = new Color(0xFF, 0xFF, 0xFF, 0x59);
    /** High-contrast panel: nearly opaque fill and a near-white border (D17). */
    private static final Color PANEL_FILL_HC = new Color(0x14, 0x24, 0x26, 0xFA);
    private static final Color PANEL_BORDER_HC = new Color(0xFF, 0xFF, 0xFF, 0xE0);
    private static final Color SHADOW = new Color(0, 0, 0, 0x40);
    private static final Color BUTTON_NORMAL = new Color(0x2E6B72);
    private static final Color BUTTON_HOVER = new Color(0x3C8A92);
    private static final Color BUTTON_FOCUSED = new Color(0xF5C542);
    private static final Color BUTTON_DISABLED = new Color(0x2A3A3C);
    private static final Color BUTTON_BORDER = new Color(0x8F, 0xDD, 0xE3, 0x99);
    private static final Color BUTTON_BORDER_FOCUSED = new Color(0xFF, 0xF3, 0xC4);
    private static final Color BUTTON_BORDER_DISABLED = new Color(0x6E7A7C);
    private static final Color FOCUS_RING = new Color(0xFFFFFF);
    private static final Color EYE_WHITE = new Color(0xFFFFFF);
    private static final Color EYE_PUPIL = new Color(0x1C2A2C);
    private static final Color BEAK = new Color(0xE8562A);

    private static final Stroke THIN = new BasicStroke(1f);
    private static final Stroke THICK = new BasicStroke(2f);
    /** High-contrast bird keyline, in unit space (the bird body is 1 unit wide). */
    private static final Stroke BIRD_OUTLINE = new BasicStroke(0.06f);

    /* Unit-space bird facing right; 1 unit = body width, origin at the body centre. */
    private static final Shape BIRD_BODY = new Ellipse2D.Double(-0.5, -0.38, 1.0, 0.76);
    private static final Shape BIRD_BELLY = new Ellipse2D.Double(-0.30, -0.02, 0.60, 0.36);
    private static final Shape BIRD_EYE = new Ellipse2D.Double(0.10, -0.32, 0.30, 0.30);
    private static final Shape BIRD_PUPIL = new Ellipse2D.Double(0.24, -0.24, 0.13, 0.13);
    private static final Shape BIRD_BEAK = polygon(0.44, -0.04, 0.74, 0.06, 0.44, 0.16);
    private static final Shape BIRD_TAIL = polygon(-0.46, -0.12, -0.72, -0.28, -0.60, 0.06);
    private static final Shape BIRD_WING = polygon(-0.12, -0.02, -0.56, 0.12, -0.46, 0.30,
            0.04, 0.18);
    /** Stroke of the one archetype detail a portrait draws (D18, M4). */
    private static final Stroke PORTRAIT_MARK = new BasicStroke(0.055f);
    /** Toolkit colours of the palette values a portrait is drawn with, cached by value. */
    private static final Map<Integer, Color> PORTRAIT_COLORS = new ConcurrentHashMap<>();
    private static final double WING_PIVOT_X = -0.12;
    private static final double WING_PIVOT_Y = -0.02;
    private static final double BIRD_TILT_UP = -0.28;
    private static final double BIRD_TILT_DEAD = 0.62;
    private static final double WING_PHASE_UP = 0.5;
    private static final double WING_PHASE_DEAD = 0.0;
    private static final Stroke EYE_CROSS = new BasicStroke(0.05f);
    private static final Shape EYE_CROSS_A = new Line2D.Double(0.14, -0.26, 0.34, -0.06);
    private static final Shape EYE_CROSS_B = new Line2D.Double(0.34, -0.26, 0.14, -0.06);

    /* Unit-space anvil; 1 unit = width, origin at the top-left of the face, height 0.5. */
    private static final Shape ANVIL = polygon(0.00, 0.04, 0.20, 0.00, 1.00, 0.00, 0.96, 0.20,
            0.62, 0.22, 0.66, 0.36, 0.84, 0.40, 0.84, 0.50, 0.16, 0.50, 0.16, 0.40, 0.34, 0.36,
            0.38, 0.22, 0.22, 0.20, 0.00, 0.12);

    private static final Map<PaletteKey, Resolved> RESOLVED = new ConcurrentHashMap<>();

    /** Cache key: the palette plus the high-contrast flag, which changes the derived colours. */
    private record PaletteKey(WorldPalette palette, boolean highContrast) {
    }

    /** Toolkit objects derived from one palette, built once. */
    private static final class Resolved {
        final Color skyTop;
        final Color skyBottom;
        final Color ground;
        final Color groundEdge;
        final Color hillFar;
        final Color hillNear;
        final Color cloud;
        final Color pipe;
        final Color pipeEdge;
        final Color pipeLight;
        final Color accent;
        final Color accentDark;
        final Color belly;
        final Color letterbox;
        final Paint sky;
        final Color[] tones = new Color[Tone.values().length];

        Resolved(WorldPalette p, boolean highContrast) {
            // High contrast stretches the obstacle tones apart (D17): a darker outline colour and
            // a brighter highlight, so the hazard edge reads without relying on hue.
            double edgeStretch = highContrast ? 0.58 : 0.42;
            double lightStretch = highContrast ? 0.55 : 0.35;
            skyTop = new Color(p.skyTop());
            skyBottom = new Color(p.skyBottom());
            ground = new Color(p.ground());
            groundEdge = new Color(WorldPalette.darken(p.ground(), 0.28));
            hillFar = new Color(WorldPalette.mix(p.pipe(), p.skyBottom(), 0.55));
            hillNear = new Color(WorldPalette.lighten(p.pipe(), 0.18));
            int fog = p.fog();
            cloud = new Color((fog >> 16) & 0xFF, (fog >> 8) & 0xFF, fog & 0xFF,
                    highContrast ? 0xEE : 0xD9);
            pipe = new Color(p.pipe());
            pipeEdge = new Color(WorldPalette.darken(p.pipe(), edgeStretch));
            pipeLight = new Color(WorldPalette.lighten(p.pipe(), lightStretch));
            accent = new Color(p.accent());
            accentDark = new Color(WorldPalette.mix(p.accent(), 0xC0501A, 0.55));
            belly = new Color(WorldPalette.lighten(p.accent(), 0.45));
            letterbox = new Color(p.letterbox());
            sky = new GradientPaint(0f, 0f, skyTop, 0f, (float) Playfield.GROUND_Y, skyBottom);
            tones[Tone.SKY_TOP.ordinal()] = skyTop;
            tones[Tone.SKY_BOTTOM.ordinal()] = skyBottom;
            tones[Tone.GROUND.ordinal()] = ground;
            tones[Tone.GROUND_EDGE.ordinal()] = groundEdge;
            tones[Tone.HILL_FAR.ordinal()] = hillFar;
            tones[Tone.HILL_NEAR.ordinal()] = hillNear;
            tones[Tone.CLOUD.ordinal()] = cloud;
            tones[Tone.PIPE.ordinal()] = pipe;
            tones[Tone.PIPE_EDGE.ordinal()] = pipeEdge;
            tones[Tone.PIPE_LIGHT.ordinal()] = pipeLight;
            tones[Tone.ACCENT.ordinal()] = accent;
            tones[Tone.ACCENT_DARK.ordinal()] = accentDark;
            tones[Tone.BELLY.ordinal()] = belly;
            tones[Tone.LETTERBOX.ordinal()] = letterbox;
        }
    }

    private ProceduralArt() {
    }

    /**
     * Turns shape smoothing on or off ({@code settings.smoothing}, D3).
     *
     * <p>Text antialiasing is deliberately <em>not</em> part of the switch: unsmoothed glyphs at
     * 420x640 are hard to read and the setting is about the look of the art, not about making the
     * game unreadable. What it does control is shape antialiasing, image interpolation (nearest
     * neighbour when off, so a future sprite pack stays crisp at integer scales) and the quality
     * hint.
     *
     * @param value {@code true} for smoothed edges (the default)
     */
    public static void setSmoothing(boolean value) {
        smoothing = value;
    }

    /**
     * Whether shapes are smoothed.
     *
     * @return {@code true} when smoothing is on
     */
    public static boolean isSmoothing() {
        return smoothing;
    }

    /**
     * Enables text antialiasing, pure stroke control and — when {@link #isSmoothing()} — shape
     * antialiasing and bilinear image interpolation on a context.
     *
     * @param g the context
     */
    public static void prepare(Graphics2D g) {
        TextPainter.prepare(g);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        boolean smooth = smoothing;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, smooth
                ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, smooth
                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, smooth
                ? RenderingHints.VALUE_RENDER_QUALITY : RenderingHints.VALUE_RENDER_SPEED);
    }

    /**
     * Fills the whole logical playfield with the world backdrop: sky gradient, clouds, two hill
     * bands and the ground strip.
     *
     * @param g the context in logical coordinates
     * @param palette the world palette
     */
    public static void fillBackground(Graphics2D g, WorldPalette palette) {
        Resolved r = resolve(palette);
        Paint oldPaint = g.getPaint();
        g.setPaint(r.sky);
        // Sky and ground run to the visible edges rather than rows 0/640, so on tall windows
        // (portrait phones) every screen fills the frame — the acyclic gradient clamps to its
        // top colour above row 0.
        g.fillRect(0, Overscan.topInt(), Playfield.WIDTH,
                Playfield.GROUND_Y - Overscan.topInt());
        g.setPaint(oldPaint);

        g.setColor(r.cloud);
        cloud(g, 72, 96, 1.0);
        cloud(g, 300, 150, 0.85);
        cloud(g, 190, 236, 0.6);

        g.setColor(r.hillFar);
        g.fillOval(-90, 430, 330, 240);
        g.fillOval(140, 410, 380, 260);
        g.fillOval(320, 440, 280, 220);
        g.setColor(r.hillNear);
        g.fillOval(-60, 500, 280, 180);
        g.fillOval(180, 512, 320, 170);

        g.setColor(r.ground);
        g.fillRect(0, Playfield.GROUND_Y, Playfield.WIDTH,
                Overscan.bottomInt() - Playfield.GROUND_Y);
        g.setColor(r.groundEdge);
        g.fillRect(0, Playfield.GROUND_Y, Playfield.WIDTH, 4);
    }

    private static void cloud(Graphics2D g, int cx, int cy, double s) {
        drawCloud(g, new Ellipse2D.Double(), cx - 24 * s, cy - 16.5 * s, 48 * s, 33 * s, 0);
    }

    /**
     * Draws one cloud (a cluster of ellipses) into a box, in the context's current colour.
     *
     * <p>Two silhouettes reproduce upstream's two cloud images: variant 0 is the wide 48x33 puff,
     * variant 1 the rounder 40x32 one. The caller owns the scratch ellipse the puffs are filled
     * through, so a per-frame call allocates nothing.
     *
     * @param g the context
     * @param scratch a reusable ellipse owned by the caller (its state is overwritten)
     * @param x the left edge of the box
     * @param y the top edge of the box
     * @param w the box width
     * @param h the box height
     * @param variant the silhouette, {@code 0} or {@code 1} (other values wrap)
     */
    public static void drawCloud(Graphics2D g, Ellipse2D.Double scratch, double x, double y,
            double w, double h, int variant) {
        if (Math.floorMod(variant, 2) == 0) {
            puff(g, scratch, x, y + h * 0.30, w * 0.52, h * 0.70);
            puff(g, scratch, x + w * 0.24, y, w * 0.54, h * 0.82);
            puff(g, scratch, x + w * 0.52, y + h * 0.24, w * 0.48, h * 0.76);
            puff(g, scratch, x + w * 0.08, y + h * 0.52, w * 0.84, h * 0.48);
        } else {
            puff(g, scratch, x + w * 0.06, y + h * 0.26, w * 0.56, h * 0.74);
            puff(g, scratch, x + w * 0.30, y, w * 0.62, h * 0.72);
            puff(g, scratch, x + w * 0.40, y + h * 0.34, w * 0.58, h * 0.66);
        }
    }

    private static void puff(Graphics2D g, Ellipse2D.Double scratch, double x, double y, double w,
            double h) {
        scratch.setFrame(x, y, w, h);
        g.fill(scratch);
    }

    /**
     * Draws a translucent dark panel with a drop shadow and a light border.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     */
    public static void panel(Graphics2D g, int x, int y, int w, int h) {
        boolean highContrast = Accessibility.isHighContrast();
        g.setColor(SHADOW);
        g.fillRoundRect(x, y + 4, w, h, PANEL_RADIUS, PANEL_RADIUS);
        g.setColor(highContrast ? PANEL_FILL_HC : PANEL_FILL);
        g.fillRoundRect(x, y, w, h, PANEL_RADIUS, PANEL_RADIUS);
        Stroke old = g.getStroke();
        g.setStroke(highContrast ? THICK : THIN);
        g.setColor(highContrast ? PANEL_BORDER_HC : PANEL_BORDER);
        g.drawRoundRect(x, y, w - 1, h - 1, PANEL_RADIUS, PANEL_RADIUS);
        g.setStroke(old);
    }

    /**
     * Draws a button body (no label) in the given state.
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param w the width
     * @param h the height
     * @param state the visual state
     */
    public static void button(Graphics2D g, int x, int y, int w, int h, ButtonState state) {
        Color fill;
        Color border;
        switch (state) {
            case HOVER:
                fill = BUTTON_HOVER;
                border = BUTTON_BORDER;
                break;
            case FOCUSED:
                fill = BUTTON_FOCUSED;
                border = BUTTON_BORDER_FOCUSED;
                break;
            case DISABLED:
                fill = BUTTON_DISABLED;
                border = BUTTON_BORDER_DISABLED;
                break;
            default:
                fill = BUTTON_NORMAL;
                border = BUTTON_BORDER;
                break;
        }
        Stroke old = g.getStroke();
        if (state != ButtonState.DISABLED) {
            g.setColor(SHADOW);
            g.fillRoundRect(x, y + 3, w, h, BUTTON_RADIUS, BUTTON_RADIUS);
        }
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, BUTTON_RADIUS, BUTTON_RADIUS);
        if (state == ButtonState.FOCUSED) {
            g.setStroke(THICK);
            g.setColor(FOCUS_RING);
            g.drawRoundRect(x - 3, y - 3, w + 5, h + 5, BUTTON_RADIUS + 3, BUTTON_RADIUS + 3);
        }
        g.setStroke(THIN);
        g.setColor(border);
        g.drawRoundRect(x, y, w - 1, h - 1, BUTTON_RADIUS, BUTTON_RADIUS);
        g.setStroke(old);
    }

    /**
     * Label colour matching {@link #button}.
     *
     * @param state the visual state
     * @return the colour
     */
    public static Color buttonTextColor(ButtonState state) {
        switch (state) {
            case FOCUSED:
                return TEXT_DARK;
            case DISABLED:
                return TEXT_MUTED;
            default:
                return TEXT_LIGHT;
        }
    }

    /**
     * Draws a stylised bird facing right.
     *
     * @param g the context
     * @param cx the body centre x
     * @param cy the body centre y
     * @param size the body width (the body is {@code 0.76 * size} tall)
     * @param wingPhase animation phase in {@code [0, 1)}: wing down at 0, up at 0.5
     * @param palette the palette providing the body (accent) colour
     */
    public static void drawBird(Graphics2D g, double cx, double cy, double size, double wingPhase,
            WorldPalette palette) {
        drawBird(g, cx, cy, size, wingPhase, palette, BirdPose.NORMAL);
    }

    /**
     * Draws a stylised bird facing right in one of the three upstream poses.
     *
     * @param g the context
     * @param cx the body centre x
     * @param cy the body centre y
     * @param size the body width (the body is {@code 0.76 * size} tall)
     * @param wingPhase animation phase in {@code [0, 1)}: wing down at 0, up at 0.5 (ignored
     *     outside {@link BirdPose#NORMAL})
     * @param palette the palette providing the body (accent) colour
     * @param pose the pose
     */
    public static void drawBird(Graphics2D g, double cx, double cy, double size, double wingPhase,
            WorldPalette palette, BirdPose pose) {
        Resolved r = resolve(palette);
        double tilt = pose == BirdPose.UP ? BIRD_TILT_UP
                : (pose == BirdPose.DEAD ? BIRD_TILT_DEAD : 0);
        double phase = pose == BirdPose.UP ? WING_PHASE_UP
                : (pose == BirdPose.DEAD ? WING_PHASE_DEAD : wingPhase);
        g.translate(cx, cy);
        g.scale(size, size);
        if (tilt != 0) {
            g.rotate(tilt);
        }
        g.setColor(r.accentDark);
        g.fill(BIRD_TAIL);
        g.setColor(r.accent);
        g.fill(BIRD_BODY);
        if (Accessibility.isHighContrast()) {
            // A dark keyline around the body and tail so the bird separates from any sky (D17).
            Stroke old = g.getStroke();
            g.setStroke(BIRD_OUTLINE);
            g.setColor(r.letterbox);
            g.draw(BIRD_TAIL);
            g.draw(BIRD_BODY);
            g.setStroke(old);
        }
        g.setColor(r.belly);
        g.fill(BIRD_BELLY);
        double angle = wingAngle(phase);
        g.rotate(angle, WING_PIVOT_X, WING_PIVOT_Y);
        g.setColor(r.accentDark);
        g.fill(BIRD_WING);
        g.rotate(-angle, WING_PIVOT_X, WING_PIVOT_Y);
        if (pose == BirdPose.DEAD) {
            Stroke old = g.getStroke();
            g.setStroke(EYE_CROSS);
            g.setColor(EYE_PUPIL);
            g.draw(EYE_CROSS_A);
            g.draw(EYE_CROSS_B);
            g.setStroke(old);
        } else {
            g.setColor(EYE_WHITE);
            g.fill(BIRD_EYE);
            g.setColor(EYE_PUPIL);
            g.fill(BIRD_PUPIL);
        }
        g.setColor(BEAK);
        g.fill(BIRD_BEAK);
        if (tilt != 0) {
            g.rotate(-tilt);
        }
        g.scale(1 / size, 1 / size);
        g.translate(-cx, -cy);
    }

    /**
     * Wing rotation for an animation phase: a triangle wave, so it needs no trigonometry.
     *
     * @param phase the phase in {@code [0, 1)} (values outside are wrapped)
     * @return the rotation in radians (positive lifts the wing tip)
     */
    public static double wingAngle(double phase) {
        double p = phase - Math.floor(phase);
        double t = p < 0.5 ? p * 2 : 2 - p * 2;
        return -0.35 + 0.85 * t;
    }

    /**
     * The squash of a spinning coin at an animation phase, read from a table built once (D18: a
     * per-frame call must not allocate and must not depend on the platform's trigonometry).
     *
     * <p>The value is the cosine of the phase, so it walks {@code 1 → 0 → −1 → 0 → 1} over
     * {@value #COIN_SPIN_TICKS} ticks: the sign says which face is turned towards the player and
     * the magnitude is the visible width of the disc as a fraction of its diameter.
     *
     * @param phaseTicks the animation phase in simulation ticks (any value; it is wrapped)
     * @return the squash in {@code [-1, 1]}
     */
    public static double coinSpin(long phaseTicks) {
        return COIN_SPIN[(int) Math.floorMod(phaseTicks, (long) COIN_SPIN_TICKS)];
    }

    /**
     * Draws one spinning coin: a gold disc squashed horizontally by {@code spin}, with a rim and,
     * while a face is turned towards the player, a highlight (D18).
     *
     * <p>The caller owns the ellipse the coin is drawn with, exactly like
     * {@link #drawCloud(Graphics2D, Ellipse2D.Double, double, double, double, double, int)}: a renderer
     * keeps one scratch shape for the life of the run and a frame allocates nothing.
     *
     * @param g the context in logical coordinates
     * @param scratch the caller's reusable ellipse
     * @param cx the centre x
     * @param cy the centre y
     * @param radius the radius of the disc seen face on
     * @param spin the squash from {@link #coinSpin(long)}; {@code ±1} is face on, {@code 0} edge on
     */
    public static void drawCoin(Graphics2D g, Ellipse2D.Double scratch, double cx, double cy,
            double radius, double spin) {
        // Edge on the disc would vanish; a minimum width keeps the coin readable through the
        // whole turn instead of blinking once per revolution.
        double halfWidth = Math.max(radius * COIN_MIN_SQUASH, radius * Math.abs(spin));
        scratch.setFrame(cx - halfWidth, cy - radius, 2 * halfWidth, 2 * radius);
        g.setColor(Accessibility.tone(COIN_BODY, Accessibility.Role.COIN));
        g.fill(scratch);
        Stroke old = g.getStroke();
        g.setStroke(THIN);
        g.setColor(Accessibility.tone(COIN_RIM, Accessibility.Role.COIN));
        g.draw(scratch);
        g.setStroke(old);
        if (halfWidth < radius * COIN_SHINE_MIN_WIDTH) {
            return;
        }
        double shineW = halfWidth * 0.30;
        double shineH = radius * 0.30;
        // The highlight follows the face that is turned towards the player, so the coin reads as
        // one disc turning rather than as two shapes swapping places.
        double shineCx = cx + halfWidth * (spin < 0 ? 0.34 : -0.34);
        scratch.setFrame(shineCx - shineW, cy - radius * 0.45 - shineH, 2 * shineW, 2 * shineH);
        g.setColor(Accessibility.tone(COIN_SHINE, Accessibility.Role.COIN));
        g.fill(scratch);
    }

    /**
     * Draws an anvil silhouette.
     *
     * @param g the context
     * @param cx the horizontal centre
     * @param topY the top edge of the face
     * @param width the width (the anvil is half as tall)
     * @param color the fill colour
     */
    public static void drawAnvil(Graphics2D g, double cx, double topY, double width, Color color) {
        double left = cx - width / 2;
        g.translate(left, topY);
        g.scale(width, width);
        g.setColor(color);
        g.fill(ANVIL);
        g.scale(1 / width, 1 / width);
        g.translate(-left, -topY);
    }

    /**
     * Draws the application icon (a bird perched on an anvil over the Green Fields sky) into a
     * square. Text-free by design (D18).
     *
     * @param g the context
     * @param x the left edge
     * @param y the top edge
     * @param size the side length
     */
    public static void drawIcon(Graphics2D g, double x, double y, double size) {
        WorldPalette palette = WorldPalette.GREEN_FIELDS;
        Resolved r = resolve(palette);
        double radius = size * 0.24;
        RoundRectangle2D tile = new RoundRectangle2D.Double(x, y, size, size, radius, radius);
        Shape oldClip = g.getClip();
        Paint oldPaint = g.getPaint();
        Stroke oldStroke = g.getStroke();
        g.clip(tile);
        g.setPaint(new GradientPaint((float) x, (float) y, r.skyTop, (float) x,
                (float) (y + size), r.skyBottom));
        g.fill(tile);
        g.setPaint(oldPaint);
        g.setColor(r.ground);
        g.fill(new Rectangle2D.Double(x, y + size * 0.82, size, size * 0.18));
        g.setColor(r.groundEdge);
        g.fill(new Rectangle2D.Double(x, y + size * 0.82, size, Math.max(1, size * 0.03)));
        drawAnvil(g, x + size * 0.50, y + size * 0.56, size * 0.70, r.letterbox);
        drawBird(g, x + size * 0.50, y + size * 0.36, size * 0.50, 0.30, palette);
        g.setClip(oldClip);
        if (size >= 32) {
            g.setStroke(THIN);
            g.setColor(r.letterbox);
            g.draw(tile);
        }
        g.setStroke(oldStroke);
    }

    /**
     * Renders the application icon into a new image.
     *
     * @param size the side length in pixels
     * @return an ARGB image
     */
    public static BufferedImage icon(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            prepare(g);
            drawIcon(g, 0, 0, size);
        } finally {
            g.dispose();
        }
        return image;
    }

    /**
     * Renders the icon at every size in {@link #ICON_SIZES}.
     *
     * @return the images, smallest first
     */
    public static List<BufferedImage> icons() {
        List<BufferedImage> out = new ArrayList<>(ICON_SIZES.size());
        for (int size : ICON_SIZES) {
            out.add(icon(size));
        }
        return out;
    }

    /**
     * A palette-derived colour, resolved once per palette and cached (D18: no per-frame
     * allocation).
     *
     * @param palette the palette
     * @param tone which colour
     * @return the toolkit colour
     */
    public static Color color(WorldPalette palette, Tone tone) {
        return resolve(palette).tones[tone.ordinal()];
    }

    /**
     * The cached vertical sky gradient of a palette, spanning {@code [0, GROUND_Y]}.
     *
     * @param palette the palette
     * @return the paint
     */
    public static Paint skyPaint(WorldPalette palette) {
        return resolve(palette).sky;
    }

    /**
     * Toolkit colour of a palette component (cached with the palette).
     *
     * @param palette the palette
     * @return the accent colour
     */
    public static Color accentColor(WorldPalette palette) {
        return resolve(palette).accent;
    }

    /**
     * Toolkit colour of the palette letterbox tone (cached with the palette).
     *
     * @param palette the palette
     * @return the letterbox colour
     */
    public static Color letterboxColor(WorldPalette palette) {
        return resolve(palette).letterbox;
    }

    private static Resolved resolve(WorldPalette palette) {
        PaletteKey key = new PaletteKey(palette, Accessibility.isHighContrast());
        Resolved r = RESOLVED.get(key);
        if (r == null) {
            r = new Resolved(key.palette(), key.highContrast());
            RESOLVED.put(key, r);
        }
        return r;
    }

    /**
     * Drops every cached palette resolution. Called when an accessibility mode changes: the cache
     * key already carries high contrast, so this is for a wholesale reset (settings applied, a
     * test switching modes).
     */
    public static void invalidatePalettes() {
        RESOLVED.clear();
    }

    /**
     * Draws a bird in the colours of one of its cosmetic palettes and the silhouette of its
     * archetype (D18, M4): the portrait the bird selection and the shop show.
     *
     * <p>{@link #drawBird(Graphics2D, double, double, double, double, WorldPalette)} takes its
     * colours from the world palette, because in a run the bird is part of the scene; a portrait
     * is the opposite — the world is irrelevant and the four colours the player bought are the
     * whole point. The shape key stretches the shared unit shapes rather than replacing them, so
     * the seven birds read as seven silhouettes without seven sets of geometry to keep in sync.
     *
     * @param g the context
     * @param cx the body centre x
     * @param cy the body centre y
     * @param size the body width before the silhouette stretch
     * @param wingPhase animation phase in {@code [0, 1)}: wing down at 0, up at 0.5
     * @param bodyRgb the body colour as {@code 0xRRGGBB}
     * @param wingRgb the wing and tail colour
     * @param eyeRgb the eye colour
     * @param accentRgb the beak and detail colour
     * @param shape the silhouette key ({@code balanced}, {@code swift}, {@code heavy},
     *     {@code guardian}, {@code gambler}, {@code mystic}, {@code forge}); anything else is
     *     drawn balanced, which is why {@code BirdDef.SHAPES} is the validated set and this
     *     switch is the other half of that contract
     */
    public static void drawBirdPortrait(Graphics2D g, double cx, double cy, double size,
            double wingPhase, int bodyRgb, int wingRgb, int eyeRgb, int accentRgb, String shape) {
        double sx = size;
        double sy = size;
        switch (shape == null ? "" : shape) {
            case "swift":
                sx = size * 1.12;
                sy = size * 0.84;
                break;
            case "heavy":
                sx = size * 0.94;
                sy = size * 1.16;
                break;
            case "guardian":
                sx = size * 1.04;
                sy = size * 1.04;
                break;
            case "gambler":
                sy = size * 0.94;
                break;
            case "mystic":
                sx = size * 0.94;
                sy = size * 1.06;
                break;
            case "forge":
                sx = size * 1.06;
                break;
            default:
                break;
        }
        Color body = shade(bodyRgb, 0);
        Color wing = shade(wingRgb, 0);
        Color belly = shade(bodyRgb, 0.35);
        Color accent = shade(accentRgb, 0);
        g.translate(cx, cy);
        g.scale(sx, sy);
        g.setColor(wing);
        g.fill(BIRD_TAIL);
        g.setColor(body);
        g.fill(BIRD_BODY);
        g.setColor(belly);
        g.fill(BIRD_BELLY);
        double angle = wingAngle(wingPhase);
        g.rotate(angle, WING_PIVOT_X, WING_PIVOT_Y);
        g.setColor(wing);
        g.fill(BIRD_WING);
        g.rotate(-angle, WING_PIVOT_X, WING_PIVOT_Y);
        g.setColor(EYE_WHITE);
        g.fill(BIRD_EYE);
        g.setColor(shade(eyeRgb, 0));
        g.fill(BIRD_PUPIL);
        g.setColor(accent);
        g.fill(BIRD_BEAK);
        drawArchetypeMark(g, shape, accent);
        g.scale(1 / sx, 1 / sy);
        g.translate(-cx, -cy);
    }

    /**
     * Draws the one detail that separates the archetypes at portrait size, in unit coordinates.
     *
     * @param g the context, already translated and scaled to the bird
     * @param shape the silhouette key
     * @param accent the palette accent colour
     */
    private static void drawArchetypeMark(Graphics2D g, String shape, Color accent) {
        Stroke old = g.getStroke();
        g.setStroke(PORTRAIT_MARK);
        g.setColor(accent);
        switch (shape == null ? "" : shape) {
            case "guardian":
                // A plate over the breast: the bird that flies with a shield.
                g.draw(new Ellipse2D.Double(-0.34, -0.10, 0.46, 0.42));
                break;
            case "mystic":
                // A halo: the bird whose abilities last longer.
                g.draw(new Ellipse2D.Double(-0.28, -0.62, 0.56, 0.22));
                break;
            case "gambler":
                // A pip, for the bird that trades width for score.
                g.fill(new Ellipse2D.Double(-0.20, 0.02, 0.14, 0.14));
                break;
            case "forge":
                // A spark off the anvil.
                g.draw(new Line2D.Double(-0.62, 0.24, -0.38, 0.12));
                g.draw(new Line2D.Double(-0.50, 0.34, -0.32, 0.24));
                break;
            case "heavy":
                // A weight band across the body.
                g.draw(new Line2D.Double(-0.30, 0.18, 0.24, 0.18));
                break;
            case "swift":
                // Two speed lines behind the tail.
                g.draw(new Line2D.Double(-0.86, -0.16, -0.62, -0.16));
                g.draw(new Line2D.Double(-0.84, 0.02, -0.60, 0.02));
                break;
            default:
                break;
        }
        g.setStroke(old);
    }

    /**
     * A cached toolkit colour for a packed {@code 0xRRGGBB} value, optionally lightened.
     *
     * @param rgb the packed colour
     * @param lighten how far to move it towards white, in {@code [0, 1]}
     * @return the colour
     */
    private static Color shade(int rgb, double lighten) {
        int value = lighten <= 0 ? (rgb & 0xFFFFFF) : WorldPalette.lighten(rgb, lighten);
        Color cached = PORTRAIT_COLORS.get(value);
        if (cached == null) {
            cached = new Color(value);
            PORTRAIT_COLORS.put(value, cached);
        }
        return cached;
    }

    private static Shape polygon(double... xy) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) {
            path.lineTo(xy[i], xy[i + 1]);
        }
        path.closePath();
        return path;
    }
}
