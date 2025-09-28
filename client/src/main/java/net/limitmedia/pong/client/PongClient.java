package net.limitmedia.pong.client;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.system.AppSettings;
import java.util.Locale;
import net.limitmedia.pong.client.audio.ClientAudioController;
import net.limitmedia.pong.client.gameplay.ArenaScene;
import net.limitmedia.pong.client.gameplay.CameraRig;
import net.limitmedia.pong.client.input.MousePaddleInput;
import net.limitmedia.pong.client.net.ClientNetworkManager;
import net.limitmedia.pong.client.ui.JavaFxHud;
import net.limitmedia.pong.client.presentation.ThemeColorUtils;
import net.limitmedia.pong.core.ai.PredictiveAiController;
import net.limitmedia.pong.core.audio.AudioMixer;
import net.limitmedia.pong.core.config.GameConfig;
import net.limitmedia.pong.core.config.GameConfig.GameplaySettings;
import net.limitmedia.pong.core.config.GameConfig.PresentationSettings;
import net.limitmedia.pong.core.gameplay.ArenaProfile;
import net.limitmedia.pong.core.gameplay.PhysicsTuning;
import net.limitmedia.pong.core.localization.LocalizationService;
import net.limitmedia.pong.core.net.LagCompensator;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;
import net.limitmedia.pong.core.physics.PhysicsEngine;
import net.limitmedia.pong.core.physics.PhysicsEngine.CollisionEvent;
import net.limitmedia.pong.core.presentation.ThemeDefinition;
import net.limitmedia.pong.core.presentation.ThemeLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main jME entry point for the LWJGL client. The class focuses on high-level
 * orchestration while specialised helpers take care of rendering, input and
 * audio management.
 */
public final class PongClient extends SimpleApplication {
    private static final Logger LOG = LoggerFactory.getLogger(PongClient.class);

    private final BallState ball = new BallState();
    private final PaddleState left = new PaddleState();
    private final PaddleState right = new PaddleState();
    private final Vector3f leftPrevious = new Vector3f();
    private final Vector3f rightPrevious = new Vector3f();
    private final Vector3f temp = new Vector3f();

    private final LagCompensator lagCompensator = new LagCompensator(20);
    private final AudioMixer mixer = new AudioMixer();
    private final ClientNetworkManager networkManager = new ClientNetworkManager(lagCompensator);

    private ArenaDimensions arena = ArenaDimensions.COMPETITIVE;
    private ArenaProfile arenaProfile = ArenaProfile.CLASSIC;
    private PhysicsTuning physicsTuning = arenaProfile.tuning();
    private PhysicsEngine physicsEngine;

    private GameConfig config;
    private ThemeDefinition theme;
    private LocalizationService localization;
    private JavaFxHud hud;
    private PredictiveAiController aiController;

    private ArenaScene arenaScene;
    private CameraRig cameraRig;
    private ClientAudioController audioController;
    private MousePaddleInput paddleInput;

    private boolean ballTrailEnabled = true;
    private float screenShakeScale = 0.3f;

    @Override
    public void simpleInitApp() {
        loadConfiguration();
        configureSettings();

        flyCam.setEnabled(false);
        inputManager.setCursorVisible(true);
        viewPort.setBackgroundColor(ThemeColorUtils.fromHex(theme.arena().backgroundColor(), 1f));

        initLighting();
        initScene();
        initHud();
        initAudio();
        initInput();

        physicsEngine = new PhysicsEngine(arena, physicsTuning, this::handleCollision);
        resetMatchState();
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
                    GameConfig.PresentationSettings.defaults(),
                    Locale.ENGLISH);
        }
        arenaProfile = ArenaProfile.from(config.gameplay().arenaProfile());
        physicsTuning = config.gameplay().enableSpin()
                ? toTuning(config.gameplay().physics())
                : toTuning(config.gameplay().physics()).withoutSpin();
        arena = arenaProfile.dimensions();
        ballTrailEnabled = config.gameplay().ballTrail() && theme.effects().trailFade() > 0.05f;
        loadTheme(config.presentation());
        screenShakeScale = config.accessibility().screenShake() * theme.effects().shakeMultiplier();
        aiController = buildAiController(config.gameplay());
        mixer.setVolume(AudioMixer.Bus.MASTER, config.audio().master());
        mixer.setVolume(AudioMixer.Bus.MUSIC, config.audio().music());
        mixer.setVolume(AudioMixer.Bus.SFX, config.audio().sfx());
        mixer.setVolume(AudioMixer.Bus.UI, config.audio().ui());
    }

    private void loadTheme(PresentationSettings presentation) {
        try {
            theme = ThemeLoader.load(presentation.theme());
        } catch (RuntimeException ex) {
            LOG.warn("Falling back to default theme after failure: {}", ex.getMessage());
            theme = ThemeLoader.loadDefault();
        }
        if (theme == null) {
            theme = ThemeLoader.loadDefault();
        }
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
        arenaScene = new ArenaScene(arena, ballTrailEnabled, theme);
        arenaScene.attach(rootNode, assetManager);

        if (config.video().postProcessing() && config.presentation().enableBloom()) {
            FilterPostProcessor fpp = new FilterPostProcessor(assetManager);
            BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Objects);
            bloom.setBloomIntensity(1.35f);
            bloom.setBlurScale(0.65f);
            fpp.addFilter(bloom);
            viewPort.addProcessor(fpp);
            if (config.presentation().enableMotionBlur()) {
                LOG.info("Motion blur shader configured: {}", theme.effects().motionBlurShader());
            }
        }

        cameraRig = new CameraRig(cam);
        cameraRig.setMaxShakeScale(screenShakeScale);
        cameraRig.configure(config.gameplay().cameraStyle(), arena);
    }

    private void initAudio() {
        audioController = new ClientAudioController(mixer, config.audio(), theme.audio());
        audioController.initialize(assetManager, rootNode);
    }

    private void initHud() {
        localization = new LocalizationService(config.locale());
        hud = new JavaFxHud(localization, mixer, theme.ui());
        hud.setScale(config.accessibility().uiScale());
        hud.show();
    }

    private void initInput() {
        paddleInput = new MousePaddleInput(inputManager, left, arena, hud::togglePause);
        paddleInput.register();
    }

    private void resetMatchState() {
        left.position().set(0, 0, -arena.depth() / 2f + 1.5f);
        right.position().set(0, 0, arena.depth() / 2f - 1.5f);
        leftPrevious.set(left.position());
        rightPrevious.set(right.position());
        ball.position().set(0, 0, 0);
        ball.velocity().set(0, 0, physicsTuning.minSpeed());
        ball.spin().set(0, 0, 0);
        if (arenaScene != null) {
            arenaScene.reset(ball, left, right);
        }
        if (cameraRig != null) {
            cameraRig.resetImmediate();
        }
    }

    @Override
    public void simpleUpdate(float tpf) {
        updatePaddleVelocities(tpf);
        if (!networkManager.isConnected() && aiController != null) {
            aiController.update(right, ball, tpf);
            clampPaddle(right);
        }

        physicsEngine.simulate(ball, left, right, tpf);
        networkManager.update(tpf, ball, left, right);
        arenaScene.sync(ball, left, right);
        arenaScene.animate(tpf);
        if (cameraRig != null) {
            cameraRig.update(tpf);
        }
        if (audioController != null) {
            audioController.update(tpf);
        }

        if (hud != null) {
            hud.updateScore(localization.translate("hud.score", networkManager.getScore().left(), networkManager.getScore().right()));
            hud.updatePing(localization.translate("hud.ping", networkManager.getPing()));
            hud.updateTimer(localization.translate("hud.timer", formatTime(networkManager.getElapsed())));
        }
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

    private void clampPaddle(PaddleState paddle) {
        float maxX = arena.width() / 2f - paddle.halfWidth();
        float maxY = arena.height() / 2f - paddle.halfHeight();
        paddle.position().x = FastMath.clamp(paddle.position().x, -maxX, maxX);
        paddle.position().y = FastMath.clamp(paddle.position().y, -maxY, maxY);
    }

    private void handleCollision(CollisionEvent event) {
        if (cameraRig != null) {
            cameraRig.triggerShake(event.intensity(), physicsTuning.maxSpeed());
        }
        if (event.type() == CollisionEvent.Type.PADDLE && audioController != null) {
            audioController.playBounce(event.intensity(), physicsTuning);
        }
        if (hud != null) {
            switch (event.type()) {
                case WALL -> hud.pushNotification(localization.translate("hud.wallHit"));
                case PADDLE -> hud.flashCombo();
            }
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (paddleInput != null) {
            paddleInput.close();
        }
        if (audioController != null) {
            audioController.close();
        }
        if (arenaScene != null) {
            arenaScene.dispose();
        }
        if (hud != null) {
            hud.dispose();
        }
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

    private static String formatTime(float seconds) {
        int totalSeconds = (int) seconds;
        int minutes = totalSeconds / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, secs);
    }
}
