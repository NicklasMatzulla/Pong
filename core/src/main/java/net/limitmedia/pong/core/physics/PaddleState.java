package net.limitmedia.pong.core.physics;

import com.jme3.math.Vector3f;

/**
 * Mutable paddle state used for both simulation and rendering.
 */
public final class PaddleState {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private float halfWidth = 1.2f;
    private float halfHeight = 2.4f;

    public Vector3f position() {
        return position;
    }

    public Vector3f velocity() {
        return velocity;
    }

    public float halfWidth() {
        return halfWidth;
    }

    public float halfHeight() {
        return halfHeight;
    }

    public PaddleState setDimensions(float halfWidth, float halfHeight) {
        this.halfWidth = Math.max(0.4f, halfWidth);
        this.halfHeight = Math.max(0.8f, halfHeight);
        return this;
    }
}
