package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The saved-file fixtures under {@code src/test/resources/fixtures} and the guard that keeps the
 * save tests off the real profile directory.
 */
final class SaveFixtures {

    /** Classpath directory of the fixtures. */
    static final String DIR = "/fixtures/";

    private SaveFixtures() {
    }

    /**
     * Reads a fixture.
     *
     * @param name the file name under {@code fixtures/}, for example {@code save_v1.json}
     * @return its text
     */
    static String read(String name) {
        try (InputStream in = SaveFixtures.class.getResourceAsStream(DIR + name)) {
            assertNotNull(in, "missing fixture " + DIR + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Writes a fixture into a directory.
     *
     * @param name the fixture name
     * @param target the file to write
     */
    static void copyTo(String name, Path target) {
        try {
            Files.createDirectories(target.toAbsolutePath().getParent());
            Files.writeString(target, read(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Points {@link SavePaths} at a temporary directory and proves that it took: a save test that
     * silently fell back to {@code ~/.flapforge} would rewrite the developer's own profile.
     *
     * @param home the temporary directory
     */
    static void useTemporaryHome(Path home) {
        SavePaths.override(home);
        assertTrue(SavePaths.profileDir().equals(home.toAbsolutePath().normalize()),
                "the profile directory must be the temporary one");
        assertTrue(SavePaths.saveFile().startsWith(home),
                "the save file must live under the temporary directory");
    }
}
