package io.github.michelbr84.flapforge.ui.screens;

import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.PaletteDef;
import io.github.michelbr84.flapforge.render.Accessibility;
import io.github.michelbr84.flapforge.render.Fonts;
import io.github.michelbr84.flapforge.render.ProceduralArt;
import io.github.michelbr84.flapforge.render.TextPainter;
import io.github.michelbr84.flapforge.render.TextPainter.Align;
import io.github.michelbr84.flapforge.render.WorldPalette;
import io.github.michelbr84.flapforge.ui.component.AttributeBadge;
import io.github.michelbr84.flapforge.ui.component.IconPainter;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.util.List;

/**
 * The bird the player is browsing, large (M11): its name, what it is, the bird itself bobbing on
 * an anvil on a floating island, and its three headline attributes.
 *
 * <p>It is the bird selection's answer to the hub's forge scene, and it is deliberately its own
 * class rather than a reuse of {@link ForgeScene}: that scene's twenty coordinates are absolute
 * hub positions, and the pieces worth sharing — the island, the anvil, the portrait — are
 * {@link ProceduralArt} helpers either screen can call at its own origin.
 *
 * <p>Changing bird slides the portrait: the outgoing bird leaves in the direction of travel while
 * the new one arrives, over {@value #SLIDE_TICKS} ticks with the ease-out-quad the coin readout
 * uses, inside the one clip bracket the slide needs. Reduce flashing caps the glow's pulse and
 * leaves the slide and the bob alone: they are motion, not luminance, exactly as the hub's own
 * bob and wing beat are.
 *
 * <p>The band is a paint region, not a clip: at a large text scale the name's ascent reaches a
 * few pixels above {@link #BAND_TOP}. The screen draws the header after the hero, so the title
 * and the coin chip own that strip and the name passes under them.
 */
public final class BirdHero {

    /** Top of the band the hero owns. */
    public static final int BAND_TOP = 48;
    /** Bottom of the band the hero owns. */
    public static final int BAND_BOTTOM = 236;
    /** Centre x of the bird, the anvil and the island. */
    public static final int HERO_CX = 210;
    /** Baseline of the bird's name. */
    public static final int NAME_BASELINE = 70;
    /** Baseline of the line under the name. */
    public static final int STATUS_BASELINE = 88;
    /** Centre y of the bird before the bob. */
    public static final int BIRD_CY = 118;
    /** Size of the bird portrait. */
    public static final int BIRD_SIZE = 56;
    /** Top of the anvil. */
    public static final int ANVIL_TOP = 140;
    /** Width of the anvil (its height is half of that). */
    public static final int ANVIL_W = 64;
    /** Top of the island. */
    public static final int ISLAND_TOP = 172;
    /** Width of the island. */
    public static final int ISLAND_W = 170;
    /** Height of the island. */
    public static final int ISLAND_H = 30;
    /** Radius of the glow behind the bird. */
    public static final int GLOW_R = 44;
    /** Centre y of that glow. */
    public static final int GLOW_CY = 122;
    /** Resting alpha of the glow. */
    public static final double GLOW_BASE = 0.24;
    /** How much the glow pulses on top of that. */
    public static final double GLOW_PULSE = 0.08;
    /** Ticks of one glow pulse. */
    public static final int GLOW_PERIOD_TICKS = 120;
    /** Ticks one bird change slides for. */
    public static final int SLIDE_TICKS = 18;
    /** How far the portraits travel during a slide. */
    public static final int SLIDE_DISTANCE = 36;
    /** Ticks of one bob. */
    public static final int BOB_PERIOD_TICKS = 96;
    /** Peak-to-peak height of the bob. */
    public static final double BOB_AMPLITUDE = 4;
    /** Ticks of one wing beat. */
    public static final int WING_PERIOD_TICKS = 48;
    /** Width of one attribute badge. */
    public static final int BADGE_W = 128;
    /** Height of one attribute badge. */
    public static final int BADGE_H = 22;
    /** Top of the badge row. */
    public static final int BADGE_TOP = 212;
    /** Left edges of the three badges. */
    private static final int[] BADGE_X = {12, 146, 280};
    /** Text column the name and the status line are centred in. */
    private static final int TEXT_W = 396;

    private static final Color[] GLOW = ProceduralArt.alphaRamp(ProceduralArt.COIN_GOLD, 16);
    private static final Color ISLAND_SOIL =
            new Color(WorldPalette.darken(WorldPalette.GREEN_FIELDS.ground(), 0.3));
    private static final Color ISLAND_GRASS = new Color(WorldPalette.GREEN_FIELDS.pipe());
    private static final Color ISLAND_ROCK =
            new Color(WorldPalette.darken(WorldPalette.GREEN_FIELDS.ground(), 0.5));
    private static final Color ANVIL_INK = new Color(WorldPalette.GREEN_FIELDS.letterbox());

    /** What the line under the name says about the bird. */
    public enum Status {
        /** The bird the profile flies with. */
        SELECTED,
        /** A bird the profile owns but does not fly with. */
        OWNED,
        /** A bird the profile has not opened yet. */
        LOCKED
    }

    private final AttributeBadge mobility;
    private final AttributeBadge defence;
    private final AttributeBadge control;
    private final List<AttributeBadge> badges;

    private String birdId;
    private String name = "";
    private String status = "";
    private Status kind = Status.OWNED;
    private int bodyRgb = BirdPortrait.CLASSIC_BODY;
    private int wingRgb = BirdPortrait.CLASSIC_WING;
    private int eyeRgb = BirdPortrait.CLASSIC_EYE;
    private int accentRgb = BirdPortrait.CLASSIC_ACCENT;
    private String shape = BirdPortrait.CLASSIC_SHAPE;
    private int prevBodyRgb = BirdPortrait.CLASSIC_BODY;
    private int prevWingRgb = BirdPortrait.CLASSIC_WING;
    private int prevEyeRgb = BirdPortrait.CLASSIC_EYE;
    private int prevAccentRgb = BirdPortrait.CLASSIC_ACCENT;
    private String prevShape = BirdPortrait.CLASSIC_SHAPE;
    private int slideLeft;
    private int slideDir;
    private boolean reduceFlashing;
    private BirdAttributes attributes = new BirdAttributes(5, 5, 5);
    private Color outline = ProceduralArt.TEXT_DARK;
    private String shownName = "";
    private String shownNameSource;
    private double shownNameScale;

    /** Creates a hero with its three badges. */
    BirdHero() {
        mobility = new AttributeBadge(iconOf(ProceduralArt::drawSpark));
        defence = new AttributeBadge(iconOf(ProceduralArt::drawShield));
        control = new AttributeBadge(iconOf(ProceduralArt::drawWing));
        badges = List.of(mobility, defence, control);
        for (int i = 0; i < badges.size(); i++) {
            badges.get(i).setBounds(BADGE_X[i], BADGE_TOP, BADGE_W, BADGE_H);
        }
    }

    private static IconPainter iconOf(IconPainter painter) {
        return painter;
    }

    /**
     * Points the hero at a bird.
     *
     * @param bird the bird
     * @param palette the palette it is drawn in, or {@code null} for the classic colours
     * @param newName the translated bird name
     * @param newStatus the line under the name
     * @param newKind what that line says
     * @param newAttributes the three scores
     * @param direction {@code +1} when the new bird sits right of the old one, {@code -1} left,
     *     {@code 0} for a refresh that is not a bird change
     */
    void bind(BirdDef bird, PaletteDef palette, String newName, String newStatus, Status newKind,
            BirdAttributes newAttributes, int direction) {
        String nextId = bird == null ? null : bird.id();
        boolean changed = birdId != null && nextId != null && !birdId.equals(nextId);
        if (changed && direction != 0) {
            prevBodyRgb = bodyRgb;
            prevWingRgb = wingRgb;
            prevEyeRgb = eyeRgb;
            prevAccentRgb = accentRgb;
            prevShape = shape;
            slideLeft = SLIDE_TICKS;
            slideDir = direction;
        }
        birdId = nextId;
        name = newName == null ? "" : newName;
        status = newStatus == null ? "" : newStatus;
        kind = newKind == null ? Status.OWNED : newKind;
        attributes = newAttributes == null ? new BirdAttributes(5, 5, 5) : newAttributes;
        if (palette == null) {
            bodyRgb = BirdPortrait.CLASSIC_BODY;
            wingRgb = BirdPortrait.CLASSIC_WING;
            eyeRgb = BirdPortrait.CLASSIC_EYE;
            accentRgb = BirdPortrait.CLASSIC_ACCENT;
        } else {
            bodyRgb = palette.bodyRgb();
            wingRgb = palette.wingRgb();
            eyeRgb = palette.eyeRgb();
            accentRgb = palette.accentRgb();
        }
        shape = bird == null ? BirdPortrait.CLASSIC_SHAPE : bird.shape();
    }

    /**
     * Re-labels the three badges (a language switch).
     *
     * @param mobilityLabel the mobility label
     * @param defenceLabel the defence label
     * @param controlLabel the control label
     */
    void setLabels(String mobilityLabel, String defenceLabel, String controlLabel) {
        mobility.bind(mobilityLabel, attributes.mobility());
        defence.bind(defenceLabel, attributes.defence());
        control.bind(controlLabel, attributes.control());
    }

    /**
     * Changes the colour the name is outlined in.
     *
     * @param newOutline the colour
     */
    void setOutline(Color newOutline) {
        this.outline = newOutline == null ? ProceduralArt.TEXT_DARK : newOutline;
    }

    /**
     * Caps the glow's pulse for players who asked for less flashing.
     *
     * @param value the flag
     */
    void setReduceFlashing(boolean value) {
        this.reduceFlashing = value;
    }

    /**
     * Advances the slide by one tick.
     *
     * @param ticks the screen's tick count
     */
    void tick(long ticks) {
        if (slideLeft > 0) {
            slideLeft--;
        }
    }

    /** Ends any running slide at once (entering the screen). */
    void snap() {
        slideLeft = 0;
    }

    /**
     * Whether the portraits are sliding.
     *
     * @return {@code true} while they move
     */
    public boolean isSliding() {
        return slideLeft > 0;
    }

    /**
     * The bird shown.
     *
     * @return the bird id, or {@code null} before the first bind
     */
    public String birdId() {
        return birdId;
    }

    /**
     * The three scores shown.
     *
     * @return the attributes
     */
    public BirdAttributes attributes() {
        return attributes;
    }

    /**
     * The three badges, in display order.
     *
     * @return the badges
     */
    public List<AttributeBadge> badges() {
        return badges;
    }

    /**
     * The badge under a point, for a tooltip: the badges are not focus-ring nodes, so the screen
     * asks the hero rather than the ring.
     *
     * @param px the x coordinate
     * @param py the y coordinate
     * @return the badge, or {@code null} when the point is elsewhere
     */
    public AttributeBadge badgeAt(double px, double py) {
        for (AttributeBadge badge : badges) {
            if (badge.contains(px, py)) {
                return badge;
            }
        }
        return null;
    }

    /**
     * The bob at a tick: a triangle wave, exactly the hub's.
     *
     * @param tick the tick count
     * @return the vertical offset
     */
    public static double bobAt(long tick) {
        long t = tick % BOB_PERIOD_TICKS;
        double half = BOB_PERIOD_TICKS / 2.0;
        double wave = t < half ? t / half : (BOB_PERIOD_TICKS - t) / half;
        return -BOB_AMPLITUDE / 2 + BOB_AMPLITUDE * wave;
    }

    /**
     * The glow's alpha at a tick.
     *
     * @param ticks the tick count
     * @return the alpha in {@code [0, 1]}
     */
    public double glowAlpha(long ticks) {
        double alpha = GLOW_BASE;
        if (!reduceFlashing) {
            double phase = (ticks % GLOW_PERIOD_TICKS) / (double) GLOW_PERIOD_TICKS;
            double wave = phase < 0.5 ? phase * 2 : 2 - phase * 2;
            alpha += GLOW_PULSE * wave;
        }
        return Accessibility.isHighContrast() ? alpha * 0.5 : alpha;
    }

    /**
     * Draws the hero.
     *
     * @param g the context
     * @param bob the bird's vertical offset this frame
     * @param ticks the screen's tick count
     */
    void render(Graphics2D g, double bob, long ticks) {
        ProceduralArt.drawIsland(g, HERO_CX, ISLAND_TOP, ISLAND_W, ISLAND_H, ISLAND_SOIL,
                ISLAND_GRASS, ISLAND_ROCK);
        ProceduralArt.drawGlow(g, HERO_CX, GLOW_CY, GLOW_R, GLOW, glowAlpha(ticks));
        ProceduralArt.drawAnvil(g, HERO_CX, ANVIL_TOP, ANVIL_W, ANVIL_INK);
        if (kind == Status.SELECTED) {
            g.setColor(ProceduralArt.COIN_GOLD);
            g.fillRect(HERO_CX - ANVIL_W / 3, ANVIL_TOP + 4, 2 * ANVIL_W / 3, 3);
        }
        double phase = (ticks % WING_PERIOD_TICKS) / (double) WING_PERIOD_TICKS;
        if (slideLeft > 0) {
            double t = 1.0 - slideLeft / (double) SLIDE_TICKS;
            double eased = 1 - (1 - t) * (1 - t);
            double dxIn = slideDir * SLIDE_DISTANCE * (1 - eased);
            double dxOut = -slideDir * SLIDE_DISTANCE * eased;
            Shape unclipped = g.getClip();
            g.clipRect(HERO_CX - ISLAND_W / 2, BAND_TOP, ISLAND_W, ANVIL_TOP - BAND_TOP + 4);
            portrait(g, HERO_CX + dxOut, BIRD_CY + bob, phase, prevBodyRgb, prevWingRgb,
                    prevEyeRgb, prevAccentRgb, prevShape);
            portrait(g, HERO_CX + dxIn, BIRD_CY + bob, phase, bodyRgb, wingRgb, eyeRgb,
                    accentRgb, shape);
            g.setClip(unclipped);
        } else {
            portrait(g, HERO_CX, BIRD_CY + bob, phase, bodyRgb, wingRgb, eyeRgb, accentRgb,
                    shape);
        }
        if (kind == Status.LOCKED) {
            ProceduralArt.drawPadlock(g, HERO_CX + 30.0, BIRD_CY - 4.0, 14, null);
        }
        g.setFont(Fonts.bold(20));
        double scale = Fonts.textScale();
        if (shownNameSource != name || shownNameScale != scale) {
            // Measured only when the name or the text scale changed.
            shownName = TextPainter.ellipsise(g, name, TEXT_W);
            shownNameSource = name;
            shownNameScale = scale;
        }
        TextPainter.drawOutlined(g, shownName, HERO_CX, NAME_BASELINE, Align.CENTER,
                ProceduralArt.TEXT_LIGHT, outline, 2);
        if (!status.isEmpty()) {
            g.setFont(Fonts.regular(12));
            TextPainter.drawOutlined(g, status, HERO_CX, STATUS_BASELINE, Align.CENTER,
                    kind == Status.SELECTED ? ProceduralArt.COIN_GOLD : ProceduralArt.TEXT_LIGHT,
                    outline, 2);
        }
        for (AttributeBadge badge : badges) {
            badge.render(g);
        }
    }

    private void portrait(Graphics2D g, double cx, double cy, double phase, int body, int wing,
            int eye, int accent, String birdShape) {
        ProceduralArt.drawBirdPortrait(g, cx, cy + BIRD_SIZE * 0.05, BIRD_SIZE * 0.9, phase,
                body, wing, eye, accent, birdShape);
    }
}
