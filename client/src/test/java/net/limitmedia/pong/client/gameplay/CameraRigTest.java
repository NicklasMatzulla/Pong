package net.limitmedia.pong.client.gameplay;

import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import net.limitmedia.pong.core.config.GameConfig;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraRigTest {
    @Test
    void configureSetsCameraLocationForTopDown() {
        Camera camera = new Camera(800, 600);
        CameraRig rig = new CameraRig(camera);

        rig.configure(GameConfig.GameplaySettings.CameraStyle.TOP_DOWN, ArenaDimensions.COMPETITIVE);

        assertEquals(new Vector3f(0f, ArenaDimensions.COMPETITIVE.height(), 0f), camera.getLocation());
    }

    @Test
    void zeroShakeScalePreventsMovement() {
        Camera camera = new Camera(800, 600);
        CameraRig rig = new CameraRig(camera);
        rig.configure(GameConfig.GameplaySettings.CameraStyle.ANGLED, ArenaDimensions.COMPETITIVE);
        Vector3f base = camera.getLocation().clone();

        rig.setMaxShakeScale(0f);
        rig.triggerShake(10f, 40f);
        rig.update(0.016f);

        assertEquals(base, camera.getLocation());
    }
}
