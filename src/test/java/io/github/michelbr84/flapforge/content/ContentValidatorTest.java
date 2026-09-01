package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.support.TestContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The minimal M1 rules of {@link ContentValidator} (E19), driven from mutated fixtures. */
class ContentValidatorTest {

    private static Map<String, JsonElement> mutate(String file, Consumer<JsonElement> edit) {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.frozenJson());
        edit.accept(files.get(file));
        return files;
    }

    private static List<String> errorsOf(Map<String, JsonElement> files) {
        ContentException e = assertThrows(ContentException.class, () -> GameContent.fromJson(files));
        return e.errors();
    }

    private static JsonObject firstBird(JsonElement birds) {
        return birds.getAsJsonArray().get(0).getAsJsonObject();
    }

    @Test
    void theFrozenFixtureIsValid() {
        assertEquals(List.of(), ContentValidator.errorsOf(TestContent.frozen()));
    }

    @Test
    void duplicateBirdIdIsRejected() {
        List<String> errors = errorsOf(mutate("birds", birds -> {
            JsonArray array = birds.getAsJsonArray();
            array.add(array.get(0).deepCopy());
        }));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("birds.json#/1/id: duplicate bird id 'classic'", errors.get(0));
    }

    @Test
    void duplicateTierIdIsRejected() {
        List<String> errors = errorsOf(mutate("difficulty", difficulty -> {
            JsonArray tiers = difficulty.getAsJsonObject().getAsJsonArray("tiers");
            JsonObject clone = tiers.get(1).getAsJsonObject().deepCopy();
            clone.addProperty("id", "normal");
            tiers.add(clone);
        }));
        assertEquals(1, errors.size(), errors.toString());
        assertEquals("difficulty.json#/tiers/3/id: duplicate tier id 'normal'", errors.get(0));
    }

    @Test
    void duplicatePaletteIdIsRejected() {
        List<String> errors = errorsOf(mutate("birds", birds -> {
            JsonArray palettes = firstBird(birds).getAsJsonArray("palettes");
            palettes.add(palettes.get(0).deepCopy());
        }));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("birds.json#/0/palettes/4/id: duplicate palette id"),
                errors.get(0));
    }

    @Test
    void idsMustMatchTheLowerSnakeCaseRegex() {
        List<String> errors = errorsOf(mutate("birds",
                birds -> firstBird(birds).addProperty("id", "Classic-1")));
        assertTrue(errors.size() >= 1, errors.toString());
        assertEquals("birds.json#/0/id: bird id 'Classic-1' does not match ^[a-z][a-z0-9_]*$",
                errors.get(0));
    }

    @Test
    void curveIdsMustMatchTheRegexToo() {
        List<String> errors = errorsOf(mutate("difficulty", difficulty -> {
            JsonObject curves = difficulty.getAsJsonObject().getAsJsonObject("curves");
            curves.add("Classic Extra", new JsonArray());
        }));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith(
                "difficulty.json#/curves/Classic Extra: curve id 'Classic Extra' does not match"),
                errors.get(0));
    }

    @Test
    void exactlyOneTierMustBeTheDefault() {
        List<String> noDefault = errorsOf(mutate("difficulty", difficulty -> difficulty
                .getAsJsonObject().getAsJsonArray("tiers").get(0).getAsJsonObject()
                .addProperty("default", false)));
        assertEquals(List.of("difficulty.json#/tiers: exactly one tier must be flagged "
                + "\"default\", found 0"), noDefault);

        List<String> twoDefaults = errorsOf(mutate("difficulty", difficulty -> difficulty
                .getAsJsonObject().getAsJsonArray("tiers").get(1).getAsJsonObject()
                .addProperty("default", true)));
        assertEquals(List.of("difficulty.json#/tiers: exactly one tier must be flagged "
                + "\"default\", found 2"), twoDefaults);
    }

    @Test
    void aBirdWithoutPalettesIsRejected() {
        List<String> errors = errorsOf(mutate("birds",
                birds -> firstBird(birds).add("palettes", new JsonArray())));
        assertEquals(List.of("birds.json#/0/palettes: bird 'classic' has no palette"), errors);
    }

    @Test
    void aChangedBaseStatBreaksTheClassicTable() {
        List<String> errors = errorsOf(mutate("birds", birds -> firstBird(birds)
                .getAsJsonObject("baseStats").addProperty("GRAVITY", 1900)));
        // The six physics numbers are pinned at gate 0 and at gate 25 (D10), hence two reports.
        assertEquals(2, errors.size(), errors.toString());
        assertEquals("difficulty.json#/curves/classic: classic table broken — GRAVITY at gate 0 "
                + "resolves to 1900.0, expected 1800.0", errors.get(0));
        assertEquals("difficulty.json#/curves/classic: classic table broken — GRAVITY at gate 25 "
                + "resolves to 1900.0, expected 1800.0", errors.get(1));
    }

    @Test
    void aCurveEntryTouchingThePhysicsBreaksTheClassicTableEvenWhenGateZeroIsClean() {
        // perGate only: gate 0 still resolves to 120, gate 25 does not. Gate 0 alone was blind.
        List<String> errors = errorsOf(mutate("difficulty", difficulty -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("stat", "SCROLL_SPEED");
            entry.addProperty("op", "MULTIPLY");
            entry.addProperty("base", 1.0);
            entry.addProperty("perGate", 0.004);
            entry.addProperty("min", 1.0);
            entry.addProperty("max", 1.5);
            difficulty.getAsJsonObject().getAsJsonObject("curves").getAsJsonArray("classic")
                    .add(entry);
        }));
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("SCROLL_SPEED at gate 25"), errors.get(0));
    }

    @Test
    void aChangedCurveBreaksTheClassicTable() {
        List<String> errors = errorsOf(mutate("difficulty", difficulty -> difficulty
                .getAsJsonObject().getAsJsonObject("curves").getAsJsonArray("classic").get(0)
                .getAsJsonObject().addProperty("perGate", 0.02)));
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("MOVING_CHANCE at gate 19"), errors.get(0));
        assertTrue(errors.get(1).contains("MOVING_CHANCE at gate 25"), errors.get(1));
    }

    @Test
    void aTierEffectLeakingIntoNormalBreaksTheClassicTable() {
        List<String> errors = errorsOf(mutate("difficulty", difficulty -> {
            JsonObject normal = difficulty.getAsJsonObject().getAsJsonArray("tiers").get(0)
                    .getAsJsonObject();
            JsonObject effect = new JsonObject();
            effect.addProperty("stat", "SCROLL_SPEED");
            effect.addProperty("op", "MULTIPLY");
            effect.addProperty("value", 1.2);
            JsonArray effects = new JsonArray();
            effects.add(effect);
            normal.add("effects", effects);
        }));
        assertEquals(2, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("SCROLL_SPEED at gate 0 resolves to 144.0"),
                errors.get(0));
        assertTrue(errors.get(1).contains("SCROLL_SPEED at gate 25 resolves to 144.0"),
                errors.get(1));
    }

    @Test
    void aMissingClassicBirdIsReportedInsteadOfCrashing() {
        List<String> errors = errorsOf(mutate("birds",
                birds -> firstBird(birds).addProperty("id", "sparrow")));
        assertEquals(List.of("birds.json#: the classic table needs a bird 'classic'"), errors);
    }

    @Test
    void everyErrorOfOnePassIsReportedTogether() {
        Map<String, JsonElement> files = mutate("birds", birds -> {
            JsonObject bird = firstBird(birds);
            bird.addProperty("id", "Classic");
            bird.getAsJsonArray("palettes").get(1).getAsJsonObject().addProperty("id", "Ember");
        });
        List<String> errors = errorsOf(files);
        assertEquals(3, errors.size(), errors.toString());
        assertTrue(errors.get(0).contains("bird id 'Classic'"), errors.get(0));
        assertTrue(errors.get(1).contains("palette id 'Ember'"), errors.get(1));
        assertTrue(errors.get(2).contains("needs a bird 'classic'"), errors.get(2));
    }

    @Test
    void unknownKeysInTheShippedShapeAreRejectedBeforeValidation() {
        Map<String, JsonElement> files = mutate("birds",
                birds -> firstBird(birds).addProperty("speed", 3));
        ContentException e = assertThrows(ContentException.class, () -> GameContent.fromJson(files));
        assertTrue(e.getMessage().contains("Content failed to bind"), e.getMessage());
        assertEquals(1, e.errors().size(), e.getMessage());
        assertTrue(e.errors().get(0).startsWith("birds.json#/0/speed: unknown key 'speed'"),
                e.errors().get(0));
    }

    /** The string-key rules of D25, checked against tables the test controls. */
    @Nested
    class StringKeys {

        private Map<String, String> englishWithout(String... removed) {
            Map<String, String> table = new LinkedHashMap<>(Strings.tableOf("en"));
            for (String key : removed) {
                assertTrue(table.remove(key) != null, "fixture key does not exist: " + key);
            }
            return table;
        }

        @Test
        void theShippedContentAndStringsAgree() {
            ContentValidator.StringReport report = ContentValidator.checkStrings(
                    GameContent.load());

            assertEquals(List.of(), report.errors());
            assertEquals(List.of(), report.warnings(), "pt_BR is complete today");
            assertTrue(report.ok());
        }

        @Test
        void everyStringKeyMustExistInEnglish() {
            ContentValidator.StringReport report = ContentValidator.checkStrings(
                    TestContent.frozen(), englishWithout(StringKey.MENU_PLAY.key()),
                    Map.of());

            assertEquals(List.of("strings/en.json#/menu.play: missing string for "
                    + "StringKey.MENU_PLAY"), report.errors());
            assertFalse(report.ok());
        }

        @Test
        void everyContentEntryNeedsANameAndADescription() {
            ContentValidator.StringReport report = ContentValidator.checkStrings(
                    TestContent.frozen(),
                    englishWithout("bird.classic.desc", "cosmetic.classic.ember.name",
                            "tier.hard.name"),
                    Map.of());

            assertEquals(List.of(
                    "strings/en.json#/bird.classic.desc: missing content string",
                    "strings/en.json#/cosmetic.classic.ember.name: missing content string",
                    "strings/en.json#/tier.hard.name: missing content string"),
                    report.errors());
        }

        @Test
        void theRequiredKeysFollowTheContentInHand() {
            assertTrue(ContentValidator.contentKeys(TestContent.frozen())
                    .containsAll(List.of("bird.classic.name", "bird.classic.desc",
                            "cosmetic.classic.voidglass.name", "tier.nightmare.desc")));
        }

        @Test
        void aKeyMissingFromATranslationIsAWarningNotAnError() {
            Map<String, String> partial = new LinkedHashMap<>(Strings.tableOf("pt_BR"));
            partial.remove(StringKey.MENU_QUIT.key());
            partial.put("menu.leftover", "Sobra");

            ContentValidator.StringReport report = ContentValidator.checkStrings(
                    TestContent.frozen(), Strings.tableOf("en"), Map.of("pt_BR", partial));

            assertEquals(List.of(), report.errors());
            assertEquals(List.of(
                    "strings/pt_BR.json#/menu.quit: missing translation, falls back to English",
                    "strings/pt_BR.json#/menu.leftover: key is not in strings/en.json"),
                    report.warnings());
        }

        @Test
        void loadingTheShippedContentRaisesMissingStrings() {
            assertTrue(ContentValidator.validateStrings(GameContent.load()).ok(),
                    "GameContent.load() validates the shipped strings too");
        }
    }

    @Test
    void aMissingFileIsReportedWithItsName() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.frozenJson());
        files.remove("difficulty");
        ContentException e = assertThrows(ContentException.class, () -> GameContent.fromJson(files));
        assertEquals(List.of("difficulty.json#: missing content file"), e.errors());
    }
}
