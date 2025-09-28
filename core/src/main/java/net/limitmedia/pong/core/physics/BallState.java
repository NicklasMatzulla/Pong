package net.limitmedia.pong.core.physics;

import com.jme3.math.Vector3f;

/**
 * Mutable structure representing the simulated ball.
 */
public final class BallState {
    private final Vector3f position = new Vector3f();
    private final Vector3f velocity = new Vector3f();
    private final Vector3f spin = new Vector3f();

    public BallState setPosition(Vector3f other) {
        this.position.set(other);
        return this;
    }

    public BallState setVelocity(Vector3f other) {
        this.velocity.set(other);
        return this;
    }

    public BallState setSpin(Vector3f other) {
        this.spin.set(other);
        return this;
    }

    public Vector3f position() {
        return position;
    }

    public Vector3f velocity() {
        return velocity;
    }

    public Vector3f spin() {
        return spin;
    }
}
