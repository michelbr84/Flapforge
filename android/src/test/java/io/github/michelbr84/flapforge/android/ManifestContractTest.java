package io.github.michelbr84.flapforge.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Pins the manifest contract no unit test can observe on a device here (M10): the activity is
 * portrait-only and carries the Android 16 opt-out that keeps it so on large screens, it
 * tolerates the configuration changes a rotation would still bring, and the target SDK is one
 * the opt-out is honoured for. Read from {@code android/src/main/AndroidManifest.xml} and
 * {@code android/build.gradle} in the source tree, found from the working directory upwards
 * the way {@code GoldenRenderTest} finds the golden generator.
 */
public class ManifestContractTest {

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    private static final String MANIFEST = "android/src/main/AndroidManifest.xml";
    private static final String BUILD_FILE = "android/build.gradle";
    /**
     * Android 16 ignores {@code screenOrientation}, resizability and aspect-ratio restrictions
     * on displays of {@code sw600dp} and more for apps targeting API 36 unless this property,
     * declared at application or activity level, opts out.
     */
    static final String RESIZABILITY_OPT_OUT =
            "android.window.PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY";
    /** The last target SDK the opt-out is honoured for: it is ignored from API 37 on. */
    static final int LAST_TARGET_SDK_WITH_OPT_OUT = 36;

    @Test
    public void theActivityIsPortraitOnlyOnLargeScreensToo() throws Exception {
        Document manifest = parse(repoRoot().resolve(MANIFEST));
        Element activity = single(manifest, "activity");
        assertEquals(".android.MainActivity", activity.getAttributeNS(ANDROID_NS, "name"));
        assertEquals("portrait", activity.getAttributeNS(ANDROID_NS, "screenOrientation"));

        // A rotation the platform forces anyway must not recreate the activity: it becomes a
        // surfaceChanged, which the bridge queues as Resized and the viewport letterboxes.
        List<String> configChanges = List.of(
                activity.getAttributeNS(ANDROID_NS, "configChanges").split("\\|"));
        for (String change : List.of("orientation", "screenSize", "screenLayout",
                "smallestScreenSize")) {
            assertTrue(change + " is a handled configuration change: " + configChanges,
                    configChanges.contains(change));
        }

        Element optOut = property(manifest, RESIZABILITY_OPT_OUT);
        assertNotNull("the Android 16 large-screen opt-out is declared", optOut);
        assertEquals("true", optOut.getAttributeNS(ANDROID_NS, "value"));
    }

    @Test
    public void theTargetSdkIsOneTheOptOutIsHonouredFor() throws IOException {
        String gradle = new String(Files.readAllBytes(repoRoot().resolve(BUILD_FILE)),
                StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("targetSdk\\s*=\\s*(\\d+)").matcher(gradle);
        assertTrue("targetSdk in " + BUILD_FILE, matcher.find());
        int targetSdk = Integer.parseInt(matcher.group(1));
        assertTrue("targetSdk " + targetSdk + ": " + RESIZABILITY_OPT_OUT + " is ignored from "
                + "API " + (LAST_TARGET_SDK_WITH_OPT_OUT + 1) + " on, so the portrait-only "
                + "contract (README: the game is always portrait) needs another answer for "
                + "large screens before the target is raised",
                targetSdk <= LAST_TARGET_SDK_WITH_OPT_OUT);
    }

    private static Document parse(Path file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(file.toFile());
    }

    private static Element single(Document document, String tag) {
        NodeList list = document.getElementsByTagName(tag);
        assertEquals("exactly one <" + tag + ">", 1, list.getLength());
        return (Element) list.item(0);
    }

    /** The {@code <property>} of that name at any level, or {@code null}. */
    private static Element property(Document document, String name) {
        NodeList list = document.getElementsByTagName("property");
        for (int i = 0; i < list.getLength(); i++) {
            Element element = (Element) list.item(i);
            if (name.equals(element.getAttributeNS(ANDROID_NS, "name"))) {
                return element;
            }
        }
        return null;
    }

    private static Path repoRoot() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(MANIFEST))) {
                return p;
            }
        }
        throw new IllegalStateException(MANIFEST + " not found at or above " + dir);
    }
}
