package dev.emkacz.sculk.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Factory for the canonical empty player profile. Centralised here so the
 * profile schema is testable without spinning up a Bukkit/Spigot runtime
 * (the {@code Sculk} plugin class itself cannot be loaded in a unit test
 * because its parent {@code JavaPlugin} is on the {@code compileOnly} scope).
 */
public final class PlayerProfile {

    private PlayerProfile() {}

    /**
     * Build a fresh empty profile with every canonical field present.
     * Two calls return independent instances — no shared mutable state.
     */
    public static JsonObject empty() {
        JsonObject p = new JsonObject();
        p.add("history", new JsonArray());
        p.add("facts", new JsonArray());
        p.add("landmarks", new JsonObject());
        p.addProperty("affection", 0);
        p.addProperty("last_affection_gain", 0L);
        p.add("active_quest", com.google.gson.JsonNull.INSTANCE);
        return p;
    }

    /**
     * Backfill any missing canonical fields on a profile loaded from disk.
     * Used after a schema upgrade so old profiles gain the new fields with
     * safe defaults.
     */
    public static void ensureShape(JsonObject p) {
        if (!p.has("history")) p.add("history", new JsonArray());
        if (!p.has("facts")) p.add("facts", new JsonArray());
        if (!p.has("landmarks")) p.add("landmarks", new JsonObject());
        if (!p.has("affection")) p.addProperty("affection", 0);
        if (!p.has("last_affection_gain")) p.addProperty("last_affection_gain", 0L);
        if (!p.has("active_quest")) p.add("active_quest", com.google.gson.JsonNull.INSTANCE);
    }
}
