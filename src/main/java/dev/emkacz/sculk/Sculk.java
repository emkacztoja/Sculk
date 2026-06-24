package dev.emkacz.sculk;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.emkacz.sculk.action.ActionManager;
import dev.emkacz.sculk.ai.AIService;
import dev.emkacz.sculk.command.SculkCommand;
import dev.emkacz.sculk.lang.LanguageManager;
import dev.emkacz.sculk.listener.ChatListener;
import dev.emkacz.sculk.listener.QuestListener;
import dev.emkacz.sculk.util.PlayerProfile;
import dev.emkacz.sculk.util.ProfileSaver;
import dev.emkacz.sculk.util.RequestGate;
import dev.emkacz.sculk.util.UsageLogger;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Sculk extends JavaPlugin {

    private BukkitAudiences adventure;
    private LanguageManager languageManager;
    private ActionManager actionManager;
    private AIService aiService;
    private RequestGate requestGate;
    private ProfileSaver profileSaver;
    private UsageLogger usageLogger;

    public BukkitAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Tried to access Adventure when the plugin was disabled!");
        }
        return this.adventure;
    }

    public LanguageManager getLanguageManager() {
        return this.languageManager;
    }

    public ActionManager getActionManager() {
        return this.actionManager;
    }

    public AIService getAIService() {
        return this.aiService;
    }

    public RequestGate getRequestGate() {
        return this.requestGate;
    }

    public ProfileSaver getProfileSaver() {
        return this.profileSaver;
    }

    public UsageLogger getUsageLogger() {
        return this.usageLogger;
    }

    /** Read-only view of the in-memory profile cache, used by the dirty-flag flush. */
    public Map<UUID, JsonObject> profileCacheView() {
        return java.util.Collections.unmodifiableMap(profileCache);
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
        File defaultFile = new File(getDataFolder(), "lore.txt");
        if (!defaultFile.exists()) {
            saveResource("lore.txt", false);
        }
        try {
            String defaultLore = Files.readString(defaultFile.toPath(), StandardCharsets.UTF_8);
            cachedLoreMap.put("default", defaultLore);
        } catch (IOException e) {
            getLogger().severe("Failed to load lore.txt: " + e.getMessage());
        }

        // 2. Load language-specific lore files (e.g. lore_en.txt, lore_pl.txt)
        java.util.List<String> defaultLangs = java.util.List.of("en", "pl", "de", "es", "fr");
        for (String lang : defaultLangs) {
            File file = new File(getDataFolder(), "lore_" + lang + ".txt");
            if (!file.exists()) {
                try {
                    saveResource("lore_" + lang + ".txt", false);
                } catch (Exception e) {
                    getLogger().severe("Could not save default lore resource: lore_" + lang + ".txt (" + e.getMessage() + ")");
                }
            }
            if (file.exists()) {
                try {
                    String lore = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    cachedLoreMap.put(lang, lore);
                } catch (IOException e) {
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

        // Initialize rate limit, profile saver, usage log
        int maxConcurrent = Math.max(0, getConfig().getInt("api.max-concurrent-requests", 4));
        this.requestGate = new RequestGate(maxConcurrent);
        this.profileSaver = new ProfileSaver(this);
        this.profileSaver.start();
        this.usageLogger = new UsageLogger(this);

        // Initialize actions and AI service
        this.actionManager = new ActionManager(this);
        this.aiService = new AIService(this);

        // Register ChatListener
        ChatListener chatListener = new ChatListener(this);
        getServer().getPluginManager().registerEvents(chatListener, this);
        getServer().getPluginManager().registerEvents(new QuestListener(this), this);

        // Register Command Executor and Tab Completer
        SculkCommand commandHandler = new SculkCommand(this);
        Objects.requireNonNull(getCommand("sculk")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("sculk")).setTabCompleter(commandHandler);

        getLogger().info("Sculk plugin enabled successfully! (request gate: " + maxConcurrent + " concurrent)");
    }

    @Override
    public void onDisable() {
        if (this.aiService != null) {
            this.aiService.shutdown();
        }
        if (this.profileSaver != null) {
            this.profileSaver.stop();
            this.profileSaver.flushAllSync();
        }
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

    /**
     * Returns the canonical "empty" profile. Every new profile MUST be created
     * via this method so the schema stays consistent across
     * {@link #getPlayerProfile(UUID)} and {@link #clearPlayerProfile(UUID)}.
     * Delegates to {@link PlayerProfile#empty()} so the shape is testable.
     */
    public static JsonObject newDefaultProfile() {
        return PlayerProfile.empty();
    }

    // Persistent Player Profile management
    public JsonObject getPlayerProfile(UUID uuid) {
        return profileCache.computeIfAbsent(uuid, id -> {
            File directory = new File(getDataFolder(), "players");
            if (!directory.exists()) {
                directory.mkdirs();
            }
            File file = new File(directory, id.toString() + ".json");
            if (!file.exists()) {
                return PlayerProfile.empty();
            }
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JsonObject loaded = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                PlayerProfile.ensureShape(loaded);
                return loaded;
            } catch (Exception e) {
                getLogger().severe("Failed to load player profile for " + id + ": " + e.getMessage());
                return PlayerProfile.empty();
            }
        });
    }

    /**
     * Mark a profile as needing persistence on the next flush tick.
     * Prefer this over {@link #savePlayerProfileAsync(UUID, JsonObject)} for
     * high-frequency call sites (mob kills, fact adds).
     */
    public void markPlayerProfileDirty(UUID uuid) {
        if (profileSaver != null) {
            profileSaver.markDirty(uuid);
        }
    }

    /**
     * Async-save a profile. Use for important state changes (player quit,
     * profile clear) where we want the write scheduled but don't want to
     * block the calling thread.
     */
    public void savePlayerProfileAsync(UUID uuid, JsonObject profile) {
        if (profileSaver != null) {
            profileSaver.saveAsync(uuid, profile);
        }
    }

    /**
     * Synchronous save. Use only on shutdown where the scheduler is gone.
     */
    public void savePlayerProfileSync(UUID uuid, JsonObject profile) {
        if (profileSaver != null) {
            profileSaver.saveSync(uuid, profile);
        }
    }

    public void clearStates(UUID uuid) {
        togglePlayers.remove(uuid);
        cooldowns.remove(uuid);
        profileCache.remove(uuid);
    }

    public void clearPlayerProfile(UUID uuid) {
        JsonObject defaultProfile = PlayerProfile.empty();
        profileCache.put(uuid, defaultProfile);
        if (profileSaver != null) {
            profileSaver.saveSync(uuid, defaultProfile);
        }

        togglePlayers.remove(uuid);
        cooldowns.remove(uuid);
    }
}
