package dev.emkacz.sculk.action;

import com.google.gson.JsonObject;
import dev.emkacz.sculk.Sculk;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

/**
 * Per-invocation context passed to a {@link ToolDefinition.ToolExecutor}.
 * Captures the plugin instance, the triggering player, and the player's
 * mutable profile (already inside a {@code synchronized} block when the
 * executor is called).
 */
public record ToolContext(Sculk plugin, Player player, JsonObject playerProfile) {

    /**
     * Localize a template, substitute placeholders, send it to the triggering
     * player, and return a null JsonObject (so callers can
     * {@code return ctx.sendLocalized("...", "k", "v");}).
     */
    public JsonObject sendLocalized(String key, String... replacements) {
        String template = plugin.getLanguageManager().getRawMessage(key, player);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            template = template.replace(replacements[i], replacements[i + 1]);
        }
        plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(template));
        return null;
    }

    /**
     * Read a per-tool affection threshold from
     * {@code actions.thresholds.<toolName>}, defaulting to {@code defaultValue}
     * if unset.
     */
    public int threshold(String toolName, int defaultValue) {
        return plugin.getConfig().getInt("actions.thresholds." + toolName, defaultValue);
    }
}
