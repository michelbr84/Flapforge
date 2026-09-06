package io.github.michelbr84.flapforge.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
        // Last resort, after every type the binder knows by name: on Android D8 desugars
        // records, so a def arrives here as a plain class and is recognised by its shape.
        if (isRecordLike(raw)) {
            return recordValue(raw, el, pointer);
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
        List<Component> components = componentsOf(raw);
        Map<String, Component> byKey = new LinkedHashMap<>();
        for (Component rc : components) {
            byKey.put(rc.key(), rc);
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
        Object[] args = new Object[components.size()];
        Class<?>[] paramTypes = new Class<?>[components.size()];
        int before = errors.size();
        for (int i = 0; i < components.size(); i++) {
            Component rc = components.get(i);
            paramTypes[i] = rc.type();
            String key = rc.key();
            JsonElement child = obj.has(key) ? obj.get(key) : null;
            args[i] = value(rc.genericType(), child, pointer + "/" + key);
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

    /**
     * One bindable component: the JSON key it reads, its declared type and its generic type.
     * Sourced from the record components on a JVM, and from the canonical constructor where the
     * runtime has no records (see {@link #isRecordLike}).
     *
     * @param key the JSON key
     * @param type the declared type
     * @param genericType the generic type, for {@code List}/{@code Map} components
     */
    record Component(String key, Class<?> type, Type genericType) {
    }

    /**
     * Whether a class binds like a record.
     *
     * <p>{@code Class.isRecord()} is not the whole answer on Android: D8 desugars records into
     * plain classes that no longer extend {@code java.lang.Record}, so every def would fall
     * through to "unsupported target type" and the content would refuse to load. The desugared
     * shape is still exact — a final class whose final instance fields are the components, built
     * by a canonical constructor over precisely those types — so it is recognised structurally.
     *
     * @param raw the candidate class
     * @return {@code true} when the class can be bound as a record
     */
    private static boolean isRecordLike(Class<?> raw) {
        if (raw.isRecord()) {
            return true;
        }
        if (raw.isInterface() || raw.isEnum() || raw.isPrimitive() || raw.isArray()
                || raw.isAnnotation() || !Modifier.isFinal(raw.getModifiers())) {
            return false;
        }
        // A platform class is never content: Double and friends are final, one-field and
        // constructible, so they would otherwise pass the shape test if one reached this far.
        // The "java" prefix covers the java and jakarta-era javax trees alike.
        String pkg = raw.getPackageName();
        if (pkg.startsWith("java") || pkg.startsWith("android.") || pkg.startsWith("kotlin.")) {
            return false;
        }
        List<Field> fields = instanceFields(raw);
        if (fields.isEmpty()) {
            return false;
        }
        for (Field f : fields) {
            if (!Modifier.isFinal(f.getModifiers())) {
                return false;
            }
        }
        return !structuralComponents(raw, fields).isEmpty();
    }

    /**
     * The components of a record-like class, in canonical-constructor order.
     *
     * @param raw the class
     * @return the components
     */
    static List<Component> componentsOf(Class<?> raw) {
        if (!raw.isRecord()) {
            return structuralComponents(raw);
        }
        List<Component> out = new ArrayList<>();
        for (RecordComponent rc : raw.getRecordComponents()) {
            out.add(new Component(jsonName(rc), rc.getType(), rc.getGenericType()));
        }
        return out;
    }

    /**
     * The components of a desugared record, read off its canonical constructor.
     *
     * @param raw the class
     * @return the components in canonical-constructor order, empty when the class is not one
     */
    static List<Component> structuralComponents(Class<?> raw) {
        return structuralComponents(raw, instanceFields(raw));
    }

    /**
     * The components of a desugared record.
     *
     * <p>The order is the constructor's, never the fields': the dex format stores a class's
     * fields sorted by name, so {@code getDeclaredFields()} on Android hands back alphabetical
     * order. Trusting it swaps same-typed components — {@code UpgradesDef(trees, nodes)} would
     * bind {@code nodes} into {@code trees} — and rejects the rest outright. The constructor's
     * parameters are declaration order by definition; each one is matched to its field by name
     * ({@code -parameters} is on for both builds) or, failing that, by a unique type.
     *
     * @param raw the class
     * @param fields the instance fields, in any order
     * @return the components in canonical-constructor order, empty when the class is not one
     */
    static List<Component> structuralComponents(Class<?> raw, List<Field> fields) {
        Constructor<?> ctor = canonicalConstructor(raw, fields);
        if (ctor == null) {
            return List.of();
        }
        Map<String, Field> byName = new LinkedHashMap<>();
        for (Field f : fields) {
            byName.put(f.getName(), f);
        }
        Parameter[] params = ctor.getParameters();
        Type[] generics = ctor.getGenericParameterTypes();
        List<Component> out = new ArrayList<>();
        List<Field> unclaimed = new ArrayList<>(fields);
        for (int i = 0; i < params.length; i++) {
            Field field = params[i].isNamePresent() ? byName.get(params[i].getName()) : null;
            if (field == null) {
                field = onlyFieldOfType(unclaimed, params[i].getType());
            }
            if (field == null) {
                // Neither a name nor a unique type: the mapping would be a guess, and a guessed
                // component silently binds the wrong JSON key onto the wrong slot.
                return List.of();
            }
            unclaimed.remove(field);
            JsonName annotation = field.getAnnotation(JsonName.class);
            out.add(new Component(annotation == null ? field.getName() : annotation.value(),
                    params[i].getType(), generics[i]));
        }
        return out;
    }

    /**
     * The constructor that takes every component once: the canonical one. Matched on the
     * multiset of parameter types, so it is found whatever order the fields arrive in.
     *
     * @param raw the class
     * @param fields the instance fields
     * @return the constructor, or {@code null} when no constructor covers exactly the fields
     */
    private static Constructor<?> canonicalConstructor(Class<?> raw, List<Field> fields) {
        List<String> wanted = new ArrayList<>();
        for (Field f : fields) {
            wanted.add(f.getType().getName());
        }
        Collections.sort(wanted);
        for (Constructor<?> candidate : raw.getDeclaredConstructors()) {
            if (candidate.getParameterCount() != fields.size()) {
                continue;
            }
            List<String> got = new ArrayList<>();
            for (Class<?> t : candidate.getParameterTypes()) {
                got.add(t.getName());
            }
            Collections.sort(got);
            if (wanted.equals(got)) {
                return candidate;
            }
        }
        return null;
    }

    private static Field onlyFieldOfType(List<Field> fields, Class<?> type) {
        Field found = null;
        for (Field f : fields) {
            if (f.getType() == type) {
                if (found != null) {
                    return null;
                }
                found = f;
            }
        }
        return found;
    }

    private static List<Field> instanceFields(Class<?> raw) {
        List<Field> out = new ArrayList<>();
        for (Field f : raw.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                out.add(f);
            }
        }
        return out;
    }
}
