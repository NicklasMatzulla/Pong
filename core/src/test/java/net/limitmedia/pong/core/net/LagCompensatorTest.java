package net.limitmedia.pong.core.net;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LagCompensatorTest {
    @Test
    void averagesPingOverHistory() {
        LagCompensator compensator = new LagCompensator(5);
        compensator.record(Instant.EPOCH, Instant.EPOCH.plusMillis(60));
        compensator.record(Instant.EPOCH, Instant.EPOCH.plusMillis(80));
        assertEquals(70f, compensator.getAverage(), 0.1f);
    }

    @Test
    void capsHistorySize() {
        LagCompensator compensator = new LagCompensator(2);
        float first = compensator.record(Instant.EPOCH, Instant.EPOCH.plusMillis(20));
        float second = compensator.record(Instant.EPOCH, Instant.EPOCH.plusMillis(40));
        float third = compensator.record(Instant.EPOCH, Instant.EPOCH.plusMillis(60));
        assertTrue(third >= 40f && third <= 60f, () -> "average was " + third + " after inputs " + first + ", " + second);
    }

    @Test
    void calculatesTicksBehind() {
        LagCompensator compensator = new LagCompensator(4);
        compensator.record(Instant.EPOCH, Instant.EPOCH.plusMillis(50));
        long ticks = compensator.ticksBehind(120, 100, 60f);
        assertTrue(ticks >= 0);
    }

    @Test
    void returnsZeroWhenNoSamples() {
        LagCompensator compensator = new LagCompensator(4);
        assertEquals(0f, compensator.getAverage());
    }
}
