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

        // Handle main command "/sculk"
        if (args.length == 0) {
            sendHelp(sender);
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
                if (enabled) {
                    sendMessage(player, "chat-mode-enabled");
                } else {
                    sendMessage(player, "chat-mode-disabled");
                }
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

            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sendMessage(sender, "help-header");
        sendMessage(sender, "help-ask");
        sendMessage(sender, "help-toggle");
        if (sender.hasPermission("sculk.admin")) {
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
