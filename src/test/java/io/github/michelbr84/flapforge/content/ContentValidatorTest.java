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
        assertEquals(List.of(), ContentValidator.warningsOf(TestContent.frozen()));
    }

    /**
     * E1: {@code coinsPerPoint} is the only thing that reads {@code points}, so setting it to 0
     * makes {@code SCORE_MULT} — and every bird spread, upgrade and modifier that touches it —
     * silently worthless. It is a warning, not an error: the game still runs.
     */
    @Test
    void aScoreMultiplierWithNoSinkIsWarnedAbout() {
        Map<String, JsonElement> files = mutate("economy", economy -> economy.getAsJsonObject()
                .getAsJsonObject("rewards").addProperty("coinsPerPoint", 0));
        GameContent content = GameContent.fromJson(files);
        assertEquals(List.of(), ContentValidator.errorsOf(content), "it still loads");
        List<String> warnings = ContentValidator.warningsOf(content);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).startsWith("economy.json#/rewards/coinsPerPoint"),
                warnings.get(0));
        assertTrue(warnings.get(0).contains("SCORE_MULT"), warnings.get(0));
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

    /** The economy rules of M3 (§4, E1, E4), driven from the frozen {@code economy.json}. */
    @Nested
    class Economy {

        private JsonObject economy(JsonElement root) {
            return root.getAsJsonObject();
        }

        @Test
        void theFrozenEconomyIsValid() {
            assertEquals(List.of(), ContentValidator.errorsOf(TestContent.frozen()));
        }

        @Test
        void anEmptyCurrencyListIsRejected() {
            List<String> errors = errorsOf(mutate("economy",
                    root -> economy(root).add("currencies", new JsonArray())));
            assertEquals(List.of("economy.json#/currencies: no currency is defined"), errors);
        }

        @Test
        void theCurrencyTheRewardsArePaidInMustExist() {
            List<String> errors = errorsOf(mutate("economy", root -> {
                JsonArray currencies = new JsonArray();
                currencies.add("shards");
                economy(root).add("currencies", currencies);
            }));
            // 'shards' itself has a source — it is the first declared currency, which is what
            // every reward block pays — so the only thing wrong here is that the wallet's own
            // 'coins' is not declared.
            assertEquals(List.of(
                    "economy.json#/currencies: every reward is paid in 'coins', "
                            + "which is not a declared currency"), errors);
        }

        /** D13: every currency has a source. A second one has none until a reward names it. */
        @Test
        void aSecondCurrencyNothingPaysOutIsRejected() {
            List<String> errors = errorsOf(mutate("economy", root -> {
                JsonArray currencies = new JsonArray();
                currencies.add("coins");
                currencies.add("shards");
                economy(root).add("currencies", currencies);
            }));
            assertEquals(1, errors.size(), errors.toString());
            assertTrue(errors.get(0).startsWith("economy.json#/currencies/1: nothing pays out"
                    + " 'shards'"), errors.get(0));
        }

        @Test
        void currencyIdsFollowTheRegexAndAreUnique() {
            List<String> errors = errorsOf(mutate("economy", root -> {
                JsonArray currencies = new JsonArray();
                currencies.add("coins");
                currencies.add("coins");
                currencies.add("Gold Bars");
                economy(root).add("currencies", currencies);
            }));
            assertEquals(3, errors.size(), errors.toString());
            assertEquals("economy.json#/currencies/1: duplicate currency id 'coins'",
                    errors.get(0));
            assertTrue(errors.get(1).startsWith(
                    "economy.json#/currencies/2: currency id 'Gold Bars' does not match"),
                    errors.get(1));
            // The unlock graph adds the other half of the story: an undeclared currency has no
            // source either, so it could never be earned (D13).
            assertTrue(errors.get(2).startsWith(
                    "economy.json#/currencies/2: nothing pays out 'Gold Bars'"), errors.get(2));
        }

        @Test
        void levelRewardKeysMustBeIntegersInsideTheCurve() {
            List<String> errors = errorsOf(mutate("economy", root -> {
                JsonObject rewards = economy(root).getAsJsonObject("xp")
                        .getAsJsonObject("levelRewards");
                rewards.add("two", new JsonObject());
                rewards.add("1", new JsonObject());
                rewards.add("99", new JsonObject());
            }));
            assertEquals(3, errors.size(), errors.toString());
            assertEquals("economy.json#/xp/levelRewards/two: level reward key 'two' is not an "
                    + "integer", errors.get(0));
            assertTrue(errors.get(1).contains("must be at least 2"), errors.get(1));
            assertTrue(errors.get(2).contains("above xp.curve.maxLevel 50"), errors.get(2));
        }

        @Test
        void featureIdsAreUniqueAndFollowTheRegex() {
            List<String> errors = errorsOf(mutate("economy", root -> {
                JsonArray features = economy(root).getAsJsonArray("features");
                features.add(features.get(0).deepCopy());
                features.get(1).getAsJsonObject().addProperty("id", "Seeded Runs");
            }));
            assertEquals(2, errors.size(), errors.toString());
            assertTrue(errors.get(0).startsWith(
                    "economy.json#/features/1/id: feature id 'Seeded Runs' does not match"),
                    errors.get(0));
            assertEquals("economy.json#/features/2/id: duplicate feature id 'modifiers'",
                    errors.get(1));
        }

        @Test
        void prestigeOnlyKeepsWhatAPrestigeCanKeep() {
            List<String> errors = errorsOf(mutate("economy", root -> {
                JsonArray keeps = new JsonArray();
                keeps.add("birds");
                keeps.add("coins");
                economy(root).getAsJsonObject("prestige").add("keeps", keeps);
            }));
            assertEquals(1, errors.size(), errors.toString());
            assertTrue(errors.get(0).startsWith("economy.json#/prestige/keeps/1: unknown keep "
                    + "'coins'"), errors.get(0));
        }

        @Test
        void aStreakStepBelowOneIsRejectedAtBindTime() {
            Map<String, JsonElement> files = mutate("economy", root -> economy(root)
                    .getAsJsonObject("rewards").getAsJsonObject("streak")
                    .addProperty("step", 0));
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(files));
            assertEquals(1, e.errors().size(), e.errors().toString());
            assertTrue(e.errors().get(0).contains("streak.step must be at least 1"),
                    e.errors().get(0));
        }

        @Test
        void prestigeShardsAreGoneForGood() {
            Map<String, JsonElement> files = mutate("economy", root -> economy(root)
                    .getAsJsonObject("prestige").addProperty("shardsPerLevel", 3));
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(files));
            assertTrue(e.errors().get(0).startsWith(
                    "economy.json#/prestige/shardsPerLevel: unknown key 'shardsPerLevel'"),
                    e.errors().get(0));
        }

        @Test
        void aMissingEconomyFileIsReportedWithItsName() {
            Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.frozenJson());
            files.remove("economy");
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(files));
            assertEquals(List.of("economy.json#: missing content file"), e.errors());
        }
    }

    @Test
    void aMissingFileIsReportedWithItsName() {
        Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.frozenJson());
        files.remove("difficulty");
        ContentException e = assertThrows(ContentException.class, () -> GameContent.fromJson(files));
        assertEquals(List.of("difficulty.json#: missing content file"), e.errors());
    }

    /**
     * The M4 rules, one broken fixture per rule
     * ({@code src/test/resources/fixtures/content_bad/*.json}). Every fixture is the shipped
     * content with exactly one file swapped for a copy carrying exactly one defect, so each test
     * can pin the rule's own message <em>and</em> its JSON pointer.
     */
    @Nested
    class BadFixtures {

        private List<String> errorsOfFixture(String file, String fixture) {
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(TestContent.shippedWith(file, fixture)));
            return e.errors();
        }

        private void assertHasError(List<String> errors, String prefix) {
            assertTrue(errors.stream().anyMatch(error -> error.startsWith(prefix)),
                    () -> "expected an error starting with\n  " + prefix + "\nbut got\n  "
                            + String.join("\n  ", errors));
        }

        @Test
        void theShippedContentPassesEveryRule() {
            GameContent shipped = GameContent.fromJson(TestContent.shippedJson());
            assertEquals(List.of(), ContentValidator.errorsOf(shipped));
        }

        @Test
        void anUnknownKeyIsRejectedWithItsPointer() {
            assertHasError(errorsOfFixture("birds", "unknown_key"),
                    "birds.json#/1/wings: unknown key 'wings' for BirdDef");
        }

        @Test
        void anUnknownEnumConstantIsRejectedWithItsPointer() {
            assertHasError(errorsOfFixture("birds", "bad_enum"),
                    "birds.json#/1/archetype: not a valid BirdArchetype: 'FLYING'");
        }

        @Test
        void aDuplicateUpgradeIdIsRejected() {
            assertHasError(errorsOfFixture("upgrades", "duplicate_id"),
                    "upgrades.json#/nodes/1/id: duplicate upgrade id 'feather_1'");
        }

        @Test
        void aPrerequisiteCycleIsRejectedWithThePathThatLoops() {
            assertHasError(errorsOfFixture("upgrades", "cycle"),
                    "upgrades.json#/nodes/feather_1/prereqs: prerequisite cycle feather_1 -> "
                            + "featherfall_2 -> slim_frame_1 -> feather_1");
        }

        @Test
        void anUnlockableNothingOpensIsRejected() {
            assertHasError(errorsOfFixture("worlds", "unreachable"),
                    "worlds.json#/worlds/4: 'world:void' cannot be reached from the default set");
        }

        @Test
        void aContradictoryChallengeIsRejected() {
            assertHasError(errorsOfFixture("challenges", "contradiction"),
                    "challenges.json#/challenges/5: challenge 'coin_rush_1' has flag NO_COINS and"
                            + " objective COLLECT_COINS");
        }

        @Test
        void tooMuchAbilityCapIsRejected() {
            assertHasError(errorsOfFixture("upgrades", "cap_exceeded"),
                    "upgrades.json#/nodes: the ability level cap reaches 4 (base 2 + 2 from"
                            + " ability_cap grants) but ability 'double_flap' has only 3 levels");
        }

        /**
         * D10: {@code ProceduralArt.drawBirdPortrait} falls through to the balanced silhouette
         * for a key it does not know, so a typo would ship the wrong art in silence.
         */
        @Test
        void anUnknownBirdSilhouetteIsRejected() {
            assertHasError(errorsOfFixture("birds", "bad_shape"),
                    "birds.json#/1/shape: unknown silhouette 'swfit'");
        }

        /**
         * D13: the shop reads a {@code purchase} branch as the price and sells the unlockable for
         * it. Under an {@code all_of} the coins are one requirement among several, so the sale
         * would hand over something its siblings still gate.
         */
        @Test
        void aPurchaseInsideAnAllOfIsRejected() {
            assertHasError(errorsOfFixture("birds", "purchase_under_all_of"),
                    "birds.json#/1/unlock/conditions/1/type: 'purchase' may only be the whole"
                            + " condition or a branch of an 'any_of'");
        }

        @Test
        void aCosmeticOnlyConditionElsewhereIsRejected() {
            assertHasError(errorsOfFixture("worlds", "cosmetic_only_condition"),
                    "worlds.json#/worlds/4/unlock/type: 'prestige' is allowed only on a cosmetic");
        }

        /**
         * A content id with no {@code name}/{@code desc} in {@code en.json} is a string error,
         * not a content error: the content itself is consistent, so it binds and validates and
         * only {@link ContentValidator#checkStrings(GameContent)} objects (D25, E31.h).
         */
        @Test
        void anIdWithNoDisplayStringIsRejected() {
            GameContent content = GameContent.fromJson(
                    TestContent.shippedWith("birds", "missing_string"));
            ContentValidator.StringReport report = ContentValidator.checkStrings(content);
            assertFalse(report.ok());
            assertTrue(report.errors().contains("strings/en.json#/bird.sparrow.name: missing content"
                    + " string"), report.errors().toString());
            assertTrue(report.errors().contains("strings/en.json#/cosmetic.sparrow.aurora.desc: missing"
                    + " content string"), report.errors().toString());
        }
    }
}
