package dev.emkacz.sculk.command;

import dev.emkacz.sculk.Sculk;
import dev.emkacz.sculk.listener.ChatListener;
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
import java.util.stream.Collectors;

public class SculkCommand implements CommandExecutor, TabCompleter {

    private final Sculk plugin;
    private final ChatListener chatListener;

    public SculkCommand(Sculk plugin, ChatListener chatListener) {
        this.plugin = plugin;
        this.chatListener = chatListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle alias "/ask <question>" directly
        if (label.equalsIgnoreCase("ask")) {
            if (!(sender instanceof Player)) {
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<red>Only players can ask Sculk.</red>"));
                return true;
            }
            Player player = (Player) sender;
            if (!player.hasPermission("sculk.use")) {
                plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to ask Sculk.</red>"));
                return true;
            }
            if (args.length == 0) {
                plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /ask <question></red>"));
                return true;
            }
            String query = String.join(" ", args);
            chatListener.processQuery(player, query);
            return true;
        }

        // Handle main command "/sculk"
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("sculk.admin")) {
                    plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to reload Sculk.</red>"));
                    return true;
                }
                plugin.reloadConfig();
                plugin.loadLore();
                plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<dark_purple>[Sculk]</dark_purple> <green>Configuration reloaded successfully!</green>"));
                break;

            case "toggle":
            case "chat":
                if (!(sender instanceof Player)) {
                    plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<red>Only players can toggle Sculk Chat Mode.</red>"));
                    return true;
                }
                Player player = (Player) sender;
                if (!player.hasPermission("sculk.use")) {
                    plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to use Sculk.</red>"));
                    return true;
                }
                boolean enabled = plugin.toggleChatMode(player.getUniqueId());
                if (enabled) {
                    plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize("<dark_purple>[Sculk]</dark_purple> <green>Sculk Chat Mode enabled! All your messages will query Sculk.</green>"));
                } else {
                    plugin.adventure().player(player).sendMessage(MiniMessage.miniMessage().deserialize("<dark_purple>[Sculk]</dark_purple> <red>Sculk Chat Mode disabled.</red>"));
                }
                break;

            case "ask":
                if (!(sender instanceof Player)) {
                    plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<red>Only players can ask Sculk.</red>"));
                    return true;
                }
                Player askPlayer = (Player) sender;
                if (!askPlayer.hasPermission("sculk.use")) {
                    plugin.adventure().player(askPlayer).sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission to ask Sculk.</red>"));
                    return true;
                }
                if (args.length < 2) {
                    plugin.adventure().player(askPlayer).sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /sculk ask <question></red>"));
                    return true;
                }
                String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                chatListener.processQuery(askPlayer, query);
                break;

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<dark_purple>--- [Sculk AI Helper] ---</dark_purple>"));
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<gold>/sculk ask <question></gold> - Ask Sculk a direct question."));
        plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<gold>/sculk toggle</gold> - Toggle chat mode (all chat queries Sculk)."));
        if (sender.hasPermission("sculk.admin")) {
            plugin.adventure().sender(sender).sendMessage(MiniMessage.miniMessage().deserialize("<gold>/sculk reload</gold> - Reload the configuration."));
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
                }
                if (sender.hasPermission("sculk.admin")) {
                    subcommands.add("reload");
                }
                return subcommands.stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }
}
