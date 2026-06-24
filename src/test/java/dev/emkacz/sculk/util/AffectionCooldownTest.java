package dev.emkacz.sculk.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AffectionCooldownTest {

    @Test
    void negativeChangeIsAlwaysAllowed() {
        long now = 1_000_000L;
        long last = now; // just had a positive change
        // points < 0 should always pass regardless of cooldown or bypass flag
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(-5, false, last, now, 120_000L));
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(-5, false, 0L, now, 120_000L));
    }

    @Test
    void bypassCooldownSkipsCooldownCheck() {
        long now = 1_000_000L;
        long last = now; // just had a positive change
        // bypass=true should always allow positive change
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(10, true, last, now, 120_000L));
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(10, true, 0L, now, 120_000L));
    }

    @Test
    void firstPositiveChangeIsAlwaysAllowed() {
        // last=0 means no prior positive change → allowed
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(10, false, 0L, 1_000_000L, 120_000L));
    }

    @Test
    void positiveChangeWithinCooldownIsBlocked() {
        long now = 1_000_000L;
        long last = now - 30_000L; // 30s ago, cooldown is 120s
        assertFalse(AffectionCooldown.isPositiveChangeAllowed(10, false, last, now, 120_000L));
    }

    @Test
    void positiveChangeAfterCooldownIsAllowed() {
        long now = 1_000_000L;
        long last = now - 121_000L; // just past cooldown
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(10, false, last, now, 120_000L));
    }

    @Test
    void zeroPointsIsNotTreatedAsPositive() {
        // points == 0 is "no change", not a positive change → should not be blocked
        long now = 1_000_000L;
        long last = now;
        assertTrue(AffectionCooldown.isPositiveChangeAllowed(0, false, last, now, 120_000L));
    }

    @Test
    void remainingSecondsClampsToZero() {
        long now = 1_000_000L;
        long last = now - 200_000L; // already past cooldown
        assertEquals(0L, AffectionCooldown.remainingSeconds(last, now, 120_000L));
    }

    @Test
    void remainingSecondsRoundsUp() {
        long now = 1_000_000L;
        long last = now - 119_500L; // 500ms left of a 120s cooldown
        // Rounded up: 1s remaining
        assertEquals(1L, AffectionCooldown.remainingSeconds(last, now, 120_000L));
    }

    @Test
    void remainingSecondsReturnsZeroWhenNeverSet() {
        assertEquals(0L, AffectionCooldown.remainingSeconds(0L, 1_000_000L, 120_000L));
    }

    @Test
    void clampIsInRange() {
        assertEquals(-100, AffectionCooldown.clamp(-500));
        assertEquals(-100, AffectionCooldown.clamp(-100));
        assertEquals(-50, AffectionCooldown.clamp(-50));
        assertEquals(0, AffectionCooldown.clamp(0));
        assertEquals(50, AffectionCooldown.clamp(50));
        assertEquals(100, AffectionCooldown.clamp(100));
        assertEquals(100, AffectionCooldown.clamp(500));
    }
}
