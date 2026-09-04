package io.github.michelbr84.flapforge.render;

import java.util.Locale;

/**
 * The parallax backdrop style of a world (D18, M7): the value of {@code worlds.json.style},
 * resolved once when a run starts. Each style is a different set of precomputed shapes in
 * {@link BackgroundRenderer}; the palette colours them, the style says what they are.
 */
public enum WorldStyle {

    /** Green Fields: two hill bands and grass tufts, exactly as M1 drew them. */
    HILLS("hills"),
    /** Wind Valley: layered mesas under a dust haze. */
    CANYON("canyon"),
    /** Iron Forge: chimneys, girders and rising embers. */
    FACTORY("factory"),
    /** Storm Sky: cloud banks, rain streaks and a distant flicker. */
    STORM("storm"),
    /** The Void: floating shards over a slow star field, no grass. */
    VOID("void");

    private final String id;

    WorldStyle(String id) {
        this.id = id;
    }

    /**
     * The value {@code worlds.json.style} carries.
     *
     * @return the id
     */
    public String id() {
        return id;
    }

    /**
     * Resolves a style id; anything unknown (or {@code null}) draws the hills, so a world with a
     * misspelt style is a green one rather than a blank one.
     *
     * @param id the {@code worlds.json.style} value
     * @return the style
     */
    public static WorldStyle fromId(String id) {
        if (id == null) {
            return HILLS;
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT);
        for (WorldStyle style : values()) {
            if (style.id.equals(wanted)) {
                return style;
            }
        }
        return HILLS;
    }
}
