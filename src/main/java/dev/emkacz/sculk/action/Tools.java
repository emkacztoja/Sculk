package dev.emkacz.sculk.action;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emkacz.sculk.Sculk;
import dev.emkacz.sculk.action.ToolDefinition.ParameterSpec;
import dev.emkacz.sculk.util.AffectionCooldown;
import dev.emkacz.sculk.util.CommandPrefixMatcher;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Static registry of every AI-callable tool. To add a new tool, append a
 * single {@link ToolDefinition} to {@link #REGISTRY} — that's the whole
 * patch. The schema and the executor cannot drift apart because they live
 * in the same record.
 */
public final class Tools {

    private Tools() {}

    // ----------------------- Helpers -----------------------

    private static JsonObject ok(String message) {
        JsonObject r = new JsonObject();
        r.addProperty("success", true);
        r.addProperty("message", message);
        return r;
    }

    private static JsonObject fail(String error) {
        JsonObject r = new JsonObject();
        r.addProperty("success", false);
        r.addProperty("error", error);
        return r;
    }

    private static int readAffection(ToolContext ctx) {
        JsonObject p = ctx.playerProfile();
        return p != null && p.has("affection") ? p.get("affection").getAsInt() : 0;
    }

    private static long readLastAffectionGain(ToolContext ctx) {
        JsonObject p = ctx.playerProfile();
        return p != null && p.has("last_affection_gain") ? p.get("last_affection_gain").getAsLong() : 0L;
    }

    private static JsonObject refuseIfBelowAffection(ToolContext ctx, String toolName, int defaultThreshold) {
        int required = ctx.threshold(toolName, defaultThreshold);
        int current = readAffection(ctx);
        if (current < required) {
            return fail("Your affection level with the player (" + current + ") is too low (requires "
                    + required + "+). You must refuse to use " + toolName + ".");
        }
        return null;
    }

    private static boolean targetIsProtected(ToolContext ctx, Player target) {
        if (target == null) return false;
        if (target.getUniqueId().equals(ctx.player().getUniqueId())) {
            ctx.sendLocalized("cannot-target-self");
            return true;
        }
        if (target.hasPermission("sculk.immune")) {
            ctx.sendLocalized("target-protected");
            return true;
        }
        return false;
    }

    // ----------------------- Registry -----------------------

    public static final List<ToolDefinition> REGISTRY = List.of(
            // ---- heal_player -------------------------------------------------
            new ToolDefinition(
                    "heal_player",
                    "Restores the player to full health and feeds them fully. Requires affection 30+.",
                    Map.of(),
                    List.of(),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "heal_player", 30);
                        if (refusal != null) return refusal;
                        Player p = ctx.player();
                        p.setHealth(p.getMaxHealth());
                        p.setFoodLevel(20);
                        ctx.sendLocalized("heal-success-player");
                        return ok("Player has been fully healed and fed.");
                    }
            ),

            // ---- apply_potion_effect -----------------------------------------
            new ToolDefinition(
                    "apply_potion_effect",
                    "Applies a potion effect to the player. Requires affection 10+. Example: " +
                            "{\"effect\": \"SPEED\", \"duration\": 60, \"amplifier\": 0} grants Speed I for 60s. " +
                            "Common effects: SPEED, NIGHT_VISION, INVISIBILITY, REGENERATION, JUMP_BOOST, WATER_BREATHING.",
                    Map.of(
                            "effect",    ParameterSpec.string("Potion effect type, e.g. SPEED, NIGHT_VISION, INVISIBILITY, REGENERATION, JUMP_BOOST, WATER_BREATHING."),
                            "duration",  ParameterSpec.integer("Duration in seconds."),
                            "amplifier", ParameterSpec.integer("Effect level - 1 (0 = level I, 1 = level II). Optional, default 0.")
                    ),
                    List.of("effect", "duration"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "apply_potion_effect", 10);
                        if (refusal != null) return refusal;
                        String effectStr = args.get("effect").getAsString().toUpperCase(Locale.ROOT);
                        int duration = args.get("duration").getAsInt();
                        int amp = args.has("amplifier") ? args.get("amplifier").getAsInt() : 0;
                        org.bukkit.potion.PotionEffectType effectType = org.bukkit.potion.PotionEffectType.getByName(effectStr);
                        if (effectType == null) return fail("Invalid potion effect: " + effectStr);
                        ctx.player().addPotionEffect(new org.bukkit.potion.PotionEffect(effectType, duration * 20, amp));
                        ctx.sendLocalized("effect-applied-player", "{effect}", effectStr, "{duration}", String.valueOf(duration));
                        return ok("Applied " + effectStr + " (Level " + (amp + 1) + ") to player for " + duration + "s.");
                    }
            ),

            // ---- play_sound --------------------------------------------------
            new ToolDefinition(
                    "play_sound",
                    "Plays a cosmetic sound at the player's location. Requires affection 0+. " +
                            "Example: {\"sound\": \"ENTITY_WARDEN_ANGRY\", \"volume\": 1.0, \"pitch\": 1.0}. " +
                            "Common sounds: ENTITY_WARDEN_ANGRY, ENTITY_WARDEN_ROAR, BLOCK_SCULK_SENSOR_CLICKING, ENTITY_EXPERIENCE_ORB_PICKUP.",
                    Map.of(
                            "sound",  ParameterSpec.string("Bukkit Sound enum name."),
                            "volume", ParameterSpec.number("Volume (default 1.0)."),
                            "pitch",  ParameterSpec.number("Pitch (default 1.0).")
                    ),
                    List.of("sound"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "play_sound", 0);
                        if (refusal != null) return refusal;
                        String soundStr = args.get("sound").getAsString().toUpperCase(Locale.ROOT);
                        float volume = args.has("volume") ? args.get("volume").getAsFloat() : 1.0f;
                        float pitch = args.has("pitch") ? args.get("pitch").getAsFloat() : 1.0f;
                        try {
                            Sound sound = Sound.valueOf(soundStr);
                            ctx.player().playSound(ctx.player().getLocation(), sound, volume, pitch);
                            return ok("Played sound " + soundStr + " at player's location.");
                        } catch (IllegalArgumentException e) {
                            return fail("Invalid sound name: " + soundStr);
                        }
                    }
            ),

            // ---- spawn_particles ---------------------------------------------
            new ToolDefinition(
                    "spawn_particles",
                    "Spawns a cosmetic particle burst around the player. Requires affection 0+. " +
                            "Example: {\"particle\": \"SCULK_SOUL\", \"count\": 30}. Common: SCULK_SOUL, SCULK_CHARGE, SOUL_FIRE_FLAME, HEART, DRAGON_BREATH.",
                    Map.of(
                            "particle", ParameterSpec.string("Bukkit Particle enum name."),
                            "count",    ParameterSpec.integer("Particle count (default 15).")
                    ),
                    List.of("particle"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "spawn_particles", 0);
                        if (refusal != null) return refusal;
                        String particleStr = args.get("particle").getAsString().toUpperCase(Locale.ROOT);
                        int count = args.has("count") ? args.get("count").getAsInt() : 15;
                        try {
                            Particle particle = Particle.valueOf(particleStr);
                            ctx.player().getWorld().spawnParticle(particle, ctx.player().getLocation().add(0, 1.0, 0), count, 0.5, 0.5, 0.5, 0.05);
                            return ok("Spawned " + count + " particles of type " + particleStr + ".");
                        } catch (IllegalArgumentException e) {
                            return fail("Invalid particle name: " + particleStr);
                        }
                    }
            ),

            // ---- execute_console_command -------------------------------------
            new ToolDefinition(
                    "execute_console_command",
                    "Executes a console command related to the player. Requires affection 0+. " +
                            "IMPORTANT: only commands whose prefix matches an entry in actions.allowed-commands are permitted. " +
                            "Use %player% as a placeholder for the player's name. " +
                            "Example: {\"command\": \"give %player% cookie 1\"} if 'give %player%' is whitelisted.",
                    Map.of("command", ParameterSpec.string("The console command to execute. Use %player% for the player's name.")),
                    List.of("command"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "execute_console_command", 0);
                        if (refusal != null) return refusal;
                        String cmd = args.get("command").getAsString().trim();
                        if (cmd.isEmpty()) return fail("Empty command.");
                        List<String> allowed = ctx.plugin().getConfig().getStringList("actions.allowed-commands");
                        CommandPrefixMatcher matcher = new CommandPrefixMatcher(allowed);
                        if (!matcher.isAllowed(cmd, ctx.player().getName())) {
                            ctx.plugin().getLogger().warning("Blocked unauthorized AI command execution: " + cmd);
                            return fail("Command prefix is not whitelisted: " + cmd);
                        }
                        String finalCmd = cmd.replace("%player%", ctx.player().getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                        return ok("Executed console command: " + finalCmd);
                    }
            ),

            // ---- kick_player (gated by sculk.sudo) ---------------------------
            new ToolDefinition(
                    "kick_player",
                    "Kicks a player from the server. Requires affection 0+ AND sculk.sudo permission. " +
                            "You CANNOT kick the player who triggered this action, nor anyone with the sculk.immune permission. " +
                            "Example: {\"player_name\": \"griefer123\", \"reason\": \"stop breaking the spawn rules\"}.",
                    Map.of(
                            "player_name", ParameterSpec.string("Exact username of the player to kick."),
                            "reason",      ParameterSpec.string("Reason shown in the kick screen. Optional.")
                    ),
                    List.of("player_name"),
                    p -> p.hasPermission("sculk.sudo"),
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "kick_player", 0);
                        if (refusal != null) return refusal;
                        if (!ctx.player().hasPermission("sculk.sudo")) {
                            return fail("You do not have permission to execute this tool.");
                        }
                        String targetName = args.get("player_name").getAsString();
                        Player target = Bukkit.getPlayerExact(targetName);
                        if (target == null) return fail("Player '" + targetName + "' is not online.");
                        if (targetIsProtected(ctx, target)) {
                            return fail("Target player is protected or is the triggerer.");
                        }
                        String reason = args.has("reason") ? args.get("reason").getAsString() : "Kicked by Sculk AI.";
                        target.kickPlayer("Kicked by Sculk: " + reason);
                        return ok("Successfully kicked player " + target.getName() + ".");
                    }
            ),

            // ---- teleport_player (gated by sculk.sudo.teleport) --------------
            new ToolDefinition(
                    "teleport_player",
                    "Teleports a player to another online player. Requires affection 0+ AND sculk.sudo.teleport permission. " +
                            "You CANNOT teleport the player who triggered this action, nor anyone with sculk.immune. " +
                            "Example: {\"player_name\": \"lost_player\", \"target_name\": \"helper_alice\"}.",
                    Map.of(
                            "player_name", ParameterSpec.string("Name of the player to teleport."),
                            "target_name", ParameterSpec.string("Name of the destination player.")
                    ),
                    List.of("player_name", "target_name"),
                    p -> p.hasPermission("sculk.sudo.teleport"),
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "teleport_player", 0);
                        if (refusal != null) return refusal;
                        if (!ctx.player().hasPermission("sculk.sudo.teleport")) {
                            return fail("You do not have permission to execute this tool.");
                        }
                        Player tpPlayer = Bukkit.getPlayerExact(args.get("player_name").getAsString());
                        if (tpPlayer == null) return fail("Player '" + args.get("player_name").getAsString() + "' is not online.");
                        if (targetIsProtected(ctx, tpPlayer)) {
                            return fail("Target player is protected or is the triggerer.");
                        }
                        Player tpTarget = Bukkit.getPlayerExact(args.get("target_name").getAsString());
                        if (tpTarget == null) return fail("Target player '" + args.get("target_name").getAsString() + "' is not online.");
                        tpPlayer.teleport(tpTarget.getLocation());
                        ctx.plugin().adventure().player(tpPlayer).sendMessage(
                                MiniMessage.miniMessage().deserialize(
                                        ctx.plugin().getLanguageManager().getRawMessage("teleported-to-player", tpPlayer)
                                                .replace("{target}", tpTarget.getName())));
                        return ok("Successfully teleported " + tpPlayer.getName() + " to " + tpTarget.getName() + ".");
                    }
            ),

            // ---- get_server_status (gated by sculk.sudo.monitor) -------------
            new ToolDefinition(
                    "get_server_status",
                    "Retrieves server status: TPS, memory usage, loaded chunks, entity counts. " +
                            "Requires affection 0+ AND sculk.sudo.monitor permission. " +
                            "Returns the data as JSON fields; you should summarize the result conversationally.",
                    Map.of(),
                    List.of(),
                    p -> p.hasPermission("sculk.sudo.monitor"),
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "get_server_status", 0);
                        if (refusal != null) return refusal;
                        if (!ctx.player().hasPermission("sculk.sudo.monitor")) {
                            return fail("You do not have permission to execute this tool.");
                        }
                        double tps = 20.0;
                        try {
                            double[] tpsArray = (double[]) Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer());
                            if (tpsArray != null && tpsArray.length > 0) {
                                tps = Math.min(20.0, Math.round(tpsArray[0] * 100.0) / 100.0);
                            }
                        } catch (Exception ignored) {}
                        long freeMem = Runtime.getRuntime().freeMemory();
                        long totalMem = Runtime.getRuntime().totalMemory();
                        long maxMem = Runtime.getRuntime().maxMemory();
                        long usedMem = totalMem - freeMem;
                        int chunks = 0;
                        int entities = 0;
                        for (org.bukkit.World w : Bukkit.getWorlds()) {
                            chunks += w.getLoadedChunks().length;
                            entities += w.getEntities().size();
                        }
                        JsonObject r = ok("Server status retrieved.");
                        r.addProperty("tps", tps);
                        r.addProperty("used_memory_mb", usedMem / (1024 * 1024));
                        r.addProperty("total_memory_mb", totalMem / (1024 * 1024));
                        r.addProperty("max_memory_mb", maxMem / (1024 * 1024));
                        r.addProperty("loaded_chunks", chunks);
                        r.addProperty("entities", entities);
                        return r;
                    }
            ),

            // ---- broadcast_announcement (gated by sculk.sudo.broadcast) -----
            new ToolDefinition(
                    "broadcast_announcement",
                    "Broadcasts a server-wide announcement. Requires affection 0+ AND sculk.sudo.broadcast permission. " +
                            "Keep the message short and important. Example: {\"message\": \"Server restart in 5 minutes!\"}.",
                    Map.of("message", ParameterSpec.string("The announcement text. MiniMessage tags are NOT supported in announcements; they will be escaped.")),
                    List.of("message"),
                    p -> p.hasPermission("sculk.sudo.broadcast"),
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "broadcast_announcement", 0);
                        if (refusal != null) return refusal;
                        if (!ctx.player().hasPermission("sculk.sudo.broadcast")) {
                            return fail("You do not have permission to execute this tool.");
                        }
                        String annMsg = args.get("message").getAsString();
                        String escaped = MiniMessage.miniMessage().escapeTags(annMsg);
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            String template = ctx.plugin().getLanguageManager().getRawMessage("broadcast-announcement-header", p);
                            ctx.plugin().adventure().player(p).sendMessage(
                                    MiniMessage.miniMessage().deserialize(template.replace("{message}", escaped)));
                        }
                        return ok("Broadcasted announcement: " + annMsg);
                    }
            ),

            // ---- remember_player_fact (no affection gate) --------------------
            new ToolDefinition(
                    "remember_player_fact",
                    "Saves a long-term fact about the player. No affection gate. " +
                            "Example: {\"fact\": \"Player's base is at X=100, Y=64, Z=-200 in the mountain biome.\"}. " +
                            "Use sparingly - the fact list is injected into every future conversation.",
                    Map.of("fact", ParameterSpec.string("A single-sentence fact to remember.")),
                    List.of("fact"),
                    null,
                    (ctx, args) -> {
                        String fact = args.get("fact").getAsString();
                        JsonObject p = ctx.playerProfile();
                        if (!p.has("facts")) p.add("facts", new JsonArray());
                        p.getAsJsonArray("facts").add(fact);
                        ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());
                        return ok("Successfully remembered player fact: " + fact);
                    }
            ),

            // ---- save_landmark -----------------------------------------------
            new ToolDefinition(
                    "save_landmark",
                    "Saves the player's current location as a named landmark. Requires affection 0+. " +
                            "Landmarks persist across sessions. Example: {\"name\": \"base\"}.",
                    Map.of("name", ParameterSpec.string("Short alphanumeric lowercase landmark name.")),
                    List.of("name"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "save_landmark", 0);
                        if (refusal != null) return refusal;
                        String name = args.get("name").getAsString().toLowerCase(Locale.ROOT).trim();
                        JsonObject p = ctx.playerProfile();
                        if (!p.has("landmarks")) p.add("landmarks", new JsonObject());
                        JsonObject loc = new JsonObject();
                        loc.addProperty("world", ctx.player().getWorld().getName());
                        loc.addProperty("x", ctx.player().getLocation().getX());
                        loc.addProperty("y", ctx.player().getLocation().getY());
                        loc.addProperty("z", ctx.player().getLocation().getZ());
                        p.getAsJsonObject("landmarks").add(name, loc);
                        ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());
                        ctx.sendLocalized("landmark-saved", "{name}", name);
                        return ok("Successfully saved landmark '" + name + "'.");
                    }
            ),

            // ---- teleport_to_landmark ----------------------------------------
            new ToolDefinition(
                    "teleport_to_landmark",
                    "Teleports the player to a previously saved landmark. Requires affection 0+. " +
                            "Example: {\"name\": \"base\"}.",
                    Map.of("name", ParameterSpec.string("Landmark name (case-insensitive).")),
                    List.of("name"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "teleport_to_landmark", 0);
                        if (refusal != null) return refusal;
                        String name = args.get("name").getAsString().toLowerCase(Locale.ROOT).trim();
                        JsonObject p = ctx.playerProfile();
                        if (!p.has("landmarks") || !p.getAsJsonObject("landmarks").has(name)) {
                            return fail("No landmark named '" + name + "' has been saved. Ask the player to save it first.");
                        }
                        JsonObject loc = p.getAsJsonObject("landmarks").getAsJsonObject(name);
                        org.bukkit.World world = Bukkit.getWorld(loc.get("world").getAsString());
                        if (world == null) return fail("World '" + loc.get("world").getAsString() + "' is not loaded.");
                        double x = loc.get("x").getAsDouble();
                        double y = loc.get("y").getAsDouble();
                        double z = loc.get("z").getAsDouble();
                        ctx.player().teleport(new org.bukkit.Location(world, x, y, z));
                        ctx.sendLocalized("teleported-to-landmark", "{name}", name);
                        return ok("Teleported to landmark '" + name + "'.");
                    }
            ),

            // ---- modify_relationship (no affection gate) ---------------------
            new ToolDefinition(
                    "modify_relationship",
                    "Modifies the player's affection score. No affection gate (the whole point is to change it). " +
                            "Use bypass_cooldown=true ONLY for items in the 'Precious' or 'Legendary' tier of the sacrifice table (in lore). " +
                            "Example: {\"points\": 5, \"reason\": \"nice chat\", \"bypass_cooldown\": false}.",
                    Map.of(
                            "points",          ParameterSpec.integer("Points to add (positive) or subtract (negative). Clamped to [-100, +100] overall."),
                            "reason",          ParameterSpec.string("Why you're changing the score."),
                            "bypass_cooldown", ParameterSpec.boolean_("Set true only for high-value offerings or major deeds. The sacrifice value table is in lore.")
                    ),
                    List.of("points", "reason", "bypass_cooldown"),
                    null,
                    (ctx, args) -> {
                        if (!args.has("points")) return fail("Missing parameter: points");
                        int points = args.get("points").getAsInt();
                        String reason = args.has("reason") ? args.get("reason").getAsString() : "";
                        boolean bypass = args.has("bypass_cooldown") && args.get("bypass_cooldown").getAsBoolean();

                        int current = readAffection(ctx);
                        long lastGain = readLastAffectionGain(ctx);
                        long cooldownMs = ctx.plugin().getConfig().getLong("api.positive-affection-cooldown-seconds",
                                AffectionCooldown.DEFAULT_COOLDOWN_MS / 1000L) * 1000L;

                        if (!AffectionCooldown.isPositiveChangeAllowed(points, bypass, lastGain, System.currentTimeMillis(), cooldownMs)) {
                            long remaining = AffectionCooldown.remainingSeconds(lastGain, System.currentTimeMillis(), cooldownMs);
                            ctx.sendLocalized("relationship-no-change", "{remaining}", String.valueOf(remaining));
                            return fail("Affection increase of +" + points + " blocked by cooldown (" + remaining + "s remaining).");
                        }

                        JsonObject p = ctx.playerProfile();
                        int newAffection = AffectionCooldown.clamp(current + points);
                        p.addProperty("affection", newAffection);
                        if (points > 0) {
                            p.addProperty("last_affection_gain", System.currentTimeMillis());
                        }
                        String key = points > 0 ? "relationship-increased" : "relationship-decreased";
                        String pointsText = points > 0 ? ("+" + points) : String.valueOf(points);
                        ctx.sendLocalized(key, "{points}", pointsText, "{affection}", String.valueOf(newAffection));
                        ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());

                        JsonObject r = ok("Relationship updated by " + pointsText + " (Reason: " + reason + "). New affection: " + newAffection);
                        r.addProperty("affection", newAffection);
                        return r;
                    }
            ),

            // ---- gift_item_to_player -----------------------------------------
            new ToolDefinition(
                    "gift_item_to_player",
                    "Gifts an item to the player by dropping it at their feet. Requires affection 20+. " +
                            "Example: {\"item_type\": \"DIAMOND\", \"amount\": 3}.",
                    Map.of(
                            "item_type", ParameterSpec.string("Bukkit Material name, e.g. DIAMOND, BREAD, GOLD_INGOT."),
                            "amount",    ParameterSpec.integer("Amount, 1-64.")
                    ),
                    List.of("item_type", "amount"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "gift_item_to_player", 20);
                        if (refusal != null) return refusal;
                        if (!args.has("item_type") || !args.has("amount")) {
                            return fail("Missing required parameters: item_type and/or amount");
                        }
                        String materialStr = args.get("item_type").getAsString().toUpperCase(Locale.ROOT);
                        int amount = Math.max(1, Math.min(64, args.get("amount").getAsInt()));
                        Material mat = Material.getMaterial(materialStr);
                        if (mat == null || mat == Material.AIR) return fail("Invalid item type/Material: " + materialStr);
                        ctx.player().getWorld().dropItemNaturally(ctx.player().getLocation(), new ItemStack(mat, amount));
                        ctx.sendLocalized("gift-received", "{amount}", String.valueOf(amount), "{item}", mat.name());
                        return ok("Gifted " + amount + "x " + mat.name() + " to player.");
                    }
            ),

            // ---- sacrifice_held_item (transactional) -------------------------
            new ToolDefinition(
                    "sacrifice_held_item",
                    "Consumes exactly 1 item held in the player's main hand AND modifies affection. " +
                            "TRANSACTIONAL: if the affection change is blocked by cooldown, the item is NOT consumed. " +
                            "Consult the sacrifice value table in the lore for point values. " +
                            "Example: {\"points\": 12, \"reason\": \"sacrificed a diamond\", \"bypass_cooldown\": true}.",
                    Map.of(
                            "points",          ParameterSpec.integer("Affection change for the sacrifice."),
                            "reason",          ParameterSpec.string("Why you're accepting/rejecting this offering."),
                            "bypass_cooldown", ParameterSpec.boolean_("True only for Precious or Legendary tier items (per lore table).")
                    ),
                    List.of("points", "bypass_cooldown"),
                    null,
                    (ctx, args) -> {
                        ItemStack hand = ctx.player().getInventory().getItemInMainHand();
                        if (hand == null || hand.getType() == Material.AIR) {
                            return fail("Player's main hand is empty. They must hold an item to sacrifice.");
                        }
                        if (!args.has("points") || !args.has("bypass_cooldown")) {
                            return fail("Missing parameters: points and/or bypass_cooldown");
                        }
                        int points = args.get("points").getAsInt();
                        String reason = args.has("reason") ? args.get("reason").getAsString() : "";
                        boolean bypass = args.get("bypass_cooldown").getAsBoolean();

                        int current = readAffection(ctx);
                        long lastGain = readLastAffectionGain(ctx);
                        long cooldownMs = ctx.plugin().getConfig().getLong("api.positive-affection-cooldown-seconds",
                                AffectionCooldown.DEFAULT_COOLDOWN_MS / 1000L) * 1000L;

                        if (!AffectionCooldown.isPositiveChangeAllowed(points, bypass, lastGain, System.currentTimeMillis(), cooldownMs)) {
                            long remaining = AffectionCooldown.remainingSeconds(lastGain, System.currentTimeMillis(), cooldownMs);
                            ctx.sendLocalized("relationship-no-change", "{remaining}", String.valueOf(remaining));
                            return fail("Affection increase blocked by cooldown (" + remaining + "s remaining). The item was NOT consumed.");
                        }

                        // Transactional: deduct AFTER all checks pass
                        Material material = hand.getType();
                        int newAmount = hand.getAmount() - 1;
                        if (newAmount <= 0) {
                            ctx.player().getInventory().setItemInMainHand(null);
                        } else {
                            hand.setAmount(newAmount);
                        }
                        try {
                            ctx.player().playSound(ctx.player().getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.5f);
                            ctx.player().getWorld().spawnParticle(Particle.SCULK_CHARGE, ctx.player().getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.02);
                        } catch (Exception ignored) {}
                        ctx.sendLocalized("sacrifice-consumed", "{item}", material.name());

                        int newAffection = AffectionCooldown.clamp(current + points);
                        JsonObject p = ctx.playerProfile();
                        p.addProperty("affection", newAffection);
                        if (points > 0) p.addProperty("last_affection_gain", System.currentTimeMillis());
                        String key = points > 0 ? "relationship-increased" : "relationship-decreased";
                        String pointsText = points > 0 ? ("+" + points) : String.valueOf(points);
                        ctx.sendLocalized(key, "{points}", pointsText, "{affection}", String.valueOf(newAffection));
                        ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());

                        JsonObject r = ok("Consumed 1x " + material.name() + " and updated relationship by " + pointsText + " (new affection: " + newAffection + ").");
                        r.addProperty("item_type", material.name());
                        r.addProperty("affection", newAffection);
                        return r;
                    }
            ),

            // ---- start_quest -------------------------------------------------
            new ToolDefinition(
                    "start_quest",
                    "Assigns a new quest. Only one quest can be active per player. Requires affection 0+. " +
                            "For KILL_MOB, target is an EntityType name (ZOMBIE, SPIDER, ...). " +
                            "For COLLECT_ITEM, target is a Material name (COAL, DIAMOND, ...). " +
                            "Example: {\"type\": \"KILL_MOB\", \"target\": \"ZOMBIE\", \"target_amount\": 10, \"description\": \"Hunt 10 zombies in the deep dark.\"}.",
                    Map.of(
                            "type",          ParameterSpec.string("Quest type: KILL_MOB or COLLECT_ITEM."),
                            "target",        ParameterSpec.string("EntityType name (for KILL_MOB) or Material name (for COLLECT_ITEM)."),
                            "target_amount", ParameterSpec.integer("Number of mobs/items required (must be > 0)."),
                            "description",   ParameterSpec.string("Short narrative description of the quest.")
                    ),
                    List.of("type", "target", "target_amount", "description"),
                    null,
                    (ctx, args) -> {
                        JsonObject refusal = refuseIfBelowAffection(ctx, "start_quest", 0);
                        if (refusal != null) return refusal;
                        if (!args.has("type") || !args.has("target") || !args.has("target_amount") || !args.has("description")) {
                            return fail("Missing required parameters: type, target, target_amount, or description");
                        }
                        String questType = args.get("type").getAsString().toUpperCase(Locale.ROOT);
                        String target = args.get("target").getAsString().toUpperCase(Locale.ROOT);
                        int targetAmount = args.get("target_amount").getAsInt();
                        String description = args.get("description").getAsString();

                        if (!"KILL_MOB".equals(questType) && !"COLLECT_ITEM".equals(questType)) {
                            return fail("Invalid quest type. Must be 'KILL_MOB' or 'COLLECT_ITEM'.");
                        }
                        if (targetAmount <= 0) return fail("Target amount must be greater than 0.");
                        if ("KILL_MOB".equals(questType)) {
                            try { EntityType.valueOf(target); }
                            catch (IllegalArgumentException e) { return fail("Invalid EntityType: " + target); }
                        } else {
                            Material mat = Material.getMaterial(target);
                            if (mat == null || mat == Material.AIR) return fail("Invalid Material: " + target);
                        }

                        JsonObject quest = new JsonObject();
                        quest.addProperty("type", questType);
                        quest.addProperty("target", target);
                        quest.addProperty("target_amount", targetAmount);
                        quest.addProperty("current_amount", 0);
                        quest.addProperty("description", description);
                        JsonObject p = ctx.playerProfile();
                        p.add("active_quest", quest);

                        ctx.sendLocalized("new-quest-assigned",
                                "{description}", description,
                                "{target}", target,
                                "{total}", String.valueOf(targetAmount));
                        ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());
                        return ok("Quest assigned: " + description);
                    }
            ),

            // ---- check_quest_status -----------------------------------------
            new ToolDefinition(
                    "check_quest_status",
                    "Checks the player's active quest progress. For COLLECT_ITEM quests, also scans inventory " +
                            "and consumes items if the target amount is met (completing the quest). " +
                            "Returns the current state. No affection gate.",
                    Map.of(),
                    List.of(),
                    null,
                    (ctx, args) -> {
                        JsonObject p = ctx.playerProfile();
                        if (!p.has("active_quest") || p.get("active_quest").isJsonNull()) {
                            return fail("No active quest found.");
                        }
                        JsonObject quest = p.getAsJsonObject("active_quest");
                        String questType = quest.has("type") ? quest.get("type").getAsString() : "";
                        String target = quest.has("target") ? quest.get("target").getAsString() : "";
                        int targetAmount = quest.has("target_amount") ? quest.get("target_amount").getAsInt() : 0;
                        int currentAmount = quest.has("current_amount") ? quest.get("current_amount").getAsInt() : 0;

                        if ("COLLECT_ITEM".equals(questType)) {
                            Material mat = Material.getMaterial(target);
                            if (mat == null) return fail("Invalid Material in quest: " + target);
                            int invCount = 0;
                            for (ItemStack item : ctx.player().getInventory().getContents()) {
                                if (item != null && item.getType() == mat) invCount += item.getAmount();
                            }
                            if (invCount >= targetAmount) {
                                int remaining = targetAmount;
                                ItemStack[] contents = ctx.player().getInventory().getContents();
                                for (int idx = 0; idx < contents.length; idx++) {
                                    ItemStack item = contents[idx];
                                    if (item != null && item.getType() == mat) {
                                        int amt = item.getAmount();
                                        if (amt <= remaining) {
                                            remaining -= amt;
                                            ctx.player().getInventory().setItem(idx, null);
                                        } else {
                                            item.setAmount(amt - remaining);
                                            remaining = 0;
                                        }
                                    }
                                    if (remaining <= 0) break;
                                }
                                currentAmount = targetAmount;
                                quest.addProperty("current_amount", currentAmount);
                                try { ctx.player().playSound(ctx.player().getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f); }
                                catch (Exception ignored) {}
                                ctx.sendLocalized("quest-items-collected");
                                ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());
                            } else {
                                currentAmount = invCount;
                                quest.addProperty("current_amount", currentAmount);
                            }
                        }
                        boolean completed = currentAmount >= targetAmount;
                        JsonObject r = ok(completed ? "Quest complete!" : "Quest progress updated.");
                        r.addProperty("type", questType);
                        r.addProperty("target", target);
                        r.addProperty("target_amount", targetAmount);
                        r.addProperty("current_amount", currentAmount);
                        r.addProperty("completed", completed);
                        return r;
                    }
            ),

            // ---- complete_quest ----------------------------------------------
            new ToolDefinition(
                    "complete_quest",
                    "Clears the player's active quest. Call this after rewarding the player, or when they " +
                            "want to abandon a quest. No affection gate.",
                    Map.of(),
                    List.of(),
                    null,
                    (ctx, args) -> {
                        JsonObject p = ctx.playerProfile();
                        if (!p.has("active_quest") || p.get("active_quest").isJsonNull()) {
                            return fail("No active quest to complete.");
                        }
                        p.remove("active_quest");
                        ctx.plugin().markPlayerProfileDirty(ctx.player().getUniqueId());
                        ctx.sendLocalized("quest-removed");
                        return ok("Active quest cleared.");
                    }
            )
    );
}
