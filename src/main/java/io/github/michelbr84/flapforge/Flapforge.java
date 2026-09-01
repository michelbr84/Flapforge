package io.github.michelbr84.flapforge;

import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.LaunchOptions;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Entry point. Parses {@link LaunchOptions} before any windowing class loads so headless runs
 * can switch the JVM to headless mode first (E10), then starts the {@link GameApplication}.
 * A usage error prints the message and the usage text to stderr and returns without starting
 * the game; {@code System.exit} is reserved for the shutdown watchdog (D4).
 */
public final class Flapforge {

    private static final String VERSION_RESOURCE = "/version.properties";
    private static volatile String version;

    private Flapforge() {
    }

    /**
     * Program entry.
     *
     * @param args command-line arguments (see {@link LaunchOptions#usage()})
     */
    public static void main(String[] args) {
        LaunchOptions options;
        try {
            options = LaunchOptions.parse(args);
        } catch (LaunchOptions.UsageException e) {
            System.err.println(e.getMessage());
            System.err.println(LaunchOptions.usage());
            return;
        }
        if (options.help()) {
            System.out.println("Flapforge " + version());
            System.out.println(LaunchOptions.usage());
            return;
        }
        if (options.headless()) {
            System.setProperty("java.awt.headless", "true");
        }
        GameApplication.start(options);
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
        try (InputStream in = Flapforge.class.getResourceAsStream(VERSION_RESOURCE)) {
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
