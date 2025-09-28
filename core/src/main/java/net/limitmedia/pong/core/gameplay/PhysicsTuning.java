package net.limitmedia.pong.core.gameplay;

/**
 * Encapsulates tunable physics parameters for the rally simulation.
 */
public record PhysicsTuning(
        float wallRestitution,
        float paddleRestitution,
        float spinInfluence,
        float linearDamping,
        float spinDamping,
        float minSpeed,
        float maxSpeed,
        float paddleControl
) {
    public PhysicsTuning {
        wallRestitution = clamp(wallRestitution, 0.5f, 1.1f);
        paddleRestitution = clamp(paddleRestitution, 0.5f, 1.1f);
        spinInfluence = Math.max(0f, spinInfluence);
        linearDamping = clamp(linearDamping, 0f, 1f);
        spinDamping = clamp(spinDamping, 0f, 1f);
        minSpeed = Math.max(2f, minSpeed);
        maxSpeed = Math.max(minSpeed, maxSpeed);
        paddleControl = clamp(paddleControl, 0f, 2f);
    }

    public PhysicsTuning withoutSpin() {
        return new PhysicsTuning(wallRestitution, paddleRestitution, 0f, linearDamping, 1f, minSpeed, maxSpeed, paddleControl);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
