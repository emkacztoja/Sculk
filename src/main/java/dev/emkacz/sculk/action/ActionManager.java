package dev.emkacz.sculk.action;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.emkacz.sculk.Sculk;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ActionManager {

    private final Sculk plugin;
    private final Gson gson;

    public ActionManager(Sculk plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /**
     * Builds standard OpenAI-compatible tool schemas for in-game actions.
     */
    public JsonArray buildToolsDefinition(Player player) {
        JsonArray tools = new JsonArray();

        // 1. heal_player
        JsonObject healTool = new JsonObject();
        healTool.addProperty("type", "function");
        JsonObject healFunc = new JsonObject();
        healFunc.addProperty("name", "heal_player");
        healFunc.addProperty("description", "Restores the player to full health and feeds them fully.");
        healTool.add("function", healFunc);
        tools.add(healTool);

        // 2. apply_potion_effect
        JsonObject effectTool = new JsonObject();
        effectTool.addProperty("type", "function");
        JsonObject effectFunc = new JsonObject();
        effectFunc.addProperty("name", "apply_potion_effect");
        effectFunc.addProperty("description", "Applies a potion effect to the player.");
        JsonObject effectParams = new JsonObject();
        effectParams.addProperty("type", "object");
        JsonObject effectProps = new JsonObject();
        JsonObject effectArg = new JsonObject();
        effectArg.addProperty("type", "string");
        effectArg.addProperty("description", "The type of potion effect, e.g. SPEED, NIGHT_VISION, INVISIBILITY, REGENERATION, JUMP_BOOST, WATER_BREATHING.");
        effectProps.add("effect", effectArg);
        JsonObject durationArg = new JsonObject();
        durationArg.addProperty("type", "integer");
        durationArg.addProperty("description", "The duration of the effect in seconds.");
        effectProps.add("duration", durationArg);
        JsonObject ampArg = new JsonObject();
        ampArg.addProperty("type", "integer");
        ampArg.addProperty("description", "The amplifier/level of the effect (0 is level I, 1 is level II, etc.).");
        effectProps.add("amplifier", ampArg);
        effectParams.add("properties", effectProps);
        JsonArray effectRequired = new JsonArray();
        effectRequired.add("effect");
        effectRequired.add("duration");
        effectParams.add("required", effectRequired);
        effectFunc.add("parameters", effectParams);
        effectTool.add("function", effectFunc);
        tools.add(effectTool);

        // 3. play_sound
        JsonObject soundTool = new JsonObject();
        soundTool.addProperty("type", "function");
        JsonObject soundFunc = new JsonObject();
        soundFunc.addProperty("name", "play_sound");
        soundFunc.addProperty("description", "Plays a cosmetic sound effect at the player's location.");
        JsonObject soundParams = new JsonObject();
        soundParams.addProperty("type", "object");
        JsonObject soundProps = new JsonObject();
        JsonObject soundArg = new JsonObject();
        soundArg.addProperty("type", "string");
        soundArg.addProperty("description", "The Bukkit sound name, e.g. ENTITY_WARDEN_ANGRY, ENTITY_WARDEN_ROAR, BLOCK_SCULK_SENSOR_CLICKING, ENTITY_EXPERIENCE_ORB_PICKUP.");
        soundProps.add("sound", soundArg);
        JsonObject volArg = new JsonObject();
        volArg.addProperty("type", "number");
        volArg.addProperty("description", "The volume of the sound (default is 1.0).");
        soundProps.add("volume", volArg);
        JsonObject pitchArg = new JsonObject();
        pitchArg.addProperty("type", "number");
        pitchArg.addProperty("description", "The pitch of the sound (default is 1.0).");
        soundProps.add("pitch", pitchArg);
        soundParams.add("properties", soundProps);
        JsonArray soundRequired = new JsonArray();
        soundRequired.add("sound");
        soundParams.add("required", soundRequired);
        soundFunc.add("parameters", soundParams);
        soundTool.add("function", soundFunc);
        tools.add(soundTool);

        // 4. spawn_particles
        JsonObject particleTool = new JsonObject();
        particleTool.addProperty("type", "function");
        JsonObject particleFunc = new JsonObject();
        particleFunc.addProperty("name", "spawn_particles");
        particleFunc.addProperty("description", "Spawns a cosmetic particle burst around the player.");
        JsonObject particleParams = new JsonObject();
        particleParams.addProperty("type", "object");
        JsonObject particleProps = new JsonObject();
        JsonObject particleArg = new JsonObject();
        particleArg.addProperty("type", "string");
        particleArg.addProperty("description", "The Bukkit particle name, e.g. SCULK_SOUL, SCULK_CHARGE, SOUL_FIRE_FLAME, HEART, DRAGON_BREATH.");
        particleProps.add("particle", particleArg);
        JsonObject countArg = new JsonObject();
        countArg.addProperty("type", "integer");
        countArg.addProperty("description", "The number of particles to spawn (default is 15).");
        particleProps.add("count", countArg);
        particleParams.add("properties", particleProps);
        JsonArray particleRequired = new JsonArray();
        particleRequired.add("particle");
        particleParams.add("required", particleRequired);
        particleFunc.add("parameters", particleParams);
        particleTool.add("function", particleFunc);
        tools.add(particleTool);

        // 5. execute_console_command
        JsonObject cmdTool = new JsonObject();
        cmdTool.addProperty("type", "function");
        JsonObject cmdFunc = new JsonObject();
        cmdFunc.addProperty("name", "execute_console_command");
        cmdFunc.addProperty("description", "Executes a console command related to the player.");
        JsonObject cmdParams = new JsonObject();
        cmdParams.addProperty("type", "object");
        JsonObject cmdProps = new JsonObject();
        JsonObject cmdArg = new JsonObject();
        cmdArg.addProperty("type", "string");
        cmdArg.addProperty("description", "The console command to execute. Use '%player%' as a placeholder for the player's name.");
        cmdProps.add("command", cmdArg);
        cmdParams.add("properties", cmdProps);
        JsonArray cmdRequired = new JsonArray();
        cmdRequired.add("command");
        cmdParams.add("required", cmdRequired);
        cmdFunc.add("parameters", cmdParams);
        cmdTool.add("function", cmdFunc);
        tools.add(cmdTool);

        // 6. kick_player (Conditional on sculk.sudo permission)
        if (player.hasPermission("sculk.sudo")) {
            JsonObject kickTool = new JsonObject();
            kickTool.addProperty("type", "function");
            JsonObject kickFunc = new JsonObject();
            kickFunc.addProperty("name", "kick_player");
            kickFunc.addProperty("description", "Kicks a specified player from the server.");
            JsonObject kickParams = new JsonObject();
            kickParams.addProperty("type", "object");
            JsonObject kickProps = new JsonObject();
            
            JsonObject pArg = new JsonObject();
            pArg.addProperty("type", "string");
            pArg.addProperty("description", "The exact username of the player to kick.");
            kickProps.add("player_name", pArg);

            JsonObject reasonArg = new JsonObject();
            reasonArg.addProperty("type", "string");
            reasonArg.addProperty("description", "The reason for the kick.");
            kickProps.add("reason", reasonArg);

            kickParams.add("properties", kickProps);
            JsonArray kickRequired = new JsonArray();
            kickRequired.add("player_name");
            kickParams.add("required", kickRequired);

            kickFunc.add("parameters", kickParams);
            kickTool.add("function", kickFunc);
            tools.add(kickTool);
        }

        // 7. teleport_player (Conditional on sculk.sudo.teleport permission)
        if (player.hasPermission("sculk.sudo.teleport")) {
            JsonObject tpTool = new JsonObject();
            tpTool.addProperty("type", "function");
            JsonObject tpFunc = new JsonObject();
            tpFunc.addProperty("name", "teleport_player");
            tpFunc.addProperty("description", "Teleports a player to another target player.");
            JsonObject tpParams = new JsonObject();
            tpParams.addProperty("type", "object");
            JsonObject tpProps = new JsonObject();
            
            JsonObject pArg = new JsonObject();
            pArg.addProperty("type", "string");
            pArg.addProperty("description", "The name of the player to teleport.");
            tpProps.add("player_name", pArg);

            JsonObject tArg = new JsonObject();
            tArg.addProperty("type", "string");
            tArg.addProperty("description", "The name of the destination player.");
            tpProps.add("target_name", tArg);

            tpParams.add("properties", tpProps);
            JsonArray tpRequired = new JsonArray();
            tpRequired.add("player_name");
            tpRequired.add("target_name");
            tpParams.add("required", tpRequired);

            tpFunc.add("parameters", tpParams);
            tpTool.add("function", tpFunc);
            tools.add(tpTool);
        }

        // 8. get_server_status (Conditional on sculk.sudo.monitor permission)
        if (player.hasPermission("sculk.sudo.monitor")) {
            JsonObject statusTool = new JsonObject();
            statusTool.addProperty("type", "function");
            JsonObject statusFunc = new JsonObject();
            statusFunc.addProperty("name", "get_server_status");
            statusFunc.addProperty("description", "Retrieves server status information including TPS, RAM usage, loaded chunks, and entities.");
            statusTool.add("function", statusFunc);
            tools.add(statusTool);
        }

        // 9. broadcast_announcement (Conditional on sculk.sudo.broadcast permission)
        if (player.hasPermission("sculk.sudo.broadcast")) {
            JsonObject broadcastTool = new JsonObject();
            broadcastTool.addProperty("type", "function");
            JsonObject broadcastFunc = new JsonObject();
            broadcastFunc.addProperty("name", "broadcast_announcement");
            broadcastFunc.addProperty("description", "Broadcasts an important server-wide announcement message to all players.");
            JsonObject broadcastParams = new JsonObject();
            broadcastParams.addProperty("type", "object");
            JsonObject broadcastProps = new JsonObject();
            
            JsonObject msgArg = new JsonObject();
            msgArg.addProperty("type", "string");
            msgArg.addProperty("description", "The message to broadcast to all players on the server.");
            broadcastProps.add("message", msgArg);

            broadcastParams.add("properties", broadcastProps);
            JsonArray broadcastRequired = new JsonArray();
            broadcastRequired.add("message");
            broadcastParams.add("required", broadcastRequired);

            broadcastFunc.add("parameters", broadcastParams);
            broadcastTool.add("function", broadcastFunc);
            tools.add(broadcastTool);
        }

        // 10. remember_player_fact
        JsonObject factTool = new JsonObject();
        factTool.addProperty("type", "function");
        JsonObject factFunc = new JsonObject();
        factFunc.addProperty("name", "remember_player_fact");
        factFunc.addProperty("description", "Saves a new fact or detail about the player into their long-term memory.");
        JsonObject factParams = new JsonObject();
        factParams.addProperty("type", "object");
        JsonObject factProps = new JsonObject();
        JsonObject factArg = new JsonObject();
        factArg.addProperty("type", "string");
        factArg.addProperty("description", "A summary description of the fact to remember (e.g. 'Player built their base in a mountain at 100, 64, -200').");
        factProps.add("fact", factArg);
        factParams.add("properties", factProps);
        JsonArray factRequired = new JsonArray();
        factRequired.add("fact");
        factParams.add("required", factRequired);
        factFunc.add("parameters", factParams);
        factTool.add("function", factFunc);
        tools.add(factTool);

        // 11. save_landmark
        JsonObject saveTool = new JsonObject();
        saveTool.addProperty("type", "function");
        JsonObject saveFunc = new JsonObject();
        saveFunc.addProperty("name", "save_landmark");
        saveFunc.addProperty("description", "Saves the player's current location as a named landmark (e.g. 'baza', 'dom').");
        JsonObject saveParams = new JsonObject();
        saveParams.addProperty("type", "object");
        JsonObject saveProps = new JsonObject();
        JsonObject nameArg = new JsonObject();
        nameArg.addProperty("type", "string");
        nameArg.addProperty("description", "The name of the landmark to save (e.g., 'baza'). Keep it short, alphanumeric, and lowercase.");
        saveProps.add("name", nameArg);
        saveParams.add("properties", saveProps);
        JsonArray saveRequired = new JsonArray();
        saveRequired.add("name");
        saveParams.add("required", saveRequired);
        saveFunc.add("parameters", saveParams);
        saveTool.add("function", saveFunc);
        tools.add(saveTool);

        // 12. teleport_to_landmark
        JsonObject tpLandTool = new JsonObject();
        tpLandTool.addProperty("type", "function");
        JsonObject tpLandFunc = new JsonObject();
        tpLandFunc.addProperty("name", "teleport_to_landmark");
        tpLandFunc.addProperty("description", "Teleports the player to a previously saved landmark.");
        JsonObject tpLandParams = new JsonObject();
        tpLandParams.addProperty("type", "object");
        JsonObject tpLandProps = new JsonObject();
        JsonObject landArg = new JsonObject();
        landArg.addProperty("type", "string");
        landArg.addProperty("description", "The name of the landmark to teleport to.");
        tpLandProps.add("name", landArg);
        tpLandParams.add("properties", tpLandProps);
        JsonArray tpLandRequired = new JsonArray();
        tpLandRequired.add("name");
        tpLandParams.add("required", tpLandRequired);
        tpLandFunc.add("parameters", tpLandParams);
        tpLandTool.add("function", tpLandFunc);
        tools.add(tpLandTool);

        // 13. modify_relationship
        JsonObject modifyRelTool = new JsonObject();
        modifyRelTool.addProperty("type", "function");
        JsonObject modifyRelFunc = new JsonObject();
        modifyRelFunc.addProperty("name", "modify_relationship");
        modifyRelFunc.addProperty("description", "Modifies the player's relationship/affection score towards you.");
        JsonObject modifyRelParams = new JsonObject();
        modifyRelParams.addProperty("type", "object");
        JsonObject modifyRelProps = new JsonObject();
        
        JsonObject pointsArg = new JsonObject();
        pointsArg.addProperty("type", "integer");
        pointsArg.addProperty("description", "The points to add or subtract (e.g. -10 to 10). Clamped between -100 and +100 overall.");
        modifyRelProps.add("points", pointsArg);

        JsonObject reasonArg = new JsonObject();
        reasonArg.addProperty("type", "string");
        reasonArg.addProperty("description", "The reason for the relationship change.");
        modifyRelProps.add("reason", reasonArg);

        JsonObject bypassArg = new JsonObject();
        bypassArg.addProperty("type", "boolean");
        bypassArg.addProperty("description", "Set to true to bypass the 2-minute positive affection gain cooldown (for high-value sacrifices/offerings). Set to false for standard interactions or low-value gifts.");
        modifyRelProps.add("bypass_cooldown", bypassArg);

        modifyRelParams.add("properties", modifyRelProps);
        JsonArray modifyRelRequired = new JsonArray();
        modifyRelRequired.add("points");
        modifyRelRequired.add("reason");
        modifyRelRequired.add("bypass_cooldown");
        modifyRelParams.add("required", modifyRelRequired);
        modifyRelFunc.add("parameters", modifyRelParams);
        modifyRelTool.add("function", modifyRelFunc);
        tools.add(modifyRelTool);

        // 14. gift_item_to_player
        JsonObject giftTool = new JsonObject();
        giftTool.addProperty("type", "function");
        JsonObject giftFunc = new JsonObject();
        giftFunc.addProperty("name", "gift_item_to_player");
        giftFunc.addProperty("description", "Gifts/drops a specified item at the player's current location in-game.");
        JsonObject giftParams = new JsonObject();
        giftParams.addProperty("type", "object");
        JsonObject giftProps = new JsonObject();

        JsonObject itemTypeArg = new JsonObject();
        itemTypeArg.addProperty("type", "string");
        itemTypeArg.addProperty("description", "The Bukkit Material name (e.g. DIAMOND, BREAD, COAL, GOLD_INGOT, RAW_COPPER).");
        giftProps.add("item_type", itemTypeArg);

        JsonObject amountArg = new JsonObject();
        amountArg.addProperty("type", "integer");
        amountArg.addProperty("description", "The amount of the item to gift (1 to 64).");
        giftProps.add("amount", amountArg);

        giftParams.add("properties", giftProps);
        JsonArray giftRequired = new JsonArray();
        giftRequired.add("item_type");
        giftRequired.add("amount");
        giftParams.add("required", giftRequired);
        giftFunc.add("parameters", giftParams);
        giftTool.add("function", giftFunc);
        tools.add(giftTool);

        // 15. sacrifice_held_item
        JsonObject sacrificeTool = new JsonObject();
        sacrificeTool.addProperty("type", "function");
        JsonObject sacrificeFunc = new JsonObject();
        sacrificeFunc.addProperty("name", "sacrifice_held_item");
        sacrificeFunc.addProperty("description", "Consumes/takes exactly 1 item currently held in the player's main hand as a sacrifice, and modifies their affection score. If the affection change is blocked by cooldown, the item is NOT consumed.");
        JsonObject sacrificeParams = new JsonObject();
        sacrificeParams.addProperty("type", "object");
        JsonObject sacrificeProps = new JsonObject();
        
        JsonObject sacPointsArg = new JsonObject();
        sacPointsArg.addProperty("type", "integer");
        sacPointsArg.addProperty("description", "The points to add or subtract (e.g. -10 to 10). Clamped between -100 and +100 overall.");
        sacrificeProps.add("points", sacPointsArg);

        JsonObject sacReasonArg = new JsonObject();
        sacReasonArg.addProperty("type", "string");
        sacReasonArg.addProperty("description", "The reason for the relationship change (e.g. 'Sacrificed a diamond').");
        sacrificeProps.add("reason", sacReasonArg);

        JsonObject sacBypassArg = new JsonObject();
        sacBypassArg.addProperty("type", "boolean");
        sacBypassArg.addProperty("description", "Set to true to bypass the 2-minute cooldown (for high-value items like diamonds, beacons, netherite). Set to false for standard items.");
        sacrificeProps.add("bypass_cooldown", sacBypassArg);

        sacrificeParams.add("properties", sacrificeProps);
        JsonArray sacrificeRequired = new JsonArray();
        sacrificeRequired.add("points");
        sacrificeRequired.add("reason");
        sacrificeRequired.add("bypass_cooldown");
        sacrificeParams.add("required", sacrificeRequired);
        sacrificeFunc.add("parameters", sacrificeParams);
        sacrificeTool.add("function", sacrificeFunc);
        tools.add(sacrificeTool);

        // 16. start_quest
        JsonObject startQuestTool = new JsonObject();
        startQuestTool.addProperty("type", "function");
        JsonObject startQuestFunc = new JsonObject();
        startQuestFunc.addProperty("name", "start_quest");
        startQuestFunc.addProperty("description", "Assigns a new quest to the player. Only one quest can be active at a time.");
        JsonObject startQuestParams = new JsonObject();
        startQuestParams.addProperty("type", "object");
        JsonObject startQuestProps = new JsonObject();

        JsonObject qTypeArg = new JsonObject();
        qTypeArg.addProperty("type", "string");
        qTypeArg.addProperty("description", "The type of the quest: 'KILL_MOB' or 'COLLECT_ITEM'.");
        startQuestProps.add("type", qTypeArg);

        JsonObject qTargetArg = new JsonObject();
        qTargetArg.addProperty("type", "string");
        qTargetArg.addProperty("description", "The target for the quest. If type is KILL_MOB, this is the EntityType name (e.g. ZOMBIE, SPIDER, CREEPER, SKELETON). If type is COLLECT_ITEM, this is the Material name (e.g. RAW_COPPER, COAL, DIAMOND).");
        startQuestProps.add("target", qTargetArg);

        JsonObject qTargetAmtArg = new JsonObject();
        qTargetAmtArg.addProperty("type", "integer");
        qTargetAmtArg.addProperty("description", "The target quantity to kill or collect.");
        startQuestProps.add("target_amount", qTargetAmtArg);

        JsonObject qDescArg = new JsonObject();
        qDescArg.addProperty("type", "string");
        qDescArg.addProperty("description", "A narrative flavor description of the quest for the player.");
        startQuestProps.add("description", qDescArg);

        startQuestParams.add("properties", startQuestProps);
        JsonArray startQuestRequired = new JsonArray();
        startQuestRequired.add("type");
        startQuestRequired.add("target");
        startQuestRequired.add("target_amount");
        startQuestRequired.add("description");
        startQuestParams.add("required", startQuestRequired);
        startQuestFunc.add("parameters", startQuestParams);
        startQuestTool.add("function", startQuestFunc);
        tools.add(startQuestTool);

        // 17. check_quest_status
        JsonObject checkQuestTool = new JsonObject();
        checkQuestTool.addProperty("type", "function");
        JsonObject checkQuestFunc = new JsonObject();
        checkQuestFunc.addProperty("name", "check_quest_status");
        checkQuestFunc.addProperty("description", "Checks/updates the player's active quest progress. For COLLECT_ITEM quests, it scans their inventory and consumes the items if they have enough, completing the quest.");
        checkQuestTool.add("function", checkQuestFunc);
        tools.add(checkQuestTool);

        // 18. complete_quest
        JsonObject completeQuestTool = new JsonObject();
        completeQuestTool.addProperty("type", "function");
        JsonObject completeQuestFunc = new JsonObject();
        completeQuestFunc.addProperty("name", "complete_quest");
        completeQuestFunc.addProperty("description", "Finishes/clears the active quest from the player's profile (usually called after they succeed).");
        completeQuestTool.add("function", completeQuestFunc);
        tools.add(completeQuestTool);

        return tools;
    }

    /**
     * Executes AI-requested tool calls sequentially on the main server thread.
     */
    public CompletableFuture<JsonArray> executeTools(Player player, JsonArray toolCalls, JsonObject playerProfile) {
        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            JsonArray toolResultMessages = new JsonArray();
            try {
                for (int i = 0; i < toolCalls.size(); i++) {
                    JsonObject callObj = toolCalls.get(i).getAsJsonObject();
                    String callId = callObj.get("id").getAsString();
                    JsonObject funcObj = callObj.getAsJsonObject("function");
                    String funcName = funcObj.get("name").getAsString();
                    String argumentsStr = funcObj.get("arguments").getAsString();
                    JsonObject arguments = JsonParser.parseString(argumentsStr).getAsJsonObject();

                    JsonObject resultJson = executeSingleTool(player, funcName, arguments, playerProfile);

                    JsonObject toolMsg = new JsonObject();
                    toolMsg.addProperty("role", "tool");
                    toolMsg.addProperty("tool_call_id", callId);
                    toolMsg.addProperty("content", gson.toJson(resultJson));
                    
                    toolResultMessages.add(toolMsg);
                }
                future.complete(toolResultMessages);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Performs a single action based on tool parameters. Must be run on main thread.
     */
    public JsonObject executeSingleTool(Player player, String name, JsonObject arguments, JsonObject playerProfile) {
        JsonObject response = new JsonObject();
        FileConfiguration config = plugin.getConfig();

        int affection = 0;
        if (playerProfile != null && playerProfile.has("affection")) {
            affection = playerProfile.get("affection").getAsInt();
        }

        if (!config.getBoolean("actions.enable-actions", true)) {
            response.addProperty("success", false);
            response.addProperty("error", "Actions are currently disabled on this server.");
            return response;
        }

        try {
            synchronized (playerProfile) {
                switch (name) {
                    case "heal_player": {
                        if (affection < 30) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 30+). You must refuse to heal them.");
                            break;
                        }
                        player.setHealth(player.getMaxHealth());
                        player.setFoodLevel(20);
                        String msg = plugin.getLanguageManager().getRawMessage("heal-success-player", player);
                        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        response.addProperty("success", true);
                        response.addProperty("message", "Player has been fully healed and fed.");
                        break;
                    }

                    case "apply_potion_effect": {
                        if (affection < 10) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 10+). You must refuse to apply potion effects.");
                            break;
                        }
                        String effectStr = arguments.get("effect").getAsString().toUpperCase();
                        int duration = arguments.get("duration").getAsInt();
                        int amp = arguments.has("amplifier") ? arguments.get("amplifier").getAsInt() : 0;

                        org.bukkit.potion.PotionEffectType effectType = org.bukkit.potion.PotionEffectType.getByName(effectStr);
                        if (effectType == null) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Invalid potion effect: " + effectStr);
                        } else {
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(effectType, duration * 20, amp));
                            String template = plugin.getLanguageManager().getRawMessage("effect-applied-player", player);
                            String msg = template.replace("{effect}", effectStr).replace("{duration}", String.valueOf(duration));
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                            response.addProperty("success", true);
                            response.addProperty("message", "Applied " + effectStr + " (Level " + (amp + 1) + ") to player for " + duration + "s.");
                        }
                        break;
                    }

                    case "play_sound": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to play sound effects.");
                            break;
                        }
                        String soundStr = arguments.get("sound").getAsString().toUpperCase();
                        float volume = arguments.has("volume") ? arguments.get("volume").getAsFloat() : 1.0f;
                        float pitch = arguments.has("pitch") ? arguments.get("pitch").getAsFloat() : 1.0f;

                        try {
                            Sound sound = Sound.valueOf(soundStr);
                            player.playSound(player.getLocation(), sound, volume, pitch);
                            response.addProperty("success", true);
                            response.addProperty("message", "Played sound " + soundStr + " at player's location.");
                        } catch (IllegalArgumentException e) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Invalid sound name: " + soundStr);
                        }
                        break;
                    }

                    case "spawn_particles": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to spawn particles.");
                            break;
                        }
                        String particleStr = arguments.get("particle").getAsString().toUpperCase();
                        int count = arguments.has("count") ? arguments.get("count").getAsInt() : 15;

                        try {
                            Particle particle = Particle.valueOf(particleStr);
                            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1.0, 0), count, 0.5, 0.5, 0.5, 0.05);
                            response.addProperty("success", true);
                            response.addProperty("message", "Spawned " + count + " particles of type " + particleStr + ".");
                        } catch (IllegalArgumentException e) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Invalid particle name: " + particleStr);
                        }
                        break;
                    }

                    case "execute_console_command": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to execute commands.");
                            break;
                        }
                        String cmd = arguments.get("command").getAsString().trim();
                        List<String> allowedCommands = config.getStringList("actions.allowed-commands");
                        boolean allowed = false;
                        for (String prefix : allowedCommands) {
                            String processedPrefix = prefix.replace("%player%", player.getName());
                            String lowerCmd = cmd.toLowerCase();
                            String lowerPrefix = processedPrefix.toLowerCase();
                            String rawLowerPrefix = prefix.toLowerCase();
                            
                            if (lowerCmd.equals(lowerPrefix) || lowerCmd.startsWith(lowerPrefix + " ") || 
                                lowerCmd.equals(rawLowerPrefix) || lowerCmd.startsWith(rawLowerPrefix + " ")) {
                                allowed = true;
                                break;
                            }
                        }

                        if (allowed) {
                            String finalCmd = cmd.replace("%player%", player.getName());
                            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), finalCmd);
                            response.addProperty("success", true);
                            response.addProperty("message", "Executed console command: " + finalCmd);
                        } else {
                            plugin.getLogger().warning("Blocked unauthorized AI command execution: " + cmd);
                            response.addProperty("success", false);
                            response.addProperty("error", "Command prefix is not whitelisted: " + cmd);
                        }
                        break;
                    }

                    case "kick_player": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to kick players.");
                            break;
                        }
                        if (!player.hasPermission("sculk.sudo")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "You do not have permission to execute this tool.");
                            break;
                        }
                        String targetName = arguments.get("player_name").getAsString();
                        String reason = arguments.has("reason") ? arguments.get("reason").getAsString() : "Kicked by Sculk AI.";
                        
                        Player target = plugin.getServer().getPlayer(targetName);
                        if (target == null) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Player '" + targetName + "' is not online.");
                        } else {
                            target.kickPlayer("Kicked by Sculk: " + reason);
                            response.addProperty("success", true);
                            response.addProperty("message", "Successfully kicked player " + targetName + ".");
                        }
                        break;
                    }

                    case "teleport_player": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to teleport players.");
                            break;
                        }
                        if (!player.hasPermission("sculk.sudo.teleport")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "You do not have permission to execute this tool.");
                            break;
                        }
                        String tpPlayerName = arguments.get("player_name").getAsString();
                        String tpTargetName = arguments.get("target_name").getAsString();
                        
                        Player tpPlayer = plugin.getServer().getPlayer(tpPlayerName);
                        Player tpTarget = plugin.getServer().getPlayer(tpTargetName);
                        
                        if (tpPlayer == null) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Player '" + tpPlayerName + "' is not online.");
                        } else if (tpTarget == null) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Target player '" + tpTargetName + "' is not online.");
                        } else {
                            tpPlayer.teleport(tpTarget.getLocation());
                            String template = plugin.getLanguageManager().getRawMessage("teleported-to-player", tpPlayer);
                            String msg = template.replace("{target}", tpTarget.getName());
                            plugin.adventure().player(tpPlayer).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                            response.addProperty("success", true);
                            response.addProperty("message", "Successfully teleported " + tpPlayer.getName() + " to " + tpTarget.getName() + ".");
                        }
                        break;
                    }

                    case "get_server_status": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to show server status.");
                            break;
                        }
                        if (!player.hasPermission("sculk.sudo.monitor")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "You do not have permission to execute this tool.");
                            break;
                        }
                        
                        double tps = 20.0;
                        try {
                            double[] tpsArray = (double[]) plugin.getServer().getClass().getMethod("getTPS").invoke(plugin.getServer());
                            if (tpsArray != null && tpsArray.length > 0) {
                                tps = Math.min(20.0, Math.round(tpsArray[0] * 100.0) / 100.0);
                            }
                        } catch (Exception ignored) {}
                        
                        long freeMem = Runtime.getRuntime().freeMemory();
                        long totalMem = Runtime.getRuntime().totalMemory();
                        long maxMem = Runtime.getRuntime().maxMemory();
                        long usedMem = totalMem - freeMem;
                        
                        int chunks = 0;
                        int activeEntities = 0;
                        for (org.bukkit.World w : plugin.getServer().getWorlds()) {
                            chunks += w.getLoadedChunks().length;
                            activeEntities += w.getEntities().size();
                        }
                        
                        response.addProperty("success", true);
                        response.addProperty("tps", tps);
                        response.addProperty("used_memory_mb", usedMem / (1024 * 1024));
                        response.addProperty("total_memory_mb", totalMem / (1024 * 1024));
                        response.addProperty("max_memory_mb", maxMem / (1024 * 1024));
                        response.addProperty("loaded_chunks", chunks);
                        response.addProperty("entities", activeEntities);
                        break;
                    }

                    case "broadcast_announcement": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to broadcast announcements.");
                            break;
                        }
                        if (!player.hasPermission("sculk.sudo.broadcast")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "You do not have permission to execute this tool.");
                            break;
                        }
                        String annMsg = arguments.get("message").getAsString();
                        String escapedMsg = MiniMessage.miniMessage().escapeTags(annMsg);
                        
                        for (Player p : plugin.getServer().getOnlinePlayers()) {
                            String template = plugin.getLanguageManager().getRawMessage("broadcast-announcement-header", p);
                            String msg = template.replace("{message}", escapedMsg);
                            plugin.adventure().player(p).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        }
                        String consoleTemplate = plugin.getLanguageManager().getRawMessage("broadcast-announcement-header", plugin.getServer().getConsoleSender());
                        plugin.adventure().sender(plugin.getServer().getConsoleSender()).sendMessage(
                            MiniMessage.miniMessage().deserialize(consoleTemplate.replace("{message}", escapedMsg))
                        );
                        
                        response.addProperty("success", true);
                        response.addProperty("message", "Broadcasted announcement: " + annMsg);
                        break;
                    }

                    case "remember_player_fact": {
                        String fact = arguments.get("fact").getAsString();
                        if (!playerProfile.has("facts")) {
                            playerProfile.add("facts", new JsonArray());
                        }
                        playerProfile.getAsJsonArray("facts").add(fact);
                        
                        response.addProperty("success", true);
                        response.addProperty("message", "Successfully remembered player fact: " + fact);
                        break;
                    }

                    case "save_landmark": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to save landmarks.");
                            break;
                        }
                        String landmarkName = arguments.get("name").getAsString().toLowerCase().trim();
                        if (!playerProfile.has("landmarks")) {
                            playerProfile.add("landmarks", new JsonObject());
                        }
                        JsonObject landmarkLocation = new JsonObject();
                        landmarkLocation.addProperty("world", player.getWorld().getName());
                        landmarkLocation.addProperty("x", player.getLocation().getX());
                        landmarkLocation.addProperty("y", player.getLocation().getY());
                        landmarkLocation.addProperty("z", player.getLocation().getZ());
                        
                        playerProfile.getAsJsonObject("landmarks").add(landmarkName, landmarkLocation);
                        
                        String template = plugin.getLanguageManager().getRawMessage("landmark-saved", player);
                        String msg = template.replace("{name}", landmarkName);
                        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        response.addProperty("success", true);
                        response.addProperty("message", "Successfully saved landmark '" + landmarkName + "' at player's current location.");
                        break;
                    }

                    case "teleport_to_landmark": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to teleport them.");
                            break;
                        }
                        String landName = arguments.get("name").getAsString().toLowerCase().trim();
                        if (!playerProfile.has("landmarks") || !playerProfile.getAsJsonObject("landmarks").has(landName)) {
                            response.addProperty("success", false);
                            response.addProperty("error", "No landmark named '" + landName + "' has been saved. Ask the player to save it first using save_landmark.");
                        } else {
                            JsonObject locJson = playerProfile.getAsJsonObject("landmarks").getAsJsonObject(landName);
                            String worldName = locJson.get("world").getAsString();
                            double lx = locJson.get("x").getAsDouble();
                            double ly = locJson.get("y").getAsDouble();
                            double lz = locJson.get("z").getAsDouble();
                            
                            org.bukkit.World world = plugin.getServer().getWorld(worldName);
                            if (world == null) {
                                response.addProperty("success", false);
                                response.addProperty("error", "World '" + worldName + "' is not loaded.");
                            } else {
                                player.teleport(new org.bukkit.Location(world, lx, ly, lz));
                                String template = plugin.getLanguageManager().getRawMessage("teleported-to-landmark", player);
                                String msg = template.replace("{name}", landName);
                                plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                                response.addProperty("success", true);
                                response.addProperty("message", "Successfully teleported player to landmark '" + landName + "'.");
                            }
                        }
                        break;
                    }

                    case "modify_relationship": {
                        if (!arguments.has("points")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Missing parameter: points");
                            break;
                        }
                        int points = arguments.get("points").getAsInt();
                        String reason = arguments.has("reason") ? arguments.get("reason").getAsString() : "";
                        boolean bypassCooldown = arguments.has("bypass_cooldown") && arguments.get("bypass_cooldown").getAsBoolean();
                        
                        int currentAffection = 0;
                        if (playerProfile.has("affection")) {
                            currentAffection = playerProfile.get("affection").getAsInt();
                        }
                        
                        boolean isChangeAllowed = true;
                        long lastAffectionGain = 0;
                        if (playerProfile.has("last_affection_gain")) {
                            lastAffectionGain = playerProfile.get("last_affection_gain").getAsLong();
                        }
                        
                        if (points > 0 && !bypassCooldown) {
                            long now = System.currentTimeMillis();
                            long cooldownMs = 120000; // 2 minutes cooldown
                            if (now - lastAffectionGain < cooldownMs) {
                                isChangeAllowed = false;
                            }
                        }
                        
                        if (isChangeAllowed) {
                            int newAffection = Math.clamp(currentAffection + points, -100, 100);
                            playerProfile.addProperty("affection", newAffection);
                            
                            if (points > 0) {
                                playerProfile.addProperty("last_affection_gain", System.currentTimeMillis());
                            }
                            
                            String key = points > 0 ? "relationship-increased" : "relationship-decreased";
                            String template = plugin.getLanguageManager().getRawMessage(key, player);
                            String pointsText = points > 0 ? ("+" + points) : String.valueOf(points);
                            String msg = template.replace("{points}", pointsText).replace("{affection}", String.valueOf(newAffection));
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                            
                            response.addProperty("success", true);
                            response.addProperty("affection", newAffection);
                            response.addProperty("message", "Relationship updated by " + pointsText + " (Reason: " + reason + "). New affection: " + newAffection);
                        } else {
                            long now = System.currentTimeMillis();
                            long cooldownMs = 120000;
                            long remainingSec = Math.max(0, (cooldownMs - (now - lastAffectionGain) + 999) / 1000);
                            
                            String template = plugin.getLanguageManager().getRawMessage("relationship-no-change", player);
                            String msg = template.replace("{remaining}", String.valueOf(remainingSec));
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                            response.addProperty("success", false);
                            response.addProperty("error", "Affection increase of +" + points + " was ignored due to cooldown. Cooldown active for another " + remainingSec + " seconds.");
                        }
                        break;
                    }

                    case "gift_item_to_player": {
                        if (affection < 20) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 20+). You must refuse to gift them items.");
                            break;
                        }
                        if (!arguments.has("item_type") || !arguments.has("amount")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Missing required parameters: item_type and/or amount");
                            break;
                        }
                        String itemTypeStr = arguments.get("item_type").getAsString().toUpperCase();
                        int amount = arguments.get("amount").getAsInt();
                        if (amount < 1) amount = 1;
                        if (amount > 64) amount = 64;

                        Material material = Material.getMaterial(itemTypeStr);
                        if (material == null || material == Material.AIR) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Invalid item type/Material: " + itemTypeStr);
                        } else {
                            ItemStack itemStack = new ItemStack(material, amount);
                            player.getWorld().dropItemNaturally(player.getLocation(), itemStack);
                            
                            String template = plugin.getLanguageManager().getRawMessage("gift-received", player);
                            String msg = template.replace("{amount}", String.valueOf(amount)).replace("{item}", material.name());
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                            
                            response.addProperty("success", true);
                            response.addProperty("message", "Gifted " + amount + "x " + material.name() + " to player (dropped at their feet).");
                        }
                        break;
                    }

                    case "sacrifice_held_item": {
                        ItemStack handItem = player.getInventory().getItemInMainHand();
                        if (handItem == null || handItem.getType() == Material.AIR) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Player's main hand is empty. They must hold an item to sacrifice.");
                            break;
                        }

                        if (!arguments.has("points") || !arguments.has("bypass_cooldown")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Missing parameters: points and/or bypass_cooldown");
                            break;
                        }

                        int points = arguments.get("points").getAsInt();
                        String reason = arguments.has("reason") ? arguments.get("reason").getAsString() : "";
                        boolean bypassCooldown = arguments.get("bypass_cooldown").getAsBoolean();

                        int currentAffection = 0;
                        if (playerProfile.has("affection")) {
                            currentAffection = playerProfile.get("affection").getAsInt();
                        }

                        boolean isChangeAllowed = true;
                        long lastAffectionGain = 0;
                        if (playerProfile.has("last_affection_gain")) {
                            lastAffectionGain = playerProfile.get("last_affection_gain").getAsLong();
                        }

                        if (points > 0 && !bypassCooldown) {
                            long now = System.currentTimeMillis();
                            long cooldownMs = 120000; // 2 minutes cooldown
                            if (now - lastAffectionGain < cooldownMs) {
                                isChangeAllowed = false;
                            }
                        }

                        if (isChangeAllowed) {
                            Material material = handItem.getType();
                            int newAmount = handItem.getAmount() - 1;
                            if (newAmount <= 0) {
                                player.getInventory().setItemInMainHand(null);
                            } else {
                                handItem.setAmount(newAmount);
                            }

                            try {
                                player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 0.5f);
                                player.getWorld().spawnParticle(Particle.SCULK_CHARGE, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.02);
                            } catch (Exception ignored) {}

                            String templateConsumed = plugin.getLanguageManager().getRawMessage("sacrifice-consumed", player);
                            String msgConsumed = templateConsumed.replace("{item}", material.name());
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msgConsumed));

                            int newAffection = Math.clamp(currentAffection + points, -100, 100);
                            playerProfile.addProperty("affection", newAffection);

                            if (points > 0) {
                                playerProfile.addProperty("last_affection_gain", System.currentTimeMillis());
                            }

                            String key = points > 0 ? "relationship-increased" : "relationship-decreased";
                            String templateRel = plugin.getLanguageManager().getRawMessage(key, player);
                            String pointsText = points > 0 ? ("+" + points) : String.valueOf(points);
                            String msgRel = templateRel.replace("{points}", pointsText).replace("{affection}", String.valueOf(newAffection));
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msgRel));

                            response.addProperty("success", true);
                            response.addProperty("item_type", material.name());
                            response.addProperty("affection", newAffection);
                            response.addProperty("message", "Consumed 1x " + material.name() + " and updated relationship by " + pointsText + " (New affection: " + newAffection + ").");
                        } else {
                            long now = System.currentTimeMillis();
                            long cooldownMs = 120000;
                            long remainingSec = Math.max(0, (cooldownMs - (now - lastAffectionGain) + 999) / 1000);

                            String templateNoChange = plugin.getLanguageManager().getRawMessage("relationship-no-change", player);
                            String msgNoChange = templateNoChange.replace("{remaining}", String.valueOf(remainingSec));
                            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msgNoChange));

                            response.addProperty("success", false);
                            response.addProperty("error", "Affection increase of +" + points + " was ignored due to cooldown (" + remainingSec + "s remaining). The item was NOT consumed.");
                        }
                        break;
                    }

                    case "start_quest": {
                        if (affection < 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Your affection level with the player (" + affection + ") is too low (requires 0+). You must refuse to assign quests.");
                            break;
                        }
                        if (!arguments.has("type") || !arguments.has("target") || !arguments.has("target_amount") || !arguments.has("description")) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Missing required parameters: type, target, target_amount, or description");
                            break;
                        }
                        String questType = arguments.get("type").getAsString().toUpperCase();
                        String target = arguments.get("target").getAsString().toUpperCase();
                        int targetAmount = arguments.get("target_amount").getAsInt();
                        String description = arguments.get("description").getAsString();

                        if (!"KILL_MOB".equals(questType) && !"COLLECT_ITEM".equals(questType)) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Invalid quest type. Must be 'KILL_MOB' or 'COLLECT_ITEM'.");
                            break;
                        }

                        if (targetAmount <= 0) {
                            response.addProperty("success", false);
                            response.addProperty("error", "Target amount must be greater than 0.");
                            break;
                        }

                        if ("KILL_MOB".equals(questType)) {
                            try {
                                org.bukkit.entity.EntityType.valueOf(target);
                            } catch (IllegalArgumentException e) {
                                response.addProperty("success", false);
                                response.addProperty("error", "Invalid EntityType: " + target);
                                break;
                            }
                        } else {
                            Material mat = Material.getMaterial(target);
                            if (mat == null || mat == Material.AIR) {
                                response.addProperty("success", false);
                                response.addProperty("error", "Invalid Material: " + target);
                                break;
                            }
                        }

                        JsonObject activeQuest = new JsonObject();
                        activeQuest.addProperty("type", questType);
                        activeQuest.addProperty("target", target);
                        activeQuest.addProperty("target_amount", targetAmount);
                        activeQuest.addProperty("current_amount", 0);
                        activeQuest.addProperty("description", description);

                        playerProfile.add("active_quest", activeQuest);

                        String template = plugin.getLanguageManager().getRawMessage("new-quest-assigned", player);
                        String msg = template.replace("{description}", description)
                                             .replace("{target}", target)
                                             .replace("{total}", String.valueOf(targetAmount));
                        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        
                        response.addProperty("success", true);
                        response.addProperty("message", "Quest successfully assigned: " + description);
                        break;
                    }

                    case "check_quest_status": {
                        if (!playerProfile.has("active_quest") || playerProfile.get("active_quest").isJsonNull()) {
                            response.addProperty("success", false);
                            response.addProperty("error", "No active quest found.");
                            break;
                        }

                        JsonObject quest = playerProfile.getAsJsonObject("active_quest");
                        String questType = quest.has("type") ? quest.get("type").getAsString() : "";
                        String target = quest.has("target") ? quest.get("target").getAsString() : "";
                        int targetAmount = quest.has("target_amount") ? quest.get("target_amount").getAsInt() : 0;
                        int currentAmount = quest.has("current_amount") ? quest.get("current_amount").getAsInt() : 0;

                        if ("COLLECT_ITEM".equals(questType)) {
                            Material mat = Material.getMaterial(target);
                            if (mat == null) {
                                response.addProperty("success", false);
                                response.addProperty("error", "Invalid Material in quest: " + target);
                                break;
                            }

                            int inventoryCount = 0;
                            for (ItemStack item : player.getInventory().getContents()) {
                                if (item != null && item.getType() == mat) {
                                    inventoryCount += item.getAmount();
                                }
                            }

                            if (inventoryCount >= targetAmount) {
                                int remainingToRemove = targetAmount;
                                ItemStack[] contents = player.getInventory().getContents();
                                for (int idx = 0; idx < contents.length; idx++) {
                                    ItemStack item = contents[idx];
                                    if (item != null && item.getType() == mat) {
                                        int amt = item.getAmount();
                                        if (amt <= remainingToRemove) {
                                            remainingToRemove -= amt;
                                            player.getInventory().setItem(idx, null);
                                        } else {
                                            item.setAmount(amt - remainingToRemove);
                                            remainingToRemove = 0;
                                        }
                                    }
                                    if (remainingToRemove <= 0) break;
                                }
                                player.updateInventory();

                                currentAmount = targetAmount;
                                quest.addProperty("current_amount", currentAmount);
                                
                                try {
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                } catch (Exception ignored) {}

                                String msg = plugin.getLanguageManager().getRawMessage("quest-items-collected", player);
                                plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                            } else {
                                currentAmount = inventoryCount;
                                quest.addProperty("current_amount", currentAmount);
                            }
                        }

                        boolean completed = currentAmount >= targetAmount;
                        response.addProperty("success", true);
                        response.addProperty("type", questType);
                        response.addProperty("target", target);
                        response.addProperty("target_amount", targetAmount);
                        response.addProperty("current_amount", currentAmount);
                        response.addProperty("completed", completed);
                        break;
                    }

                    case "complete_quest": {
                        if (!playerProfile.has("active_quest") || playerProfile.get("active_quest").isJsonNull()) {
                            response.addProperty("success", false);
                            response.addProperty("error", "No active quest to complete.");
                            break;
                        }

                        playerProfile.remove("active_quest");
                        String msg = plugin.getLanguageManager().getRawMessage("quest-removed", player);
                        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));

                        response.addProperty("success", true);
                        response.addProperty("message", "Active quest cleared/completed.");
                        break;
                    }

                    default:
                        response.addProperty("success", false);
                        response.addProperty("error", "Unknown tool: " + name);
                        break;
                }
            }
        } catch (Exception e) {
            response.addProperty("success", false);
            response.addProperty("error", "Exception executing action: " + e.getMessage());
        }

        return response;
    }
}
