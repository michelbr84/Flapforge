package jrecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Record reflection shim for the M10 build-time source transform (rules T4b and T4c:
 * {@code raw.isRecord()} -> {@link #isRecord(Class)}, {@code raw.getRecordComponents()} ->
 * {@link #components(Class)}).
 *
 * <p><b>Why.</b> {@code content.StrictBinder} (D10) binds JSON onto the game's records by
 * reflection over their record components, in declaration order — the order the canonical
 * constructor takes. With {@code minSdk 33} D8 desugars every record in the APK: the fields,
 * the canonical constructor and the compact-constructor checks survive, but the {@code Record}
 * attribute does not. So on Android 13 {@code Class.isRecord()} does not exist at all
 * ({@code NoSuchMethodError} in {@code GameApplication.start}, the boot failure every device
 * showed), on Android 14+ it answers {@code false} and {@code getRecordComponents()} answers
 * nothing, and {@code Class.getDeclaredFields()} comes back in dex order (alphabetical), which
 * is not the constructor's order. Robolectric never saw any of this: it runs on the JVM, where
 * records are real.
 *
 * <p><b>The table.</b> The Android build's {@code recordMetadata} task compiles the desktop tree
 * with the JDK, where records are still records, and writes {@value #TABLE_RESOURCE} into the
 * APK: one line per record, {@code <binary class name>=<component names in declaration
 * order, comma-separated>} (an empty value for a zero-component record). That table is the
 * source of truth on <em>every</em> runtime — the JVM, ART 13, ART 14+ — so the binder behaves
 * identically wherever it runs; the platform is never asked about a class the table lists. A
 * component is then the record's declared {@link java.lang.reflect.Field} of that name
 * ({@link RecordComponent}); a table entry whose field is missing is an
 * {@link IllegalStateException} naming the class and the field, never a silent {@code null},
 * because it means the table and the classes come from different builds.
 *
 * <p><b>Fallback.</b> A class absent from the table is asked of the platform, reflectively —
 * {@code Class.isRecord()} / {@code getRecordComponents()} looked up by name, so the shim
 * itself loads and verifies on Android 13, where neither exists (no method: not a record). The
 * fallback covers a record the table missed on any runtime that still has real records: the
 * JVM, or ART 14+ for a record D8 did not desugar; a desugared record the table missed is not
 * a record to the platform either, and the binder reports it as an unsupported type.
 *
 * <p><b>Loading and caching.</b> The table is read once, lazily, on the first query, through
 * {@code Records.class.getResourceAsStream} (the class's own loader: the APK, or the test
 * classpath), under a lock so concurrent first callers share one parse; a missing table is an
 * {@link IllegalStateException} rather than an empty one, since on a device it would turn every
 * record into a content error. Components are resolved once per class and cached;
 * {@link #components(Class)} returns a fresh copy of the cached array on every call, so a
 * caller may reorder or overwrite its array without affecting the next caller (the platform's
 * {@code getRecordComponents()} allocates a new array each time as well).
 */
public final class Records {

    /** Classpath-relative path of the table the Android build writes. */
    public static final String TABLE_RESOURCE = "/META-INF/flapforge-records.properties";

    private static final Object TABLE_LOCK = new Object();
    /** {@code <binary class name> -> component names in declaration order}; immutable. */
    private static volatile Map<String, List<String>> table;
    private static final ConcurrentMap<Class<?>, RecordComponent[]> COMPONENTS =
            new ConcurrentHashMap<>();
    private static final ConcurrentMap<Class<?>, Boolean> PLATFORM_RECORDS =
            new ConcurrentHashMap<>();
    /** {@code Class.isRecord()}, or {@code null} where the platform has none (Android 13). */
    private static final Method PLATFORM_IS_RECORD = platformMethod("isRecord");
    /** {@code Class.getRecordComponents()}, or {@code null} where the platform has none. */
    private static final Method PLATFORM_COMPONENTS = platformMethod("getRecordComponents");

    private Records() {
    }

    /**
     * Whether a class is a record (census: StrictBinder.java:176): listed in the table, or —
     * for a class the table does not know — a record to the platform.
     *
     * @param type the class
     * @return {@code true} for a record
     */
    public static boolean isRecord(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return table().containsKey(type.getName()) || platformIsRecord(type);
    }

    /**
     * The components of a record in declaration order (census: StrictBinder.java:297), which is
     * the parameter order of its canonical constructor.
     *
     * @param type the record class
     * @return the components, a fresh array on every call
     * @throws IllegalArgumentException when the class is neither in the table nor a record to
     *     the platform
     * @throws IllegalStateException when a component the table lists has no field of that name
     *     in the class (the table and the classes come from different builds)
     */
    public static RecordComponent[] components(Class<?> type) {
        Objects.requireNonNull(type, "type");
        RecordComponent[] cached = COMPONENTS.get(type);
        if (cached == null) {
            cached = resolve(type);
            RecordComponent[] raced = COMPONENTS.putIfAbsent(type, cached);
            if (raced != null) {
                cached = raced;
            }
        }
        return cached.clone();
    }

    /**
     * Wraps the class's declared fields of the given names, in that order; package-private so a
     * test can exercise the missing-field contract without a doctored table.
     *
     * @param type the record class
     * @param names the component names in declaration order
     * @return the components
     * @throws IllegalStateException when a name has no declared field
     */
    static RecordComponent[] fromNames(Class<?> type, List<String> names) {
        RecordComponent[] components = new RecordComponent[names.size()];
        for (int i = 0; i < components.length; i++) {
            String name = names.get(i);
            try {
                components[i] = new RecordComponent(type.getDeclaredField(name));
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("jrecord: the record table lists component '"
                        + name + "' of " + type.getName() + " but the class declares no such "
                        + "field (the table and the classes come from different builds)", e);
            }
        }
        return components;
    }

    private static RecordComponent[] resolve(Class<?> type) {
        List<String> names = table().get(type.getName());
        if (names != null) {
            return fromNames(type, names);
        }
        if (!platformIsRecord(type)) {
            throw new IllegalArgumentException("jrecord: " + type.getName() + " is not a record "
                    + "(absent from " + TABLE_RESOURCE + ", and not a record to the platform)");
        }
        Object[] platform = (Object[]) invoke(PLATFORM_COMPONENTS, type);
        List<String> platformNames = new ArrayList<>(platform.length);
        for (Object component : platform) {
            // java.lang.reflect.RecordComponent.getName(), without naming the type: it does not
            // exist on Android 13, and the fallback must stay loadable there.
            try {
                platformNames.add((String) invoke(
                        component.getClass().getMethod("getName"), component));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("jrecord: the platform's record component "
                        + component.getClass().getName() + " has no getName()", e);
            }
        }
        return fromNames(type, platformNames);
    }

    private static boolean platformIsRecord(Class<?> type) {
        if (PLATFORM_IS_RECORD == null || PLATFORM_COMPONENTS == null) {
            return false;
        }
        Boolean known = PLATFORM_RECORDS.get(type);
        if (known == null) {
            known = Boolean.TRUE.equals(invoke(PLATFORM_IS_RECORD, type));
            PLATFORM_RECORDS.put(type, known);
        }
        return known;
    }

    private static Object invoke(Method method, Object target) {
        try {
            return method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("jrecord: cannot call " + method, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("jrecord: " + method + " failed", cause);
        }
    }

    private static Method platformMethod(String name) {
        try {
            return Class.class.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Map<String, List<String>> table() {
        Map<String, List<String>> loaded = table;
        if (loaded == null) {
            synchronized (TABLE_LOCK) {
                loaded = table;
                if (loaded == null) {
                    loaded = load();
                    table = loaded;
                }
            }
        }
        return loaded;
    }

    private static Map<String, List<String>> load() {
        Properties properties = new Properties();
        try (InputStream in = Records.class.getResourceAsStream(TABLE_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("jrecord: the record table " + TABLE_RESOURCE
                        + " is missing from the classpath (the Android build's recordMetadata "
                        + "task writes it into build/transformed/resources)");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException e) {
            throw new IllegalStateException("jrecord: cannot read " + TABLE_RESOURCE, e);
        }
        Map<String, List<String>> parsed = new HashMap<>(properties.size() * 2);
        for (String name : properties.stringPropertyNames()) {
            String value = properties.getProperty(name).trim();
            List<String> names = value.isEmpty()
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(Arrays.asList(value.split(",")));
            parsed.put(name, names);
        }
        return Collections.unmodifiableMap(parsed);
    }
}
