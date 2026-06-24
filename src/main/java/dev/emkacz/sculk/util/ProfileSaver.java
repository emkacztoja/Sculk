package dev.emkacz.sculk.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.emkacz.sculk.Sculk;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Debounced profile persistence. High-frequency mutations (e.g. a mob-farm
 * killing 100 zombies) call {@link #markDirty(UUID, JsonObject)} to register
 * a change. A periodic async task flushes all dirty profiles in one pass.
 * Forced saves (player quit, profile clear) bypass the dirty flag and write
 * immediately.
 */
public final class ProfileSaver {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Sculk plugin;
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private BukkitTask flushTask;

    public ProfileSaver(Sculk plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the periodic flush task. Call from {@code onEnable}.
     */
    public void start() {
        if (flushTask != null) {
            return;
        }
        long intervalTicks = 100L; // 5s default
        flushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flushDirty, intervalTicks, intervalTicks);
    }

    /**
     * Cancel the flush task. Call from {@code onDisable} after {@link #flushAllSync()}.
     */
    public void stop() {
        if (flushTask != null) {
            try {
                flushTask.cancel();
            } catch (Exception ignored) {}
            flushTask = null;
        }
    }

    /**
     * Register a profile as dirty. The next flush tick will write it. Safe to
     * call from any thread, and safe to call many times for the same player
     * (the profile is only written once per flush window).
     */
    public void markDirty(UUID uuid) {
        dirty.add(uuid);
    }

    /**
     * Force-write a single profile immediately on the calling thread. Use for
     * shutdowns, profile clears, or anywhere we MUST persist before returning.
     */
    public void saveSync(UUID uuid, JsonObject profile) {
        writeProfile(uuid, profile);
    }

    /**
     * Force-write a single profile asynchronously. Used for high-importance
     * saves (player quit) where a short delay is acceptable but the calling
     * thread must not block on disk.
     */
    public void saveAsync(UUID uuid, JsonObject profile) {
        JsonObject copy;
        synchronized (profile) {
            copy = profile.deepCopy();
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeProfile(uuid, copy));
    }

    /**
     * Flush all dirty profiles. Called periodically by the timer and once
     * synchronously on plugin disable.
     */
    public void flushDirty() {
        if (dirty.isEmpty()) {
            return;
        }
        // Snapshot and clear the dirty set so concurrent markDirty calls re-register
        Set<UUID> snapshot = new HashSet<>(dirty);
        dirty.removeAll(snapshot);
        Map<UUID, JsonObject> cache = plugin.profileCacheView();
        for (UUID uuid : snapshot) {
            JsonObject profile = cache.get(uuid);
            if (profile == null) {
                continue;
            }
            JsonObject copy;
            synchronized (profile) {
                copy = profile.deepCopy();
            }
            writeProfile(uuid, copy);
        }
    }

    /**
     * Flush all dirty profiles synchronously. Call from {@code onDisable} after
     * scheduler has been told to stop accepting new tasks.
     */
    public void flushAllSync() {
        flushDirty();
    }

    private void writeProfile(UUID uuid, JsonObject copy) {
        try {
            File folder = new File(plugin.getDataFolder(), "players");
            if (!folder.exists() && !folder.mkdirs()) {
                plugin.getLogger().warning("Could not create players folder: " + folder.getAbsolutePath());
                return;
            }
            restrictFolderPermissions(folder);
            Path file = folder.toPath().resolve(uuid + ".json");
            Files.writeString(file, GSON.toJson(copy), StandardCharsets.UTF_8);
            restrictFilePermissions(file.toFile());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player profile for " + uuid + ": " + e.getMessage());
        }
    }

    private static void restrictFolderPermissions(File folder) {
        try {
            // POSIX only — silently no-op on Windows / unsupported FS
            java.nio.file.attribute.PosixFileAttributeView view = java.nio.file.Files.getFileAttributeView(
                    folder.toPath(), java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE));
            }
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static void restrictFilePermissions(File file) {
        try {
            java.nio.file.attribute.PosixFileAttributeView view = java.nio.file.Files.getFileAttributeView(
                    file.toPath(), java.nio.file.attribute.PosixFileAttributeView.class);
            if (view != null) {
                view.setPermissions(java.util.EnumSet.of(
                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
            }
        } catch (Exception ignored) {
            // best effort
        }
    }
}
