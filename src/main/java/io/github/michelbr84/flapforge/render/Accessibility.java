package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.core.MathUtil;
import java.awt.Color;
import java.util.Objects;

/**
 * The two render-side accessibility modes (D17, §4): high contrast and the colour-blind
 * palettes. Everything the modes change funnels through this class so a mode is one state, not a
 * flag smeared across the renderers.
 *
 * <p><strong>High contrast</strong> is structural, not chromatic: the darkness veil is capped at
 * {@value #HIGH_CONTRAST_DARKNESS}, HUD panels go opaque, hazard and bird outlines thicken and
 * text outlines gain a pixel. The colours themselves stay the world's own — high contrast is
 * about edges and separation, not a recolour.
 *
 * <p><strong>Colour-blind palettes</strong> recolour. A palette name from
 * {@code settings.colorBlindPalette} ({@code none}, {@code protanopia}, {@code deuteranopia},
 * {@code tritanopia}) selects a linear RGB transform (Machado, Oliveira and Fernandes 2009,
 * severity 1.0), applied in two places:
 *
 * <ul>
 *   <li>to a whole {@link WorldPalette} — {@link #palette(WorldPalette)} returns a recoloured
 *       copy that {@code GameRenderer} installs for a run, so every palette-derived colour (sky,
 *       ground, pipes, gears, the bird) shifts together;</li>
 *   <li>to the semantic colours that do not come from the palette — coins, the streak flame, the
 *       piston telegraph glow, the lightning warning, the rule-shift flash — through
 *       {@link #tone(Color, Role)}.</li>
 * </ul>
 *
 * <p>A linear matrix alone cannot keep two colours apart that a dichromat sees as one, so after
 * the matrix each semantic role is pushed to a <em>luminance target</em> fixed per world: the
 * hazard tone moves 60 luma away from the sky, the danger tone 45 further the other way, and the
 * coin tone 45 away from the accent. Luminance is the one channel every colour vision deficiency
 * preserves, so the required pairs — hazard vs background, telegraph vs idle, coin vs accent —
 * hold their separation by construction in every mode. {@code ProceduralRenderTest} asserts the
 * numbers.
 *
 * <p>State is global and volatile because it is read per frame from the render thread and written
 * from {@code GameContext.applySettings} (or a test); a torn read would draw exactly one frame in
 * the old mode, which is invisible. Changing a mode invalidates the {@link ProceduralArt} palette
 * caches, whose keys carry the high-contrast flag and whose palettes are already transformed.
 */
public final class Accessibility {

    /**
     * What a semantic colour means, which decides where the luminance target sits after the
     * matrix. {@code PLAIN} keeps the matrix result untouched.
     */
    public enum Role {
        /** No special meaning: matrix only. */
        PLAIN,
        /** A hazard body: pushed away from the background luminance. */
        HAZARD,
        /** An imminent-danger telegraph: pushed to the opposite side from the hazard. */
        DANGER,
        /** A reward pickup or positive feedback: pushed away from the accent luminance. */
        COIN
    }

    /** Darkness ceiling while high contrast is on (plan §4: the veil must stay light). */
    public static final double HIGH_CONTRAST_DARKNESS = 0.25;
    /** Luminance a hazard keeps from the background, on the 0–255 luma scale. */
    public static final double HAZARD_SEPARATION = 60.0;
    /** Luminance a telegraph keeps from the hazard, and a coin from the accent. */
    public static final double SEMANTIC_SEPARATION = 45.0;

    /** Machado et al. 2009, severity 1.0, protanopia, row-major on linear-ish sRGB. */
    private static final float[] PROTANOPIA = {
        0.152286f, 1.052583f, -0.204868f,
        0.114503f, 0.786281f, 0.099216f,
        -0.003882f, -0.048116f, 1.051998f };
    /** Machado et al. 2009, severity 1.0, deuteranopia. */
    private static final float[] DEUTERANOPIA = {
        0.367322f, 0.860646f, -0.227968f,
        0.280085f, 0.672501f, 0.047413f,
        -0.011820f, 0.042940f, 0.968881f };
    /** Machado et al. 2009, severity 1.0, tritanopia. */
    private static final float[] TRITANOPIA = {
        1.255528f, -0.076749f, -0.178779f,
        -0.078411f, 0.930809f, 0.147602f,
        0.004733f, 0.691367f, 0.303900f };

    private static volatile boolean highContrast;
    private static volatile float[] matrix;
    private static volatile String paletteName = "none";

    /* Luminance targets of the active world, recomputed by setWorldContext when a run's palette
     * is installed. NaN targets mean "keep the matrix result" — the state before any run. */
    private static volatile double hazardTarget = Double.NaN;
    private static volatile double dangerTarget = Double.NaN;
    private static volatile double coinTarget = Double.NaN;

    private Accessibility() {
    }

    /**
     * Turns high contrast on or off.
     *
     * @param on {@code true} for opaque panels, capped darkness and thicker outlines
     */
    public static void setHighContrast(boolean on) {
        if (highContrast != on) {
            highContrast = on;
            ProceduralArt.invalidatePalettes();
        }
    }

    /**
     * Whether high contrast is on.
     *
     * @return {@code true} when on
     */
    public static boolean isHighContrast() {
        return highContrast;
    }

    /**
     * Selects the colour-blind transform. Unknown names clear the transform, which is what the
     * settings normaliser already prevents.
     *
     * @param name one of {@code none}, {@code protanopia}, {@code deuteranopia},
     *     {@code tritanopia}; {@code null} means {@code none}
     */
    public static void setPalette(String name) {
        String next = name == null ? "none" : name;
        float[] nextMatrix;
        switch (next) {
            case "protanopia":
                nextMatrix = PROTANOPIA;
                break;
            case "deuteranopia":
                nextMatrix = DEUTERANOPIA;
                break;
            case "tritanopia":
                nextMatrix = TRITANOPIA;
                break;
            default:
                nextMatrix = null;
                next = "none";
                break;
        }
        matrix = nextMatrix;
        paletteName = next;
    }

    /**
     * The active colour-blind palette.
     *
     * @return the name, {@code none} when no transform is applied
     */
    public static String paletteName() {
        return paletteName;
    }

    /**
     * Whether a colour-blind transform is active.
     *
     * @return {@code true} for any palette other than {@code none}
     */
    public static boolean isColourBlindActive() {
        return matrix != null;
    }

    /**
     * Resets every mode and world context to the defaults. Tests call this so one test's modes
     * cannot leak into the next.
     */
    public static void clear() {
        highContrast = false;
        matrix = null;
        paletteName = "none";
        hazardTarget = Double.NaN;
        dangerTarget = Double.NaN;
        coinTarget = Double.NaN;
        ProceduralArt.invalidatePalettes();
    }

    /**
     * The darkness ceiling the veil may reach right now: the world's own darkness capped by
     * {@value #HIGH_CONTRAST_DARKNESS} under high contrast.
     *
     * @return the cap in {@code (0, 1]}
     */
    public static double darknessCap() {
        return highContrast ? HIGH_CONTRAST_DARKNESS : 1.0;
    }

    /**
     * Outline thickness for hazards, the bird and text under the current mode: high contrast adds
     * one logical pixel to whatever the caller chose.
     *
     * @param base the thickness without high contrast
     * @return the thickness to draw with
     */
    public static int outlineThickness(int base) {
        return highContrast ? base + 1 : base;
    }

    /**
     * Recolours a whole world palette for a run. With no colour-blind transform active the
     * palette comes back unchanged, and the callers downstream see the exact record they passed.
     *
     * @param palette the authored palette
     * @return the palette to render with
     */
    public static WorldPalette palette(WorldPalette palette) {
        Objects.requireNonNull(palette, "palette");
        float[] m = matrix;
        if (m == null) {
            setWorldContext(palette);
            return palette;
        }
        int skyBottom = shift(m, palette.skyBottom());
        int pipe = shift(m, palette.pipe());
        int accent = shift(m, palette.accent());
        // The targets are decided from the shifted colours, so the pipe is then pushed to its
        // target measured against the sky the run will actually show.
        double background = luminance(skyBottom);
        double pipeLuminance = luminance(pipe);
        boolean pipeAbove = pipeLuminance >= background;
        hazardTarget = pipeAbove
                ? Math.min(255.0, background + HAZARD_SEPARATION)
                : Math.max(0.0, background - HAZARD_SEPARATION);
        dangerTarget = pipeAbove
                ? Math.max(0.0, background - SEMANTIC_SEPARATION)
                : Math.min(255.0, background + SEMANTIC_SEPARATION);
        double accentLuminance = luminance(accent);
        coinTarget = accentLuminance <= 255.0 - SEMANTIC_SEPARATION
                ? accentLuminance + SEMANTIC_SEPARATION
                : accentLuminance - SEMANTIC_SEPARATION;
        WorldPalette shifted = new WorldPalette(
                shift(m, palette.skyTop()), skyBottom,
                shift(m, palette.ground()), toLuminance(pipe, hazardTarget),
                accent, shift(m, palette.fog()),
                shift(m, palette.letterbox()));
        return shifted;
    }

    /**
     * Records the luminance targets the semantic roles resolve against, from the palette the run
     * actually renders with (already recoloured). Called by {@link #palette(WorldPalette)} and
     * again by the renderer when high contrast changes nothing about colour, so a run started
     * before a mode switch still has targets.
     *
     * @param active the palette in use, transformed or not
     */
    public static void setWorldContext(WorldPalette active) {
        Objects.requireNonNull(active, "active");
        double background = luminance(active.skyBottom());
        double pipe = luminance(active.pipe());
        boolean pipeAbove = pipe >= background;
        hazardTarget = pipeAbove
                ? Math.min(255.0, background + HAZARD_SEPARATION)
                : Math.max(0.0, background - HAZARD_SEPARATION);
        dangerTarget = pipeAbove
                ? Math.max(0.0, background - SEMANTIC_SEPARATION)
                : Math.min(255.0, background + SEMANTIC_SEPARATION);
        double accent = luminance(active.accent());
        coinTarget = accent <= 255.0 - SEMANTIC_SEPARATION
                ? accent + SEMANTIC_SEPARATION
                : accent - SEMANTIC_SEPARATION;
    }

    /**
     * Transforms one semantic colour. {@code PLAIN} is the matrix alone; the other roles are then
     * pulled to their luminance target so the pairs that must stay readable do, whatever the
     * matrix did to their hue.
     *
     * @param color the authored colour
     * @param role what the colour means
     * @return the colour to draw with
     */
    public static Color tone(Color color, Role role) {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(role, "role");
        int rgb = color.getRGB();
        int shifted = tone(rgb & 0xFFFFFF, role);
        return shifted == (rgb & 0xFFFFFF) ? color
                : new Color((rgb & 0xFF000000) | shifted, true);
    }

    /**
     * Transforms a plain colour (no semantic role).
     *
     * @param color the authored colour
     * @return the colour to draw with
     */
    public static Color tone(Color color) {
        return tone(color, Role.PLAIN);
    }

    /**
     * Transforms one packed {@code 0xRRGGBB} semantic colour, the shape {@link WorldPalette}
     * fields arrive in — the renderers that recolour a palette channel (the piston telegraph,
     * the lightning warning) call this so the matrix and the luminance target are applied
     * exactly as {@link #tone(Color, Role)} applies them, without an intermediate
     * {@code Color}.
     *
     * @param rgb the authored colour, {@code 0xRRGGBB}
     * @param role what the colour means
     * @return the colour to draw with, {@code 0xRRGGBB}
     */
    public static int tone(int rgb, Role role) {
        Objects.requireNonNull(role, "role");
        float[] m = matrix;
        if (m == null) {
            return rgb;
        }
        int shifted = shift(m, rgb & 0xFFFFFF);
        double target = target(role);
        if (!Double.isNaN(target)) {
            shifted = toLuminance(shifted, target);
        }
        return shifted;
    }

    /**
     * Relative luminance of a packed {@code 0xRRGGBB} colour on the 0–255 scale, the Rec. 709
     * coefficients the contrast rule is written against.
     *
     * @param rgb the colour
     * @return the luminance
     */
    public static double luminance(int rgb) {
        double r = (rgb >> 16) & 0xFF;
        double g = (rgb >> 8) & 0xFF;
        double b = rgb & 0xFF;
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    /**
     * Moves a colour's luminance to a target without losing more hue than it must: a
     * multiplicative step first (which keeps every channel's ratio, but can clamp at white), then
     * a blend towards white for whatever the clamp left. Darkening never clamps, so it is exact.
     *
     * @param rgb the colour, {@code 0xRRGGBB}
     * @param targetLuminance the luminance to reach, in {@code [0, 255]}
     * @return the recoloured value
     */
    public static int toLuminance(int rgb, double targetLuminance) {
        double current = luminance(rgb);
        double target = MathUtil.clamp(targetLuminance, 0.0, 255.0);
        if (current <= 0.0) {
            // Black has no scale or blend direction; the only grey of the asked-for luminance is
            // the flat one.
            int grey = (int) Math.round(target);
            return pack(grey, grey, grey);
        }
        if (Math.abs(current - target) < 0.5) {
            return rgb;
        }
        if (target < current) {
            return scaleTowards(rgb, 0x000000, target / current);
        }
        int stepped = scaleTowards(rgb, 0xFFFFFF, target / current);
        double reached = luminance(stepped);
        if (target - reached < 0.5) {
            return stepped;
        }
        return scaleTowards(stepped, 0xFFFFFF,
                (target - reached) / Math.max(1.0, 255.0 - reached));
    }

    /**
     * Blends a colour towards another by a luminance-ratio factor: each channel moves to
     * {@code channel * factor} (towards black) or {@code channel + (255 - channel) * t} towards
     * white, where {@code t} is solved from the factor so one call is enough at each stage.
     *
     * @param rgb the colour, {@code 0xRRGGBB}
     * @param towards {@code 0x000000} or {@code 0xFFFFFF}
     * @param factor the luminance ratio to apply
     * @return the blended value
     */
    private static int scaleTowards(int rgb, int towards, double factor) {
        double r = (rgb >> 16) & 0xFF;
        double g = (rgb >> 8) & 0xFF;
        double b = rgb & 0xFF;
        double tr = (towards >> 16) & 0xFF;
        double tg = (towards >> 8) & 0xFF;
        double tb = towards & 0xFF;
        if (towards == 0x000000) {
            // Pure scaling: luminance moves by exactly the factor.
            r *= factor;
            g *= factor;
            b *= factor;
        } else {
            // Solve t from factor * current = current + (white - current) * t per channel, then
            // take the t that reaches the asked-for luminance exactly (they agree because the
            // blend is linear in every channel).
            double t = solveTowards(rgb, factor);
            r += (tr - r) * t;
            g += (tg - g) * t;
            b += (tb - b) * t;
        }
        return pack(r, g, b);
    }

    /**
     * The white-blend fraction that multiplies a colour's luminance by {@code factor}.
     *
     * @param rgb the colour
     * @param factor the asked-for luminance ratio, {@code > 1}
     * @return the fraction in {@code [0, 1]}
     */
    private static double solveTowards(int rgb, double factor) {
        double l = luminance(rgb);
        if (l <= 0.0) {
            return 0.0;
        }
        return MathUtil.clamp((factor - 1.0) * l / (255.0 - l), 0.0, 1.0);
    }

    private static int pack(double r, double g, double b) {
        int ri = (int) Math.round(r);
        int gi = (int) Math.round(g);
        int bi = (int) Math.round(b);
        return (MathUtil.clamp(ri, 0, 255) << 16) | (MathUtil.clamp(gi, 0, 255) << 8)
                | MathUtil.clamp(bi, 0, 255);
    }

    private static double target(Role role) {
        switch (role) {
            case HAZARD:
                return hazardTarget;
            case DANGER:
                return dangerTarget;
            case COIN:
                return coinTarget;
            default:
                return Double.NaN;
        }
    }

    /** Applies the 3x3 matrix to one {@code 0xRRGGBB} colour, clamping to the gamut. */
    private static int shift(float[] m, int rgb) {
        double r = (rgb >> 16) & 0xFF;
        double g = (rgb >> 8) & 0xFF;
        double b = rgb & 0xFF;
        double nr = m[0] * r + m[1] * g + m[2] * b;
        double ng = m[3] * r + m[4] * g + m[5] * b;
        double nb = m[6] * r + m[7] * g + m[8] * b;
        return pack(nr, ng, nb);
    }
}
