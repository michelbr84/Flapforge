package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The procedural music of a world (§4), consumed by {@code MusicSequencer} in M8.
 *
 * @param tempo beats per minute
 * @param scale the scale name the sequencer draws notes from
 * @param seed the sequencer seed, so a world always sounds like itself
 * @param layers the voices to play
 */
public record MusicDef(int tempo, String scale, long seed, List<String> layers) {

    /** Slowest tempo a block may ask for, in BPM (the render cost is bounded by it). */
    public static final int MIN_TEMPO = 60;
    /** Fastest tempo a block may ask for, in BPM. */
    public static final int MAX_TEMPO = 200;

    /**
     * The scales a block may name, as semitone offsets from the root. This is the content's own
     * vocabulary: the validator checks names against it and the sequencer plays from it, so the
     * two can never disagree.
     */
    public static final Map<String, int[]> SCALES = Map.of(
            "major_pent", new int[] {0, 2, 4, 7, 9},
            "minor_pent", new int[] {0, 3, 5, 7, 10},
            "dorian", new int[] {0, 2, 3, 5, 7, 9, 10},
            "phrygian", new int[] {0, 1, 3, 5, 7, 8, 10},
            "whole_tone", new int[] {0, 2, 4, 6, 8, 10});

    /** The layers a block may name, in the sequencer's canonical draw order. */
    public static final List<String> LAYERS = List.of("bass", "lead", "arp", "pad", "drums");

    /**
     * Copies the layer list and checks the tempo.
     *
     * @throws NullPointerException when the scale is missing
     * @throws IllegalArgumentException when the tempo is not positive
     */
    public MusicDef {
        Objects.requireNonNull(scale, "scale");
        if (tempo < 1) {
            throw new IllegalArgumentException("music.tempo must be at least 1: " + tempo);
        }
        layers = List.copyOf(layers);
    }

    /**
     * Whether a block may name this scale.
     *
     * @param scale the scale name
     * @return {@code true} when known
     */
    public static boolean isKnownScale(String scale) {
        return scale != null && SCALES.containsKey(scale);
    }

    /**
     * Whether a block may name this layer.
     *
     * @param layer the layer name
     * @return {@code true} when known
     */
    public static boolean isKnownLayer(String layer) {
        return layer != null && LAYERS.contains(layer);
    }

    /**
     * The scale's semitone offsets.
     *
     * @return the offsets from the root, ascending
     */
    public int[] scaleOffsets() {
        int[] offsets = SCALES.get(scale);
        if (offsets == null) {
            throw new IllegalArgumentException("unknown music scale: " + scale);
        }
        return offsets;
    }
}
