package dev.emkacz.sculk.action;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.emkacz.sculk.Sculk;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Dispatches AI tool calls. The actual tool definitions and executors live in
 * {@link Tools#REGISTRY}; this class only handles JSON marshalling and
 * main-thread scheduling.
 */
public class ActionManager {

    private final Sculk plugin;
    private final Gson gson;

    public ActionManager(Sculk plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    /** Cached schema-by-name lookup, built once per player. */
    private final Map<String, ToolDefinition> byName = new HashMap<>();
    private java.util.Set<String> allowedByName = java.util.Set.of();

    private void refreshRegistryCache(Player player) {
        byName.clear();
        allowedByName = new java.util.HashSet<>();
        for (ToolDefinition def : Tools.REGISTRY) {
            if (def.visibility() != null && !def.visibility().test(player)) {
                continue;
            }
            byName.put(def.name(), def);
            allowedByName.add(def.name());
        }
    }

    /**
     * Builds standard OpenAI-compatible tool schemas, filtered by the player's
     * permissions (e.g. sculk.sudo for kick_player).
     */
    public JsonArray buildToolsDefinition(Player player) {
        refreshRegistryCache(player);
        JsonArray tools = new JsonArray();
        for (ToolDefinition def : Tools.REGISTRY) {
            if (def.visibility() != null && !def.visibility().test(player)) {
                continue;
            }
            tools.add(def.toJsonSchema());
        }
        return tools;
    }

    /**
     * Executes AI-requested tool calls sequentially on the main server thread.
     */
    public CompletableFuture<JsonArray> executeTools(Player player, JsonArray toolCalls, JsonObject playerProfile) {
        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            refreshRegistryCache(player);
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
        if (!plugin.getConfig().getBoolean("actions.enable-actions", true)) {
            JsonObject r = new JsonObject();
            r.addProperty("success", false);
            r.addProperty("error", "Actions are currently disabled on this server.");
            return r;
        }
        ToolDefinition def = byName.get(name);
        if (def == null) {
            JsonObject r = new JsonObject();
            r.addProperty("success", false);
            r.addProperty("error", "Unknown tool: " + name);
            return r;
        }
        try {
            synchronized (playerProfile) {
                return def.executor().execute(new ToolContext(plugin, player, playerProfile), arguments);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Tool " + name + " failed: " + e.getMessage());
            JsonObject r = new JsonObject();
            r.addProperty("success", false);
            r.addProperty("error", "Exception executing action: " + e.getMessage());
            return r;
        }
    }
}
