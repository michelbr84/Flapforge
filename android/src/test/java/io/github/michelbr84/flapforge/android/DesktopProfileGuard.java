package io.github.michelbr84.flapforge.android;

import io.github.michelbr84.flapforge.persistence.SavePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The guard for the developer's real profile (M10): every activity test fingerprints the
 * desktop's {@code ~/.flapforge} before the activity is created and again after the game has
 * shut down, and the two must be identical. The boots are the real ones — content, settings,
 * profile, fonts, the audio warm-up over Robolectric's {@code ShadowAudioTrack} — so a
 * regression that let the Android path read or write the desktop directory shows up here
 * rather than on a device.
 */
final class DesktopProfileGuard {

    /** The desktop's Linux profile directory, which no Android test must ever touch. */
    static final Path DESKTOP_PROFILE_DIR =
            Path.of(System.getProperty("user.home", ".")).resolve(SavePaths.DOT_DIR_NAME)
                    .toAbsolutePath().normalize();
    private static final List<String> GUARDED_FILES = List.of(SavePaths.SAVE_FILE,
            SavePaths.SAVE_BACKUP_FILE, SavePaths.SETTINGS_FILE);

    private DesktopProfileGuard() {
    }

    /**
     * MD5 of each guarded file under {@code ~/.flapforge}, or {@code "absent"}; an empty map
     * when the directory does not exist (CI).
     */
    static Map<String, String> fingerprint() throws IOException {
        Map<String, String> fingerprint = new LinkedHashMap<>();
        if (!Files.isDirectory(DESKTOP_PROFILE_DIR)) {
            return fingerprint;
        }
        for (String name : GUARDED_FILES) {
            Path file = DESKTOP_PROFILE_DIR.resolve(name);
            fingerprint.put(name, Files.isRegularFile(file) ? md5(file) : "absent");
        }
        return fingerprint;
    }

    private static String md5(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
