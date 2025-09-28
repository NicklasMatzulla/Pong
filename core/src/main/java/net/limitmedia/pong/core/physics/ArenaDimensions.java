package net.limitmedia.pong.core.physics;

/**
 * Immutable arena description measured in meters.
 */
public record ArenaDimensions(float width, float height, float depth) {
    public static final ArenaDimensions COMPETITIVE = new ArenaDimensions(18f, 9f, 36f);

    public ArenaDimensions {
        width = Math.max(6f, width);
        height = Math.max(4f, height);
        depth = Math.max(12f, depth);
    }

    public ArenaDimensions widen(float delta) {
        return new ArenaDimensions(width + delta, height, depth);
    }

    public ArenaDimensions lengthen(float delta) {
        return new ArenaDimensions(width, height, depth + delta);
    }
}
