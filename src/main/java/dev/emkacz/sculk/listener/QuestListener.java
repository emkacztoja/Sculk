package dev.emkacz.sculk.listener;

import com.google.gson.JsonObject;
import dev.emkacz.sculk.Sculk;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.UUID;

public class QuestListener implements Listener {

    private final Sculk plugin;

    public QuestListener(Sculk plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        Player player = event.getEntity().getKiller();
        UUID uuid = player.getUniqueId();

        JsonObject profile = plugin.getPlayerProfile(uuid);
        if (profile == null) {
            return;
        }
        synchronized (profile) {
            if (!profile.has("active_quest") || profile.get("active_quest").isJsonNull()) {
                return;
            }
            JsonObject quest = profile.getAsJsonObject("active_quest");
            if (quest == null || !quest.has("type") || !"KILL_MOB".equals(quest.get("type").getAsString())) {
                return;
            }
            String targetMob = quest.get("target").getAsString();
            String killedMobType = event.getEntityType().name();
            if (!killedMobType.equalsIgnoreCase(targetMob)) {
                return;
            }
            int targetAmount = quest.get("target_amount").getAsInt();
            int currentAmount = quest.has("current_amount") ? quest.get("current_amount").getAsInt() : 0;
            if (currentAmount >= targetAmount) {
                return;
            }
            currentAmount++;
            quest.addProperty("current_amount", currentAmount);

            // Mark dirty — the periodic flush will write the profile
            plugin.markPlayerProfileDirty(uuid);

            if (currentAmount >= targetAmount) {
                // Quest finished this kill — auto-notify so the player doesn't
                // have to come back and ask Sculk to claim their reward.
                String msg = plugin.getLanguageManager().getRawMessage("quest-auto-completed", player);
                plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                } catch (Exception ignored) {}
            } else {
                String template = plugin.getLanguageManager().getRawMessage("quest-progress-actionbar", player);
                String msg = template.replace("{target}", targetMob)
                                     .replace("{current}", String.valueOf(currentAmount))
                                     .replace("{total}", String.valueOf(targetAmount));
                plugin.adventure().player(player).sendActionBar(MiniMessage.miniMessage().deserialize(msg));
                try {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                } catch (Exception ignored) {}
            }
        }
    }
}
