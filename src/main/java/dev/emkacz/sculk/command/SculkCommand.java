package dev.emkacz.sculk.command;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emkacz.sculk.Sculk;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SculkCommand implements CommandExecutor, TabCompleter {

    private final Sculk plugin;

    public SculkCommand(Sculk plugin) {
        this.plugin = plugin;
    }

    private void sendMessage(CommandSender sender, String key) {
        String msg = plugin.getLanguageManager().getRawMessage(key, sender);
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(msg));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle alias "/ask <question>" directly
        if (label.equalsIgnoreCase("ask")) {
            if (!(sender instanceof Player)) {
                sendMessage(sender, "only-players-ask");
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("sculk.use")) {
                sendMessage(player, "no-permission-ask");
                return true;
            }
            if (args.length == 0) {
                sendMessage(player, "usage-ask");
                return true;
            }
            String query = String.join(" ", args);
            plugin.getAIService().processQuery(player, query);
            return true;
        }

        // Handle main command "/sculk" — no args now means "show my status"
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sendMessage(sender, "only-players-status");
                return true;
            }
            Player p = (Player) sender;
            if (!p.hasPermission("sculk.use")) {
                sendMessage(p, "no-permission-status");
                return true;
            }
            displayStatus(sender, p.getUniqueId(), p.getName());
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("sculk.admin")) {
                    sendMessage(sender, "no-permission-reload");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getLanguageManager().loadTranslations();
                plugin.loadLore();
                sendMessage(sender, "reload-success");
                break;

            case "toggle":
            case "chat":
                if (!(sender instanceof Player)) {
                    sendMessage(sender, "only-players-toggle");
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission("sculk.use")) {
                    sendMessage(player, "no-permission-toggle");
                    return true;
                }
                boolean enabled = plugin.toggleChatMode(player.getUniqueId());
                sendMessage(player, enabled ? "chat-mode-enabled" : "chat-mode-disabled");
                break;

            case "ask":
                if (!(sender instanceof Player)) {
                    sendMessage(sender, "only-players-ask");
                    return true;
                }
                Player askPlayer = (Player) sender;
                if (!askPlayer.hasPermission("sculk.use")) {
                    sendMessage(askPlayer, "no-permission-ask");
                    return true;
                }
                if (args.length < 2) {
                    sendMessage(askPlayer, "usage-sculk-ask");
                    return true;
                }
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                plugin.getAIService().processQuery(askPlayer, query);
                break;

            case "clear":
                if (!sender.hasPermission("sculk.admin")) {
                    sendMessage(sender, "no-permission-clear");
                    return true;
                }
                if (args.length < 2) {
                    if (!(sender instanceof Player)) {
                        sendMessage(sender, "only-players-clear-self");
                        return true;
                    }
                    Player p = (Player) sender;
                    plugin.clearPlayerProfile(p.getUniqueId());
                    sendMessage(sender, "command-clear-success-self");
                } else {
                    String targetName = args[1];
                    org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
                    if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                        String msg = plugin.getLanguageManager().getRawMessage("player-not-found", sender)
                                .replace("{player}", targetName);
                        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        return true;
                    }
                    plugin.clearPlayerProfile(target.getUniqueId());
                    String msg = plugin.getLanguageManager().getRawMessage("command-clear-success-other", sender)
                            .replace("{player}", target.getName() != null ? target.getName() : targetName);
                    plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                }
                break;

            case "status":
                if (args.length < 2) {
                    if (!(sender instanceof Player)) {
                        sendMessage(sender, "only-players-status");
                        return true;
                    }
                    Player p = (Player) sender;
                    if (!p.hasPermission("sculk.use")) {
                        sendMessage(p, "no-permission-status");
                        return true;
                    }
                    displayStatus(sender, p.getUniqueId(), p.getName());
                } else {
                    if (!sender.hasPermission("sculk.admin")) {
                        sendMessage(sender, "no-permission-status-other");
                        return true;
                    }
                    String targetName = args[1];
                    org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
                    if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                        String msg = plugin.getLanguageManager().getRawMessage("player-not-found", sender)
                                .replace("{player}", targetName);
                        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                        return true;
                    }
                    displayStatus(sender, target.getUniqueId(), target.getName() != null ? target.getName() : targetName);
                }
                break;

            case "profile":
                if (!sender.hasPermission("sculk.admin")) {
                    sendMessage(sender, "no-permission-profile");
                    return true;
                }
                if (args.length < 2) {
                    sendMessage(sender, "usage-profile");
                    return true;
                }
                String profileTargetName = args[1];
                org.bukkit.OfflinePlayer profileTarget = plugin.getServer().getOfflinePlayer(profileTargetName);
                if (profileTarget == null || (!profileTarget.hasPlayedBefore() && !profileTarget.isOnline())) {
                    String msg = plugin.getLanguageManager().getRawMessage("player-not-found", sender)
                            .replace("{player}", profileTargetName);
                    plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(msg));
                    return true;
                }
                displayProfile(sender, profileTarget.getUniqueId(),
                        profileTarget.getName() != null ? profileTarget.getName() : profileTargetName);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void displayStatus(CommandSender sender, UUID uuid, String playerName) {
        JsonObject profile = plugin.getPlayerProfile(uuid);
        int affection = profile.has("affection") ? profile.get("affection").getAsInt() : 0;

        String questStr = plugin.getLanguageManager().getRawMessage("status-quest-none", sender);
        if (profile.has("active_quest") && !profile.get("active_quest").isJsonNull()) {
            JsonObject quest = profile.getAsJsonObject("active_quest");
            String desc = quest.has("description") ? quest.get("description").getAsString() : "";
            String target = quest.has("target") ? quest.get("target").getAsString() : "";
            int targetAmount = quest.has("target_amount") ? quest.get("target_amount").getAsInt() : 0;
            int currentAmount = quest.has("current_amount") ? quest.get("current_amount").getAsInt() : 0;

            String questActiveTemplate = plugin.getLanguageManager().getRawMessage("status-quest-active", sender);
            questStr = questActiveTemplate
                    .replace("{description}", desc)
                    .replace("{current}", String.valueOf(currentAmount))
                    .replace("{total}", String.valueOf(targetAmount))
                    .replace("{target}", target);
        }

        String landmarksStr = "None";
        if (profile.has("landmarks")) {
            JsonObject landmarks = profile.getAsJsonObject("landmarks");
            java.util.Set<String> keys = landmarks.keySet();
            if (!keys.isEmpty()) {
                landmarksStr = String.join(", ", keys);
            }
        }

        String header = plugin.getLanguageManager().getRawMessage("status-header", sender).replace("{player}", playerName);
        String affMsg = plugin.getLanguageManager().getRawMessage("status-affection", sender).replace("{affection}", String.valueOf(affection));
        String landMsg = plugin.getLanguageManager().getRawMessage("status-landmarks", sender).replace("{landmarks}", landmarksStr);

        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(header));
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(affMsg));
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(questStr));
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(landMsg));
    }

    /**
     * Admin-only profile dump. Shows affection, quest, landmarks, facts, and
     * a trimmed history. Profiles are pretty-printed JSON for readability.
     */
    private void displayProfile(CommandSender sender, UUID uuid, String playerName) {
        JsonObject profile = plugin.getPlayerProfile(uuid);
        String header = plugin.getLanguageManager().getRawMessage("profile-header", sender).replace("{player}", playerName);
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(header));

        int affection = profile.has("affection") ? profile.get("affection").getAsInt() : 0;
        String affLine = plugin.getLanguageManager().getRawMessage("profile-section-affection", sender)
                .replace("{affection}", String.valueOf(affection));
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(affLine));

        // Quest
        if (profile.has("active_quest") && !profile.get("active_quest").isJsonNull()) {
            JsonObject quest = profile.getAsJsonObject("active_quest");
            String questSummary = String.format("%s %d/%d — %s",
                    quest.has("type") ? quest.get("type").getAsString() : "?",
                    quest.has("current_amount") ? quest.get("current_amount").getAsInt() : 0,
                    quest.has("target_amount") ? quest.get("target_amount").getAsInt() : 0,
                    quest.has("description") ? quest.get("description").getAsString() : "");
            String questLine = plugin.getLanguageManager().getRawMessage("profile-section-quest", sender)
                    .replace("{quest}", questSummary);
            plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(questLine));
        } else {
            String questLine = plugin.getLanguageManager().getRawMessage("profile-section-quest-none", sender);
            plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(questLine));
        }

        // Landmarks
        String landmarksStr = "none";
        if (profile.has("landmarks")) {
            JsonObject landmarks = profile.getAsJsonObject("landmarks");
            if (!landmarks.keySet().isEmpty()) {
                landmarksStr = String.join(", ", landmarks.keySet());
                String lLine = plugin.getLanguageManager().getRawMessage("profile-section-landmarks", sender)
                        .replace("{landmarks}", landmarksStr);
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(lLine));
            } else {
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getRawMessage("profile-section-landmarks-none", sender)));
            }
        }

        // Facts
        if (profile.has("facts")) {
            JsonArray facts = profile.getAsJsonArray("facts");
            if (facts.size() > 0) {
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getRawMessage("profile-section-facts", sender)));
                for (int i = 0; i < facts.size(); i++) {
                    plugin.adventure().sender(sender).sendMessage(
                            MiniMessage.miniMessage().deserialize(" <white>- " + facts.get(i).getAsString() + "</white>"));
                }
            } else {
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getRawMessage("profile-section-facts-none", sender)));
            }
        }

        // History (last few turns, JSON pretty-printed)
        if (profile.has("history")) {
            JsonArray history = profile.getAsJsonArray("history");
            if (history.size() > 0) {
                int turns = history.size() / 2;
                String hLine = plugin.getLanguageManager().getRawMessage("profile-section-history", sender)
                        .replace("{count}", String.valueOf(turns));
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(hLine));
                String pretty = new GsonBuilder().setPrettyPrinting().create().toJson(history);
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(
                        "<grey>" + pretty.replace("\n", "\n<grey>") + "</grey>"));
            } else {
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize(
                        plugin.getLanguageManager().getRawMessage("profile-section-history-none", sender)));
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "help-header");
        sendMessage(sender, "help-ask");
        sendMessage(sender, "help-toggle");
        sendMessage(sender, "help-status");
        if (sender.hasPermission("sculk.admin")) {
            sendMessage(sender, "help-clear");
            sendMessage(sender, "help-profile");
            sendMessage(sender, "help-reload");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sculk")) {
            if (args.length == 1) {
                List<String> subcommands = new ArrayList<>();
                if (sender.hasPermission("sculk.use")) {
                    subcommands.add("ask");
                    subcommands.add("toggle");
                    subcommands.add("status");
                }
                if (sender.hasPermission("sculk.admin")) {
                    subcommands.add("reload");
                    subcommands.add("clear");
                    subcommands.add("profile");
                }
                return subcommands.stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args.length == 2) {
                String sub = args[0].toLowerCase();
                if (sender.hasPermission("sculk.admin") &&
                        (sub.equals("clear") || sub.equals("status") || sub.equals("profile"))) {
                    return plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }
}
