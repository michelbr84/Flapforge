package io.github.michelbr84.flapforge.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The forward-compatible overlay rule of E22 and the codec's fixed configuration (D15). */
class JsonCodecTest {

    private static JsonObject parse(String json) {
        JsonObject tree = JsonCodec.parseObject(json);
        assertTrue(tree != null, "fixture must parse: " + json);
        return tree;
    }

    @Test
    void nestedObjectsMergeKeyByKey() {
        JsonObject original = parse("{\"profile\":{\"xp\":5,\"unknownDepth\":{\"a\":1}},"
                + "\"unknownRoot\":true}");
        JsonObject fresh = parse("{\"profile\":{\"xp\":9}}");

        JsonObject merged = JsonCodec.overlay(original, fresh);

        JsonObject profile = merged.getAsJsonObject("profile");
        assertEquals(9, profile.get("xp").getAsInt(), "the fresh value wins");
        assertEquals(1, profile.getAsJsonObject("unknownDepth").get("a").getAsInt(),
                "an unknown nested node survives");
        assertTrue(merged.get("unknownRoot").getAsBoolean(), "an unknown root key survives");
    }

    @Test
    void arraysAreReplacedWholesale() {
        JsonObject original = parse("{\"unlocked\":[\"a\",\"b\",\"c\"]}");
        JsonObject fresh = parse("{\"unlocked\":[\"a\"]}");

        JsonObject merged = JsonCodec.overlay(original, fresh);

        assertEquals(1, merged.getAsJsonArray("unlocked").size(),
                "a removed list entry must not come back");
        assertEquals("a", merged.getAsJsonArray("unlocked").get(0).getAsString());
    }

    @Test
    void declaredMapNodesAreReplacedWholesale() {
        JsonObject original = parse("{\"profile\":{\"xp\":5,\"upgrades\":{\"feather_1\":2}}}");
        JsonObject fresh = parse("{\"profile\":{\"xp\":5,\"upgrades\":{\"talon_1\":1}}}");

        JsonObject merged = JsonCodec.overlay(original, fresh, Set.of("profile.upgrades"));

        JsonObject upgrades = merged.getAsJsonObject("profile").getAsJsonObject("upgrades");
        assertFalse(upgrades.has("feather_1"), "a prestige reset must not be undone by the merge");
        assertEquals(1, upgrades.get("talon_1").getAsInt());
        assertEquals(5, merged.getAsJsonObject("profile").get("xp").getAsInt(),
                "the sibling POJO field still merges");
    }

    @Test
    void aMapPathOnlyMatchesItsOwnDepth() {
        JsonObject original = parse("{\"a\":{\"upgrades\":{\"keep\":1}},"
                + "\"profile\":{\"upgrades\":{\"drop\":1}}}");
        JsonObject fresh = parse("{\"a\":{\"upgrades\":{\"new\":2}},"
                + "\"profile\":{\"upgrades\":{\"new\":2}}}");

        JsonObject merged = JsonCodec.overlay(original, fresh, Set.of("profile.upgrades"));

        assertTrue(merged.getAsJsonObject("a").getAsJsonObject("upgrades").has("keep"),
                "a path that was not declared still merges");
        assertFalse(merged.getAsJsonObject("profile").getAsJsonObject("upgrades").has("drop"));
    }

    @Test
    void aTypeChangeReplacesTheOldValue() {
        JsonObject original = parse("{\"node\":{\"a\":1}}");
        JsonObject fresh = parse("{\"node\":[1,2]}");

        assertTrue(JsonCodec.overlay(original, fresh).get("node").isJsonArray());
    }

    @Test
    void neitherArgumentIsModified() {
        JsonObject original = parse("{\"profile\":{\"xp\":5}}");
        JsonObject fresh = parse("{\"profile\":{\"xp\":9}}");

        JsonCodec.overlay(original, fresh);

        assertEquals(5, original.getAsJsonObject("profile").get("xp").getAsInt());
        assertEquals(9, fresh.getAsJsonObject("profile").get("xp").getAsInt());
    }

    @Test
    void aMissingOriginalOverlaysOntoNothing() {
        JsonObject fresh = parse("{\"version\":1}");
        assertEquals(1, JsonCodec.overlay(null, fresh).get("version").getAsInt());
    }

    @Test
    void garbageParsesToNullInsteadOfThrowing() {
        assertNull(JsonCodec.parseObject("{oops"));
        assertNull(JsonCodec.parseObject("[1,2]"), "the persisted files are objects");
        assertNull(JsonCodec.parseObject("   "));
        assertNull(JsonCodec.parseObject(null));
    }

    @Test
    void integralNumbersStayLongAndTextIsNotHtmlEscaped() {
        JsonObject tree = parse("{\"big\":9007199254740993,\"text\":\"a<b & c='d'\"}");

        assertEquals(9007199254740993L, tree.get("big").getAsLong());
        String json = JsonCodec.toJson(tree);
        assertTrue(json.contains("a<b & c='d'"), json);
        assertTrue(json.contains("\n"), "the persisted files are pretty-printed for the player");
    }
}
