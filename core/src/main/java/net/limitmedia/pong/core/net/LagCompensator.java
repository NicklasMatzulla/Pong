package net.limitmedia.pong.core.net;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Simple latency tracker that can smooth ping and compensate tick offsets.
 */
public final class LagCompensator {
    private final Deque<Float> history = new ArrayDeque<>();
    private final int maxSamples;

    public LagCompensator(int maxSamples) {
        this.maxSamples = Math.max(4, maxSamples);
    }

    public float record(Instant sendTime, Instant receiveTime) {
        float rtt = Duration.between(sendTime, receiveTime).toMillis();
        if (history.size() == maxSamples) {
            history.removeFirst();
        }
        history.addLast(rtt);
        return getAverage();
    }

    public float getAverage() {
        if (history.isEmpty()) {
            return 0f;
        }
        float total = 0f;
        for (float value : history) {
            total += value;
        }
        return total / history.size();
    }

    public long ticksBehind(long serverTick, long localTick, float tickRate) {
        float deltaTicks = (serverTick - localTick) - (getAverage() / 1000f * tickRate);
        return Math.round(deltaTicks);
    }
}
