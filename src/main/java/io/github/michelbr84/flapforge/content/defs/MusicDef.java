package io.github.michelbr84.flapforge.content.defs;

import java.util.List;
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
}
