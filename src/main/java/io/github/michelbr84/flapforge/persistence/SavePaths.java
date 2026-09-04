package io.github.michelbr84.flapforge.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Where the game keeps the player's files (D15): Linux {@code ~/.flapforge}, Windows
 * {@code %APPDATA%/Flapforge}, macOS {@code ~/Library/Application Support/Flapforge}.
 *
 * <p>The location is overridable, in this order of precedence: {@link #override(Path)} (tests and
 * tools), the system property {@code flapforge.home}, the environment variable
 * {@code FLAPFORGE_HOME}, then the per-OS default. Nothing here creates a directory on its own —
 * {@link #ensureProfileDir()} does it lazily, and {@link AtomicFiles} creates the parent of a file
 * it is asked to write. The default never points inside a source tree: only an explicit override
 * can do that.
 */
public final class SavePaths {

    /** System property that overrides the profile directory. */
    public static final String HOME_PROPERTY = "flapforge.home";
    /** Environment variable that overrides the profile directory. */
    public static final String HOME_ENV = "FLAPFORGE_HOME";
    /** File name of the player save. */
    public static final String SAVE_FILE = "save.json";
    /** File name of the once-per-session save backup. */
    public static final String SAVE_BACKUP_FILE = "save.json.bak";
    /** File name of the settings store. */
    public static final String SETTINGS_FILE = "settings.json";
    /** Directory holding pre-migration backups. */
    public static final String BACKUP_DIR = "backups";
    /** Directory name used on Windows and macOS. */
    public static final String APP_DIR_NAME = "Flapforge";
    /** Directory name used on Linux and on unknown platforms. */
    public static final String DOT_DIR_NAME = ".flapforge";

    private static volatile Path override;

    private SavePaths() {
    }

    /**
     * Points every path at a directory of the caller's choosing (tests, tools, {@code --home}).
     *
     * @param dir the directory, or {@code null} to fall back to the property/env/OS default
     */
    public static void override(Path dir) {
        override = dir == null ? null : dir.toAbsolutePath().normalize();
    }

    /**
     * Drops a previous {@link #override(Path)}.
     */
    public static void clearOverride() {
        override = null;
    }

    /**
     * The active override, if any.
     *
     * @return the overridden directory or {@code null}
     */
    public static Path overrideDir() {
        return override;
    }

    /**
     * The profile directory. The directory is <em>not</em> created.
     *
     * @return the absolute, normalised directory
     */
    public static Path profileDir() {
        Path chosen = override;
        if (chosen != null) {
            return chosen;
        }
        Path fromProperty = pathOf(System.getProperty(HOME_PROPERTY));
        if (fromProperty != null) {
            return fromProperty;
        }
        Path fromEnv = pathOf(System.getenv(HOME_ENV));
        if (fromEnv != null) {
            return fromEnv;
        }
        return defaultDir(System.getProperty("os.name", ""), System.getProperty("user.home", "."),
                System.getenv("APPDATA"));
    }

    /**
     * The profile directory, created if it does not exist yet.
     *
     * @return the directory
     * @throws IOException when the directory cannot be created
     */
    public static Path ensureProfileDir() throws IOException {
        Path dir = profileDir();
        Files.createDirectories(dir);
        return dir;
    }

    /**
     * Path of the player save.
     *
     * @return {@code &lt;profileDir&gt;/save.json}
     */
    public static Path saveFile() {
        return profileDir().resolve(SAVE_FILE);
    }

    /**
     * Path of the save backup.
     *
     * @return {@code &lt;profileDir&gt;/save.json.bak}
     */
    public static Path saveBackupFile() {
        return profileDir().resolve(SAVE_BACKUP_FILE);
    }

    /**
     * Path of the settings file.
     *
     * @return {@code &lt;profileDir&gt;/settings.json}
     */
    public static Path settingsFile() {
        return profileDir().resolve(SETTINGS_FILE);
    }

    /**
     * Path the settings file of an older schema is renamed to when the version does not match.
     *
     * @param version the version found in the old file
     * @return {@code &lt;profileDir&gt;/settings.v&lt;N&gt;.json}
     */
    public static Path settingsArchiveFile(int version) {
        return profileDir().resolve("settings.v" + version + ".json");
    }

    /**
     * Directory holding pre-migration save backups.
     *
     * @return {@code &lt;profileDir&gt;/backups}
     */
    public static Path backupsDir() {
        return profileDir().resolve(BACKUP_DIR);
    }

    /**
     * Path an unreadable save is quarantined to.
     *
     * @param epochMillis the timestamp of the quarantine, from an injected time source
     * @return {@code &lt;profileDir&gt;/save.corrupt-&lt;epochMillis&gt;.json}
     */
    public static Path corruptSaveFile(long epochMillis) {
        return profileDir().resolve("save.corrupt-" + epochMillis + ".json");
    }

    /**
     * The per-OS default directory, exposed for tests of the three branches.
     *
     * @param osName value of {@code os.name}
     * @param userHome value of {@code user.home}
     * @param appData value of {@code %APPDATA%}, may be {@code null}
     * @return the directory
     */
    static Path defaultDir(String osName, String userHome, String appData) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        Path home = Paths.get(userHome == null || userHome.isEmpty() ? "." : userHome);
        if (os.contains("win")) {
            Path base = pathOf(appData);
            Path roaming = base != null ? base : home.resolve("AppData").resolve("Roaming");
            return roaming.resolve(APP_DIR_NAME).toAbsolutePath().normalize();
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return home.resolve("Library").resolve("Application Support").resolve(APP_DIR_NAME)
                    .toAbsolutePath().normalize();
        }
        return home.resolve(DOT_DIR_NAME).toAbsolutePath().normalize();
    }

    private static Path pathOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Paths.get(raw.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
