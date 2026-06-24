package dev.emkacz.sculk.util;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-wide request gate backed by a counting semaphore. Caps the number
 * of in-flight LLM HTTP requests so a burst of triggers can't flood the
 * upstream API. Excess callers wait up to {@code acquireTimeoutMs} for a
 * permit, then give up.
 */
public final class RequestGate {

    private final Semaphore semaphore;
    private final int maxPermits;
    private final AtomicInteger inFlight = new AtomicInteger(0);
    private final AtomicInteger totalAcquired = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);

    public RequestGate(int maxConcurrent) {
        this.maxPermits = Math.max(1, maxConcurrent);
        this.semaphore = new Semaphore(this.maxPermits, true);
    }

    /**
     * Try to acquire a permit within {@code acquireTimeoutMs}. Returns
     * {@code null} on success (caller must {@link Permit#release()} it), or
     * a non-null rejection reason string if the gate is full.
     */
    public String tryAcquire(long acquireTimeoutMs) {
        if (maxPermits <= 0) {
            return null; // gate disabled
        }
        boolean ok;
        try {
            ok = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            totalRejected.incrementAndGet();
            return "Interrupted while waiting for a request slot.";
        }
        if (!ok) {
            totalRejected.incrementAndGet();
            return "Sculk is at capacity (" + maxPermits + " concurrent requests). Please try again in a moment.";
        }
        inFlight.incrementAndGet();
        totalAcquired.incrementAndGet();
        return null;
    }

    /** Release a previously-acquired permit. */
    public void release() {
        if (inFlight.get() <= 0) {
            return;
        }
        inFlight.decrementAndGet();
        semaphore.release();
    }

    public int inFlight() { return inFlight.get(); }
    public int maxPermits() { return maxPermits; }
    public long totalAcquired() { return totalAcquired.get(); }
    public long totalRejected() { return totalRejected.get(); }
}
