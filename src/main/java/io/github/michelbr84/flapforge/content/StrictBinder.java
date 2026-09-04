package io.github.michelbr84.flapforge.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Binds a {@link JsonElement} tree onto Java records by reflection over their record components
 * (D10). There is no per-record hand-written code: the component names, declared types and
 * generic parameters are the schema.
 *
 * <p>Strictness rules:
 * <ul>
 *   <li>a JSON key that matches no record component is an error (typos never produce
 *       effect-less content); keys starting with {@code _comment} are ignored, since shipped JSON
 *       carries no comments;</li>
 *   <li>an enum is bound with {@code valueOf}, retried once upper-cased so {@code "any_of"} maps
 *       to {@code ANY_OF}; an unknown constant is an error listing the valid ones;</li>
 *   <li>a missing {@code List} or {@code Map} becomes empty, a missing primitive becomes
 *       {@code 0}/{@code false}, and any other missing component becomes {@code null} — that is
 *       how an optional block such as {@code sprite} is expressed;</li>
 *   <li>a number is converted the way {@link com.google.gson.ToNumberPolicy#LONG_OR_DOUBLE}
 *       would; a fractional value in an {@code int}/{@code long} component is an error;</li>
 *   <li>{@link IllegalArgumentException} and {@link NullPointerException} thrown by a compact
 *       constructor (range checks, {@code Objects.requireNonNull}) are reported at the record's
 *       own pointer instead of escaping.</li>
 * </ul>
 *
 * <p>Every error carries a {@code file#/json/pointer} location. Errors accumulate; call
 * {@link #check()} once at the end of a file to raise them together as a
 * {@link ContentException}.
 */
public final class StrictBinder {

    /** JSON keys with this prefix are documentation and are never bound. */
    public static final String COMMENT_PREFIX = "_comment";

    private final String file;
    private final List<String> errors = new ArrayList<>();

    /**
     * Creates a binder for one file.
     *
     * @param file the file label used in error locations, for example {@code birds.json}
     */
    public StrictBinder(String file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    /**
     * Binds a whole file and raises every error at once.
     *
     * @param type the target record type
     * @param root the parsed tree
     * @param file the file label used in error locations
     * @param <T> the target type
     * @return the bound record
     * @throws ContentException when anything failed to bind
     */
    public static <T> T bindStrict(Class<T> type, JsonElement root, String file) {
        StrictBinder binder = new StrictBinder(file);
        T value = binder.bind(type, root);
        binder.check();
        return value;
    }

    /**
     * Binds a whole file whose root is a JSON array and raises every error at once.
     *
     * @param elementType the target record type of one entry
     * @param root the parsed tree
     * @param file the file label used in error locations
     * @param <T> the entry type
     * @return the bound entries in file order
     * @throws ContentException when anything failed to bind
     */
    public static <T> List<T> bindListStrict(Class<T> elementType, JsonElement root, String file) {
        StrictBinder binder = new StrictBinder(file);
        List<T> value = binder.bindList(elementType, root);
        binder.check();
        return value;
    }

    /**
     * Binds a tree onto a record, accumulating errors.
     *
     * @param type the target record type
     * @param root the parsed tree
     * @param <T> the target type
     * @return the bound record, or {@code null} when it could not be built
     */
    public <T> T bind(Class<T> type, JsonElement root) {
        return type.cast(value(type, root, ""));
    }

    /**
     * Binds a JSON array onto a list of records, accumulating errors.
     *
     * @param elementType the target record type of one entry
     * @param root the parsed tree
     * @param <T> the entry type
     * @return the bound entries in file order (empty when the root is not an array)
     */
    public <T> List<T> bindList(Class<T> elementType, JsonElement root) {
        if (root == null || !root.isJsonArray()) {
            error("", "expected an array of " + elementType.getSimpleName());
            return List.of();
        }
        JsonArray array = root.getAsJsonArray();
        List<T> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            T bound = elementType.cast(value(elementType, array.get(i), "/" + i));
            if (bound != null) {
                out.add(bound);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * The errors found so far, in discovery order.
     *
     * @return an unmodifiable list
     */
    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    /**
     * Raises the accumulated errors, if any.
     *
     * @throws ContentException when at least one error was recorded
     */
    public void check() {
        if (!errors.isEmpty()) {
            throw new ContentException("Failed to bind " + file, errors);
        }
    }

    /**
     * Records an error.
     *
     * @param pointer the JSON pointer inside the file
     * @param message what is wrong
     */
    public void error(String pointer, String message) {
        errors.add(file + "#" + pointer + ": " + message);
    }

    // ---------------------------------------------------------------- binding

    private Object value(Type type, JsonElement el, String pointer) {
        Class<?> raw = rawType(type);
        if (el == null || el.isJsonNull()) {
            return missing(raw, pointer);
        }
        if (raw == List.class) {
            return listValue(type, el, pointer);
        }
        if (raw == Map.class) {
            return mapValue(type, el, pointer);
        }
        if (raw.isRecord()) {
            return recordValue(raw, el, pointer);
        }
        if (raw.isEnum()) {
            String name = stringValue(el, pointer);
            return name == null ? null : enumValue(raw, name, pointer);
        }
        if (raw == String.class) {
            return stringValue(el, pointer);
        }
        if (raw == boolean.class || raw == Boolean.class) {
            return booleanValue(el, pointer);
        }
        if (raw == double.class || raw == Double.class) {
            Number n = numberValue(el, pointer);
            return n == null ? missing(raw, pointer) : n.doubleValue();
        }
        if (raw == int.class || raw == Integer.class) {
            Long n = integralValue(el, pointer);
            if (n == null) {
                return missing(raw, pointer);
            }
            if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) {
                error(pointer, "value out of int range: " + n);
                return missing(raw, pointer);
            }
            return n.intValue();
        }
        if (raw == long.class || raw == Long.class) {
            Long n = integralValue(el, pointer);
            return n == null ? missing(raw, pointer) : n;
        }
        if (raw == Object.class) {
            return genericValue(el, pointer);
        }
        error(pointer, "unsupported target type " + raw.getName());
        return null;
    }

    /**
     * Binds a component declared as {@code Object} (or a {@code Map<String, Object>} value) to the
     * plain shape of the JSON: a string, a boolean, a {@code LONG_OR_DOUBLE} number, an
     * unmodifiable {@code Map<String, Object>} (comment keys dropped) or an unmodifiable
     * {@code List<Object>}. This is how a pattern step's kind-dependent {@code params} reach
     * {@code ObstacleParams}, which validates them against the kind's contract.
     *
     * @param el the element
     * @param pointer the pointer, for errors
     * @return the value, never {@code null} for a non-null element
     */
    private Object genericValue(JsonElement el, String pointer) {
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            Map<String, Object> out = new LinkedHashMap<>();
            for (String key : obj.keySet()) {
                if (key.startsWith(COMMENT_PREFIX)) {
                    continue;
                }
                JsonElement child = obj.get(key);
                if (child == null || child.isJsonNull()) {
                    error(pointer + "/" + key, "a null value is not allowed here");
                    continue;
                }
                out.put(key, genericValue(child, pointer + "/" + key));
            }
            return Collections.unmodifiableMap(out);
        }
        if (el.isJsonArray()) {
            JsonArray array = el.getAsJsonArray();
            List<Object> out = new ArrayList<>(array.size());
            for (int i = 0; i < array.size(); i++) {
                JsonElement child = array.get(i);
                if (child == null || child.isJsonNull()) {
                    error(pointer + "/" + i, "a null value is not allowed here");
                    continue;
                }
                out.add(genericValue(child, pointer + "/" + i));
            }
            return Collections.unmodifiableList(out);
        }
        JsonPrimitive prim = el.getAsJsonPrimitive();
        if (prim.isBoolean()) {
            return prim.getAsBoolean();
        }
        if (prim.isNumber()) {
            return longOrDouble(prim.getAsString());
        }
        return prim.getAsString();
    }

    private Object missing(Class<?> raw, String pointer) {
        if (raw == List.class) {
            return List.of();
        }
        if (raw == Map.class) {
            return Map.of();
        }
        if (raw == boolean.class) {
            return Boolean.FALSE;
        }
        if (raw == double.class) {
            return 0.0d;
        }
        if (raw == int.class) {
            return 0;
        }
        if (raw == long.class) {
            return 0L;
        }
        if (raw.isPrimitive()) {
            error(pointer, "unsupported primitive component type " + raw.getName());
        }
        return null;
    }

    private Object recordValue(Class<?> raw, JsonElement el, String pointer) {
        if (!el.isJsonObject()) {
            error(pointer, "expected an object for " + raw.getSimpleName());
            return null;
        }
        JsonObject obj = el.getAsJsonObject();
        RecordComponent[] components = raw.getRecordComponents();
        Map<String, RecordComponent> byKey = new LinkedHashMap<>();
        for (RecordComponent rc : components) {
            byKey.put(jsonName(rc), rc);
        }
        for (String key : obj.keySet()) {
            if (key.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            if (!byKey.containsKey(key)) {
                error(pointer + "/" + key, "unknown key '" + key + "' for "
                        + raw.getSimpleName() + " (known keys: " + byKey.keySet() + ")");
            }
        }
        Object[] args = new Object[components.length];
        Class<?>[] paramTypes = new Class<?>[components.length];
        int before = errors.size();
        for (int i = 0; i < components.length; i++) {
            RecordComponent rc = components[i];
            paramTypes[i] = rc.getType();
            String key = jsonName(rc);
            JsonElement child = obj.has(key) ? obj.get(key) : null;
            args[i] = value(rc.getGenericType(), child, pointer + "/" + key);
        }
        if (errors.size() > before) {
            // A component already failed; constructing would only add a cascading complaint.
            return null;
        }
        try {
            Constructor<?> ctor = raw.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (NoSuchMethodException e) {
            error(pointer, "no canonical constructor for " + raw.getSimpleName());
        } catch (InstantiationException | IllegalAccessException e) {
            error(pointer, "cannot instantiate " + raw.getSimpleName() + ": " + e);
        } catch (InvocationTargetException e) {
            reportConstructorFailure(raw, pointer, e.getCause());
        }
        return null;
    }

    private void reportConstructorFailure(Class<?> raw, String pointer, Throwable cause) {
        String detail = cause == null ? "unknown failure" : cause.getMessage();
        if (cause instanceof NullPointerException) {
            error(pointer, "missing required value in " + raw.getSimpleName() + ": " + detail);
        } else if (cause instanceof IllegalArgumentException) {
            error(pointer, "invalid " + raw.getSimpleName() + ": " + detail);
        } else {
            error(pointer, "invalid " + raw.getSimpleName() + ": " + cause);
        }
    }

    private Object listValue(Type type, JsonElement el, String pointer) {
        if (!el.isJsonArray()) {
            error(pointer, "expected an array");
            return List.of();
        }
        Type elementType = typeArgument(type, 0, pointer);
        if (elementType == null) {
            return List.of();
        }
        JsonArray array = el.getAsJsonArray();
        List<Object> out = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); i++) {
            Object bound = value(elementType, array.get(i), pointer + "/" + i);
            if (bound != null) {
                out.add(bound);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private Object mapValue(Type type, JsonElement el, String pointer) {
        if (!el.isJsonObject()) {
            error(pointer, "expected an object");
            return Map.of();
        }
        Type keyType = typeArgument(type, 0, pointer);
        Type valueType = typeArgument(type, 1, pointer);
        if (keyType == null || valueType == null) {
            return Map.of();
        }
        Class<?> rawKey = rawType(keyType);
        JsonObject obj = el.getAsJsonObject();
        Map<Object, Object> out = new LinkedHashMap<>();
        for (String key : obj.keySet()) {
            if (key.startsWith(COMMENT_PREFIX)) {
                continue;
            }
            String childPointer = pointer + "/" + key;
            Object boundKey;
            if (rawKey == String.class) {
                boundKey = key;
            } else if (rawKey.isEnum()) {
                boundKey = enumValue(rawKey, key, childPointer);
            } else {
                error(childPointer, "unsupported map key type " + rawKey.getName());
                boundKey = null;
            }
            Object boundValue = value(valueType, obj.get(key), childPointer);
            if (boundKey != null && boundValue != null) {
                out.put(boundKey, boundValue);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private Object enumValue(Class<?> raw, String name, String pointer) {
        Object[] constants = raw.getEnumConstants();
        for (Object c : constants) {
            if (((Enum<?>) c).name().equals(name)) {
                return c;
            }
        }
        String upper = name.toUpperCase(Locale.ROOT);
        for (Object c : constants) {
            if (((Enum<?>) c).name().equals(upper)) {
                return c;
            }
        }
        List<String> valid = new ArrayList<>(constants.length);
        for (Object c : constants) {
            valid.add(((Enum<?>) c).name());
        }
        error(pointer, "not a valid " + raw.getSimpleName() + ": '" + name + "' (expected one of "
                + valid + ")");
        return null;
    }

    private String stringValue(JsonElement el, String pointer) {
        JsonPrimitive prim = primitive(el, pointer, "a string");
        if (prim == null) {
            return null;
        }
        if (!prim.isString()) {
            error(pointer, "expected a string, got " + prim);
            return null;
        }
        return prim.getAsString();
    }

    private Object booleanValue(JsonElement el, String pointer) {
        JsonPrimitive prim = primitive(el, pointer, "a boolean");
        if (prim == null) {
            return null;
        }
        if (!prim.isBoolean()) {
            error(pointer, "expected a boolean, got " + prim);
            return null;
        }
        return prim.getAsBoolean();
    }

    private Number numberValue(JsonElement el, String pointer) {
        JsonPrimitive prim = primitive(el, pointer, "a number");
        if (prim == null) {
            return null;
        }
        if (!prim.isNumber()) {
            error(pointer, "expected a number, got " + prim);
            return null;
        }
        return longOrDouble(prim.getAsString());
    }

    private Long integralValue(JsonElement el, String pointer) {
        Number n = numberValue(el, pointer);
        if (n == null) {
            return null;
        }
        if (n instanceof Long) {
            return (Long) n;
        }
        double d = n.doubleValue();
        if (d != Math.rint(d) || Double.isNaN(d) || Double.isInfinite(d)) {
            error(pointer, "expected a whole number, got " + n);
            return null;
        }
        return (long) d;
    }

    private JsonPrimitive primitive(JsonElement el, String pointer, String expected) {
        if (!el.isJsonPrimitive()) {
            error(pointer, "expected " + expected + ", got " + kindOf(el));
            return null;
        }
        return el.getAsJsonPrimitive();
    }

    /**
     * Converts a JSON number the way {@code ToNumberPolicy.LONG_OR_DOUBLE} does: a literal with
     * no fraction, no exponent and a value that fits becomes a {@code Long}, everything else a
     * {@code Double}.
     *
     * @param literal the raw JSON literal
     * @return the number
     */
    static Number longOrDouble(String literal) {
        if (literal.indexOf('.') < 0 && literal.indexOf('e') < 0 && literal.indexOf('E') < 0) {
            try {
                return Long.valueOf(literal);
            } catch (NumberFormatException ignored) {
                // Falls through to double for values outside the long range.
            }
        }
        return Double.valueOf(literal);
    }

    private static String kindOf(JsonElement el) {
        if (el.isJsonArray()) {
            return "an array";
        }
        if (el.isJsonObject()) {
            return "an object";
        }
        return "null";
    }

    private Type typeArgument(Type type, int index, String pointer) {
        if (type instanceof ParameterizedType) {
            Type[] args = ((ParameterizedType) type).getActualTypeArguments();
            if (index < args.length) {
                return args[index];
            }
        }
        error(pointer, "raw collection type is not supported: " + type);
        return null;
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        }
        if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        }
        throw new IllegalArgumentException("Unsupported component type: " + type);
    }

    private static String jsonName(RecordComponent rc) {
        JsonName annotation = rc.getAnnotation(JsonName.class);
        return annotation == null ? rc.getName() : annotation.value();
    }
}
