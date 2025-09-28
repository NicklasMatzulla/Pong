package net.limitmedia.pong.client.gameplay;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import net.limitmedia.pong.core.config.GameConfig.GameplaySettings;
import net.limitmedia.pong.core.physics.ArenaDimensions;

/**
 * Encapsulates camera placement and shake logic for the gameplay view so the
 * {@link net.limitmedia.pong.client.PongClient} can delegate visual updates to a
 * focused component.
 */
public final class CameraRig {
    private final Camera camera;
    private final Vector3f baseLocation = new Vector3f();
    private final Vector3f shakeOffset = new Vector3f();
    private final Vector3f working = new Vector3f();

    private float shakeStrength;
    private float shakeTimer;
    private float maxShakeScale = 0.3f;

    public CameraRig(Camera camera) {
        this.camera = camera;
    }

    /**
     * Applies the camera preset defined by the gameplay configuration and
     * resets any previous shake offset.
     */
    public void configure(GameplaySettings.CameraStyle style, ArenaDimensions arena) {
        switch (style) {
            case COURTSIDE -> baseLocation.set(0f, arena.height() * 0.55f, arena.depth() * 0.45f);
            case ANGLED -> baseLocation.set(0f, arena.height() * 0.9f, arena.depth() * 0.25f);
            default -> baseLocation.set(0f, arena.height(), 0f);
        }
        resetImmediate();
        shakeStrength = 0f;
        shakeTimer = 0f;
    }

    public void setMaxShakeScale(float scale) {
        maxShakeScale = FastMath.clamp(scale, 0f, 1f);
    }

    /**
     * Schedules a shake impulse scaled by the collision intensity.
     */
    public void triggerShake(float intensity, float maxSpeed) {
        if (maxShakeScale <= FastMath.ZERO_TOLERANCE || maxSpeed <= FastMath.ZERO_TOLERANCE) {
            return;
        }
        float normalized = FastMath.clamp(intensity / maxSpeed, 0f, 1f);
        shakeStrength = FastMath.clamp(shakeStrength + normalized * maxShakeScale, 0f, maxShakeScale);
        shakeTimer = 0f;
    }

    /**
     * Advances the shake animation each frame.
     */
    public void update(float tpf) {
        if (shakeStrength <= 0f) {
            resetImmediate();
            return;
        }
        shakeTimer += tpf;
        float attenuation = FastMath.exp(-3.2f * shakeTimer);
        float amplitude = shakeStrength * attenuation;
        if (amplitude < 0.01f) {
            shakeStrength = 0f;
            resetImmediate();
            return;
        }
        shakeOffset.set(
                FastMath.sin(shakeTimer * 18f) * amplitude,
                FastMath.cos(shakeTimer * 16f) * amplitude * 0.5f,
                FastMath.sin(shakeTimer * 14f) * amplitude * 0.4f);
        camera.setLocation(baseLocation.add(shakeOffset, working));
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    public void resetImmediate() {
        working.set(baseLocation);
        camera.setLocation(working);
        camera.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }
}
