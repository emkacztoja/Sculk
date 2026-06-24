package dev.emkacz.sculk.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestGateTest {

    @Test
    void permitsUpToMax() {
        RequestGate gate = new RequestGate(2);
        assertNull(gate.tryAcquire(10));
        assertNull(gate.tryAcquire(10));
        assertEquals(2, gate.inFlight());
    }

    @Test
    void thirdCallerIsRejectedAfterTimeout() {
        RequestGate gate = new RequestGate(2);
        assertNull(gate.tryAcquire(10));
        assertNull(gate.tryAcquire(10));
        // both permits taken; third caller must be rejected, not block
        String rejection = gate.tryAcquire(50);
        assertNotNull(rejection);
        assertTrue(rejection.toLowerCase().contains("capacity"));
    }

    @Test
    void releasedPermitIsReusable() {
        RequestGate gate = new RequestGate(1);
        assertNull(gate.tryAcquire(10));
        assertNotNull(gate.tryAcquire(10));
        gate.release();
        assertEquals(0, gate.inFlight());
        assertNull(gate.tryAcquire(10));
    }

    @Test
    void countersTrackAcquiredAndRejected() {
        RequestGate gate = new RequestGate(1);
        gate.tryAcquire(10);
        gate.tryAcquire(10); // rejected
        gate.tryAcquire(10); // rejected
        assertEquals(1, gate.totalAcquired());
        assertEquals(2, gate.totalRejected());
    }

    @Test
    void maxPermitsFloorIsOne() {
        RequestGate gate = new RequestGate(0);
        // Even with 0 in config, we should still allow 1 (the floor) so the
        // plugin is never completely dead.
        assertEquals(1, gate.maxPermits());
    }

    @Test
    void releaseIsIdempotent() {
        RequestGate gate = new RequestGate(2);
        gate.release();
        gate.release();
        // inFlight stays at 0
        assertEquals(0, gate.inFlight());
        assertNull(gate.tryAcquire(10));
        gate.release();
        assertEquals(0, gate.inFlight());
    }
}
