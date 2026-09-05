package io.github.michelbr84.flapforge;

import io.github.michelbr84.flapforge.app.AppVersion;
import io.github.michelbr84.flapforge.app.AwtHost;
import io.github.michelbr84.flapforge.app.GameApplication;
import io.github.michelbr84.flapforge.app.LaunchOptions;

/**
 * Desktop entry point. Parses {@link LaunchOptions} before any windowing class loads so headless
 * runs can switch the JVM to headless mode first (E10), then starts the {@link GameApplication}
 * on the {@link AwtHost}. A usage error prints the message and the usage text to stderr and
 * returns without starting the game; {@code System.exit} is reserved for the shutdown watchdog
 * (D4).
 *
 * <p>This is a desktop-only file (M10, D8): the Android port has its own entry point and its own
 * host, so nothing that the transformed game needs may live here — the version moved to
 * {@link AppVersion} for that reason.
 */
public final class Flapforge {

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
        GameApplication.start(options, new AwtHost());
    }

    /**
     * Application version from {@code version.properties}, as {@link AppVersion#version()}
     * reads it.
     *
     * @return the version string, or {@code "unknown"} when the resource is missing
     */
    public static String version() {
        return AppVersion.version();
    }
}
