package dev.emkacz.sculk.listener;

import dev.emkacz.sculk.Sculk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Locale;
import java.util.UUID;

public class ChatListener implements Listener {

    public enum TriggerMode {
        /** The trigger keyword appears anywhere in the message (default). */
        CONTAINS,
        /** The message starts with the trigger keyword followed by a space. */
        PREFIX,
        /** The message is @<mention-keyword> <rest>. */
        MENTION;

        public static TriggerMode fromConfig(String value) {
            if (value == null) return CONTAINS;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "prefix" -> PREFIX;
                case "mention" -> MENTION;
                default -> CONTAINS;
            };
        }
    }

    private final Sculk plugin;

    public ChatListener(Sculk plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rawText = event.getMessage();
        UUID uuid = player.getUniqueId();

        if (matchesTrigger(rawText) || plugin.isChatModeEnabled(uuid)) {
            plugin.getAIService().processQuery(player, rawText);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.clearStates(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        plugin.clearStates(event.getPlayer().getUniqueId());
    }

    /**
     * Decide whether {@code rawText} should trigger Sculk based on the configured
     * trigger mode. Chat-mode toggled players always match.
     */
    private boolean matchesTrigger(String rawText) {
        TriggerMode mode = TriggerMode.fromConfig(plugin.getConfig().getString("trigger-mode", "contains"));
        return switch (mode) {
            case PREFIX -> {
                String keyword = plugin.getConfig().getString("trigger-keyword", "sculk").toLowerCase(Locale.ROOT);
                String lower = rawText.toLowerCase(Locale.ROOT);
                yield lower.startsWith(keyword + " ") || lower.equals(keyword);
            }
            case MENTION -> {
                String mention = plugin.getConfig().getString("mention-keyword", "sculk");
                String lower = rawText.toLowerCase(Locale.ROOT);
                yield lower.startsWith("@" + mention.toLowerCase(Locale.ROOT) + " ")
                        || lower.startsWith("@" + mention.toLowerCase(Locale.ROOT) + ":")
                        || lower.equals("@" + mention.toLowerCase(Locale.ROOT));
            }
            case CONTAINS -> {
                String keyword = plugin.getConfig().getString("trigger-keyword", "sculk").toLowerCase(Locale.ROOT);
                yield rawText.toLowerCase(Locale.ROOT).contains(keyword);
            }
        };
    }
}
