package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.github.michelbr84.flapforge.app.AppVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * The version reader after its move out of the desktop entry point (M10, D8): the window title,
 * the save stamp and the menu footer all read {@link AppVersion#version()}, so it must say what
 * {@code version.properties} says, and {@link Flapforge#version()} — which the desktop keeps
 * for its callers — must say the same thing.
 */
class AppVersionTest {

    @Test
    void versionIsTheOneInVersionProperties() throws IOException {
        Properties props = new Properties();
        try (InputStream in = AppVersionTest.class.getResourceAsStream("/version.properties")) {
            assertNotNull(in, "version.properties is on the test classpath");
            props.load(in);
        }
        String expected = props.getProperty("version");
        assertNotNull(expected, "version.properties names a version");
        assertNotEquals("unknown", expected);
        assertEquals(expected, AppVersion.version());
    }

    @Test
    void flapforgeDelegatesToAppVersion() {
        assertEquals(AppVersion.version(), Flapforge.version());
        assertSame(AppVersion.version(), AppVersion.version(), "read once, then cached");
    }
}
