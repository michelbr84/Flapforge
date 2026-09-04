package io.github.michelbr84.flapforge.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable mapping from {@link InputAction} to the key codes that trigger it (D17).
 *
 * <p>Defaults: flap Space/Up, ability X/Shift, pause Esc, confirm Enter, back Esc, arrows,
 * mute M, debug F3, fullscreen F11. The mapping serialises to {@code Map<String, List<Integer>>}
 * keyed by action name so the settings store can persist it without knowing the enum.
 *
 * <p>Only {@link #PERSISTED} is written: §4's {@code keyBindings} block lists exactly the seven
 * actions the settings screen can rebind. {@code BACK} and the four focus arrows are fixed, so
 * writing them would put keys in the file that no screen can edit and that a hand edit could
 * silently double-bind. {@link #fromMap} falls back to the defaults for anything absent, so a file
 * written by an older build still loads.
 */
public final class KeyBindings {

    /**
     * The actions {@link #toMap()} writes and the settings screen may rebind, in §4's order. The
     * rest keep their defaults.
     */
    public static final List<InputAction> PERSISTED = List.of(InputAction.FLAP,
            InputAction.ABILITY, InputAction.PAUSE, InputAction.CONFIRM, InputAction.MUTE,
            InputAction.DEBUG, InputAction.FULLSCREEN);

    private static final Map<InputAction, List<Integer>> DEFAULTS;

    static {
        Map<InputAction, List<Integer>> d = new EnumMap<>(InputAction.class);
        d.put(InputAction.FLAP, List.of(Keys.SPACE, Keys.UP));
        d.put(InputAction.ABILITY, List.of(Keys.X, Keys.SHIFT));
        d.put(InputAction.PAUSE, List.of(Keys.ESCAPE));
        d.put(InputAction.CONFIRM, List.of(Keys.ENTER));
        d.put(InputAction.BACK, List.of(Keys.ESCAPE));
        d.put(InputAction.UP, List.of(Keys.UP));
        d.put(InputAction.DOWN, List.of(Keys.DOWN));
        d.put(InputAction.LEFT, List.of(Keys.LEFT));
        d.put(InputAction.RIGHT, List.of(Keys.RIGHT));
        d.put(InputAction.MUTE, List.of(Keys.M));
        d.put(InputAction.DEBUG, List.of(Keys.F3));
        d.put(InputAction.FULLSCREEN, List.of(Keys.F11));
        DEFAULTS = Collections.unmodifiableMap(d);
    }

    private final Map<InputAction, List<Integer>> byAction;
    private final Map<Integer, EnumSet<InputAction>> byCode;

    private KeyBindings(Map<InputAction, List<Integer>> bindings) {
        Map<InputAction, List<Integer>> copy = new EnumMap<>(InputAction.class);
        Map<Integer, EnumSet<InputAction>> reverse = new LinkedHashMap<>();
        for (InputAction action : InputAction.values()) {
            List<Integer> codes = bindings.getOrDefault(action, DEFAULTS.get(action));
            List<Integer> distinct = new ArrayList<>();
            for (Integer code : codes) {
                if (code != null && !distinct.contains(code)) {
                    distinct.add(code);
                }
            }
            copy.put(action, Collections.unmodifiableList(distinct));
            for (Integer code : distinct) {
                reverse.computeIfAbsent(code, c -> EnumSet.noneOf(InputAction.class)).add(action);
            }
        }
        this.byAction = Collections.unmodifiableMap(copy);
        this.byCode = Collections.unmodifiableMap(reverse);
    }

    /**
     * The default bindings.
     *
     * @return the defaults
     */
    public static KeyBindings defaults() {
        return new KeyBindings(DEFAULTS);
    }

    /**
     * Rebuilds bindings from their serialised form. Unknown action names are ignored and missing
     * actions fall back to their defaults.
     *
     * @param serialized action name to key codes
     * @return the bindings
     */
    public static KeyBindings fromMap(Map<String, List<Integer>> serialized) {
        Map<InputAction, List<Integer>> parsed = new EnumMap<>(InputAction.class);
        if (serialized != null) {
            for (Map.Entry<String, List<Integer>> e : serialized.entrySet()) {
                InputAction action = parseAction(e.getKey());
                if (action != null && e.getValue() != null) {
                    parsed.put(action, e.getValue());
                }
            }
        }
        return new KeyBindings(parsed);
    }

    private static InputAction parseAction(String name) {
        if (name == null) {
            return null;
        }
        for (InputAction a : InputAction.values()) {
            if (a.name().equalsIgnoreCase(name)) {
                return a;
            }
        }
        return null;
    }

    /**
     * Serialises the rebindable bindings to a plain map.
     *
     * @return action name to key codes for every action in {@link #PERSISTED}, in that order
     */
    public Map<String, List<Integer>> toMap() {
        Map<String, List<Integer>> out = new LinkedHashMap<>();
        for (InputAction a : PERSISTED) {
            out.put(a.name(), new ArrayList<>(byAction.get(a)));
        }
        return out;
    }

    /**
     * Key codes bound to an action.
     *
     * @param action the action
     * @return an unmodifiable list, possibly empty
     */
    public List<Integer> keysFor(InputAction action) {
        return byAction.get(action);
    }

    /**
     * Actions bound to a key code.
     *
     * @param code the key code
     * @return the actions (a fresh set, possibly empty)
     */
    public EnumSet<InputAction> actionsFor(int code) {
        EnumSet<InputAction> set = byCode.get(code);
        return set == null ? EnumSet.noneOf(InputAction.class) : EnumSet.copyOf(set);
    }

    /**
     * Tells whether a key code is bound to an action.
     *
     * @param code the key code
     * @param action the action
     * @return {@code true} when bound
     */
    public boolean isBound(int code, InputAction action) {
        EnumSet<InputAction> set = byCode.get(code);
        return set != null && set.contains(action);
    }

    /**
     * Returns a copy with one action rebound.
     *
     * @param action the action
     * @param codes the new key codes (empty unbinds the action)
     * @return the new bindings
     */
    public KeyBindings withBinding(InputAction action, List<Integer> codes) {
        Objects.requireNonNull(action, "action");
        Map<InputAction, List<Integer>> next = new EnumMap<>(byAction);
        next.put(action, List.copyOf(codes));
        return new KeyBindings(next);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof KeyBindings other && byAction.equals(other.byAction);
    }

    @Override
    public int hashCode() {
        return byAction.hashCode();
    }

    @Override
    public String toString() {
        return "KeyBindings" + byAction;
    }
}
