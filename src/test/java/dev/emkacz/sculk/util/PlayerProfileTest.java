package dev.emkacz.sculk.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerProfileTest {

    @Test
    void emptyHasAllCanonicalFields() {
        JsonObject p = PlayerProfile.empty();
        assertTrue(p.has("history"), "missing 'history'");
        assertTrue(p.has("facts"), "missing 'facts'");
        assertTrue(p.has("landmarks"), "missing 'landmarks'");
        assertTrue(p.has("affection"), "missing 'affection'");
        assertTrue(p.has("last_affection_gain"), "missing 'last_affection_gain'");
        assertTrue(p.has("active_quest"), "missing 'active_quest'");
    }

    @Test
    void emptyCollectionsAreEmpty() {
        JsonObject p = PlayerProfile.empty();
        assertEquals(0, p.getAsJsonArray("history").size());
        assertEquals(0, p.getAsJsonArray("facts").size());
        assertEquals(0, p.getAsJsonObject("landmarks").size());
    }

    @Test
    void emptyAffectionIsZero() {
        JsonObject p = PlayerProfile.empty();
        assertEquals(0, p.get("affection").getAsInt());
    }

    @Test
    void emptyLastAffectionGainIsZero() {
        JsonObject p = PlayerProfile.empty();
        assertEquals(0L, p.get("last_affection_gain").getAsLong());
    }

    @Test
    void emptyActiveQuestIsNull() {
        JsonObject p = PlayerProfile.empty();
        assertTrue(p.get("active_quest").isJsonNull());
    }

    @Test
    void emptyReturnsIndependentInstances() {
        // Two calls must NOT share mutable state — otherwise mutations leak
        // across players.
        JsonObject a = PlayerProfile.empty();
        JsonObject b = PlayerProfile.empty();
        a.getAsJsonArray("facts").add("secret");
        assertEquals(0, b.getAsJsonArray("facts").size(),
                "empty() must return a fresh instance every time");
    }

    @Test
    void mutatingCollectionsDoesNotCorruptDefaults() {
        JsonObject p = PlayerProfile.empty();
        p.getAsJsonArray("history").add("a message");
        p.getAsJsonObject("landmarks").addProperty("base", "coords");
        JsonObject p2 = PlayerProfile.empty();
        assertEquals(0, p2.getAsJsonArray("history").size());
        assertEquals(0, p2.getAsJsonObject("landmarks").size());
    }

    @Test
    void canMutateProfileCollections() {
        JsonObject p = PlayerProfile.empty();
        JsonArray facts = p.getAsJsonArray("facts");
        facts.add("Player likes diamonds");
        facts.add("Player built a base at 100, 64, -200");
        assertEquals(2, facts.size());

        p.addProperty("affection", 42);
        assertEquals(42, p.get("affection").getAsInt());

        p.getAsJsonObject("landmarks").addProperty("home", "overworld:100:64:-200");
        assertTrue(p.getAsJsonObject("landmarks").has("home"));
    }

    @Test
    void ensureShapeBackfillsMissingFields() {
        // Simulate a profile loaded from an older version that didn't have
        // last_affection_gain or active_quest.
        JsonObject old = new JsonObject();
        old.add("history", new JsonArray());
        old.add("facts", new JsonArray());
        old.add("landmarks", new JsonObject());
        old.addProperty("affection", -25);

        PlayerProfile.ensureShape(old);

        assertTrue(old.has("last_affection_gain"));
        assertEquals(0L, old.get("last_affection_gain").getAsLong());
        assertTrue(old.has("active_quest"));
        assertTrue(old.get("active_quest").isJsonNull());
        // Existing fields are preserved
        assertEquals(-25, old.get("affection").getAsInt());
    }

    @Test
    void ensureShapeLeavesExistingFieldsAlone() {
        // A modern profile should pass through unchanged.
        JsonObject p = PlayerProfile.empty();
        p.addProperty("affection", 77);
        p.addProperty("last_affection_gain", 1718834567890L);

        PlayerProfile.ensureShape(p);

        assertEquals(77, p.get("affection").getAsInt());
        assertEquals(1718834567890L, p.get("last_affection_gain").getAsLong());
    }
}
