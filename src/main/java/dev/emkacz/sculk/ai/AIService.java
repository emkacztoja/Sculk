package dev.emkacz.sculk.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.emkacz.sculk.Sculk;
import dev.emkacz.sculk.util.MarkdownParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIService {

    private static final Pattern DSML_TAG_PATTERN = Pattern.compile("<[^>]*DSML[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOOL_CALLS_PATTERN = Pattern.compile("<\\s*[｜|\\s]+DSML[｜|\\s]+tool_calls\\s*>(.*?)</\\s*[｜|\\s]+DSML[｜|\\s]+tool_calls\\s*>", Pattern.DOTALL);
    private static final Pattern INVOKE_PATTERN = Pattern.compile("<\\s*[｜|\\s]+DSML[｜|\\s]+invoke\\s+name\\s*=\\s*\"(.*?)\"\\s*>(.*?)</\\s*[｜|\\s]+DSML[｜|\\s]+invoke\\s*>", Pattern.DOTALL);
    private static final Pattern PARAM_PATTERN = Pattern.compile("<\\s*[｜|\\s]+DSML[｜|\\s]+parameter\\s+name\\s*=\\s*\"(.*?)\"(?:\\s+string\\s*=\\s*\"(?:true|false)\")?\\s*>(.*?)</\\s*[｜|\\s]+DSML[｜|\\s]+parameter\\s*>", Pattern.DOTALL);

    private final Sculk plugin;
    private final HttpClient httpClient;
    private final Gson gson;

    public AIService(Sculk plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    /**
     * Processes the player query asynchronously without blocking the server main loop.
     * Safely schedules context-gathering tasks and feedback on the main Bukkit thread first.
     */
    public void processQuery(Player player, String rawText) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        // 1. Cooldown Check
        if (plugin.isOnCooldown(uuid)) {
            long remaining = plugin.getRemainingCooldownSeconds(uuid);
            String template = plugin.getLanguageManager().getRawMessage("cooldown-message", player);
            Component cooldownMsg = MiniMessage.miniMessage().deserialize(template.replace("{remaining}", String.valueOf(remaining)));
            plugin.adventure().player(player).sendActionBar(cooldownMsg);
            return;
        }

        // Apply Cooldown
        int cooldownSec = config.getInt("api.cooldown-seconds", 10);
        plugin.setCooldown(uuid, cooldownSec);

        // 2. Play sensory feedback and gather context safely on the main thread
        boolean playSound = config.getBoolean("feedback.play-sound", true);
        boolean spawnParticles = config.getBoolean("feedback.spawn-particles", true);

        CompletableFuture<String> contextFuture = new CompletableFuture<>();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                if (playSound) {
                    try {
                        player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 1.0f, 1.0f);
                    } catch (Exception ignored) {}
                }
                if (spawnParticles) {
                    try {
                        player.getWorld().spawnParticle(Particle.SCULK_CHARGE, player.getLocation().add(0, 1.5, 0), 15, 0.4, 0.4, 0.4, 0.05);
                    } catch (Exception ignored) {}
                }
                // Gather context safely while on the main Bukkit thread
                String context = gatherContextSynchronously(player);
                contextFuture.complete(context);
            } catch (Exception e) {
                contextFuture.completeExceptionally(e);
            }
        });

        // 3. Initiate the query once context is successfully acquired
        contextFuture.thenAcceptAsync(context -> {
            queryAI(player, rawText, context);
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to gather query context: " + ex.getMessage());
            String errorMessage = plugin.getLanguageManager().getRawMessage("error-message", player);
            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(errorMessage));
            return null;
        });
    }

    /**
     * Gathers server and player specific context safely. 
     * MUST be called from the server's main thread.
     */
    private String gatherContextSynchronously(Player player) {
        StringBuilder sb = new StringBuilder();
        FileConfiguration config = plugin.getConfig();

        if (config.getBoolean("context.enable-player-context", true)) {
            sb.append("Player Context:\n");
            sb.append("- Name: ").append(player.getName()).append("\n");
            sb.append("- Location: ").append(player.getWorld().getName())
              .append(" (X: ").append(player.getLocation().getBlockX())
              .append(", Y: ").append(player.getLocation().getBlockY())
              .append(", Z: ").append(player.getLocation().getBlockZ()).append(")\n");
            sb.append("- Biome: ").append(player.getLocation().getBlock().getBiome().name()).append("\n");
            sb.append("- Health: ").append(Math.round(player.getHealth())).append("/").append(Math.round(player.getMaxHealth())).append("\n");
            sb.append("- Hunger: ").append(player.getFoodLevel()).append("/20\n");
            
            if (player.getInventory().getItemInMainHand() != null && player.getInventory().getItemInMainHand().getType() != org.bukkit.Material.AIR) {
                sb.append("- Equipment: ").append(player.getInventory().getItemInMainHand().getType().name()).append("\n");
            } else {
                sb.append("- Equipment: Empty hand\n");
            }
        }

        if (config.getBoolean("context.enable-server-context", true)) {
            sb.append("Server Context:\n");
            sb.append("- Time: ").append(player.getWorld().getTime() < 12000 ? "Day" : "Night")
              .append(" (").append(player.getWorld().getTime()).append(")\n");
            sb.append("- Weather: ").append(player.getWorld().hasStorm() ? "Stormy" : "Clear").append("\n");
            sb.append("- Online Players: ").append(plugin.getServer().getOnlinePlayers().size()).append("\n");
        }

        return sb.toString();
    }

    public void queryAI(Player player, String rawText, String gatheredContext) {
        UUID uuid = player.getUniqueId();
        FileConfiguration config = plugin.getConfig();

        // Load configuration parameters
        String apiUrl = config.getString("api.url", "https://api.deepseek.com/chat/completions");
        String apiToken = config.getString("api.token", "");
        String model = config.getString("api.model", "gpt-4o");
        String prefix = plugin.getLanguageManager().getRawMessage("prefix", player);
        String errorMessage = plugin.getLanguageManager().getRawMessage("error-message", player);
        String systemPrompt = config.getString("api.system-prompt", 
                "You are a mystical Sculk helper in Minecraft. Keep replies brief, under 2 sentences.");

        // Inject Lore if enabled
        if (config.getBoolean("lore.enable-lore", true)) {
            String playerLang = plugin.getLanguageManager().getLanguageCode(player);
            String cachedLore = plugin.getCachedLore(playerLang);
            if (cachedLore != null && !cachedLore.trim().isEmpty()) {
                systemPrompt = systemPrompt + "\n\nLore & Knowledge Base Context:\n" + cachedLore;
            }
        }

        // Load Persistent Player Profile JSON
        JsonObject playerProfile = plugin.getPlayerProfile(uuid);

        // Inject Player Facts & Landmarks into the System Prompt
        StringBuilder memoryContext = new StringBuilder();
        JsonArray messages = new JsonArray();

        synchronized (playerProfile) {
            if (playerProfile.has("facts")) {
                JsonArray facts = playerProfile.getAsJsonArray("facts");
                if (facts.size() > 0) {
                    memoryContext.append("\n\nKnown facts about this player (long-term memory):\n");
                    for (int i = 0; i < facts.size(); i++) {
                        memoryContext.append("- ").append(facts.get(i).getAsString()).append("\n");
                    }
                }
            }
            if (playerProfile.has("landmarks")) {
                JsonObject landmarks = playerProfile.getAsJsonObject("landmarks");
                java.util.Set<String> keys = landmarks.keySet();
                if (!keys.isEmpty()) {
                    memoryContext.append("\nSaved teleportation landmarks available for this player (landmarks they can teleport to): ")
                                 .append(keys.toString()).append("\n");
                }
            }

            // Inject Affection/Relationship Score
            int affection = 0;
            if (playerProfile.has("affection")) {
                affection = playerProfile.get("affection").getAsInt();
            }
            memoryContext.append("\nYour current relationship/affection level with this player is: ")
                         .append(affection).append(" (on a scale from -100 to +100). ")
                         .append("If this level is high (positive), you should treat them warmly and be more willing to help. ")
                         .append("If it is low (negative), you should be cold, suspicious, or demanding. ")
                         .append("You can change this score by calling modify_relationship(points, reason, bypass_cooldown).\n")
                         .append("Guidance for modifying affection:\n")
                         .append("- You should modify the player's affection score based on their actions, behavior, and offerings using modify_relationship(points, reason, bypass_cooldown).\n")
                         .append("- For sacrifices (using sacrifice_held_item), evaluate the item value yourself: gold/iron/valuable food: +3 to +5, diamonds/emeralds: +10 to +15, beacons/netherite: +20 to +30. Insulting sacrifices (like dirt, cobblestone, gravel, rotten flesh, rotten materials) should reduce affection (e.g. -5 to -10).\n")
                         .append("- By default, positive affection increases are subject to a 2-minute cooldown on the server side to prevent players from spamming low-value gifts. However, you can bypass this cooldown for high-value offerings (diamonds, emeralds, netherite, beacons, enchanted items) or major player deeds by setting 'bypass_cooldown' to true. For low-value gifts (like coal, raw iron, standard food) or friendly chats, set 'bypass_cooldown' to false to let the server enforce the cooldown.\n");

            // Inject Active Quest Details
            if (playerProfile.has("active_quest") && !playerProfile.get("active_quest").isJsonNull()) {
                JsonObject quest = playerProfile.getAsJsonObject("active_quest");
                String type = quest.has("type") ? quest.get("type").getAsString() : "";
                String target = quest.has("target") ? quest.get("target").getAsString() : "";
                int targetAmount = quest.has("target_amount") ? quest.get("target_amount").getAsInt() : 0;
                int currentAmount = quest.has("current_amount") ? quest.get("current_amount").getAsInt() : 0;
                String desc = quest.has("description") ? quest.get("description").getAsString() : "";

                memoryContext.append("\nThe player has an active quest:\n")
                             .append("- Type: ").append(type).append("\n")
                             .append("- Target: ").append(target).append("\n")
                             .append("- Target Amount: ").append(targetAmount).append("\n")
                             .append("- Current Progress: ").append(currentAmount).append(" / ").append(targetAmount).append("\n")
                             .append("- Description: ").append(desc).append("\n");

                if (currentAmount >= targetAmount) {
                    memoryContext.append("The quest is completed! Acknowledge this, and run the complete_quest() tool. ")
                                 .append("You might also want to reward the player by gifting them an item using gift_item_to_player().\n");
                } else {
                    memoryContext.append("The quest is still in progress. The player can talk to you about it, or you can call check_quest_status() to check/refresh their items (for COLLECT_ITEM quests).\n");
                }
            } else {
                memoryContext.append("\nThe player currently has no active quest. You can assign them one using the start_quest(type, target, target_amount, description) tool.\n");
            }

            if (memoryContext.length() > 0) {
                systemPrompt = systemPrompt + memoryContext.toString();
            }

            // Instruct LLM to use the server's configured language code
            String playerLang = plugin.getLanguageManager().getLanguageCode(player);
            systemPrompt = systemPrompt + "\n\nCRITICAL: Write your conversational reply in the configured language of the server: '" 
                         + playerLang + "' (e.g., 'pl' is Polish, 'en' is English). Even if instructions, system messages, or lore are in English, converse ONLY in '" + playerLang + "'.";

            // System instructions
            JsonObject systemMsg = new JsonObject();
            systemMsg.addProperty("role", "system");
            systemMsg.addProperty("content", systemPrompt);
            messages.add(systemMsg);

            // Chat Memory (loaded from persistent history)
            if (playerProfile.has("history")) {
                JsonArray historyArray = playerProfile.getAsJsonArray("history");
                for (int i = 0; i < historyArray.size(); i++) {
                    messages.add(historyArray.get(i).deepCopy());
                }
            }
        }

        // Current request with context
        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        String fullUserPrompt = rawText;
        if (gatheredContext != null && !gatheredContext.trim().isEmpty()) {
            fullUserPrompt = gatheredContext + "\nUser Input:\n" + rawText;
        }
        userMsg.addProperty("content", fullUserPrompt);
        messages.add(userMsg);

        // Start pulsing action bar thinking message
        String thinkingMessage = plugin.getLanguageManager().getRawMessage("thinking-message", player);
        Component thinkingComponent = MiniMessage.miniMessage().deserialize(thinkingMessage);
        
        AtomicReference<BukkitTask> thinkingTaskRef = new AtomicReference<>();
        BukkitTask thinkingTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.adventure().player(player).sendActionBar(thinkingComponent);
        }, 0L, 10L);
        thinkingTaskRef.set(thinkingTask);

        // Start recursive completions loop
        runCompletionLoop(player, rawText, messages, 1, thinkingTaskRef, playerProfile);
    }

    private void runCompletionLoop(Player player, String rawText, JsonArray messages, int turn, AtomicReference<BukkitTask> thinkingTaskRef, JsonObject playerProfile) {
        if (turn > 5) {
            stopThinkingTask(thinkingTaskRef);
            plugin.getLogger().warning("Sculk AI reached maximum tool loop depth (5 turns).");
            String errorMessage = plugin.getLanguageManager().getRawMessage("error-message", player);
            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(errorMessage));
            return;
        }

        FileConfiguration config = plugin.getConfig();
        String apiUrl = config.getString("api.url", "https://api.deepseek.com/chat/completions");
        String apiToken = config.getString("api.token", "");
        String model = config.getString("api.model", "gpt-4o");
        String prefix = plugin.getLanguageManager().getRawMessage("prefix", player);
        String errorMessage = plugin.getLanguageManager().getRawMessage("error-message", player);

        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.add("messages", messages);
        if (config.getBoolean("actions.enable-actions", true)) {
            requestBody.add("tools", plugin.getActionManager().buildToolsDefinition(player));
        }

        sendHttpRequestAsync(apiUrl, apiToken, requestBody, turn)
                .thenAccept(responseJson -> {
                    if (responseJson.has("choices") && responseJson.getAsJsonArray("choices").size() > 0) {
                        JsonObject choice = responseJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                        JsonObject messageObj = choice.getAsJsonObject("message");
                        
                        JsonArray toolCalls = null;
                        if (messageObj.has("tool_calls")) {
                            toolCalls = messageObj.getAsJsonArray("tool_calls");
                        }
                        
                        if ((toolCalls == null || toolCalls.size() == 0) && messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                            String contentStr = messageObj.get("content").getAsString();
                            if (contentStr.contains("DSML") && contentStr.contains("tool_calls")) {
                                toolCalls = parseDsmlToolCalls(contentStr);
                            }
                        }

                        if (toolCalls != null && toolCalls.size() > 0) {
                            final JsonArray finalToolCalls = toolCalls;
                            plugin.getActionManager().executeTools(player, finalToolCalls, playerProfile)
                                    .thenAccept(toolResultMsgs -> {
                                        JsonArray updatedMessages = new JsonArray();
                                        for (int i = 0; i < messages.size(); i++) {
                                            updatedMessages.add(messages.get(i).deepCopy());
                                        }
                                        
                                        JsonObject assistantMsg = new JsonObject();
                                        assistantMsg.addProperty("role", "assistant");
                                        if (messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                                            assistantMsg.addProperty("content", messageObj.get("content").getAsString());
                                        } else {
                                            assistantMsg.add("content", com.google.gson.JsonNull.INSTANCE);
                                        }
                                        assistantMsg.add("tool_calls", finalToolCalls.deepCopy());
                                        updatedMessages.add(assistantMsg);

                                        for (int i = 0; i < toolResultMsgs.size(); i++) {
                                            updatedMessages.add(toolResultMsgs.get(i).deepCopy());
                                        }

                                        runCompletionLoop(player, rawText, updatedMessages, turn + 1, thinkingTaskRef, playerProfile);
                                    })
                                    .exceptionally(ex -> {
                                        stopThinkingTask(thinkingTaskRef);
                                        handleError(player, ex, errorMessage);
                                        return null;
                                    });
                            return;
                        }

                        stopThinkingTask(thinkingTaskRef);
                        handleFinalTextResponse(player, rawText, responseJson, prefix, errorMessage, player.getUniqueId(), playerProfile);
                    } else {
                        stopThinkingTask(thinkingTaskRef);
                        plugin.getLogger().warning("Invalid response format received from LLM API: " + responseJson);
                        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(errorMessage));
                    }
                })
                .exceptionally(ex -> {
                    stopThinkingTask(thinkingTaskRef);
                    handleError(player, ex, errorMessage);
                    return null;
                });
    }

    /**
     * Sends the asynchronous API post request.
     */
    private CompletableFuture<JsonObject> sendHttpRequestAsync(String apiUrl, String apiToken, JsonObject payload, int turn) {
        int timeoutSec = plugin.getConfig().getInt("api.timeout-seconds", 60);
        String jsonPayload = gson.toJson(payload);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(timeoutSec));

        if (apiToken != null && !apiToken.trim().isEmpty()) {
            requestBuilder.header("Authorization", "Bearer " + apiToken);
        }

        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String responseBody = response.body();

                    if (statusCode != 200) {
                        throw new RuntimeException("API request failed on turn " + turn + " with HTTP " + statusCode + ": " + responseBody);
                    }

                    return JsonParser.parseString(responseBody).getAsJsonObject();
                });
    }

    /**
     * Handles the final text response, parsing markdown formatting and broadcasting the result.
     */
    private void handleFinalTextResponse(Player player, String rawText, JsonObject responseJson, String prefix, String errorMessage, UUID uuid, JsonObject playerProfile) {
        try {
            if (responseJson.has("choices") && responseJson.getAsJsonArray("choices").size() > 0) {
                JsonObject choice = responseJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                JsonObject messageObj = choice.getAsJsonObject("message");
                if (messageObj.has("content") && !messageObj.get("content").isJsonNull()) {
                    String responseText = messageObj.get("content").getAsString();

                    // Strip any DSML tag residues from the printed/stored response
                    Matcher dsmlMatcher = DSML_TAG_PATTERN.matcher(responseText);
                    if (dsmlMatcher.find()) {
                        responseText = responseText.substring(0, dsmlMatcher.start()).trim();
                    }

                    // Update Chat History in player JSON profile under synchronized block
                    synchronized (playerProfile) {
                        JsonObject userMsg = new JsonObject();
                        userMsg.addProperty("role", "user");
                        userMsg.addProperty("content", rawText);
                        
                        JsonObject assistantMsg = new JsonObject();
                        assistantMsg.addProperty("role", "assistant");
                        assistantMsg.addProperty("content", responseText);

                        if (!playerProfile.has("history")) {
                            playerProfile.add("history", new JsonArray());
                        }
                        JsonArray historyArray = playerProfile.getAsJsonArray("history");
                        historyArray.add(userMsg);
                        historyArray.add(assistantMsg);

                        // Truncate history size in turns (pairs of user + assistant)
                        int maxHistory = plugin.getConfig().getInt("api.chat-history-size", 5);
                        while (historyArray.size() > maxHistory * 2 && historyArray.size() >= 2) {
                            historyArray.remove(0);
                            historyArray.remove(0);
                        }

                        // Asynchronously save updated profile (history + newly saved facts/landmarks)
                        plugin.savePlayerProfileAsync(uuid, playerProfile);
                    }

                    // Parse standard Markdown into Kyori MiniMessage format
                    String miniMessageFormatted = MarkdownParser.toMiniMessage(responseText);

                    // Format message
                    Component parsedPrefix = MiniMessage.miniMessage().deserialize(prefix);
                    Component parsedResponse = MiniMessage.miniMessage().deserialize(miniMessageFormatted);
                    Component finalMessage = parsedPrefix.append(parsedResponse);

                    // Broadcast to all players
                    plugin.adventure().all().sendMessage(finalMessage);
                    return;
                }
            }
            plugin.getLogger().warning("Empty choices received from final API response: " + responseJson);
            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(errorMessage));
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to parse final LLM API response: " + e.getMessage());
            plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(errorMessage));
        }
    }

    private void stopThinkingTask(AtomicReference<BukkitTask> thinkingTaskRef) {
        BukkitTask task = thinkingTaskRef.get();
        if (task != null) {
            task.cancel();
        }
    }

    private void handleError(Player player, Throwable ex, String errorMessage) {
        plugin.getLogger().severe("Sculk AI request failed: " + ex.getMessage());
        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(errorMessage));
    }

    private JsonArray parseDsmlToolCalls(String content) {
        JsonArray toolCalls = new JsonArray();
        if (content == null) return toolCalls;

        Matcher matcher = TOOL_CALLS_PATTERN.matcher(content);
        if (matcher.find()) {
            String innerContent = matcher.group(1);
            Matcher invokeMatcher = INVOKE_PATTERN.matcher(innerContent);
            
            while (invokeMatcher.find()) {
                String toolName = invokeMatcher.group(1);
                String paramsContent = invokeMatcher.group(2);
                
                JsonObject arguments = new JsonObject();
                Matcher paramMatcher = PARAM_PATTERN.matcher(paramsContent);
                
                while (paramMatcher.find()) {
                    String paramName = paramMatcher.group(1);
                    String paramValue = paramMatcher.group(2).trim();
                    arguments.addProperty(paramName, paramValue);
                }
                
                JsonObject toolCall = new JsonObject();
                toolCall.addProperty("id", "call_dsml_" + UUID.randomUUID().toString().substring(0, 8));
                toolCall.addProperty("type", "function");
                
                JsonObject funcObj = new JsonObject();
                funcObj.addProperty("name", toolName);
                funcObj.addProperty("arguments", gson.toJson(arguments));
                
                toolCall.add("function", funcObj);
                toolCalls.add(toolCall);
            }
        }
        return toolCalls;
    }
}
