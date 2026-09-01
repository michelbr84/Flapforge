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

    private static final Color PANEL_FILL = new Color(0x1C, 0x3A, 0x3E, 0xD2);
    private static final Color PANEL_BORDER = new Color(0xFF, 0xFF, 0xFF, 0x59);
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

    /* Unit-space bird facing right; 1 unit = body width, origin at the body centre. */
    private static final Shape BIRD_BODY = new Ellipse2D.Double(-0.5, -0.38, 1.0, 0.76);
    private static final Shape BIRD_BELLY = new Ellipse2D.Double(-0.30, -0.02, 0.60, 0.36);
    private static final Shape BIRD_EYE = new Ellipse2D.Double(0.10, -0.32, 0.30, 0.30);
    private static final Shape BIRD_PUPIL = new Ellipse2D.Double(0.24, -0.24, 0.13, 0.13);
    private static final Shape BIRD_BEAK = polygon(0.44, -0.04, 0.74, 0.06, 0.44, 0.16);
    private static final Shape BIRD_TAIL = polygon(-0.46, -0.12, -0.72, -0.28, -0.60, 0.06);
    private static final Shape BIRD_WING = polygon(-0.12, -0.02, -0.56, 0.12, -0.46, 0.30,
            0.04, 0.18);
    private static final double WING_PIVOT_X = -0.12;
    private static final double WING_PIVOT_Y = -0.02;

    /* Unit-space anvil; 1 unit = width, origin at the top-left of the face, height 0.5. */
    private static final Shape ANVIL = polygon(0.00, 0.04, 0.20, 0.00, 1.00, 0.00, 0.96, 0.20,
            0.62, 0.22, 0.66, 0.36, 0.84, 0.40, 0.84, 0.50, 0.16, 0.50, 0.16, 0.40, 0.34, 0.36,
            0.38, 0.22, 0.22, 0.20, 0.00, 0.12);

    private static final Map<WorldPalette, Resolved> RESOLVED = new ConcurrentHashMap<>();

    /** Toolkit objects derived from one palette, built once. */
    private static final class Resolved {
        final Color skyTop;
        final Color skyBottom;
        final Color ground;
        final Color groundEdge;
        final Color hillFar;
        final Color hillNear;
        final Color cloud;
        final Color accent;
        final Color accentDark;
        final Color belly;
        final Color letterbox;
        final Paint sky;

        Resolved(WorldPalette p) {
            skyTop = new Color(p.skyTop());
            skyBottom = new Color(p.skyBottom());
            ground = new Color(p.ground());
            groundEdge = new Color(WorldPalette.darken(p.ground(), 0.28));
            hillFar = new Color(WorldPalette.mix(p.pipe(), p.skyBottom(), 0.55));
            hillNear = new Color(WorldPalette.lighten(p.pipe(), 0.18));
            int fog = p.fog();
            cloud = new Color((fog >> 16) & 0xFF, (fog >> 8) & 0xFF, fog & 0xFF, 0xD9);
            accent = new Color(p.accent());
            accentDark = new Color(WorldPalette.mix(p.accent(), 0xC0501A, 0.55));
            belly = new Color(WorldPalette.lighten(p.accent(), 0.45));
            letterbox = new Color(p.letterbox());
            sky = new GradientPaint(0f, 0f, skyTop, 0f, (float) Playfield.GROUND_Y, skyBottom);
        }
    }

    private ProceduralArt() {
    }

    /**
     * Enables antialiasing and pure stroke control on a context.
     *
     * @param g the context
     */
    public static void prepare(Graphics2D g) {
        TextPainter.prepare(g);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
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
        g.fillRect(0, 0, Playfield.WIDTH, Playfield.GROUND_Y);
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
        g.fillRect(0, Playfield.GROUND_Y, Playfield.WIDTH, Playfield.GROUND_HEIGHT);
        g.setColor(r.groundEdge);
        g.fillRect(0, Playfield.GROUND_Y, Playfield.WIDTH, 4);
    }

    private static void cloud(Graphics2D g, int cx, int cy, double s) {
        int w = (int) (48 * s);
        int h = (int) (33 * s);
        g.fillOval(cx - w / 2, cy - h / 2, w, h);
        g.fillOval(cx - w / 2 - w / 3, cy - h / 3, (int) (w * 0.7), (int) (h * 0.7));
        g.fillOval(cx + w / 6, cy - h / 3, (int) (w * 0.65), (int) (h * 0.7));
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
        g.setColor(SHADOW);
        g.fillRoundRect(x, y + 4, w, h, PANEL_RADIUS, PANEL_RADIUS);
        g.setColor(PANEL_FILL);
        g.fillRoundRect(x, y, w, h, PANEL_RADIUS, PANEL_RADIUS);
        Stroke old = g.getStroke();
        g.setStroke(THIN);
        g.setColor(PANEL_BORDER);
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
        Resolved r = resolve(palette);
        g.translate(cx, cy);
        g.scale(size, size);
        g.setColor(r.accentDark);
        g.fill(BIRD_TAIL);
        g.setColor(r.accent);
        g.fill(BIRD_BODY);
        g.setColor(r.belly);
        g.fill(BIRD_BELLY);
        double angle = wingAngle(wingPhase);
        g.rotate(angle, WING_PIVOT_X, WING_PIVOT_Y);
        g.setColor(r.accentDark);
        g.fill(BIRD_WING);
        g.rotate(-angle, WING_PIVOT_X, WING_PIVOT_Y);
        g.setColor(EYE_WHITE);
        g.fill(BIRD_EYE);
        g.setColor(EYE_PUPIL);
        g.fill(BIRD_PUPIL);
        g.setColor(BEAK);
        g.fill(BIRD_BEAK);
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
        Resolved r = RESOLVED.get(palette);
        if (r == null) {
            r = new Resolved(palette);
            RESOLVED.put(palette, r);
        }
        return r;
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
