package io.github.michelbr84.flapforge.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * An immutable, insertion-ordered lookup table for one kind of content definition (D10).
 *
 * <p>Iteration order is the order the entries appear in their JSON file, so anything derived from
 * a registry (spawn tables, UI lists, hashes) is deterministic. A duplicate id is <em>kept</em> in
 * {@link #all()} and the first occurrence wins in {@link #get(String)}; reporting duplicates is
 * {@link ContentValidator}'s job, which needs to see all of them at once.
 *
 * @param <T> the definition type
 */
public final class Registry<T> implements Iterable<T> {

    private final String kind;
    private final List<T> items;
    private final Map<String, T> byId;
    private final List<String> ids;

    /**
     * Creates a registry.
     *
     * @param kind the kind name used in {@link UnknownIdException} ({@code bird}, {@code tier}, …)
     * @param items the definitions in file order
     * @param idOf extracts the id of a definition
     */
    public Registry(String kind, List<T> items, Function<? super T, String> idOf) {
        this.kind = Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(idOf, "idOf");
        this.items = List.copyOf(items);
        Map<String, T> map = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(this.items.size());
        for (T item : this.items) {
            String id = idOf.apply(item);
            names.add(id);
            map.putIfAbsent(id, item);
        }
        this.byId = Collections.unmodifiableMap(map);
        this.ids = Collections.unmodifiableList(names);
    }

    /**
     * An empty registry.
     *
     * @param kind the kind name
     * @param <T> the definition type
     * @return the registry
     */
    public static <T> Registry<T> empty(String kind) {
        return new Registry<>(kind, List.of(), t -> "");
    }

    /**
     * Looks up a definition.
     *
     * @param id the id
     * @return the definition
     * @throws UnknownIdException when no definition carries the id
     */
    public T get(String id) {
        T value = byId.get(id);
        if (value == null) {
            throw new UnknownIdException(kind, id);
        }
        return value;
    }

    /**
     * Whether an id is known.
     *
     * @param id the id
     * @return {@code true} when {@link #get(String)} would succeed
     */
    public boolean contains(String id) {
        return byId.containsKey(id);
    }

    /**
     * Every definition in file order.
     *
     * @return an unmodifiable list
     */
    public List<T> all() {
        return items;
    }

    /**
     * Every id in file order, duplicates included.
     *
     * @return an unmodifiable list
     */
    public List<String> ids() {
        return ids;
    }

    /**
     * The kind name.
     *
     * @return the kind
     */
    public String kind() {
        return kind;
    }

    /**
     * Number of definitions.
     *
     * @return the size
     */
    public int size() {
        return items.size();
    }

    @Override
    public Iterator<T> iterator() {
        return items.iterator();
    }

    @Override
    public String toString() {
        return "Registry{" + kind + ", " + ids + '}';
    }
}
