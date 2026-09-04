package io.github.michelbr84.flapforge.content.defs;

/**
 * The colours a world is drawn with (§4). Every entry is a {@code #RRGGBB} string, validated the
 * same way {@link PaletteDef} validates a bird palette.
 *
 * @param skyTop the top of the sky gradient
 * @param skyBottom the bottom of the sky gradient
 * @param ground the scrolling ground
 * @param pipe the gate bodies
 * @param accent highlights and pickups
 * @param fog the haze layer
 * @param letterbox the bars outside the playfield
 */
public record WorldPaletteDef(String skyTop, String skyBottom, String ground, String pipe,
        String accent, String fog, String letterbox) {

    /**
     * Checks every colour.
     *
     * @throws NullPointerException when a colour is missing
     * @throws IllegalArgumentException when a colour is not {@code #RRGGBB}
     */
    public WorldPaletteDef {
        requireColor("skyTop", skyTop);
        requireColor("skyBottom", skyBottom);
        requireColor("ground", ground);
        requireColor("pipe", pipe);
        requireColor("accent", accent);
        requireColor("fog", fog);
        requireColor("letterbox", letterbox);
    }

    private static void requireColor(String field, String value) {
        if (value == null) {
            throw new NullPointerException(field);
        }
        if (!PaletteDef.COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "world palette " + field + " is not a #RRGGBB colour: '" + value + "'");
        }
    }

    /**
     * One colour as a packed RGB integer.
     *
     * @param color the {@code #RRGGBB} text
     * @return the packed value
     */
    public static int rgb(String color) {
        return PaletteDef.rgb(color);
    }
}
