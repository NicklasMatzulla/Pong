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
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.debug.WireBox;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Line;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;
import com.jme3.system.AppSettings;
import java.util.Locale;
import net.limitmedia.pong.client.net.ClientNetworkManager;
import net.limitmedia.pong.client.ui.JavaFxHud;
import net.limitmedia.pong.core.ai.PredictiveAiController;
import net.limitmedia.pong.core.audio.AudioMixer;
import net.limitmedia.pong.core.config.GameConfig;
import net.limitmedia.pong.core.config.GameConfig.GameplaySettings;
import net.limitmedia.pong.core.gameplay.ArenaProfile;
import net.limitmedia.pong.core.gameplay.PhysicsTuning;
import net.limitmedia.pong.core.localization.LocalizationService;
import net.limitmedia.pong.core.net.LagCompensator;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;
import net.limitmedia.pong.core.physics.PhysicsEngine;
import net.limitmedia.pong.core.physics.PhysicsEngine.CollisionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PongClient extends SimpleApplication {
    private static final Logger LOG = LoggerFactory.getLogger(PongClient.class);

    private final BallState ball = new BallState();
    private final PaddleState left = new PaddleState();
    private final PaddleState right = new PaddleState();
    private final LagCompensator lagCompensator = new LagCompensator(20);
    private final AudioMixer mixer = new AudioMixer();
    private final ClientNetworkManager networkManager = new ClientNetworkManager(lagCompensator);
    private final Vector3f leftPrevious = new Vector3f();
    private final Vector3f rightPrevious = new Vector3f();
    private final Vector3f baseCamera = new Vector3f();
    private final Vector3f shakeOffset = new Vector3f();
    private final Vector3f temp = new Vector3f();

    private ArenaDimensions arena = ArenaDimensions.COMPETITIVE;
    private ArenaProfile arenaProfile = ArenaProfile.CLASSIC;
    private PhysicsTuning physicsTuning = arenaProfile.tuning();
    private PhysicsEngine physicsEngine;
    private GameConfig config;
    private JavaFxHud hud;
    private LocalizationService localization;
    private PredictiveAiController aiController;
    private AudioNode ambience;
    private AudioNode bounceSfx;
    private Geometry ballGeometry;
    private Geometry ballTrailGeometry;
    private Geometry leftGeometry;
    private Geometry rightGeometry;
    private Geometry auroraRing;
    private Material auroraMaterial;
    private Node ballNode;
    private float screenShakeScale = 0.3f;
    private float shakeStrength;
    private float shakeTimer;
    private float environmentTimer;
    private boolean ballTrailEnabled = true;
    private final Vector3f lastBallPosition = new Vector3f();

    @Override
    public void simpleInitApp() {
        loadConfiguration();
        configureSettings();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        viewPort.setBackgroundColor(new ColorRGBA(0.05f, 0.06f, 0.08f, 1f));

        initLighting();
        initScene();
        initAudio();
        initHud();
        initInput();

        physicsEngine = new PhysicsEngine(arena, physicsTuning, this::handleCollision);
    }

    private void loadConfiguration() {
        try {
            config = GameConfig.loadDefault();
        } catch (Exception ex) {
            LOG.warn("Falling back to default configuration: {}", ex.getMessage());
            config = new GameConfig(
                    new GameConfig.VideoSettings(1920, 1080, false, true, 144, true,
                            GameConfig.VideoSettings.ShadowQuality.MEDIUM,
                            GameConfig.VideoSettings.Antialiasing.FXAA),
                    new GameConfig.AudioSettings(0.9f, 0.6f, 0.8f, 0.7f, 0.5f),
                    new GameplaySettings(GameplaySettings.BotDifficulty.MEDIUM, true, true, 11, 120,
                            GameplaySettings.ArenaProfileType.CLASSIC,
                            GameplaySettings.CameraStyle.ANGLED, 0.12f, true,
                            GameplaySettings.PhysicsSettings.defaults()),
                    new GameConfig.AccessibilitySettings(GameConfig.AccessibilitySettings.ColorBlindMode.OFF, 1f, 0.4f),
                    Locale.ENGLISH);
        }
        arenaProfile = ArenaProfile.from(config.gameplay().arenaProfile());
        physicsTuning = config.gameplay().enableSpin()
                ? toTuning(config.gameplay().physics())
                : toTuning(config.gameplay().physics()).withoutSpin();
        arena = arenaProfile.dimensions();
        ballTrailEnabled = config.gameplay().ballTrail();
        screenShakeScale = config.accessibility().screenShake();
        aiController = buildAiController(config.gameplay());
        mixer.setVolume(AudioMixer.Bus.MASTER, config.audio().master());
        mixer.setVolume(AudioMixer.Bus.MUSIC, config.audio().music());
        mixer.setVolume(AudioMixer.Bus.SFX, config.audio().sfx());
        mixer.setVolume(AudioMixer.Bus.UI, config.audio().ui());
    }

    private void configureSettings() {
        AppSettings appSettings = new AppSettings(true);
        GameConfig.VideoSettings video = config.video();
        appSettings.setTitle("Pong Velocity");
        appSettings.setResolution(video.width(), video.height());
        appSettings.setFullscreen(video.fullscreen());
        appSettings.setVSync(video.vsync());
        setSettings(appSettings);
    }

    private void initLighting() {
        AmbientLight ambient = new AmbientLight(ColorRGBA.White.mult(0.55f));
        rootNode.addLight(ambient);

        DirectionalLight keyLight = new DirectionalLight();
        keyLight.setColor(new ColorRGBA(0.9f, 0.95f, 1f, 1f));
        keyLight.setDirection(new Vector3f(-0.3f, -1f, -0.6f).normalizeLocal());
        rootNode.addLight(keyLight);

        DirectionalLight rimLight = new DirectionalLight();
        rimLight.setColor(new ColorRGBA(0.35f, 0.55f, 0.9f, 1f));
        rimLight.setDirection(new Vector3f(0.3f, -0.4f, 0.8f).normalizeLocal());
        rootNode.addLight(rimLight);
    }

    private void initScene() {
        Node environment = new Node("Environment");
        rootNode.attachChild(environment);

        Geometry table = new Geometry("ArenaFloor", new Box(arena.width() / 2f, 0.15f, arena.depth() / 2f));
        Material floorMat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        floorMat.setBoolean("UseMaterialColors", true);
        floorMat.setColor("Diffuse", new ColorRGBA(0.18f, 0.2f, 0.24f, 1f));
        floorMat.setColor("Ambient", new ColorRGBA(0.08f, 0.09f, 0.12f, 1f));
        table.setMaterial(floorMat);
        table.setShadowMode(RenderQueue.ShadowMode.Receive);
        environment.attachChild(table);

        WireBox bounds = new WireBox(arena.width() / 2f, arena.height() / 2f, arena.depth() / 2f);
        Geometry boundsGeom = new Geometry("Bounds", bounds);
        Material boundsMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        boundsMat.setColor("Color", new ColorRGBA(0.25f, 0.3f, 0.35f, 0.55f));
        boundsMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        boundsGeom.setMaterial(boundsMat);
        boundsGeom.setQueueBucket(RenderQueue.Bucket.Transparent);
        environment.attachChild(boundsGeom);

        Torus ring = new Torus(64, 128, 0.35f, arena.width());
        auroraRing = new Geometry("Aurora", ring);
        auroraMaterial = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        auroraMaterial.setColor("Color", new ColorRGBA(0.3f, 0.5f, 0.6f, 0.25f));
        auroraMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        auroraRing.setMaterial(auroraMaterial);
        auroraRing.rotate(FastMath.HALF_PI, 0, 0);
        auroraRing.setQueueBucket(RenderQueue.Bucket.Transparent);
        auroraRing.move(0, arena.height() * 0.35f, 0);
        environment.attachChild(auroraRing);

        ballGeometry = buildBallGeometry();
        ballNode = new Node("BallNode");
        ballNode.attachChild(ballGeometry);
        if (ballTrailEnabled) {
            ballTrailGeometry = buildBallTrail();
            ballNode.attachChild(ballTrailGeometry);
        }
        rootNode.attachChild(ballNode);

        leftGeometry = buildPaddleGeometry(new ColorRGBA(0.4f, 0.72f, 0.92f, 1f));
        rightGeometry = buildPaddleGeometry(new ColorRGBA(0.92f, 0.5f, 0.8f, 1f));
        rootNode.attachChild(leftGeometry);
        rootNode.attachChild(rightGeometry);

        FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
        if (config.video().postProcessing()) {
            BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
            bloom.setBloomIntensity(1.35f);
            bloom.setBlurScale(0.65f);
            fpp.addFilter(bloom);
            viewPort.addProcessor(fpp);
        }

        resetMatchState();
        configureCamera(config.gameplay().cameraStyle());
    }

    private void resetMatchState() {
        left.position().set(0, 0, -arena.depth() / 2f + 1.5f);
        right.position().set(0, 0, arena.depth() / 2f - 1.5f);
        leftPrevious.set(left.position());
        rightPrevious.set(right.position());
        ball.position().set(0, 0, 0);
        ball.velocity().set(0, 0, physicsTuning.minSpeed());
        ball.spin().set(0, 0, 0);
        lastBallPosition.set(ball.position());
        updateVisuals();
    }

    private Geometry buildBallGeometry() {
        Geometry geometry = new Geometry("Ball", new Sphere(32, 32, 0.6f));
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", new ColorRGBA(0.82f, 0.86f, 1f, 1f));
        mat.setColor("Ambient", new ColorRGBA(0.24f, 0.32f, 0.45f, 1f));
        mat.setColor("GlowColor", new ColorRGBA(0.35f, 0.55f, 1f, 1f));
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(mat);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        geometry.move(0, 0.6f, 0);
        return geometry;
    }

    private Geometry buildBallTrail() {
        Line streak = new Line(ball.position().clone(), ball.position().clone());
        streak.setLineWidth(2f);
        Geometry geometry = new Geometry("BallTrail", streak);
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", new ColorRGBA(0.35f, 0.6f, 1f, 0.45f));
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geometry.setMaterial(material);
        geometry.setQueueBucket(RenderQueue.Bucket.Transparent);
        return geometry;
    }

    private Geometry buildPaddleGeometry(ColorRGBA tint) {
        Geometry geometry = new Geometry("Paddle", new Box(1.2f, 2.4f, 0.25f));
        Material mat = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        mat.setBoolean("UseMaterialColors", true);
        mat.setColor("Diffuse", tint);
        mat.setColor("Ambient", tint.mult(0.55f));
        mat.setColor("Specular", ColorRGBA.White.mult(0.35f));
        mat.setFloat("Shininess", 16f);
        geometry.setMaterial(mat);
        geometry.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
        return geometry;
    }

    private void initAudio() {
        ambience = loadLoop("Sound/Effects/Footsteps.ogg", AudioMixer.Bus.MUSIC, 0.25f);
        if (ambience != null) {
            ambience.play();
        }
        bounceSfx = loadOneShot("Sound/Effects/Footsteps.ogg", AudioMixer.Bus.SFX);
    }

    private AudioNode loadLoop(String path, AudioMixer.Bus bus, float gain) {
        try {
            AudioNode node = new AudioNode(assetManager, path, false);
            node.setLooping(true);
            node.setPositional(false);
            node.setVolume(mixer.resolveGain(bus) * gain);
            rootNode.attachChild(node);
            return node;
        } catch (Exception ex) {
            LOG.warn("Could not load loop {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private AudioNode loadOneShot(String path, AudioMixer.Bus bus) {
        try {
            AudioNode node = new AudioNode(assetManager, path, false);
            node.setPositional(false);
            node.setLooping(false);
            node.setVolume(mixer.resolveGain(bus));
            rootNode.attachChild(node);
            return node;
        } catch (Exception ex) {
            LOG.warn("Could not load sfx {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private void initHud() {
        localization = new LocalizationService(config.locale());
        hud = new JavaFxHud(localization, mixer);
        hud.setScale(config.accessibility().uiScale());
        hud.show();
    }

    private void initInput() {
        inputManager.addMapping("MoveLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MoveRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MoveUp", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping("MoveDown", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("Pause", new KeyTrigger(com.jme3.input.KeyInput.KEY_ESCAPE));

        ActionListener actionListener = (name, isPressed, tpf) -> {
            if ("Pause".equals(name) && isPressed) {
                hud.togglePause();
            }
        };
        inputManager.addListener(actionListener, "Pause");

        AnalogListener analogListener = (name, value, tpf) -> {
            float sensitivity = 18f;
            switch (name) {
                case "MoveLeft" -> left.position().addLocal(-value * sensitivity, 0, 0);
                case "MoveRight" -> left.position().addLocal(value * sensitivity, 0, 0);
                case "MoveUp" -> left.position().addLocal(0, value * sensitivity, 0);
                case "MoveDown" -> left.position().addLocal(0, -value * sensitivity, 0);
            }
            clampPaddle(left);
        };
        inputManager.addListener(analogListener, "MoveLeft", "MoveRight", "MoveUp", "MoveDown");
    }

    private void clampPaddle(PaddleState paddle) {
        float maxX = arena.width() / 2f - paddle.halfWidth();
        float maxY = arena.height() / 2f - paddle.halfHeight();
        paddle.position().x = FastMath.clamp(paddle.position().x, -maxX, maxX);
        paddle.position().y = FastMath.clamp(paddle.position().y, -maxY, maxY);
    }

    private void configureCamera(GameplaySettings.CameraStyle style) {
        switch (style) {
            case COURTSIDE -> {
                cam.setLocation(new Vector3f(0f, arena.height() * 0.55f, arena.depth() * 0.45f));
                cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
            }
            case ANGLED -> {
                cam.setLocation(new Vector3f(0f, arena.height() * 0.9f, arena.depth() * 0.25f));
                cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
            }
            default -> {
                cam.setLocation(new Vector3f(0, arena.height(), 0));
                cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
            }
        }
        baseCamera.set(cam.getLocation());
        shakeOffset.set(0, 0, 0);
    }

    @Override
    public void simpleUpdate(float tpf) {
        updatePaddleVelocities(tpf);
        if (!networkManager.isConnected() && aiController != null) {
            aiController.update(right, ball, tpf);
            clampPaddle(right);
        }

        physicsEngine.simulate(ball, left, right, tpf);
        updateEnvironment(tpf);
        networkManager.update(tpf, ball, left, right);
        updateVisuals();
        applyCameraShake(tpf);
        recoverAudioDuck(tpf);

        hud.updateScore(localization.translate("hud.score", networkManager.getScore().left(), networkManager.getScore().right()));
        hud.updatePing(localization.translate("hud.ping", networkManager.getPing()));
        hud.updateTimer(localization.translate("hud.timer", formatTime(networkManager.getElapsed())));
    }

    private void updatePaddleVelocities(float tpf) {
        updateVelocity(left, leftPrevious, tpf);
        updateVelocity(right, rightPrevious, tpf);
    }

    private void updateVelocity(PaddleState paddle, Vector3f cache, float tpf) {
        if (tpf <= FastMath.ZERO_TOLERANCE) {
            return;
        }
        temp.set(paddle.position()).subtractLocal(cache).divideLocal(tpf);
        paddle.velocity().set(temp);
        cache.set(paddle.position());
    }

    private void updateVisuals() {
        ballNode.setLocalTranslation(ball.position());
        if (ballTrailGeometry != null) {
            Line line = (Line) ballTrailGeometry.getMesh();
            line.updatePoints(lastBallPosition, ball.position());
            lastBallPosition.set(ball.position());
        }
        leftGeometry.setLocalTranslation(left.position());
        rightGeometry.setLocalTranslation(right.position());
    }

    private void updateEnvironment(float tpf) {
        environmentTimer += tpf;
        if (auroraMaterial != null) {
            float hue = (FastMath.sin(environmentTimer * 0.3f) * 0.5f) + 0.5f;
            ColorRGBA color = fromHsv(hue, 0.45f, 0.75f, 0.28f);
            auroraMaterial.setColor("Color", color);
        }
        if (auroraRing != null) {
            auroraRing.rotate(0, tpf * 0.25f, 0);
        }
    }

    private void applyCameraShake(float tpf) {
        if (shakeStrength <= 0f) {
            if (!cam.getLocation().equals(baseCamera)) {
                cam.setLocation(baseCamera.clone());
                cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
            }
            return;
        }
        shakeTimer += tpf;
        float attenuation = FastMath.exp(-3.2f * shakeTimer);
        float amplitude = shakeStrength * attenuation;
        if (amplitude < 0.01f) {
            shakeStrength = 0f;
            cam.setLocation(baseCamera.clone());
            cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
            return;
        }
        shakeOffset.set(
                FastMath.sin(shakeTimer * 18f) * amplitude,
                FastMath.cos(shakeTimer * 16f) * amplitude * 0.5f,
                FastMath.sin(shakeTimer * 14f) * amplitude * 0.4f);
        cam.setLocation(baseCamera.add(shakeOffset, temp));
        cam.lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);
    }

    private void handleCollision(CollisionEvent event) {
        if (screenShakeScale > 0f) {
            shakeStrength = Math.min(screenShakeScale, shakeStrength + (event.intensity() / physicsTuning.maxSpeed()) * screenShakeScale);
            shakeTimer = 0f;
        }
        switch (event.type()) {
            case WALL -> hud.pushNotification(localization.translate("hud.wallHit"));
            case PADDLE -> {
                hud.flashCombo();
                playBounce(event.intensity());
            }
        }
    }

    private void playBounce(float intensity) {
        if (bounceSfx == null) {
            return;
        }
        float loudness = FastMath.clamp(intensity / physicsTuning.maxSpeed(), 0.25f, 1f);
        bounceSfx.setVolume(mixer.resolveGain(AudioMixer.Bus.SFX) * loudness);
        bounceSfx.playInstance();
        float duck = FastMath.clamp(1f - loudness * config.audio().ducking(), 0.35f, 1f);
        mixer.setDuck(AudioMixer.Bus.MUSIC, duck);
        if (ambience != null) {
            ambience.setVolume(mixer.resolveGain(AudioMixer.Bus.MUSIC) * 0.25f);
        }
    }

    private void recoverAudioDuck(float tpf) {
        float current = mixer.getDuck(AudioMixer.Bus.MUSIC);
        if (current >= 1f - 1e-3f) {
            return;
        }
        float restored = FastMath.clamp(current + tpf * 0.9f, 0f, 1f);
        if (restored != current) {
            mixer.setDuck(AudioMixer.Bus.MUSIC, restored);
            if (ambience != null) {
                ambience.setVolume(mixer.resolveGain(AudioMixer.Bus.MUSIC) * 0.25f);
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (ambience != null) {
            ambience.stop();
            ambience.removeFromParent();
        }
        if (bounceSfx != null) {
            bounceSfx.removeFromParent();
        }
        hud.dispose();
        networkManager.close();
    }

    private static PhysicsTuning toTuning(GameplaySettings.PhysicsSettings settings) {
        return new PhysicsTuning(settings.wallRestitution(), settings.paddleRestitution(), settings.spinInfluence(),
                settings.linearDamping(), settings.spinDamping(), settings.minSpeed(), settings.maxSpeed(),
                settings.paddleControl());
    }

    private PredictiveAiController buildAiController(GameplaySettings settings) {
        float speed;
        float anticipation;
        switch (settings.botDifficulty()) {
            case EASY -> {
                speed = 12f;
                anticipation = 0.12f;
            }
            case HARD -> {
                speed = 28f;
                anticipation = 0.24f;
            }
            default -> {
                speed = 18f;
                anticipation = 0.18f;
            }
        }
        return new PredictiveAiController(speed, anticipation, settings.aiErrorMargin());
    }

    private static ColorRGBA fromHsv(float h, float s, float v, float alpha) {
        h = (h % 1f + 1f) % 1f;
        s = FastMath.clamp(s, 0f, 1f);
        v = FastMath.clamp(v, 0f, 1f);
        float c = v * s;
        float x = c * (1 - Math.abs((h * 6f) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 1f / 6f) {
            r = c;
            g = x;
            b = 0;
        } else if (h < 2f / 6f) {
            r = x;
            g = c;
            b = 0;
        } else if (h < 3f / 6f) {
            r = 0;
            g = c;
            b = x;
        } else if (h < 4f / 6f) {
            r = 0;
            g = x;
            b = c;
        } else if (h < 5f / 6f) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }
        return new ColorRGBA(r + m, g + m, b + m, alpha);
    }

    private static String formatTime(float seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
