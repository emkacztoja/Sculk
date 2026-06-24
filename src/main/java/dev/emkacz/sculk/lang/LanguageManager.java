package dev.emkacz.sculk.lang;

import dev.emkacz.sculk.Sculk;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

public class LanguageManager {
    private final Sculk plugin;
    private final Map<String, FileConfiguration> translations = new HashMap<>();
    private String defaultLang = "en";

    public LanguageManager(Sculk plugin) {
        this.plugin = plugin;
    }

    public void loadTranslations() {
        translations.clear();
        defaultLang = plugin.getConfig().getString("default-language", "en").toLowerCase();

        // Ensure lang directory exists
        File langFolder = new File(plugin.getDataFolder(), "lang");
        if (!langFolder.exists()) {
            langFolder.mkdirs();
        }

        // Copy default languages from resources if not present
        List<String> defaultLangs = List.of("en", "pl", "de", "es", "fr");
        for (String lang : defaultLangs) {
            File file = new File(plugin.getDataFolder(), "lang/messages_" + lang + ".yml");
            if (!file.exists()) {
                try {
                    plugin.saveResource("lang/messages_" + lang + ".yml", false);
                } catch (Exception e) {
                    plugin.getLogger().severe("Could not save default translation resource: lang/messages_" + lang + ".yml (" + e.getMessage() + ")");
                }
            }
        }

        // Load all messages_*.yml files in the lang folder
        File[] files = langFolder.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                String langCode = name.substring("messages_".length(), name.length() - ".yml".length()).toLowerCase();
                try {
                    FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                    translations.put(langCode, config);
                    plugin.getLogger().info("Loaded translations for language: " + langCode);
                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to load translation file: " + file.getName() + " (" + e.getMessage() + ")");
                }
            }
        }
    }

    public String getLanguageCode(CommandSender sender) {
        return defaultLang;
    }

    public String getRawMessage(String key, String langCode) {
        // First try the requested language code (e.g. "en")
        FileConfiguration config = translations.get(langCode);
        if (config != null && config.contains(key)) {
            return config.getString(key);
        }

        // Fall back to configured default language
        config = translations.get(defaultLang);
        if (config != null && config.contains(key)) {
            return config.getString(key);
        }

        // Fall back to English if default language is not English
        if (!"en".equals(defaultLang)) {
            config = translations.get("en");
            if (config != null && config.contains(key)) {
                return config.getString(key);
            }
        }

        // Configuration overrides and legacy compatibility fallbacks
        if ("thinking-message".equals(key)) {
            return plugin.getConfig().getString("formatting.thinking-message", "<dark_purple><obfuscated>k</obfuscated> <dark_purple>Sculk is listening...</dark_purple> <dark_purple><obfuscated>k</obfuscated></dark_purple>");
        }
        if ("error-message".equals(key)) {
            return plugin.getConfig().getString("formatting.error-message", "<red>Sculk whispers: The void is silent... (Request failed)</red>");
        }
        if ("prefix".equals(key)) {
            return plugin.getConfig().getString("formatting.prefix", "<dark_purple>[Sculk]</dark_purple> ");
        }

        // Ultimate hardcoded fallbacks
        return getHardcodedFallback(key);
    }

    public String getRawMessage(String key, CommandSender sender) {
        return getRawMessage(key, getLanguageCode(sender));
    }

    private String getHardcodedFallback(String key) {
        return switch (key) {
            case "only-players-ask" -> "<red>Only players can ask Sculk.</red>";
            case "no-permission-ask" -> "<red>You do not have permission to ask Sculk.</red>";
            case "usage-ask" -> "<red>Usage: /ask <question></red>";
            case "no-permission-reload" -> "<red>You do not have permission to reload Sculk.</red>";
            case "reload-success" -> "<dark_purple>[Sculk]</dark_purple> <green>Configuration reloaded successfully!</green>";
            case "only-players-toggle" -> "<red>Only players can toggle Sculk Chat Mode.</red>";
            case "no-permission-toggle" -> "<red>You do not have permission to use Sculk.</red>";
            case "chat-mode-enabled" -> "<dark_purple>[Sculk]</dark_purple> <green>Sculk Chat Mode enabled! All your messages will query Sculk.</green>";
            case "chat-mode-disabled" -> "<dark_purple>[Sculk]</dark_purple> <red>Sculk Chat Mode disabled.</red>";
            case "usage-sculk-ask" -> "<red>Usage: /sculk ask <question></red>";
            case "help-header" -> "<dark_purple>--- [Sculk AI Helper] ---</dark_purple>";
            case "help-ask" -> "<gold>/sculk ask <question></gold> - Ask Sculk a direct question.";
            case "help-toggle" -> "<gold>/sculk toggle</gold> - Toggle chat mode (all chat queries Sculk).";
            case "help-reload" -> "<gold>/sculk reload</gold> - Reload the configuration.";
            case "cooldown-message" -> "<red>Please wait </red><gold>{remaining}s</gold><red> before asking Sculk again.</red>";
            case "thinking-message" -> "<dark_purple><obfuscated>k</obfuscated> <dark_purple>Sculk is listening...</dark_purple> <dark_purple><obfuscated>k</obfuscated></dark_purple>";
            case "error-message" -> "<red>Sculk whispers: The void is silent... (Request failed)</red>";
            case "only-players-clear-self" -> "<red>Only players can clear their own profile. Please specify a player name.</red>";
            case "no-permission-clear" -> "<red>You do not have permission to clear player profiles.</red>";
            case "command-clear-success-self" -> "<dark_purple>[Sculk]</dark_purple> <green>Your profile memory, facts, and landmarks have been cleared!</green>";
            case "command-clear-success-other" -> "<dark_purple>[Sculk]</dark_purple> <green>Cleared profile memory, facts, and landmarks for {player}!</green>";
            case "player-not-found" -> "<red>Player '{player}' not found or has never played here.</red>";
            case "only-players-status" -> "<red>Only players can check status. Please specify a player name.</red>";
            case "no-permission-status" -> "<red>You do not have permission to check status.</red>";
            case "no-permission-status-other" -> "<red>You do not have permission to check other players' status.</red>";
            case "status-header" -> "<dark_purple>--- [Sculk Status: {player}] ---</dark_purple>";
            case "status-affection" -> "<gold>Affection:</gold> <white>{affection} (-100 to +100)</white>";
            case "status-quest-active" -> "<gold>Active Quest:</gold> <white>{description} ({current}/{total} {target})</white>";
            case "status-quest-none" -> "<gold>Active Quest:</gold> <white>None</white>";
            case "status-landmarks" -> "<gold>Landmarks:</gold> <white>{landmarks}</white>";
            case "help-clear" -> "<gold>/sculk clear [player]</gold> - Clear profile memory, facts, and landmarks.";
            case "help-status" -> "<gold>/sculk status [player]</gold> - Display affection, active quest, and landmarks.";
            case "help-profile" -> "<gold>/sculk profile <player></gold> - (Admin) Dump the full player profile as JSON.";
            case "no-permission-profile" -> "<red>You do not have permission to view player profiles.</red>";
            case "usage-profile" -> "<red>Usage: /sculk profile <player></red>";
            case "profile-header" -> "<dark_purple>--- [Sculk Profile: {player}] ---</dark_purple>";
            case "profile-section-affection" -> "<gold>Affection:</gold> <white>{affection}</white>";
            case "profile-section-quest" -> "<gold>Active quest:</gold> <white>{quest}</white>";
            case "profile-section-quest-none" -> "<gold>Active quest:</gold> <white>none</white>";
            case "profile-section-landmarks" -> "<gold>Landmarks:</gold> <white>{landmarks}</white>";
            case "profile-section-landmarks-none" -> "<gold>Landmarks:</gold> <white>none</white>";
            case "profile-section-facts" -> "<gold>Long-term facts:</gold>";
            case "profile-section-facts-none" -> "<gold>Long-term facts:</gold> <white>none</white>";
            case "profile-section-history" -> "<gold>Recent history (last {count} turns):</gold>";
            case "profile-section-history-none" -> "<gold>Recent history:</gold> <white>empty</white>";
            case "server-busy" -> "<red>Sculk is busy serving other players. Please wait a moment and try again.</red>";
            case "quest-auto-completed" -> "<dark_purple>[Sculk]</dark_purple> <green><bold>Quest completed!</bold> Talk to Sculk to claim your reward.</green>";
            case "target-protected" -> "<red>That player is protected and cannot be targeted by Sculk.</red>";
            case "cannot-target-self" -> "<red>Sculk cannot target the player who triggered the action.</red>";
            default -> "";
        };
    }
}
