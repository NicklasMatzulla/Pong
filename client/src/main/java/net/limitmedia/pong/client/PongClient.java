package net.limitmedia.pong.client;

import com.jme3.app.SimpleApplication;
import com.jme3.audio.AudioNode;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.system.AppSettings;
import java.util.EnumMap;
import net.limitmedia.pong.core.audio.AudioMixer;
import net.limitmedia.pong.core.config.GameConfig;
import net.limitmedia.pong.core.localization.LocalizationService;
import net.limitmedia.pong.core.net.LagCompensator;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;
import net.limitmedia.pong.core.physics.PhysicsEngine;
import net.limitmedia.pong.core.powerup.PowerUpManager;
import net.limitmedia.pong.core.powerup.PowerUpType;
import net.limitmedia.pong.client.net.ClientNetworkManager;
import net.limitmedia.pong.client.ui.JavaFxHud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PongClient extends SimpleApplication {
    private static final Logger LOG = LoggerFactory.getLogger(PongClient.class);

    private final ArenaDimensions arena = ArenaDimensions.COMPETITIVE.lengthen(6f);
    private final BallState ball = new BallState();
    private final PaddleState left = new PaddleState();
    private final PaddleState right = new PaddleState();
    private final PowerUpManager powerUps = new PowerUpManager();
    private final LagCompensator lagCompensator = new LagCompensator(20);
    private final AudioMixer mixer = new AudioMixer();
    private final EnumMap<PowerUpType, Geometry> powerUpVisuals = new EnumMap<>(PowerUpType.class);
    private final ClientNetworkManager networkManager = new ClientNetworkManager(lagCompensator);

    private PhysicsEngine physicsEngine;
    private JavaFxHud hud;
    private LocalizationService localization;
    private AudioNode ambience;
    private Geometry ballGeometry;
    private Geometry leftGeometry;
    private Geometry rightGeometry;

    @Override
    public void simpleInitApp() {
        AppSettings appSettings = new AppSettings(true);
        appSettings.setTitle("Pong Velocity");
        appSettings.setVSync(true);
        setSettings(appSettings);

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);

        initLighting();
        initScene();
        initAudio();
        initHud();
        initInput();

        physicsEngine = new PhysicsEngine(arena, 0.92f, 0.96f, 1.8f, event -> {
            switch (event.type()) {
                case WALL -> hud.pushNotification(localization.translate("hud.timer", String.format("%.2f", event.detail())));
                case PADDLE -> hud.flashCombo();
            }
        });
    }

    private void initLighting() {
        AmbientLight ambient = new AmbientLight(ColorRGBA.White.mult(0.6f));
        rootNode.addLight(ambient);
        DirectionalLight keyLight = new DirectionalLight();
        keyLight.setColor(ColorRGBA.White);
        keyLight.setDirection(new Vector3f(-0.3f, -1f, -0.6f).normalizeLocal());
        rootNode.addLight(keyLight);
    }

    private void initScene() {
        Geometry table = new Geometry("Arena", new Box(arena.width() / 2f, 0.1f, arena.depth() / 2f));
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(0.2f, 0.2f, 0.25f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.1f, 0.1f, 0.15f, 1f));
        mat.setBoolean("UseMaterialColors", true);
        table.setMaterial(mat);
        table.setShadowMode(RenderQueue.ShadowMode.Receive);
        rootNode.attachChild(table);

        ballGeometry = buildBallGeometry();
        rootNode.attachChild(ballGeometry);

        leftGeometry = buildPaddleGeometry();
        rightGeometry = buildPaddleGeometry();
        rootNode.attachChild(leftGeometry);
        rootNode.attachChild(rightGeometry);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
        bloom.setBloomIntensity(1.2f);
        fpp.addFilter(bloom);
        viewPort.addProcessor(fpp);

        left.position().set(0, 0, -arena.depth() / 2f + 1.5f);
        right.position().set(0, 0, arena.depth() / 2f - 1.5f);

        cam.setLocation(new Vector3f(0, arena.height(), 0));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    private Geometry buildBallGeometry() {
        Mesh mesh = new Sphere(24, 24, 0.6f);
        Geometry geometry = new Geometry("Ball", mesh);
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(0.8f, 0.82f, 1f, 1f));
        mat.setColor("GlowColor", new ColorRGBA(0.2f, 0.4f, 1f, 1f));
        mat.setBoolean("UseMaterialColors", true);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(mat);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.move(0, 0.6f, 0);
        return geometry;
    }

    private Geometry buildPaddleGeometry() {
        Geometry geometry = new Geometry("Paddle", new Box(1.2f, 2.4f, 0.2f));
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setColor("Diffuse", new ColorRGBA(0.5f, 0.7f, 0.9f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.3f, 0.3f, 0.4f, 1f));
        mat.setBoolean("UseMaterialColors", true);
        geometry.setMaterial(mat);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return geometry;
    }

    private void initAudio() {
        ambience = new AudioNode(assetManager, "Sound/Effects/Footsteps.ogg", false);
        ambience.setLooping(true);
        ambience.setVolume(mixer.resolveGain(AudioMixer.Bus.MUSIC) * 0.3f);
        rootNode.attachChild(ambience);
        ambience.play();
    }

    private void initHud() {
        java.util.Locale locale = java.util.Locale.ENGLISH;
        try {
            locale = GameConfig.loadDefault().locale();
        } catch (Exception ex) {
            LOG.warn("Failed to load default config, falling back to EN: {}", ex.getMessage());
        }
        localization = new LocalizationService(locale);
        hud = new JavaFxHud(localization, mixer);
        hud.show();
    }

    private void initInput() {
        inputManager.addMapping("MoveLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MoveRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MoveUp", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping("MoveDown", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("Boost", new KeyTrigger(com.jme3.input.KeyInput.KEY_SPACE));
        inputManager.addMapping("Pause", new KeyTrigger(com.jme3.input.KeyInput.KEY_ESCAPE));

        ActionListener actionListener = (name, isPressed, tpf) -> {
            if ("Pause".equals(name) && isPressed) {
                hud.togglePause();
            }
        };
        inputManager.addListener(actionListener, "Pause");

        AnalogListener analogListener = (name, value, tpf) -> {
            float sensitivity = 20f;
            switch (name) {
                case "MoveLeft" -> left.position().addLocal(-value * sensitivity, 0, 0);
                case "MoveRight" -> left.position().addLocal(value * sensitivity, 0, 0);
                case "MoveUp" -> left.position().addLocal(0, value * sensitivity, 0);
                case "MoveDown" -> left.position().addLocal(0, -value * sensitivity, 0);
            }
        };
        inputManager.addListener(analogListener, "MoveLeft", "MoveRight", "MoveUp", "MoveDown");
    }

    @Override
    public void simpleUpdate(float tpf) {
        physicsEngine.simulate(ball, left, right, tpf);
        networkManager.update(tpf, ball, left, right);
        updateVisuals();
        hud.updateScore(localization.translate("hud.score", networkManager.getScore().left(), networkManager.getScore().right()));
        hud.updatePing(localization.translate("hud.ping", networkManager.getPing()));
        hud.updateTimer(localization.translate("hud.timer", formatTime(networkManager.getElapsed())));
    }

    private void updateVisuals() {
        if (ballGeometry != null) {
            ballGeometry.setLocalTranslation(ball.position());
        }
        if (leftGeometry != null) {
            leftGeometry.setLocalTranslation(left.position());
        }
        if (rightGeometry != null) {
            rightGeometry.setLocalTranslation(right.position());
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (ambience != null) {
            ambience.stop();
            ambience.removeFromParent();
        }
        hud.dispose();
        networkManager.close();
    }

    private static String formatTime(float seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
