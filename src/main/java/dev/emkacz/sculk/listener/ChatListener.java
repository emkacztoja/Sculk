package dev.emkacz.sculk.listener;

import dev.emkacz.sculk.Sculk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ChatListener implements Listener {

    private final Sculk plugin;

    public ChatListener(Sculk plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String rawText = event.getMessage();
        UUID uuid = player.getUniqueId();

        // Check if message contains the configured trigger keyword OR if player has chat mode toggled on
        String trigger = plugin.getConfig().getString("trigger-keyword", "sculk").toLowerCase();
        if (rawText.toLowerCase().contains(trigger) || plugin.isChatModeEnabled(uuid)) {
            // Process the query asynchronously via the AIService
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
}
