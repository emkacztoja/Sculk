package dev.emkacz.sculk.util;

import java.util.List;
import java.util.Locale;

/**
 * Whitelist matcher for AI-issued console commands. Performs a case-insensitive
 * prefix match (with word-boundary semantics: the command must EQUAL the
 * resolved prefix, or START with it followed by a space) against a list of
 * configured prefixes. {@code %player%} placeholders are resolved to the
 * actual player name before comparison.
 */
public final class CommandPrefixMatcher {

    private final List<String> rawPrefixes;

    public CommandPrefixMatcher(List<String> rawPrefixes) {
        this.rawPrefixes = rawPrefixes;
    }

    /**
     * @return true if {@code command} is allowed by the configured prefix list.
     */
    public boolean isAllowed(String command, String playerName) {
        if (command == null || command.isBlank()) {
            return false;
        }
        String lowerCmd = command.toLowerCase(Locale.ROOT).trim();
        for (String rawPrefix : rawPrefixes) {
            if (rawPrefix == null || rawPrefix.isBlank()) {
                continue;
            }
            String resolved = rawPrefix.replace("%player%", playerName).toLowerCase(Locale.ROOT).trim();
            if (resolved.isEmpty()) {
                continue;
            }
            if (lowerCmd.equals(resolved) || lowerCmd.startsWith(resolved + " ")) {
                return true;
            }
        }
        return false;
    }
}
