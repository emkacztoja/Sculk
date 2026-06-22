package dev.emkacz.sculk;

import com.google.gson.JsonObject;
import dev.emkacz.sculk.command.SculkCommand;
import dev.emkacz.sculk.listener.ChatListener;
import dev.emkacz.sculk.lang.LanguageManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Sculk extends JavaPlugin {

    private BukkitAudiences adventure;
    private LanguageManager languageManager;

    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    public LanguageManager getLanguageManager() {
        return this.languageManager;
    }

    // Thread-safe states tracking players in Sculk Chat Mode
    private final Set<UUID> togglePlayers = ConcurrentHashMap.newKeySet();

    // Thread-safe cooldown tracking per player
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    private final Map<String, String> cachedLoreMap = new ConcurrentHashMap<>();

    @Deprecated
    public String getCachedLore() {
        return getCachedLore("default");
    }

    public String getCachedLore(String lang) {
        String lore = cachedLoreMap.get(lang.toLowerCase());
        if (lore != null) {
            return lore;
        }
        String defaultLang = getConfig().getString("default-language", "en").toLowerCase();
        lore = cachedLoreMap.get(defaultLang);
        if (lore != null) {
            return lore;
        }
        lore = cachedLoreMap.get("en");
        if (lore != null) {
            return lore;
        }
        return cachedLoreMap.getOrDefault("default", "");
    }

    public void loadLore() {
        cachedLoreMap.clear();

        // 1. Load default lore.txt
        java.io.File defaultFile = new java.io.File(getDataFolder(), "lore.txt");
        if (!defaultFile.exists()) {
            saveResource("lore.txt", false);
        }
        try {
            String defaultLore = java.nio.file.Files.readString(defaultFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            cachedLoreMap.put("default", defaultLore);
        } catch (java.io.IOException e) {
            getLogger().severe("Failed to load lore.txt: " + e.getMessage());
        }

        // 2. Load language-specific lore files (e.g. lore_en.txt, lore_pl.txt)
        List<String> defaultLangs = List.of("en", "pl", "de", "es", "fr");
        for (String lang : defaultLangs) {
            java.io.File file = new java.io.File(getDataFolder(), "lore_" + lang + ".txt");
            if (!file.exists()) {
                try {
                    saveResource("lore_" + lang + ".txt", false);
                } catch (Exception e) {
                    getLogger().severe("Could not save default lore resource: lore_" + lang + ".txt (" + e.getMessage() + ")");
                }
            }
            if (file.exists()) {
                try {
                    String lore = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                    cachedLoreMap.put(lang, lore);
                } catch (java.io.IOException e) {
                    getLogger().severe("Failed to load lore_" + lang + ".txt: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onEnable() {
        // Initialize Adventure for Spigot integration
        this.adventure = BukkitAudiences.create(this);

        // Save default config.yml if not already present
        saveDefaultConfig();

        // Initialize and load translations
        this.languageManager = new LanguageManager(this);
        this.languageManager.loadTranslations();

        // Load custom lore/knowledge base
        loadLore();

        // Register ChatListener
        ChatListener chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);

        // Register Command Executor and Tab Completer
        SculkCommand commandHandler = new SculkCommand(this, chatListener);
        Objects.requireNonNull(getCommand("sculk")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("sculk")).setTabCompleter(commandHandler);

        getLogger().info("Sculk plugin enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        togglePlayers.clear();
        cooldowns.clear();
        getLogger().info("Sculk plugin disabled!");
    }

    // Toggle Chat Mode
    public boolean isChatModeEnabled(UUID uuid) {
        return togglePlayers.contains(uuid);
    }

    public boolean toggleChatMode(UUID uuid) {
        if (togglePlayers.contains(uuid)) {
            togglePlayers.remove(uuid);
            return false;
        } else {
            togglePlayers.add(uuid);
            return true;
        }
    }

    // Cooldown management
    public boolean isOnCooldown(UUID uuid) {
        long now = System.currentTimeMillis();
        Long expiry = cooldowns.get(uuid);
        if (expiry == null) {
            return false;
        }
        if (expiry <= now) {
            cooldowns.remove(uuid);
            return false;
        }
        return true;
    }

    public long getRemainingCooldownSeconds(UUID uuid) {
        Long expiry = cooldowns.get(uuid);
        if (expiry == null) {
            return 0;
        }
        long remaining = expiry - System.currentTimeMillis();
        return Math.max(0, (remaining + 999) / 1000);
    }

    public void setCooldown(UUID uuid, int seconds) {
        if (seconds <= 0) return;
        cooldowns.put(uuid, System.currentTimeMillis() + (seconds * 1000L));
    }

    // Thread-safe cache of loaded player profiles
    private final Map<UUID, JsonObject> profileCache = new ConcurrentHashMap<>();

    // Persistent Player Profile management
    public JsonObject getPlayerProfile(UUID uuid) {
        return profileCache.computeIfAbsent(uuid, id -> {
            java.io.File directory = new java.io.File(getDataFolder(), "players");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            java.io.File file = new java.io.File(directory, id.toString() + ".json");
            if (!file.exists()) {
                JsonObject defaultProfile = new JsonObject();
                defaultProfile.add("history", new com.google.gson.JsonArray());
                defaultProfile.add("facts", new com.google.gson.JsonArray());
                defaultProfile.add("landmarks", new JsonObject());
                return defaultProfile;
            }
            try {
                String content = java.nio.file.Files.readString(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                return com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            } catch (Exception e) {
                getLogger().severe("Failed to load player profile for " + id + ": " + e.getMessage());
                JsonObject defaultProfile = new JsonObject();
                defaultProfile.add("history", new com.google.gson.JsonArray());
                defaultProfile.add("facts", new com.google.gson.JsonArray());
                defaultProfile.add("landmarks", new JsonObject());
                return defaultProfile;
            }
        });
    }

    public void savePlayerProfileAsync(UUID uuid, JsonObject profile) {
        final JsonObject profileCopy;
        synchronized (profile) {
            profileCopy = profile.deepCopy();
        }
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            java.io.File directory = new java.io.File(getDataFolder(), "players");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            java.io.File file = new java.io.File(directory, uuid.toString() + ".json");
            try {
                java.nio.file.Files.writeString(
                    file.toPath(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(profileCopy),
                    java.nio.charset.StandardCharsets.UTF_8
                );
            } catch (java.io.IOException e) {
                getLogger().severe("Failed to save player profile for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public void clearStates(UUID uuid) {
        togglePlayers.remove(uuid);
        cooldowns.remove(uuid);
        profileCache.remove(uuid);
    }
}
