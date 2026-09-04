package io.github.michelbr84.flapforge.gameplay.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Modifiers grouped by {@link Layer} (D8). Each layer is replaced wholesale by its owner (the
 * difficulty state owns {@code DIFFICULTY}, the modifier director owns {@code MODIFIERS} ...).
 * A version counter lets {@link StatSheet} rebuild its cache only when something changed.
 *
 * <p>Iteration order is the {@link Layer} declaration order, then insertion order inside a layer,
 * so breakdowns are deterministic; the arithmetic itself does not depend on order.
 */
public final class EffectStack {

    /** One modifier with the layer it lives in. */
    public record Entry(Layer layer, StatModifier modifier) {
    }

    private final EnumMap<Layer, List<StatModifier>> layers = new EnumMap<>(Layer.class);
    private long version;

    /**
     * Replaces the content of a layer. Setting a layer to a list equal to its current content
     * does not bump the version.
     *
     * @param layer the layer
     * @param modifiers the new content (copied)
     */
    public void setLayer(Layer layer, List<StatModifier> modifiers) {
        List<StatModifier> copy = List.copyOf(modifiers);
        List<StatModifier> current = layers.get(layer);
        if (copy.isEmpty()) {
            if (current != null) {
                layers.remove(layer);
                version++;
            }
            return;
        }
        if (copy.equals(current)) {
            return;
        }
        layers.put(layer, copy);
        version++;
    }

    /**
     * Empties a layer.
     *
     * @param layer the layer
     */
    public void clearLayer(Layer layer) {
        setLayer(layer, List.of());
    }

    /** Empties every layer. */
    public void clear() {
        if (!layers.isEmpty()) {
            layers.clear();
            version++;
        }
    }

    /**
     * Content of a layer.
     *
     * @param layer the layer
     * @return an unmodifiable list (empty when the layer is unset)
     */
    public List<StatModifier> layer(Layer layer) {
        List<StatModifier> list = layers.get(layer);
        return list == null ? List.of() : list;
    }

    /**
     * Read-only view of every non-empty layer, in {@link Layer} order.
     *
     * @return the layers
     */
    public Map<Layer, List<StatModifier>> layers() {
        return Collections.unmodifiableMap(layers);
    }

    /**
     * Every modifier with its layer, in deterministic order.
     *
     * @return a new list
     */
    public List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<Layer, List<StatModifier>> e : layers.entrySet()) {
            for (StatModifier m : e.getValue()) {
                out.add(new Entry(e.getKey(), m));
            }
        }
        return out;
    }

    /**
     * Counter incremented on every change; {@link StatSheet} compares it with the version of its
     * cache.
     *
     * @return the version
     */
    public long version() {
        return version;
    }

    /**
     * Total number of modifiers across all layers.
     *
     * @return the count
     */
    public int size() {
        int n = 0;
        for (List<StatModifier> list : layers.values()) {
            n += list.size();
        }
        return n;
    }
}
