package io.github.michelbr84.flapforge.render;

import io.github.michelbr84.flapforge.content.defs.WorldPaletteDef;

/**
 * Colour set of one world, used by {@link ProceduralArt} for every generated visual (D18).
 *
 * <p>Colours are {@code 0xRRGGBB} ints so the same record can later be bound from
 * {@code worlds.json} without any toolkit type; the constructor rejects any component with bits
 * above 24 with an {@link IllegalArgumentException}. {@link ProceduralArt} caches the toolkit
 * objects derived from a palette so rendering does not allocate per frame.
 *
 * @param skyTop sky colour at the top of the playfield
 * @param skyBottom sky colour just above the ground
 * @param ground ground strip colour
 * @param pipe obstacle body colour (also the hill tone of the backdrop)
 * @param accent highlight colour (bird body, title, focus ring)
 * @param fog fog and cloud colour
 * @param letterbox colour of the bars outside the logical playfield
 */
public record WorldPalette(int skyTop, int skyBottom, int ground, int pipe, int accent, int fog,
        int letterbox) {

    /** Green Fields, the default world (plan §4). */
    public static final WorldPalette GREEN_FIELDS = new WorldPalette(0x4BC4CF, 0x8FDDE3, 0xDED895,
            0x5FBF3A, 0xF5C542, 0xFFFFFF, 0x1C3A3E);

    /**
     * Validates that every component is a plain {@code 0xRRGGBB} value.
     *
     * @param skyTop sky colour at the top of the playfield
     * @param skyBottom sky colour just above the ground
     * @param ground ground strip colour
     * @param pipe obstacle body colour
     * @param accent highlight colour
     * @param fog fog and cloud colour
     * @param letterbox letterbox colour
     * @throws IllegalArgumentException when a component carries bits above 24
     */
    public WorldPalette {
        check("skyTop", skyTop);
        check("skyBottom", skyBottom);
        check("ground", ground);
        check("pipe", pipe);
        check("accent", accent);
        check("fog", fog);
        check("letterbox", letterbox);
    }

    /**
     * The palette of a {@code worlds.json} entry (M7).
     *
     * @param def the authored colours
     * @return the palette
     */
    public static WorldPalette from(WorldPaletteDef def) {
        return new WorldPalette(WorldPaletteDef.rgb(def.skyTop()),
                WorldPaletteDef.rgb(def.skyBottom()), WorldPaletteDef.rgb(def.ground()),
                WorldPaletteDef.rgb(def.pipe()), WorldPaletteDef.rgb(def.accent()),
                WorldPaletteDef.rgb(def.fog()), WorldPaletteDef.rgb(def.letterbox()));
    }

    private static void check(String name, int rgb) {
        if ((rgb & ~0xFFFFFF) != 0) {
            throw new IllegalArgumentException(name + " must be 0xRRGGBB, got 0x"
                    + Integer.toHexString(rgb));
        }
    }

    /**
     * Mixes two colours component-wise.
     *
     * @param from the colour at {@code t = 0}
     * @param to the colour at {@code t = 1}
     * @param t the blend factor, clamped to {@code [0, 1]}
     * @return the blended {@code 0xRRGGBB}
     */
    public static int mix(int from, int to, double t) {
        double f = t < 0 ? 0 : (t > 1 ? 1 : t);
        int r = channel(from >> 16, to >> 16, f);
        int g = channel(from >> 8, to >> 8, f);
        int b = channel(from, to, f);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Darkens a colour towards black.
     *
     * @param rgb the colour
     * @param amount the fraction to remove, clamped to {@code [0, 1]}
     * @return the darker {@code 0xRRGGBB}
     */
    public static int darken(int rgb, double amount) {
        return mix(rgb, 0x000000, amount);
    }

    /**
     * Lightens a colour towards white.
     *
     * @param rgb the colour
     * @param amount the fraction to add, clamped to {@code [0, 1]}
     * @return the lighter {@code 0xRRGGBB}
     */
    public static int lighten(int rgb, double amount) {
        return mix(rgb, 0xFFFFFF, amount);
    }

    private static int channel(int from, int to, double t) {
        int a = from & 0xFF;
        int b = to & 0xFF;
        return (int) Math.round(a + (b - a) * t);
    }
}
