package dev.emkacz.sculk.util;

import dev.emkacz.sculk.Sculk;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only CSV usage log for LLM API calls. Writes one line per call to
 * {@code plugins/sculk/usage.log} with timestamp, player, turn, token counts,
 * and the model identifier. Safe to call from any thread (uses synchronized
 * append).
 */
public final class UsageLogger {

    private static final String HEADER = "timestamp,player,turn,prompt_tokens,completion_tokens,total_tokens,model";

    private final Sculk plugin;

    public UsageLogger(Sculk plugin) {
        this.plugin = plugin;
    }

    /**
     * Append a usage line. Silently no-ops if the config flag is disabled.
     */
    public synchronized void log(UUID playerUuid, String playerName, int turn,
                                 int promptTokens, int completionTokens, int totalTokens,
                                 String model) {
        if (!plugin.getConfig().getBoolean("usage.log-to-file", true)) {
            return;
        }
        Path file = plugin.getDataFolder().toPath().resolve("usage.log");
        try {
            Files.createDirectories(file.getParent());
            boolean newFile = !Files.exists(file);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                if (newFile) {
                    writer.write(HEADER);
                    writer.newLine();
                }
                writer.write(String.format("%s,%s,%d,%d,%d,%d,%s",
                        Instant.now().toString(),
                        csvEscape(playerName == null ? playerUuid.toString() : playerName),
                        turn,
                        promptTokens,
                        completionTokens,
                        totalTokens,
                        csvEscape(model == null ? "" : model)));
                writer.newLine();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write usage.log entry: " + e.getMessage());
        }
    }

    private static String csvEscape(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
