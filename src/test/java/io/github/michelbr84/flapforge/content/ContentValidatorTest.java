package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.michelbr84.flapforge.content.defs.WorldDef;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
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
    /** M5: {@code abilities.json} against the behaviours that read it (D9, E19). */
    @Nested
    class Abilities {

        private List<String> errorsOfShippedWith(Consumer<JsonObject> edit) {
            Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
            JsonElement abilities = files.get("abilities").deepCopy();
            edit.accept(abilities.getAsJsonObject());
            files.put("abilities", abilities);
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(files));
            return e.errors();
        }

        private JsonObject ability(JsonObject root, int index) {
            return root.getAsJsonArray("abilities").get(index).getAsJsonObject();
        }

        private JsonObject level(JsonObject root, int index, int level) {
            return ability(root, index).getAsJsonArray("levels").get(level).getAsJsonObject();
        }

        private void assertHasError(List<String> errors, String prefix) {
            assertTrue(errors.stream().anyMatch(error -> error.startsWith(prefix)),
                    () -> "expected an error starting with\n  " + prefix + "\nbut got\n  "
                            + String.join("\n  ", errors));
        }

        @Test
        void theShippedAbilitiesAreValid() {
            assertEquals(List.of(), ContentValidator.errorsOf(GameContent.load()));
        }

        @Test
        void anUnknownBehaviorIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            ability(root, 0).addProperty("behavior", "quantum_flap")),
                    "abilities.json#/abilities/0/behavior: unknown ability behavior "
                            + "'quantum_flap'");
        }

        @Test
        void aParameterTheBehaviorDoesNotReadIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 1, 0).getAsJsonObject("params")
                                    .addProperty("invulnTikcs", 45)),
                    "abilities.json#/abilities/1/levels/0/params/invulnTikcs: behavior 'shield' "
                            + "reads no such parameter");
        }

        @Test
        void aMissingRequiredParameterIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 1, 2).getAsJsonObject("params").remove("invulnTicks")),
                    "abilities.json#/abilities/1/levels/2/params/invulnTicks: behavior 'shield' "
                            + "requires this parameter at every level");
        }

        /**
         * An active contributes its {@code effects} only while its duration runs, so a level with
         * a duration of zero is an ability whose whole stat half silently does nothing — the dash
         * would keep its cooldown, its i-frames and its held line and lose the 2.5x scroll.
         */
        @Test
        void anActiveWithEffectsAndNoDurationIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 2, 0).addProperty("durationTicks", 0)),
                    "abilities.json#/abilities/2/levels/0/durationTicks: an ACTIVE ability with "
                            + "effects needs a duration");
        }

        @Test
        void aParameterOutOfRangeIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 1, 0).getAsJsonObject("params")
                                    .addProperty("invulnTicks", 900)),
                    "abilities.json#/abilities/1/levels/0/params/invulnTicks: 900.0 is outside");
        }

        @Test
        void aParameterThatGoesTheWrongWayWithTheLevelIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 1, 2).getAsJsonObject("params")
                                    .addProperty("invulnTicks", 30)),
                    "abilities.json#/abilities/1/levels/2/params/invulnTicks: 45.0 -> 30.0 goes "
                            + "the wrong way for a UP parameter");
        }

        @Test
        void aLongerCooldownOrAShorterDurationAtAHigherLevelIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 2, 2).addProperty("cooldownTicks", 900)),
                    "abilities.json#/abilities/2/levels/2/cooldownTicks: a level up must not "
                            + "lengthen the cooldown");
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 2, 2).addProperty("durationTicks", 10)),
                    "abilities.json#/abilities/2/levels/2/durationTicks: a level up must not "
                            + "shorten the duration");
        }

        @Test
        void aPassiveWithATimerIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 1, 0).addProperty("durationTicks", 90)),
                    "abilities.json#/abilities/1/levels/0: a PASSIVE ability is always on");
        }

        @Test
        void anActiveWithNoGateAtAllIsRejected() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonObject level = level(root, 2, 0);
                level.addProperty("cooldownTicks", 0);
                level.addProperty("durationTicks", 0);
            }), "abilities.json#/abilities/2/levels/0: an ACTIVE ability needs a cooldown");
        }

        @Test
        void aLevelThatCostsNoMoreThanTheOneBelowIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            level(root, 1, 2).addProperty("cost", 400)),
                    "abilities.json#/abilities/1/levels/2/cost: level 3 must cost more than the "
                            + "level below it");
        }
    }

    /** M6: {@code modifiers.json} and the two ways a card can look fine and do nothing. */
    @Nested
    class Modifiers {

        private List<String> errorsOfShippedWith(Consumer<JsonObject> edit) {
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(edited(edit)));
            return e.errors();
        }

        private Map<String, JsonElement> edited(Consumer<JsonObject> edit) {
            Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
            JsonElement modifiers = files.get("modifiers").deepCopy();
            edit.accept(modifiers.getAsJsonObject());
            files.put("modifiers", modifiers);
            return files;
        }

        private JsonObject modifier(JsonObject root, int index) {
            return root.getAsJsonArray("modifiers").get(index).getAsJsonObject();
        }

        private JsonObject synergy(JsonObject root, int index) {
            return root.getAsJsonArray("synergies").get(index).getAsJsonObject();
        }

        private void assertHasError(List<String> errors, String prefix) {
            assertTrue(errors.stream().anyMatch(error -> error.startsWith(prefix)),
                    () -> "expected an error starting with\n  " + prefix + "\nbut got\n  "
                            + String.join("\n  ", errors));
        }

        @Test
        void theShippedModifiersAreValid() {
            GameContent content = GameContent.load();
            assertEquals(List.of(), ContentValidator.errorsOf(content));
            assertEquals(List.of(), ContentValidator.warningsOf(content));
            assertEquals(17, content.modifiers().size());
            assertEquals(4, content.synergies().size());
        }

        @Test
        void anUnknownExcludedModifierIsRejected() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonArray excludes = new JsonArray();
                excludes.add("tail_wind");
                modifier(root, 0).add("excludes", excludes);
            }), "modifiers.json#/modifiers/0/excludes/0: unknown modifier 'tail_wind'");
        }

        @Test
        void aCardThatExcludesItselfIsRejected() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonArray excludes = new JsonArray();
                excludes.add("tailwind");
                modifier(root, 0).add("excludes", excludes);
            }), "modifiers.json#/modifiers/0/excludes/0: 'tailwind' excludes itself");
        }

        @Test
        void aStreakBonusThatSurvivesNoCoinsIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            modifier(root, 6).add("requiresFlagsAbsent", new JsonArray())),
                    "modifiers.json#/modifiers/6/requiresFlagsAbsent: 'streak_bounty' pays coins "
                            + "per streak step, so it must list NO_COINS");
        }

        @Test
        void aRarityWithNoWeightIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            root.getAsJsonObject("rarityWeights").remove("LEGENDARY")),
                    "modifiers.json#/modifiers/14/rarity: rarity LEGENDARY has no draw weight");
        }

        @Test
        void aCardThatCannotBeTakenIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            modifier(root, 0).addProperty("maxStacks", 0)),
                    "modifiers.json#/modifiers/0/maxStacks: a card must be takeable at least "
                            + "once");
        }

        @Test
        void aCardWithNothingToGiveIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            modifier(root, 0).add("effects", new JsonArray())),
                    "modifiers.json#/modifiers/0: 'tailwind' has no effect, no flag and no streak "
                            + "bonus");
        }

        /**
         * The difficulty layer resolves {@code SPEED_RAMP} when the run starts and never again, so
         * a card that turns it on halfway through would be a flag nothing looks at (D8).
         */
        @Test
        void aCardGrantingARunStartOnlyFlagIsRejected() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonArray flags = new JsonArray();
                flags.add("SPEED_RAMP");
                modifier(root, 0).add("flags", flags);
            }), "modifiers.json#/modifiers/0/flags/0: modifier 'tailwind' may not grant "
                    + "SPEED_RAMP mid-run");
        }

        @Test
        void aScheduleThatDoesNotAscendIsRejected() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonArray schedule = new JsonArray();
                schedule.add(10);
                schedule.add(10);
                root.add("offerSchedule", schedule);
            }), "modifiers.json#/offerSchedule/1: the schedule must be strictly ascending");
        }

        /** E16: one required tag can never be split across the two entries the rule asks for. */
        @Test
        void aSynergyWithOneRequiredTagIsRejected() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonArray tags = new JsonArray();
                tags.add("ECONOMY");
                synergy(root, 0).add("requiresTags", tags);
            }), "modifiers.json#/synergies/0/requiresTags: a set bonus needs at least 2 tags");
        }

        /** A set bonus no build can complete is a balance problem, so it warns rather than fails. */
        @Test
        void aSynergyNoBuildCanCompleteIsWarnedAbout() {
            // Only tailwind and stormrider carry SPEED, so three SPEED contributions cannot be
            // assembled by any build however the cards are combined.
            JsonArray tags = new JsonArray();
            tags.add("SPEED");
            tags.add("SPEED");
            tags.add("SPEED");
            GameContent content = GameContent.fromJson(edited(root ->
                    synergy(root, 3).add("requiresTags", tags)));
            assertEquals(List.of(), ContentValidator.errorsOf(content));
            assertTrue(ContentValidator.warningsOf(content).stream().anyMatch(w -> w.startsWith(
                            "modifiers.json#/synergies/3/requiresTags: no two distinct shipped "
                                    + "modifiers can ever satisfy")),
                    () -> ContentValidator.warningsOf(content).toString());
        }

        @Test
        void anUnknownTagIsRejectedAtBindTime() {
            assertHasError(errorsOfShippedWith(root -> {
                JsonArray tags = new JsonArray();
                tags.add("LUCK");
                modifier(root, 0).add("tags", tags);
            }), "modifiers.json#/modifiers/0/tags/0: not a valid ModifierTag: 'LUCK'");
        }

        @Test
        void anUnknownRarityIsRejectedAtBindTime() {
            assertHasError(errorsOfShippedWith(root ->
                            modifier(root, 0).addProperty("rarity", "MYTHIC")),
                    "modifiers.json#/modifiers/0/rarity: not a valid Rarity: 'MYTHIC'");
        }

        /**
         * E32.d: the spawn decision reads {@code MOVING_CHANCE} — both the moving flag and the
         * layout, and therefore how many draws come out of the {@code obstacle} stream — so a card
         * that changed it would make the obstacle sequence depend on what the player drafted.
         * {@code checkMidRunFlags} keeps {@code ALL_OBSTACLES_MOVE} out for the same reason; this
         * is the stat-level half of the same rule.
         */
        @Test
        void aCardThatWouldMoveTheSpawnDecisionIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            modifier(root, 0).add("effects", effects(StatId.MOVING_CHANCE))),
                    "modifiers.json#/modifiers/0/effects/0: modifier 'tailwind' may not change "
                            + "MOVING_CHANCE");
        }

        @Test
        void aSynergyThatWouldMoveTheSpawnDecisionIsRejected() {
            assertHasError(errorsOfShippedWith(root ->
                            synergy(root, 0).add("effects", effects(StatId.MOVING_CHANCE))),
                    "modifiers.json#/synergies/0/effects/0: synergy 'coin_engine' may not change "
                            + "MOVING_CHANCE");
        }

        private JsonArray effects(StatId stat) {
            JsonObject effect = new JsonObject();
            effect.addProperty("stat", stat.name());
            effect.addProperty("op", "FLAT_ADD");
            effect.addProperty("value", 0.5);
            JsonArray effects = new JsonArray();
            effects.add(effect);
            return effects;
        }
    }

    /**
     * M6, E19: {@code challenges.json.forcedModifiers} resolves against {@code modifiers.json} now
     * that the file ships. {@code ModifierDirector.start} applies the list under the authored
     * rules, so anything this misses is a card the challenge silently loses at run start.
     */
    @Nested
    class ForcedModifiers {

        private List<String> errorsOfChallengesWith(Consumer<JsonObject> edit) {
            Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
            JsonElement challenges = files.get("challenges").deepCopy();
            edit.accept(challenges.getAsJsonObject());
            files.put("challenges", challenges);
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(files));
            return e.errors();
        }

        /** Replaces the {@code forcedModifiers} of one challenge. */
        private Consumer<JsonObject> forcing(int index, String... ids) {
            return root -> {
                JsonArray forced = new JsonArray();
                for (String id : ids) {
                    forced.add(id);
                }
                root.getAsJsonArray("challenges").get(index).getAsJsonObject()
                        .add("forcedModifiers", forced);
            };
        }

        private void assertHasError(List<String> errors, String prefix) {
            assertTrue(errors.stream().anyMatch(error -> error.startsWith(prefix)),
                    () -> "expected an error starting with\n  " + prefix + "\nbut got\n  "
                            + String.join("\n  ", errors));
        }

        @Test
        void theShippedChallengesForceOnlyCardsThatExist() {
            GameContent content = GameContent.load();
            assertEquals(List.of("coin_drops"),
                    content.challenges().get("coin_rush_1").forcedModifiers(),
                    "the one live reference in the shipped content");
            assertEquals(List.of(), ContentValidator.errorsOf(content));
        }

        @Test
        void anUnknownForcedModifierIsRejected() {
            assertHasError(errorsOfChallengesWith(forcing(5, "coin_dropz")),
                    "challenges.json#/challenges/5/forcedModifiers/0: unknown modifier "
                            + "'coin_dropz'");
        }

        @Test
        void moreCopiesThanMaxStacksAreRejected() {
            assertHasError(errorsOfChallengesWith(forcing(5, "coin_drops", "coin_drops",
                            "coin_drops", "coin_drops")),
                    "challenges.json#/challenges/5/forcedModifiers/3: 'coin_drops' is forced 4 "
                            + "times but its maxStacks is 3");
        }

        @Test
        void twoCardsThatExcludeEachOtherAreRejected() {
            assertHasError(errorsOfChallengesWith(forcing(5, "light_frame", "glass_wings")),
                    "challenges.json#/challenges/5/forcedModifiers/1: 'glass_wings' and "
                            + "'light_frame' exclude each other");
        }

        @Test
        void aCardTheChallengesOwnFlagsForbidIsRejected() {
            // no_shield_1 turns NO_DEFENSIVE_ABILITIES on, which is exactly what temp_shield
            // declares it cannot live with — the run would drop the card and nothing would say so.
            assertHasError(errorsOfChallengesWith(forcing(0, "temp_shield")),
                    "challenges.json#/challenges/0/forcedModifiers/0: 'temp_shield' requires "
                            + "NO_DEFENSIVE_ABILITIES to be absent, and the challenge turns it on");
        }
    }

    /** M7: {@code patterns.json}, {@code worlds.json} ambience and cycles, and E14. */
    @Nested
    class Patterns {

        private Map<String, JsonElement> edited(String file, Consumer<JsonObject> edit) {
            Map<String, JsonElement> files = new LinkedHashMap<>(TestContent.shippedJson());
            JsonElement root = files.get(file).deepCopy();
            edit.accept(root.getAsJsonObject());
            files.put(file, root);
            return files;
        }

        private List<String> errorsOf(String file, Consumer<JsonObject> edit) {
            ContentException e = assertThrows(ContentException.class,
                    () -> GameContent.fromJson(edited(file, edit)));
            return e.errors();
        }

        private JsonObject pattern(JsonObject root, String id) {
            for (JsonElement p : root.getAsJsonArray("patterns")) {
                if (id.equals(p.getAsJsonObject().get("id").getAsString())) {
                    return p.getAsJsonObject();
                }
            }
            throw new IllegalArgumentException("no pattern " + id);
        }

        private JsonObject world(JsonObject root, String id) {
            for (JsonElement w : root.getAsJsonArray("worlds")) {
                if (id.equals(w.getAsJsonObject().get("id").getAsString())) {
                    return w.getAsJsonObject();
                }
            }
            throw new IllegalArgumentException("no world " + id);
        }

        private JsonObject challenge(JsonObject root, String id) {
            for (JsonElement c : root.getAsJsonArray("challenges")) {
                if (id.equals(c.getAsJsonObject().get("id").getAsString())) {
                    return c.getAsJsonObject();
                }
            }
            throw new IllegalArgumentException("no challenge " + id);
        }

        private void assertHasError(List<String> errors, String prefix) {
            assertTrue(errors.stream().anyMatch(error -> error.startsWith(prefix)),
                    () -> "expected an error starting with\n  " + prefix + "\nbut got\n  "
                            + String.join("\n  ", errors));
        }

        @Test
        void theShippedPatternsAreValidAndEveryReferenceResolves() {
            GameContent content = GameContent.load();
            assertEquals(List.of(), ContentValidator.errorsOf(content));
            assertEquals(List.of(), ContentValidator.warningsOf(content));
            assertEquals(21, content.patterns().size());
            for (WorldDef world : content.worlds()) {
                for (String id : world.patterns()) {
                    assertTrue(content.patterns().get(id).weight() > 0, id);
                }
                for (String id : world.boss().patterns()) {
                    assertEquals(0, content.patterns().get(id).weight(), id);
                    assertTrue(content.patterns().get(id).totalDx() >= 480, id);
                }
            }
            assertEquals("corridor_1", content.challenges().get("boss_corridor_1").forcedPattern());
            assertTrue(content.patterns().get("corridor_1").stepsScore(), "E14");
        }

        @Test
        void aPatternOfAnotherWorldCannotBeListed() {
            assertHasError(errorsOf("worlds", root -> {
                JsonArray patterns = new JsonArray();
                patterns.add("forge_gear_corridor");
                world(root, "wind_valley").add("patterns", patterns);
            }), "worlds.json#/worlds/1/patterns/0: pattern 'forge_gear_corridor' belongs to world"
                    + " 'iron_forge', not to 'wind_valley'");
        }

        @Test
        void aWeightedPatternListedNowhereAndABossPhaseWithWeightAreRejected() {
            assertHasError(errorsOf("patterns", root ->
                            pattern(root, "gf_boss_p1").addProperty("weight", 5)),
                    "worlds.json#/worlds/0/boss/patterns/0: 'gf_boss_p1' is a boss phase and"
                            + " must have weight 0 (it has 5)");
            assertHasError(errorsOf("patterns", root ->
                            pattern(root, "corridor_1").addProperty("weight", 5)),
                    "challenges.json#/challenges/6/forcedPattern: 'corridor_1' is a forced"
                            + " pattern and must have weight 0 (it has 5)");
        }

        @Test
        void aPatternWithNoStepsIsRejected() {
            assertHasError(errorsOf("patterns", root ->
                            pattern(root, "void_mixer").add("steps", new JsonArray())),
                    "patterns.json#/patterns/6/steps: a pattern needs at least one step");
        }

        @Test
        void anUnreferencedBossPhaseIsWarnedAbout() {
            GameContent content = GameContent.fromJson(edited("worlds", root -> {
                JsonArray patterns = new JsonArray();
                patterns.add("gf_boss_p1");
                world(root, "green_fields").getAsJsonObject("boss").add("patterns", patterns);
            }));
            assertEquals(List.of(), ContentValidator.errorsOf(content));
            assertTrue(ContentValidator.warningsOf(content).stream().anyMatch(w -> w.startsWith(
                    "patterns.json#/patterns/9: pattern 'gf_boss_p2' has weight 0 and no boss"
                            + " or challenge names it")),
                    () -> ContentValidator.warningsOf(content).toString());
        }

        /** E14: a forced pattern that never scores makes the challenge's boss unreachable. */
        @Test
        void aForcedPatternThatNeverScoresIsRejected() {
            assertHasError(errorsOf("patterns", root ->
                            pattern(root, "corridor_1").addProperty("scoringSteps", false)),
                    "challenges.json#/challenges/6/forcedPattern: 'corridor_1' never scores, so"
                            + " boss.atGate 20 is unreachable (E14");
        }

        @Test
        void theAmbientWindTakesAZonesRanges() {
            assertHasError(errorsOf("worlds", root -> world(root, "wind_valley")
                            .getAsJsonObject("ambient").addProperty("windX", -80)),
                    "worlds.json#/worlds/1/ambient/windX: -80.0 is outside [-60.0, 60.0] px/s");
            assertHasError(errorsOf("worlds", root -> world(root, "wind_valley")
                            .getAsJsonObject("ambient").addProperty("windY", 1000)),
                    "worlds.json#/worlds/1/ambient/windY: 1000.0 is outside [-900.0, 900.0]");
            assertHasError(errorsOf("worlds", root -> world(root, "storm_sky")
                            .getAsJsonObject("ambient").addProperty("darkness", 1.5)),
                    "worlds.json#/worlds/3/ambient");
        }

        @Test
        void aRuleCycleNeedsTwoRealOptionsAndMayNotTouchTheSpawnDecision() {
            assertHasError(errorsOf("worlds", root -> {
                JsonArray options = world(root, "void").getAsJsonObject("ruleCycles")
                        .getAsJsonArray("options");
                while (options.size() > 1) {
                    options.remove(options.size() - 1);
                }
            }), "worlds.json#/worlds/4/ruleCycles/options: a rule cycle needs at least two"
                    + " options");
            assertHasError(errorsOf("worlds", root -> {
                JsonObject empty = new JsonObject();
                empty.add("flags", new JsonArray());
                empty.add("effects", new JsonArray());
                world(root, "void").getAsJsonObject("ruleCycles").getAsJsonArray("options")
                        .add(empty);
            }), "worlds.json#/worlds/4/ruleCycles/options/4: a rule cycle option has to turn on"
                    + " a flag or apply an effect");
            assertHasError(errorsOf("worlds", root -> {
                JsonObject effect = new JsonObject();
                effect.addProperty("stat", "MOVING_CHANCE");
                effect.addProperty("op", "FLAT_ADD");
                effect.addProperty("value", 0.5);
                world(root, "void").getAsJsonObject("ruleCycles").getAsJsonArray("options")
                        .get(1).getAsJsonObject().getAsJsonArray("effects").add(effect);
            }), "worlds.json#/worlds/4/ruleCycles/options/1/effects/1: a rule cycle option may"
                    + " not change MOVING_CHANCE");
        }

        @Test
        void aWorldWithNoPositiveSpawnWeightIsRejected() {
            assertHasError(errorsOf("worlds", root -> {
                JsonObject weights = new JsonObject();
                weights.addProperty("pipe_gate", 0);
                world(root, "green_fields").add("spawnWeights", weights);
            }), "worlds.json#/worlds/0/spawnWeights: world 'green_fields' has no positive spawn"
                    + " weight");
        }

        @Test
        void aStepWithAnUnknownKindIsRejectedAtBindTime() {
            assertHasError(errorsOf("patterns", root -> pattern(root, "void_mixer")
                            .getAsJsonArray("steps").get(1).getAsJsonObject()
                            .addProperty("kind", "saw")),
                    "patterns.json#/patterns/6/steps/1/kind: not a valid ObstacleKind: 'saw'");
        }

        @Test
        void theChallengeWithABossNeedsItsForcedPatternToResolve() {
            assertHasError(errorsOf("challenges", root -> challenge(root, "boss_corridor_1")
                            .addProperty("forcedPattern", "corridor_9")),
                    "challenges.json#/challenges/6/forcedPattern: unknown pattern 'corridor_9'");
        }
    }

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

        /** M7: a step parameter outside its kind's {@code ParamSpec}, and one the kind never reads. */
        @Test
        void aBadPatternParameterIsRejectedWithItsPointer() {
            List<String> errors = errorsOfFixture("patterns", "bad_param");
            assertHasError(errors, "patterns.json#/patterns/0/steps/0/params/accelY: 1500.0 is"
                    + " outside [-900.0, 900.0]");
            assertHasError(errors, "patterns.json#/patterns/0/steps/1/params/spin: pipe_gate"
                    + " reads no such parameter; it reads [layout, gapCenter, gapSize,"
                    + " oscillate, amplitude, speed]");
        }

        /** M7: a world or a boss naming a pattern that does not exist. */
        @Test
        void anUnknownPatternReferenceIsRejectedWithItsPointer() {
            List<String> errors = errorsOfFixture("worlds", "unknown_pattern");
            assertHasError(errors, "worlds.json#/worlds/1/patterns/1: unknown pattern"
                    + " 'wv_tornado'");
            assertHasError(errors, "worlds.json#/worlds/1/boss/patterns/1: unknown pattern"
                    + " 'wv_boss_p9'");
            // The pattern the world no longer lists is unreachable content, which is an error
            // for a weighted pattern.
            assertHasError(errors, "patterns.json#/patterns/1/weight: pattern 'wv_crosswind' has"
                    + " weight 20 but world 'wind_valley' does not list it, so it is never drawn");
        }

        /** M7, §4 feasibility: the gap, the step distance and the boss phase length. */
        @Test
        void anInfeasiblePatternIsRejectedWithEveryRuleItBreaks() {
            List<String> errors = errorsOfFixture("patterns", "infeasible_pattern");
            assertHasError(errors, "patterns.json#/patterns/2/steps/0/params/gapSize: 74 px"
                    + " leaves 53.3 px on the tightest tier (× 0.80 × 0.9), less than the 54.5"
                    + " a bird fits through (§4 feasibility)");
            assertHasError(errors, "patterns.json#/patterns/2/steps/1/dx: 60 px between columns"
                    + " is less than the 100 a bird needs to change lanes (§4 feasibility)");
            assertHasError(errors, "worlds.json#/worlds/0/boss/patterns/0: boss phase"
                    + " 'gf_boss_p1' spans 450 px, less than the 480 a phase needs (§4)");
        }

        /** M7 fairness: the gate right after a bolt sits on the bolt's unlit side, authored. */
        @Test
        void aGateOnTheLitSideOfTheBoltBeforeItIsRejected() {
            List<String> errors = errorsOfFixture("patterns", "bolt_then_gate");
            assertHasError(errors, "patterns.json#/patterns/4/steps/1/params/gapCenter: 0.3 is on"
                    + " the lit side of the bolt before it; a gate right after a bolt sits at or"
                    + " above 0.5 (a TOP bolt lights the upper part) (§4 feasibility)");
            assertHasError(errors, "patterns.json#/patterns/4/steps/3/params/gapCenter: a gate"
                    + " right after a bolt needs an authored centre at or below 0.5 (a BOTTOM bolt"
                    + " lights the lower part), not 'random' (§4 feasibility)");
            assertHasError(errors, "patterns.json#/patterns/6/steps/2/params/lengthFrac: the"
                    + " bolt's safe band is 130 px from the band of the column before it, more"
                    + " than the 71 px of scroll between them at the strike; lower the fraction,"
                    + " move the previous column's band or widen dx (§4 feasibility)");
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
