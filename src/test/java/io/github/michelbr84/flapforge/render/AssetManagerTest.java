package io.github.michelbr84.flapforge.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The asset door (D18): the shipped manifest carries exactly the bundled OFL font (M8) and
 * nothing else, so every renderer still falls back to procedural art; a manifest that points at
 * something that is not there produces an <em>error</em>, never an exception; and ids resolve
 * through the manifest only — nothing scans a directory.
 */
class AssetManagerTest {

    private static final String EMPTY = "{\"version\": 1, \"assets\": []}";

    @Test
    void theShippedManifestCarriesOnlyTheFontAndParsesWithoutErrors() {
        AssetManager assets = AssetManager.fromClasspath();

        assertTrue(assets.errors().isEmpty(), () -> String.join("\n", assets.errors()));
        assertEquals(1, assets.entries().size(),
                "the shipped manifest ships only the UI font: " + assets.entries().keySet());
        AssetManager.Entry entry = assets.entries().get("font/ui");
        assertNotNull(entry, "font/ui is declared");
        assertEquals(AssetManager.Kind.FONT, entry.kind());
        assertTrue(entry.path().endsWith(".ttf"), entry.path());
        assertTrue(entry.license().contains("OFL"), entry.license());
        // Every id the renderers ask for besides the font stays a procedural miss, not an error.
        assertEquals(Optional.empty(), assets.sprite("bird/classic"));
        assertFalse(assets.has("bird/classic"));
        assertTrue(assets.errors().isEmpty(), "a miss is not an error");
    }

    @Test
    void theShippedFontDecodesIntoAUsableFace() {
        AssetManager assets = AssetManager.fromClasspath();

        Optional<Font> font = assets.font("font/ui");
        assertTrue(font.isPresent(), () -> String.join("\n", assets.errors()));
        Font face = font.get();
        assertEquals(1, face.getSize(), "a createFont face starts at size 1");
        assertTrue(face.canDisplay('ç'), "the font must carry the Portuguese cedilla");
        assertTrue(face.canDisplay('ã'), "the font must carry the Portuguese tilde a");
        Font bold = face.deriveFont(Font.BOLD, 15f);
        assertEquals(Font.BOLD, bold.getStyle(), "bold is a derived style on one face");
        assertEquals(15, bold.getSize());
        assertTrue(assets.errors().isEmpty(), () -> String.join("\n", assets.errors()));
    }

    @Test
    void anEmptyManifestSendsEveryRendererDownTheProceduralPath() {
        AssetResolver resolver = new AssetResolver(AssetManager.fromJson(EMPTY));

        assertEquals(Optional.empty(), resolver.sprite("bird", "green_fields"));
        assertEquals(Optional.empty(), resolver.sheet("bird", "green_fields"));
        assertTrue(resolver.assets().errors().isEmpty(), "a miss is not an error");

        BirdRenderer bird = new BirdRenderer();
        bird.setSheet(resolver.sheet("bird", "green_fields").orElse(null));
        assertEquals(null, bird.sheet(), "with no sheet the bird is drawn procedurally");
    }

    @Test
    void anEntryPointingAtAMissingResourceIsAnErrorNotACrash() {
        AssetManager assets = AssetManager.fromJson("{\"version\": 1, \"assets\": ["
                + "{\"id\": \"bird\", \"path\": \"sprites/birds/nope.png\", \"kind\": \"SPRITE\","
                + " \"license\": \"CC0-1.0\", \"source\": \"test\"}]}");

        assertTrue(assets.has("bird"), "the entry is declared");
        assertEquals(Optional.empty(), assets.sprite("bird"));
        assertEquals(1, assets.errors().size(), () -> String.join("\n", assets.errors()));
        assertTrue(assets.errors().get(0).contains("missing resource"), assets.errors().get(0));

        assertEquals(Optional.empty(), assets.sprite("bird"));
        assertEquals(1, assets.errors().size(), "the miss is cached, the error is recorded once");
    }

    @Test
    void aMalformedManifestIsReportedAndLeavesAnEmptyRegistry() {
        AssetManager broken = AssetManager.fromJson("{ this is not json ]");
        assertTrue(broken.entries().isEmpty());
        assertEquals(1, broken.errors().size());

        AssetManager noList = AssetManager.fromJson("{\"version\": 1}");
        assertTrue(noList.entries().isEmpty());
        assertTrue(noList.errors().get(0).contains("/assets"), noList.errors().get(0));

        AssetManager badEntries = AssetManager.fromJson("{\"assets\": ["
                + "{\"path\": \"a.png\"},"
                + "{\"id\": \"b\"},"
                + "{\"id\": \"c\", \"path\": \"c.png\", \"kind\": \"HOLOGRAM\"},"
                + "{\"id\": \"d\", \"path\": \"d.png\"},"
                + "{\"id\": \"d\", \"path\": \"d2.png\"}]}");
        assertEquals(1, badEntries.entries().size(), "only the first valid d survives");
        assertEquals(4, badEntries.errors().size(),
                () -> String.join("\n", badEntries.errors()));
        assertEquals("d.png", badEntries.entries().get("d").path());
    }

    @Test
    void aWorldOverrideIsTriedBeforeThePlainId() {
        AssetManager assets = AssetManager.fromJson("{\"assets\": ["
                + "{\"id\": \"worlds/storm_sky/pipe\", \"path\": \"a.png\"},"
                + "{\"id\": \"pipe\", \"path\": \"b.png\"}]}");
        AssetResolver resolver = new AssetResolver(assets);

        assertEquals(Optional.empty(), resolver.sprite("pipe", "storm_sky"));
        List<String> errors = assets.errors();
        assertEquals(2, errors.size(), () -> String.join("\n", errors));
        assertTrue(errors.get(0).contains("worlds/storm_sky/pipe"), errors.get(0));
        assertTrue(errors.get(1).contains("(pipe)"), errors.get(1));
        assertEquals("worlds/storm_sky/pipe", AssetResolver.worldId("pipe", "storm_sky"));
        assertEquals("pipe", AssetResolver.worldId("pipe", null));
    }

    @Test
    void aDeclaredImageLoadsCachesAndCutsIntoFrames() {
        AssetManager assets = AssetManager.fromJson("{\"version\": 1, \"assets\": ["
                + "{\"id\": \"test/sheet\", \"path\": \"sprites/test_sheet.png\","
                + " \"kind\": \"SHEET\", \"frameWidth\": 16, \"frameHeight\": 16,"
                + " \"license\": \"CC0-1.0\", \"source\": \"generated for the tests\"}]}");

        Optional<Sprite> sprite = assets.sprite("test/sheet");
        assertTrue(sprite.isPresent(), () -> String.join("\n", assets.errors()));
        assertEquals(32, sprite.get().width());
        assertEquals(16, sprite.get().height());
        assertTrue(assets.errors().isEmpty(), () -> String.join("\n", assets.errors()));
        assertTrue(sprite.get() == assets.sprite("test/sheet").orElseThrow(),
                "sprites are cached by id");

        SpriteSheet sheet = assets.sheet("test/sheet").orElseThrow();
        assertEquals(2, sheet.frameCount());
        assertEquals(16, sheet.frameWidth());
        assertNotNull(sheet.frame(0));
        assertTrue(sheet.frame(0) == sheet.frame(0), "frames are cut once");
        assertTrue(sheet.frame(2) == sheet.frame(0), "the index wraps");

        assets.clearCache();
        assertTrue(assets.sprite("test/sheet").isPresent(), "a cleared cache reloads");
    }

    @Test
    void aFontRequestForANonFontEntryIsAnErrorNotACrash() {
        AssetManager assets = AssetManager.fromJson("{\"version\": 1, \"assets\": ["
                + "{\"id\": \"not/a/font\", \"path\": \"sprites/test_sheet.png\","
                + " \"kind\": \"SPRITE\", \"license\": \"CC0-1.0\", \"source\": \"test\"}]}");

        assertEquals(Optional.empty(), assets.font("not/a/font"));
        assertEquals(Optional.empty(), assets.font("undeclared"));
        assertEquals(1, assets.errors().size(), () -> String.join("\n", assets.errors()));
        assertTrue(assets.errors().get(0).contains("not a font"), assets.errors().get(0));
        assertEquals(Optional.empty(), assets.font("not/a/font"),
                "the failure is cached, the error is recorded once");
    }

    @Test
    void anAnimationTimesSheetFramesInTicks() {
        Animation animation = new Animation(8, 20);
        assertEquals(0, animation.frame());
        for (int i = 0; i < 20; i++) {
            animation.tick();
        }
        assertEquals(1, animation.frame());
        for (int i = 0; i < 20 * 7; i++) {
            animation.tick();
        }
        assertEquals(0, animation.frame(), "a looping animation wraps");
        assertFalse(animation.isFinished());

        Animation once = new Animation(3, 2, false);
        for (int i = 0; i < 50; i++) {
            once.tick();
        }
        assertEquals(2, once.frame());
        assertTrue(once.isFinished());
    }
}
