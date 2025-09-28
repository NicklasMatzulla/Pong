package net.limitmedia.pong.core.net;

import java.time.Instant;

/**
 * Captures a player's input for client side prediction.
 */
public record InputFrame(long tick, float verticalAxis, float horizontalAxis, boolean boost, Instant timestamp) {
    public InputFrame clamp() {
        float vy = Math.max(-1f, Math.min(1f, verticalAxis));
        float hx = Math.max(-1f, Math.min(1f, horizontalAxis));
        return new InputFrame(tick, vy, hx, boost, timestamp);
    }
}
