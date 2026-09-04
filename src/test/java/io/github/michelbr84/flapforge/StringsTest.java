package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.content.ContentException;
import io.github.michelbr84.flapforge.content.StringKey;
import io.github.michelbr84.flapforge.content.Strings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The string table, its per-key fallback and its plain {@code {0}} substitution (D25, E30.h). */
class StringsTest {

    private static final String BEST_HINT = StringKey.GAMEOVER_BEST_HINT.key();

    @Test
    void everyStringKeyResolvesInEveryShippedLanguage() {
        for (String language : Strings.LANGUAGES) {
            Strings strings = Strings.load(language);
            for (StringKey key : StringKey.values()) {
                assertTrue(strings.has(key.key()),
                        "missing " + key.key() + " in " + language + ".json");
                assertFalse(strings.get(key).isBlank(), key + " is blank in " + language);
            }
            assertTrue(strings.missingKeys().isEmpty(), "no key fell through");
        }
    }

    @Test
    void theTranslationIsARealOneAndNotACopy() {
        Strings en = Strings.load("en");
        Strings pt = Strings.load("pt_BR");

        assertEquals("pt_BR", pt.language());
        assertNotEquals(en.get(StringKey.MENU_PLAY), pt.get(StringKey.MENU_PLAY));
        assertEquals("Jogar", pt.get(StringKey.MENU_PLAY));
        assertTrue(pt.get(StringKey.SETTINGS_TITLE).contains("õ"),
                "accents must survive the UTF-8 read: " + pt.get(StringKey.SETTINGS_TITLE));
    }

    @Test
    void aKeyMissingFromTheTranslationFallsBackToEnglish() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("menu.play", "Play");
        source.put("menu.quit", "Quit");
        Strings partial = Strings.of("pt_BR", source, Map.of("menu.play", "Jogar"));

        assertEquals("Jogar", partial.text("menu.play"));
        assertEquals("Quit", partial.text("menu.quit"), "the untranslated key falls back");
        assertTrue(partial.missingKeys().isEmpty());
    }

    @Test
    void substitutionKeepsApostrophesAndDoesNotGroupNumbers() {
        Strings en = Strings.load("en");

        String text = en.format(StringKey.GAMEOVER_BEST_HINT, 1234);

        assertTrue(en.get(StringKey.GAMEOVER_BEST_HINT).contains("'"),
                "the fixture must contain an apostrophe to be worth testing");
        assertTrue(text.contains("That's"), text);
        assertTrue(text.contains("1234"), text);
        assertFalse(text.contains("1,234"), "MessageFormat grouping must not appear: " + text);
        assertFalse(text.contains("{0}"), text);
    }

    @Test
    void substitutionLeavesEverythingElseAlone() {
        assertEquals("a {0} b", Strings.substitute("a {0} b"));
        assertEquals("100%", Strings.substitute("{0}%", 100));
        assertEquals("{9} kept", Strings.substitute("{9} kept", "x"),
                "an index without an argument stays literal");
        assertEquals("{a} kept", Strings.substitute("{a} kept", "x"));
        assertEquals("unclosed {0", Strings.substitute("unclosed {0", "x"));
        assertEquals("1 then 0", Strings.substitute("{1} then {0}", "0", "1"));
        assertEquals("it's 5", Strings.substitute("it's {0}", 5));
    }

    @Test
    void anUnknownKeyReturnsItselfAndIsRecorded() {
        Strings en = Strings.load("en");

        assertEquals("menu.nonexistent", en.text("menu.nonexistent"));
        assertEquals("bird.ghost.name", en.name("bird", "ghost"));
        assertEquals("bird.ghost.desc", en.desc("bird", "ghost"));

        assertEquals(List.of("menu.nonexistent", "bird.ghost.name", "bird.ghost.desc"),
                List.copyOf(en.missingKeys()));
        en.clearMissing();
        assertTrue(en.missingKeys().isEmpty());
    }

    @Test
    void contentTextResolvesByKindAndId() {
        Strings en = Strings.load("en");

        assertEquals("Forgewing", en.name("bird", "classic"));
        assertEquals("Ember", en.name("cosmetic", "classic.ember"));
        assertEquals("Green Fields", en.name("world", "green_fields"));
        assertEquals("Nightmare", en.name("tier", "nightmare"));
        assertFalse(en.desc("bird", "classic").isBlank());
        assertEquals("bird.classic.name", Strings.nameKey("bird", "classic"));
        assertEquals("bird.classic.desc", Strings.descKey("bird", "classic"));
    }

    @Test
    void reloadSwapsTheActiveTable() {
        Strings strings = Strings.load("en");
        assertEquals("Play", strings.get(StringKey.MENU_PLAY));

        strings.reload("pt_BR");

        assertEquals("pt_BR", strings.language());
        assertEquals("Jogar", strings.get(StringKey.MENU_PLAY));
        strings.reload("en");
        assertEquals("Play", strings.get(StringKey.MENU_PLAY));
    }

    @Test
    void anUnknownLanguageStaysEnglish() {
        Strings strings = Strings.load("kl");

        assertFalse(Strings.exists("kl"));
        assertEquals("Play", strings.get(StringKey.MENU_PLAY));
    }

    @Test
    void aMissingFileIsReportedWithItsName() {
        ContentException e = assertThrows(ContentException.class, () -> Strings.tableOf("nope"));
        assertEquals(1, e.errors().size());
        assertTrue(e.errors().get(0).startsWith("strings/nope.json#: missing resource"),
                e.errors().get(0));
    }

    @Test
    void theShippedFilesAreFlatStringTables() {
        for (String language : Strings.LANGUAGES) {
            Map<String, String> table = Strings.tableOf(language);
            assertTrue(table.size() >= StringKey.values().length, language);
            assertTrue(table.containsKey(BEST_HINT), language);
        }
    }

    @Test
    void everyShippedFileCarriesExactlyTheSameKeys() {
        // Strings.load() merges over en.json, so a key dropped from pt_BR still resolves through
        // the fallback and no other assertion in this class would notice it went missing.
        Set<String> source = Strings.tableOf(Strings.SOURCE_LANGUAGE).keySet();
        for (String language : Strings.LANGUAGES) {
            assertEquals(source, Strings.tableOf(language).keySet(),
                    "strings/" + language + ".json must carry exactly the keys en.json does");
        }
    }
}
