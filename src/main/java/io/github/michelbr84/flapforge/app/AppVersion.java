package io.github.michelbr84.flapforge.app;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The application version, read once from the {@code version.properties} the build writes onto
 * the classpath (M10, D8).
 *
 * <p>It used to live in {@code Flapforge}, the desktop entry point. That class is excluded from
 * the Android source transform — it sets {@code java.awt.headless} and builds the desktop host —
 * so the two readers that are transformed, {@link GameApplication} (the window title and the
 * save stamp) and the main menu's footer, take the version from here instead;
 * {@code Flapforge.version()} delegates to keep its callers working.
 */
public final class AppVersion {

    private static final String VERSION_RESOURCE = "/version.properties";
    private static volatile String version;

    private AppVersion() {
    }

    /**
     * Application version from {@code version.properties}.
     *
     * @return the version string, or {@code "unknown"} when the resource is missing
     */
    public static String version() {
        String v = version;
        if (v == null) {
            v = loadVersion();
            version = v;
        }
        return v;
    }

    private static String loadVersion() {
        try (InputStream in = AppVersion.class.getResourceAsStream(VERSION_RESOURCE)) {
            if (in == null) {
                return "unknown";
            }
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("version", "unknown");
        } catch (IOException e) {
            return "unknown";
        }
    }
}
