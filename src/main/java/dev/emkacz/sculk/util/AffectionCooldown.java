package dev.emkacz.sculk.util;

/**
 * Pure logic for the positive-affection cooldown. Extracted from
 * {@code ActionManager} so it can be unit-tested without spinning up a
 * Bukkit server.
 */
public final class AffectionCooldown {

    /** Default 2-minute cooldown, matching the historical hardcoded value. */
    public static final long DEFAULT_COOLDOWN_MS = 120_000L;

    private AffectionCooldown() {}

    /**
     * Decide whether a positive affection change should be allowed right now.
     *
     * @param points             how much the relationship is changing (negative
     *                           means decrease, which is never on cooldown)
     * @param bypassCooldown     whether the AI claimed a high-value sacrifice
     *                           and wants to skip the cooldown
     * @param lastAffectionGain  epoch ms of the player's last positive gain
     *                           (0 if never)
     * @param now                current epoch ms
     * @param cooldownMs         the configured cooldown duration in ms
     * @return {@code true} if the change is allowed
     */
    public static boolean isPositiveChangeAllowed(
            int points,
            boolean bypassCooldown,
            long lastAffectionGain,
            long now,
            long cooldownMs
    ) {
        if (points <= 0 || bypassCooldown) {
            return true;
        }
        if (lastAffectionGain <= 0L) {
            return true;
        }
        return (now - lastAffectionGain) >= cooldownMs;
    }

    /**
     * Seconds remaining on the cooldown, clamped to 0. Returns 0 when no
     * cooldown is active.
     */
    public static long remainingSeconds(
            long lastAffectionGain,
            long now,
            long cooldownMs
    ) {
        if (lastAffectionGain <= 0L) {
            return 0L;
        }
        long elapsed = now - lastAffectionGain;
        if (elapsed >= cooldownMs) {
            return 0L;
        }
        return (cooldownMs - elapsed + 999L) / 1000L;
    }

    /**
     * Clamp an affection value to the [-100, +100] range.
     */
    public static int clamp(int value) {
        if (value < -100) return -100;
        if (value > 100) return 100;
        return value;
    }
}
