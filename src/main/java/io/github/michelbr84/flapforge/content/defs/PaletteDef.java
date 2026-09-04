package io.github.michelbr84.flapforge.content.defs;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A cosmetic palette of one bird (D19, E20). Colours are authored as {@code "#RRGGBB"} strings
 * and validated here; the pure packages hand the renderer plain {@code 0xRRGGBB} ints (D5).
 *
 * @param id the palette id, unique inside its bird
 * @param body the body colour
 * @param wing the wing colour
 * @param eye the eye colour
 * @param accent the accent colour
 * @param unlock how the palette is earned
 */
public record PaletteDef(String id, String body, String wing, String eye, String accent,
        UnlockConditionDef unlock) {

    /** The colour syntax accepted in content files. */
    public static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    /**
     * Validates the colours.
     *
     * @param id the palette id
     * @param body the body colour
     * @param wing the wing colour
     * @param eye the eye colour
     * @param accent the accent colour
     * @param unlock the unlock condition
     */
    public PaletteDef {
        Objects.requireNonNull(id, "id");
        requireColor(id, "body", body);
        requireColor(id, "wing", wing);
        requireColor(id, "eye", eye);
        requireColor(id, "accent", accent);
    }

    private static void requireColor(String id, String field, String value) {
        if (value == null) {
            throw new NullPointerException(field);
        }
        if (!COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException("palette '" + id + "' " + field
                    + " is not a #RRGGBB colour: '" + value + "'");
        }
    }

    /**
     * Parses a {@code "#RRGGBB"} colour.
     *
     * @param color the colour text
     * @return the packed {@code 0xRRGGBB} value
     */
    public static int rgb(String color) {
        return Integer.parseInt(color.substring(1), 16);
    }

    /**
     * The body colour as {@code 0xRRGGBB}.
     *
     * @return the packed value
     */
    public int bodyRgb() {
        return rgb(body);
    }

    /**
     * The wing colour as {@code 0xRRGGBB}.
     *
     * @return the packed value
     */
    public int wingRgb() {
        return rgb(wing);
    }

    /**
     * The eye colour as {@code 0xRRGGBB}.
     *
     * @return the packed value
     */
    public int eyeRgb() {
        return rgb(eye);
    }

    /**
     * The accent colour as {@code 0xRRGGBB}.
     *
     * @return the packed value
     */
    public int accentRgb() {
        return rgb(accent);
    }
}
