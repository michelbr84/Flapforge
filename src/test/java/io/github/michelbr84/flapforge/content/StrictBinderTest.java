package io.github.michelbr84.flapforge.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import io.github.michelbr84.flapforge.content.defs.BirdDef;
import io.github.michelbr84.flapforge.content.defs.CurveEntryDef;
import io.github.michelbr84.flapforge.content.defs.DifficultyDef;
import io.github.michelbr84.flapforge.content.defs.TierDef;
import io.github.michelbr84.flapforge.content.defs.UnlockConditionDef;
import io.github.michelbr84.flapforge.content.defs.UnlockType;
import io.github.michelbr84.flapforge.gameplay.stats.RuleFlag;
import io.github.michelbr84.flapforge.gameplay.stats.StatId;
import io.github.michelbr84.flapforge.gameplay.stats.StatOp;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Binding rules of {@link StrictBinder} (D10): pointers, enums, nesting and numbers. */
class StrictBinderTest {

    /** A record covering every shape the binder supports, used only by this test. */
    record Nested(String name, int count, double ratio, boolean flag, StatId stat,
            List<CurveEntryDef> entries, Map<StatId, Double> stats, UnlockConditionDef unlock) {
    }

    private static JsonElement json(String text) {
        return ContentLoader.parse("probe", text);
    }

    private static List<String> errorsOf(Class<?> type, String text) {
        StrictBinder binder = new StrictBinder("probe.json");
        binder.bind(type, json(text));
        return binder.errors();
    }

    @Test
    void unknownKeyIsRejectedWithItsPointer() {
        List<String> errors = errorsOf(Nested.class,
                "{\"name\":\"a\",\"typo\":1,\"entries\":[]}");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("probe.json#/typo: unknown key 'typo'"), errors.get(0));
    }

    @Test
    void unknownKeyDeepInsideCarriesTheFullPointer() {
        String text = "{\"name\":\"a\",\"entries\":[{\"stat\":\"GAP_SIZE\",\"op\":\"MULTIPLY\","
                + "\"base\":1,\"perGate\":0,\"min\":0,\"max\":1,\"oops\":true}]}";
        List<String> errors = errorsOf(Nested.class, text);
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("probe.json#/entries/0/oops: unknown key 'oops'"),
                errors.get(0));
    }

    @Test
    void commentKeysAreIgnored() {
        StrictBinder binder = new StrictBinder("probe.json");
        Nested bound = binder.bind(Nested.class,
                json("{\"_comment\":\"why\",\"_comment_2\":\"more\",\"name\":\"a\"}"));
        assertEquals(List.of(), binder.errors());
        assertEquals("a", bound.name());
    }

    @Test
    void badEnumNamesTheValidConstants() {
        List<String> errors = errorsOf(Nested.class, "{\"name\":\"a\",\"stat\":\"GRAVITYY\"}");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("probe.json#/stat: not a valid StatId: 'GRAVITYY'"),
                errors.get(0));
        assertTrue(errors.get(0).contains("GRAVITY"), errors.get(0));
    }

    @Test
    void enumsMapCaseInsensitively() {
        StrictBinder binder = new StrictBinder("probe.json");
        UnlockConditionDef bound = binder.bind(UnlockConditionDef.class,
                json("{\"type\":\"any_of\",\"conditions\":[{\"type\":\"world_cleared\","
                        + "\"id\":\"void\"}]}"));
        assertEquals(List.of(), binder.errors());
        assertEquals(UnlockType.ANY_OF, bound.type());
        assertEquals(UnlockType.WORLD_CLEARED, bound.conditions().get(0).type());
        assertEquals("void", bound.conditions().get(0).id());
    }

    @Test
    void nestedListsAndMapsBind() {
        String text = "{\"name\":\"a\",\"count\":3,\"ratio\":0.5,\"flag\":true,"
                + "\"stats\":{\"GRAVITY\":1800,\"FLAP_VELOCITY\":405},"
                + "\"entries\":[{\"stat\":\"MOVING_CHANCE\",\"op\":\"FLAT_ADD\",\"base\":0.05,"
                + "\"perGate\":0.05,\"min\":0,\"max\":1}]}";
        StrictBinder binder = new StrictBinder("probe.json");
        Nested bound = binder.bind(Nested.class, json(text));
        assertEquals(List.of(), binder.errors());
        assertEquals(3, bound.count());
        assertEquals(0.5, bound.ratio());
        assertTrue(bound.flag());
        assertEquals(Map.of(StatId.GRAVITY, 1800.0, StatId.FLAP_VELOCITY, 405.0), bound.stats());
        assertEquals(1, bound.entries().size());
        assertEquals(StatOp.FLAT_ADD, bound.entries().get(0).op());
        assertEquals(0.05, bound.entries().get(0).perGate());
    }

    @Test
    void missingListsAndMapsBecomeEmptyAndMissingObjectsBecomeNull() {
        StrictBinder binder = new StrictBinder("probe.json");
        Nested bound = binder.bind(Nested.class, json("{\"name\":\"a\"}"));
        assertEquals(List.of(), binder.errors());
        assertEquals(List.of(), bound.entries());
        assertEquals(Map.of(), bound.stats());
        assertNull(bound.unlock());
        assertNull(bound.stat());
        assertEquals(0, bound.count());
        assertEquals(0.0, bound.ratio());
    }

    @Test
    void numberPolicyKeepsWholeNumbersExact() {
        // 2^53 + 1 cannot round-trip through a double; LONG_OR_DOUBLE keeps it a long.
        StrictBinder binder = new StrictBinder("probe.json");
        Nested bound = binder.bind(Nested.class, json("{\"name\":\"a\",\"ratio\":1e3}"));
        assertEquals(List.of(), binder.errors());
        assertEquals(1000.0, bound.ratio());
        assertEquals(9007199254740993L, StrictBinder.longOrDouble("9007199254740993"));
        assertEquals(0.05, StrictBinder.longOrDouble("0.05"));
        assertEquals(1.0e300, StrictBinder.longOrDouble("1e300"));
        assertEquals(1.0e30, StrictBinder.longOrDouble("1000000000000000000000000000000"));
    }

    @Test
    void fractionalValueInAnIntComponentIsRejected() {
        List<String> errors = errorsOf(Nested.class, "{\"name\":\"a\",\"count\":2.5}");
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("probe.json#/count: expected a whole number"),
                errors.get(0));
    }

    @Test
    void typeMismatchesAreRejected() {
        assertTrue(errorsOf(Nested.class, "{\"name\":7}").get(0).contains("expected a string"));
        assertTrue(errorsOf(Nested.class, "{\"name\":\"a\",\"ratio\":\"fast\"}").get(0)
                .contains("expected a number"));
        assertTrue(errorsOf(Nested.class, "{\"name\":\"a\",\"entries\":{}}").get(0)
                .contains("expected an array"));
        assertTrue(errorsOf(Nested.class, "{\"name\":\"a\",\"unlock\":[]}").get(0)
                .contains("expected an object"));
    }

    @Test
    void compactConstructorFailuresAreReportedAtTheRecordPointer() {
        String text = "[{\"id\":\"classic\",\"archetype\":\"BALANCED\",\"shape\":\"balanced\","
                + "\"hitbox\":{\"w\":0,\"h\":31,\"ox\":-17,\"oy\":-12},"
                + "\"unlock\":{\"type\":\"default\"}}]";
        StrictBinder binder = new StrictBinder("birds.json");
        binder.bindList(BirdDef.class, ContentLoader.parse("birds", text));
        List<String> errors = binder.errors();
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("birds.json#/0/hitbox: invalid HitboxDef"),
                errors.get(0));
    }

    @Test
    void missingRequiredValueIsReportedAtTheRecordPointer() {
        StrictBinder binder = new StrictBinder("birds.json");
        binder.bindList(BirdDef.class, ContentLoader.parse("birds", "[{\"id\":\"classic\"}]"));
        List<String> errors = binder.errors();
        assertEquals(1, errors.size(), errors.toString());
        assertTrue(errors.get(0).startsWith("birds.json#/0: missing required value in BirdDef"),
                errors.get(0));
    }

    @Test
    void jsonNameMapsTheDefaultKeyword() {
        StrictBinder binder = new StrictBinder("difficulty.json");
        TierDef bound = binder.bind(TierDef.class, json("{\"id\":\"normal\",\"default\":true,"
                + "\"rewardMult\":1.0,\"unlock\":{\"type\":\"default\"}}"));
        assertEquals(List.of(), binder.errors());
        assertTrue(bound.defaultTier());
        assertEquals("normal", bound.id());
    }

    @Test
    void nullValuesFallBackToTheMissingDefaults() {
        StrictBinder binder = new StrictBinder("difficulty.json");
        DifficultyDef bound = binder.bind(DifficultyDef.class,
                json("{\"curves\":{},\"speedRampPerTick\":0.0005,\"tiers\":[],"
                        + "\"tierGenerator\":null}"));
        assertEquals(List.of(), binder.errors());
        assertNull(bound.tierGenerator());
        assertEquals(0.0005, bound.speedRampPerTick());
    }

    @Test
    void ruleFlagListsBind() {
        StrictBinder binder = new StrictBinder("difficulty.json");
        TierDef bound = binder.bind(TierDef.class,
                json("{\"id\":\"nightmare\",\"flags\":[\"ALL_OBSTACLES_MOVE\",\"LETHAL_CEILING\"],"
                        + "\"rewardMult\":2.5,\"unlock\":{\"type\":\"default\"}}"));
        assertEquals(List.of(), binder.errors());
        assertEquals(List.of(RuleFlag.ALL_OBSTACLES_MOVE, RuleFlag.LETHAL_CEILING), bound.flags());
    }

    @Test
    void checkRaisesEveryErrorAtOnce() {
        StrictBinder binder = new StrictBinder("probe.json");
        binder.bind(Nested.class, json("{\"typo\":1,\"other\":2,\"stat\":\"NOPE\"}"));
        ContentException e = assertThrows(ContentException.class, binder::check);
        assertEquals(3, e.errors().size(), e.getMessage());
        assertTrue(e.getMessage().contains("Failed to bind probe.json"), e.getMessage());
    }

    @Test
    void aNonArrayRootIsRejected() {
        StrictBinder binder = new StrictBinder("birds.json");
        assertEquals(List.of(), binder.bindList(BirdDef.class, json("{}")));
        assertTrue(binder.errors().get(0).startsWith("birds.json#: expected an array of BirdDef"),
                binder.errors().toString());
    }
}
