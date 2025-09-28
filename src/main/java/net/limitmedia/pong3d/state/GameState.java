
package net.limitmedia.pong3d.state;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.audio.AudioNode;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;
import com.jme3.effect.shapes.EmitterSphereShape;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.DepthOfFieldFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.LightScatteringFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.AbstractControl;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.shape.Sphere;
import com.jme3.scene.shape.Torus;

import net.limitmedia.pong3d.audio.ProceduralAudioFactory;
import net.limitmedia.pong3d.net.NetworkClient;

import java.util.Random;

/**
 * Ansicht mit Sternen-Hintergrund, Glow, Trails und Maus-Kamerafahrt.
 * Spieler vorne (Z+) bewegt sich mit A/D; Gegner hinten (Z-) mit KI oder Pfeilen.
 */
public class GameState extends BaseAppState implements ActionListener {

    private final SimpleApplication app;
    private final Runnable onPause;
    private final NetworkClient networkClient;
    private final boolean multiplayer;
    private NetworkClient.Role networkRole = NetworkClient.Role.FRONT;

    private final Node root = new Node("game");
    private Geometry playerPaddle, enemyPaddle, ball, table;
    private final Node environment = new Node("environment");
    private Spatial skyDome;
    private Geometry auroraRing;
    private Geometry auroraRibbon;
    private ParticleEmitter starfield;
    private Material skyMaterial;
    private Material auroraMaterial;
    private Material ribbonMaterial;
    private Material tableMaterial;
    private float backgroundTime = 0f;
    private final ColorRGBA[] backgroundPalette = new ColorRGBA[] {
            new ColorRGBA(0.025f, 0.025f, 0.03f, 1f),
            new ColorRGBA(0.04f, 0.04f, 0.05f, 1f),
            new ColorRGBA(0.032f, 0.032f, 0.04f, 1f),
            new ColorRGBA(0.055f, 0.055f, 0.065f, 1f)
    };
    private final ColorRGBA blendedBackground = new ColorRGBA();
    private final ColorRGBA auroraColor = new ColorRGBA();
    private final ColorRGBA ribbonColor = new ColorRGBA();
    private final Vector3f ballVel = new Vector3f(5f, 0f, -6f);
    private final float paddleSpeed = 12f;

    private boolean movePlayerLeft, movePlayerRight, moveEnemyLeft, moveEnemyRight;
    private int scorePlayer = 0, scoreEnemy = 0;
    private BitmapText hud;
    private final ColorRGBA hudColor = new ColorRGBA(0.86f, 0.95f, 1f, 1f);
    private BitmapText networkStatusText;
    private float networkStatusPulse = 0f;
    private String currentNetworkMessage = "";
    private boolean networkReady = false;

    private final float halfWidth = 6.25f;
    private final float halfDepth = 10.25f;

    private final float ballRadius = 0.25f;
    private final ColorRGBA playerColor = new ColorRGBA(0.3f,0.8f,1f,1f);
    private final ColorRGBA enemyColor = new ColorRGBA(1f,0.3f,0.9f,1f);

    private final Node effects = new Node("fx");

    private float playerVelocity = 0f;
    private float enemyVelocity = 0f;

    private final float gravity = -22f;
    private final float bounceDamping = 0.5f;

    private float cameraShakeDuration = 0f;
    private float cameraShakeElapsed = 0f;
    private float cameraShakeStrength = 0f;
    private final Vector3f cameraBasePos = new Vector3f();
    private final Vector3f cameraFocusPos = new Vector3f();
    private final Vector3f cameraInitialPos = new Vector3f();
    private final Vector3f cameraInitialFocus = new Vector3f();
    private final Vector3f tempCameraPos = new Vector3f();
    private final Vector3f cameraShakeOffset = new Vector3f();
    private final ColorRGBA networkStatusBaseColor = new ColorRGBA(0.8f, 0.9f, 1f, 0.85f);

    private final Random random = new Random();

    private FilterPostProcessor postProcessor;

    private final Vector3f bounceStartVel = new Vector3f();
    private final Vector3f bounceTargetVel = new Vector3f();
    private final Vector3f bounceMix = new Vector3f();
    private float bounceBlendTime = 0f;
    private float bounceBlendDuration = 0f;

    private final Vector3f networkPlayerTarget = new Vector3f();
    private final Vector3f networkEnemyTarget = new Vector3f();
    private final Vector3f networkBallTarget = new Vector3f();
    private String lastClientStatus = "";
    private float lastNetworkBallZ = 0f;
    private float lastNetworkDeltaZ = 0f;
    private float lastNetworkBallX = 0f;
    private float lastNetworkDeltaX = 0f;
    private boolean networkBallPrimed = false;

    private AudioNode bounceSound;
    private AudioNode goalSound;

    public GameState(SimpleApplication app, Runnable onPause) {
        this(app, onPause, null);
    }

    public GameState(SimpleApplication app, Runnable onPause, NetworkClient networkClient) {
        this.app = app;
        this.onPause = onPause;
        this.networkClient = networkClient;
        this.multiplayer = networkClient != null;
    }

    @Override
    protected void initialize(Application ignored) {
        // Kamera fixieren (kein Maus-FlyCam)
        app.getFlyByCamera().setEnabled(false);
        app.getFlyByCamera().setMoveSpeed(0f);
        app.getFlyByCamera().setDragToRotate(false);
        app.getInputManager().setCursorVisible(false);

        // Kamera-Startposition hinter der Spielerwand
        float initialDistance = 11.2f;
        float initialHeight = 4.8f;
        cameraInitialPos.set(0f, initialHeight, halfDepth + initialDistance);
        cameraInitialFocus.set(0f, 1.6f, 0.4f);
        cameraBasePos.set(cameraInitialPos);
        cameraFocusPos.set(cameraInitialFocus);
        app.getCamera().setLocation(cameraInitialPos.clone());
        app.getCamera().lookAt(cameraInitialFocus, Vector3f.UNIT_Y);

        // Licht
        AmbientLight amb = new AmbientLight();
        amb.setColor(new ColorRGBA(0.35f,0.35f,0.4f,1f));
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.7f,-1f,-0.3f).normalizeLocal());
        sun.setColor(new ColorRGBA(0.95f,0.95f,0.95f,1f));
        root.addLight(amb);
        root.addLight(sun);

        // Umgebung: Himmelskugel, Aurora und Partikel-Hintergrund
        root.attachChild(environment);

        skyMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        skyMaterial.setColor("Color", backgroundPalette[0]);
        Sphere dome = new Sphere(24, 24, 80f);
        dome.setTextureMode(Sphere.TextureMode.Polar);
        skyDome = new Geometry("sky-dome", dome);
        skyDome.setMaterial(skyMaterial);
        skyDome.setQueueBucket(RenderQueue.Bucket.Sky);
        skyDome.setCullHint(Spatial.CullHint.Never);
        skyDome.setLocalScale(-1f);
        environment.attachChild(skyDome);

        starfield = new ParticleEmitter("stars", ParticleMesh.Type.Triangle, 900);
        starfield.setGravity(0,0,0);
        starfield.setLowLife(4f);
        starfield.setHighLife(8f);
        starfield.getParticleInfluencer().setInitialVelocity(new Vector3f(0,0,-2f));
        starfield.getParticleInfluencer().setVelocityVariation(1f);
        starfield.setStartSize(0.03f);
        starfield.setEndSize(0.03f);
        starfield.setStartColor(new ColorRGBA(0.82f, 0.85f, 0.9f, 0.28f));
        starfield.setEndColor(new ColorRGBA(0.78f, 0.8f, 0.84f, 0.04f));
        starfield.setImagesX(1);
        starfield.setImagesY(1);
        Material starMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        starMat.setColor("Color", ColorRGBA.White);
        starfield.setMaterial(starMat);
        starfield.setQueueBucket(RenderQueue.Bucket.Sky);
        starfield.setLocalTranslation(0, 6f, 0);
        environment.attachChild(starfield);

        auroraMaterial = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        auroraMaterial.setColor("Color", new ColorRGBA(0.16f, 0.18f, 0.2f, 0.18f));
        auroraMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Additive);
        Torus ringMesh = new Torus(64, 16, 0.4f, halfWidth + 5f);
        auroraRing = new Geometry("aurora-ring", ringMesh);
        auroraRing.setQueueBucket(RenderQueue.Bucket.Transparent);
        auroraRing.setMaterial(auroraMaterial);
        auroraRing.rotate(FastMath.HALF_PI, 0f, 0f);
        auroraRing.setLocalTranslation(0, 0.6f, 0);
        environment.attachChild(auroraRing);

        ribbonMaterial = auroraMaterial.clone();
        ribbonMaterial.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        Quad ribbonMesh = new Quad((halfWidth + 6f) * 2f, (halfDepth + 8f) * 2f);
        auroraRibbon = new Geometry("aurora-ribbon", ribbonMesh);
        auroraRibbon.setQueueBucket(RenderQueue.Bucket.Transparent);
        auroraRibbon.setMaterial(ribbonMaterial);
        auroraRibbon.setLocalTranslation(-(ribbonMesh.getWidth() / 2f), 0.2f, -(ribbonMesh.getHeight() / 2f));
        auroraRibbon.rotate(-FastMath.HALF_PI, 0f, 0f);
        environment.attachChild(auroraRibbon);

        // Materialien (Lighting + Glow)
        Material matBlue = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matBlue.setBoolean("UseMaterialColors", true);
        matBlue.setColor("Diffuse", playerColor.clone());
        matBlue.setColor("Specular", new ColorRGBA(0.7f,0.9f,1f,1f));
        matBlue.setColor("GlowColor", playerColor.clone());
        matBlue.setFloat("Shininess", 24f);

        Material matRed = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matRed.setBoolean("UseMaterialColors", true);
        matRed.setColor("Diffuse", enemyColor.clone());
        matRed.setColor("Specular", new ColorRGBA(1f,0.8f,1f,1f));
        matRed.setColor("GlowColor", enemyColor.clone());
        matRed.setFloat("Shininess", 24f);

        Material matTable = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matTable.setBoolean("UseMaterialColors", true);
        matTable.setColor("Diffuse", new ColorRGBA(0.08f,0.08f,0.09f,1f));
        matTable.setColor("Specular", new ColorRGBA(0.18f,0.18f,0.18f,1f));
        matTable.setFloat("Shininess", 10f);

        Material matBall = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matBall.setBoolean("UseMaterialColors", true);
        matBall.setColor("Diffuse", ColorRGBA.White);
        matBall.setColor("Specular", new ColorRGBA(1f,1f,1f,1f));
        matBall.setColor("GlowColor", new ColorRGBA(1f,1f,1f,0.6f));
        matBall.setFloat("Shininess", 64f);

        // Tisch
        tableMaterial = matTable;
        table = new Geometry("table", new Box(halfWidth+0.2f, 0.1f, halfDepth+0.2f));
        table.setMaterial(tableMaterial);
        table.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(table);

        // Neon-Rails um die Spielfläche
        Material railMat = matBlue.clone();
        railMat.setColor("Diffuse", new ColorRGBA(0.09f,0.1f,0.12f,1f));
        railMat.setColor("GlowColor", new ColorRGBA(0.5f,0.6f,0.7f,0.6f));
        for (int i = -1; i <= 1; i += 2) {
            Geometry side = new Geometry("rail-side-" + i, new Box(0.12f, 0.2f, halfDepth + 0.3f));
            side.setMaterial(railMat);
            side.setQueueBucket(RenderQueue.Bucket.Transparent);
            side.setLocalTranslation(i * (halfWidth + 0.12f), 0.6f, 0);
            root.attachChild(side);
        }
        Geometry frontRail = new Geometry("rail-front", new Box(halfWidth + 0.3f, 0.12f, 0.12f));
        frontRail.setMaterial(railMat);
        frontRail.setQueueBucket(RenderQueue.Bucket.Transparent);
        frontRail.setLocalTranslation(0, 0.6f, halfDepth + 0.12f);
        root.attachChild(frontRail);
        Geometry backRail = new Geometry("rail-back", new Box(halfWidth + 0.3f, 0.12f, 0.12f));
        backRail.setMaterial(railMat);
        backRail.setQueueBucket(RenderQueue.Bucket.Transparent);
        backRail.setLocalTranslation(0, 0.6f, -halfDepth - 0.12f);
        root.attachChild(backRail);

        // Schläger (leicht angewinkelt für Perspektive)
        playerPaddle = new Geometry("playerPaddle", new Box(1.8f, 0.4f, 0.2f));
        playerPaddle.setMaterial(matBlue);
        playerPaddle.setLocalTranslation(0, 0.5f, halfDepth - 0.6f);

        enemyPaddle = new Geometry("enemyPaddle", new Box(1.8f, 0.4f, 0.2f));
        enemyPaddle.setMaterial(matRed);
        enemyPaddle.setLocalTranslation(0, 0.5f, -halfDepth + 0.6f);

        // Ball
        Sphere sph = new Sphere(24, 24, ballRadius);
        ball = new Geometry("ball", sph);
        ball.setMaterial(matBall);
        ball.setLocalTranslation(0, ballRadius, 0);

        root.attachChild(playerPaddle);
        root.attachChild(enemyPaddle);
        root.attachChild(ball);

        // FX-Wurzel
        effects.setQueueBucket(RenderQueue.Bucket.Transparent);
        root.attachChild(effects);

        bounceSound = ProceduralAudioFactory.createBounceSound();
        root.attachChild(bounceSound);

        goalSound = ProceduralAudioFactory.createGoalSound();
        root.attachChild(goalSound);

        // Trails: einfache Fake-Trails via nachziehenden Geometrien (leicht skalierte Boxen)
        attachTrail(playerPaddle, matBlue);
        attachTrail(enemyPaddle, matRed);

        // HUD
        var font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        hud = new BitmapText(font, false);
        hud.setSize(32);
        hud.setColor(hudColor);
        app.getGuiNode().attachChild(hud);
        updateHud();

        if (multiplayer) {
            networkStatusText = new BitmapText(font, false);
            networkStatusText.setSize(24);
            networkStatusText.setText("");
            networkStatusText.setColor(networkStatusBaseColor);
            networkStatusText.setLocalTranslation(0, app.getCamera().getHeight() / 2f, 0);
            app.getGuiNode().attachChild(networkStatusText);
            setNetworkStatus("Verbinde mit Gegner...", true);
        }

        // Input
        var im = app.getInputManager();
        im.addMapping("PL_LEFT", new KeyTrigger(KeyInput.KEY_A));
        im.addMapping("PL_RIGHT", new KeyTrigger(KeyInput.KEY_D));
        if (!multiplayer) {
            im.addMapping("EN_LEFT", new KeyTrigger(KeyInput.KEY_LEFT));
            im.addMapping("EN_RIGHT", new KeyTrigger(KeyInput.KEY_RIGHT));
            im.addListener(this, "EN_LEFT", "EN_RIGHT");
        }
        im.addMapping("PAUSE", new KeyTrigger(KeyInput.KEY_ESCAPE));
        im.addListener(this, "PL_LEFT","PL_RIGHT","PAUSE");

        // Postprocessing: Bloom, SSAO, God Rays, Tiefenunschärfe und FXAA für ein modernes Bild
        postProcessor = new FilterPostProcessor(app.getAssetManager());
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setBloomIntensity(0.85f);
        postProcessor.addFilter(bloom);

        SSAOFilter ssao = new SSAOFilter(3.2f, 4.5f, 0.25f, 0.12f);
        postProcessor.addFilter(ssao);

        LightScatteringFilter lightScattering = new LightScatteringFilter(sun.getDirection().negate().mult(120f));
        lightScattering.setLightDensity(1.05f);
        postProcessor.addFilter(lightScattering);

        DepthOfFieldFilter depthOfField = new DepthOfFieldFilter();
        depthOfField.setFocusDistance(25f);
        depthOfField.setFocusRange(18f);
        depthOfField.setBlurScale(1.2f);
        postProcessor.addFilter(depthOfField);

        postProcessor.addFilter(new FXAAFilter());
        app.getViewPort().addProcessor(postProcessor);

        // Start-Richtung
        float signZ = random.nextBoolean() ? -1f : 1f;
        ballVel.set( (random.nextFloat()-0.5f)*6f, 0f, signZ * (5f + random.nextFloat()*2f));

        if (multiplayer) {
            sendNetworkDirection();
        }
    }

    private void attachTrail(Geometry owner, Material baseMat) {
        // Simpler "Trail": 3 dünne Geometrien, die Owner-Position folgen und ausfaden
        for (int i=1;i<=3;i++) {
            Geometry g = new Geometry(owner.getName()+"-trail-"+i, new Box(1.8f * (1f - i*0.15f), 0.05f, 0.05f));
            Material m = baseMat.clone();
            m.setColor("Diffuse", new ColorRGBA(
                    baseMat.getParam("Diffuse") != null ? ((ColorRGBA)baseMat.getParam("Diffuse").getValue()).r : 1f,
                    baseMat.getParam("Diffuse") != null ? ((ColorRGBA)baseMat.getParam("Diffuse").getValue()).g : 1f,
                    baseMat.getParam("Diffuse") != null ? ((ColorRGBA)baseMat.getParam("Diffuse").getValue()).b : 1f,
                    0.5f - i*0.12f));
            m.setColor("GlowColor", new ColorRGBA(
                    1f,1f,1f, 0.4f - i*0.1f));
            g.setMaterial(m);
            g.setQueueBucket(RenderQueue.Bucket.Transparent);
            g.setCullHint(Spatial.CullHint.Never);
            g.setLocalTranslation(owner.getLocalTranslation().clone());
            g.rotate(owner.getLocalRotation());
            g.addControl(new TrailFollowControl(owner, 0.06f * i));
            root.attachChild(g);
        }
    }

    @Override
    public void update(float tpf) {
        updateBackground(tpf);
        updateCameraFollow(tpf);
        updateCameraShake(tpf);
        if (multiplayer) {
            pollNetworkStatus();
            if (!applyNetworkState()) {
                updateNetworkStatus(tpf);
                updateHud();
                return;
            }
            smoothNetworkSpatials(tpf);
            updateNetworkStatus(tpf);
            updateHud();
            return;
        }
        // Player movement X
        float dxPlayer = (movePlayerRight ? 1 : 0) - (movePlayerLeft ? 1 : 0);
        float targetPlayerVel = dxPlayer * paddleSpeed;
        float smoothing = FastMath.clamp(tpf * 12f, 0f, 1f);
        playerVelocity = FastMath.interpolateLinear(smoothing, playerVelocity, targetPlayerVel);
        playerPaddle.move(playerVelocity * tpf, 0, 0);
        clampPaddleX(playerPaddle);

        // Enemy movement: AI unless overridden
        if (moveEnemyLeft || moveEnemyRight) {
            float dxEnemy = (moveEnemyRight ? 1 : 0) - (moveEnemyLeft ? 1 : 0);
            float targetEnemyVel = dxEnemy * paddleSpeed;
            enemyVelocity = FastMath.interpolateLinear(smoothing, enemyVelocity, targetEnemyVel);
            enemyPaddle.move(enemyVelocity * tpf, 0, 0);
            clampPaddleX(enemyPaddle);
        } else {
            float diff = ball.getLocalTranslation().x - enemyPaddle.getLocalTranslation().x;
            float desired = FastMath.clamp(diff, -paddleSpeed * 0.8f, paddleSpeed * 0.8f);
            enemyVelocity = FastMath.interpolateLinear(smoothing, enemyVelocity, desired);
            enemyPaddle.move(enemyVelocity * tpf, 0, 0);
            clampPaddleX(enemyPaddle);
        }

        updateBallResponse(tpf);

        // Ball
        ball.move(ballVel.x * tpf, ballVel.y * tpf, ballVel.z * tpf);
        ballVel.y += gravity * tpf;

        // Tischkontakt (sanftes Aufsetzen)
        if (ball.getLocalTranslation().y < ballRadius) {
            ball.setLocalTranslation(ball.getLocalTranslation().x, ballRadius, ball.getLocalTranslation().z);
            if (ballVel.y < 0) {
                ballVel.y = -ballVel.y * bounceDamping;
                if (Math.abs(ballVel.y) < 0.8f) {
                    ballVel.y = 0f;
                }
            }
        }

        // Spin
        float speed = FastMath.sqrt(ballVel.x*ballVel.x + ballVel.z*ballVel.z);
        if (speed > 1e-4f) {
            float angular = speed / ballRadius;
            Vector3f axis = new Vector3f(ballVel.z, 0, -ballVel.x).normalizeLocal();
            Quaternion q = new Quaternion().fromAngleAxis(angular * tpf, axis);
            ball.rotate(q);
        }

        // Walls X
        Vector3f ballPos = ball.getLocalTranslation();
        if (ballPos.x > halfWidth - ballRadius && ballVel.x > 0) {
            ball.setLocalTranslation(halfWidth - ballRadius, ballPos.y, ballPos.z);
            float newX = -FastMath.abs(ballVel.x) * (0.82f + random.nextFloat() * 0.08f);
            float newZ = ballVel.z * (0.98f + random.nextFloat() * 0.035f);
            float newY = Math.max(ballVel.y, 2.6f + FastMath.abs(ballVel.z) * 0.04f);
            scheduleBallResponse(newX, newY, newZ, 0.18f);
            triggerCameraShake(0.1f, 0.16f);
            spawnImpactEffect(ball.getLocalTranslation().clone(), playerColor);
            playBounceSound();
        } else if (ballPos.x < -halfWidth + ballRadius && ballVel.x < 0) {
            ball.setLocalTranslation(-halfWidth + ballRadius, ballPos.y, ballPos.z);
            float newX = FastMath.abs(ballVel.x) * (0.82f + random.nextFloat() * 0.08f);
            float newZ = ballVel.z * (0.98f + random.nextFloat() * 0.035f);
            float newY = Math.max(ballVel.y, 2.6f + FastMath.abs(ballVel.z) * 0.04f);
            scheduleBallResponse(newX, newY, newZ, 0.18f);
            triggerCameraShake(0.1f, 0.16f);
            spawnImpactEffect(ball.getLocalTranslation().clone(), enemyColor);
            playBounceSound();
        }

        // Paddles Z
        if (ball.getLocalTranslation().z > (halfDepth - 0.6f) && ballVel.z > 0) {
            if (Math.abs(ball.getLocalTranslation().x - playerPaddle.getLocalTranslation().x) <= 1.4f) {
                handlePaddleBounce(playerPaddle, true);
            }
        }
        if (ball.getLocalTranslation().z < (-halfDepth + 0.6f) && ballVel.z < 0) {
            if (Math.abs(ball.getLocalTranslation().x - enemyPaddle.getLocalTranslation().x) <= 1.4f) {
                handlePaddleBounce(enemyPaddle, false);
            }
        }

        // Goals
        if (ball.getLocalTranslation().z > halfDepth + 0.35f) {
            scoreEnemy++;
            spawnGoalEffect(false);
            triggerCameraShake(0.22f, 0.32f);
            playGoalSound();
            resetBall(false);
        } else if (ball.getLocalTranslation().z < -halfDepth - 0.35f) {
            scorePlayer++;
            spawnGoalEffect(true);
            triggerCameraShake(0.22f, 0.32f);
            playGoalSound();
            resetBall(true);
        }

        limitBallSpeed();
        updateHud();
    }

    private void updateBallResponse(float tpf) {
        if (bounceBlendDuration <= 0f) {
            return;
        }
        bounceBlendTime = Math.min(bounceBlendTime + tpf, bounceBlendDuration);
        float alpha = bounceBlendTime / bounceBlendDuration;
        float smooth = smootherStep(alpha);
        bounceMix.set(bounceStartVel).multLocal(1f - smooth);
        bounceMix.addLocal(bounceTargetVel.x * smooth, bounceTargetVel.y * smooth, bounceTargetVel.z * smooth);
        ballVel.set(bounceMix);
        if (alpha >= 0.999f) {
            ballVel.set(bounceTargetVel);
            bounceBlendDuration = 0f;
        }
        limitBallSpeed();
    }

    private void updateCameraFollow(float tpf) {
        if (cameraShakeDuration <= 0f) {
            float blend = FastMath.clamp(tpf * 4.5f, 0f, 1f);
            cameraBasePos.interpolateLocal(cameraInitialPos, blend);
            cameraFocusPos.interpolateLocal(cameraInitialFocus, blend);
            app.getCamera().setLocation(cameraBasePos);
        }
        app.getCamera().lookAt(cameraFocusPos, Vector3f.UNIT_Y);
    }

    private void updateHud() {
        if (hud == null) return;
        String txt = String.format("%02d  |  %02d", scorePlayer, scoreEnemy);
        if (!txt.equals(hud.getText())) hud.setText(txt);
        float x = (app.getCamera().getWidth()/2f) - (hud.getLineWidth()/2f);
        float y = app.getCamera().getHeight() - 20f;
        hud.setLocalTranslation(x, y, 0);
        hud.setColor(hudColor);
    }

    private void updateBackground(float tpf) {
        if (skyMaterial == null) {
            return;
        }
        backgroundTime += tpf;
        float cycleDuration = 18f;
        float cyclePosition = (backgroundTime % cycleDuration) / cycleDuration;
        int currentIndex = (int) ((backgroundTime / cycleDuration) % backgroundPalette.length);
        int nextIndex = (currentIndex + 1) % backgroundPalette.length;
        blendedBackground.set(backgroundPalette[currentIndex]);
        blendedBackground.interpolateLocal(backgroundPalette[nextIndex], cyclePosition);

        skyMaterial.setColor("Color", blendedBackground);

        if (starfield != null) {
            ColorRGBA start = blendedBackground.clone();
            start.a = 0.45f;
            starfield.setStartColor(start);
            ColorRGBA end = blendedBackground.clone();
            end.a = 0.08f;
            starfield.setEndColor(end);
        }

        float auroraPulse = 0.12f + FastMath.sin(backgroundTime * 0.9f) * 0.08f;
        auroraColor.set(blendedBackground);
        float accent = 0.06f + FastMath.sin(backgroundTime * 0.4f) * 0.035f;
        auroraColor.r = FastMath.clamp(auroraColor.r + accent, 0f, 0.36f);
        auroraColor.g = FastMath.clamp(auroraColor.g + accent * 1.1f, 0f, 0.4f);
        auroraColor.b = FastMath.clamp(auroraColor.b + accent * 1.35f, 0f, 0.46f);
        auroraColor.a = FastMath.clamp(auroraPulse, 0.06f, 0.22f);

        if (auroraMaterial != null) {
            auroraMaterial.setColor("Color", auroraColor);
        }
        if (auroraRing != null) {
            auroraRing.rotate(0f, tpf * 0.35f, 0f);
        }

        if (ribbonMaterial != null) {
            ribbonColor.set(auroraColor);
            ribbonColor.a = 0.08f + FastMath.sin(backgroundTime * 0.7f) * 0.05f;
            ribbonMaterial.setColor("Color", ribbonColor);
        }
        if (auroraRibbon != null) {
            auroraRibbon.rotate(0f, tpf * 0.1f, 0f);
        }

        if (tableMaterial != null) {
            ColorRGBA tableDiffuse = new ColorRGBA(
                    FastMath.clamp(blendedBackground.r * 0.4f + 0.08f, 0f, 1f),
                    FastMath.clamp(blendedBackground.g * 0.3f + 0.07f, 0f, 1f),
                    FastMath.clamp(blendedBackground.b * 0.5f + 0.1f, 0f, 1f),
                    1f);
            tableMaterial.setColor("Diffuse", tableDiffuse);
        }

        hudColor.set(
                FastMath.clamp(0.6f + blendedBackground.r * 0.35f, 0f, 1f),
                FastMath.clamp(0.75f + blendedBackground.g * 0.2f, 0f, 1f),
                FastMath.clamp(0.85f + blendedBackground.b * 0.25f, 0f, 1f),
                1f);

        networkStatusBaseColor.r = FastMath.clamp(0.6f + blendedBackground.r * 0.25f, 0f, 1f);
        networkStatusBaseColor.g = FastMath.clamp(0.75f + blendedBackground.g * 0.2f, 0f, 1f);
        networkStatusBaseColor.b = FastMath.clamp(0.95f + blendedBackground.b * 0.15f, 0f, 1f);
    }

    private void setNetworkStatus(String text, boolean visible) {
        if (networkStatusText == null) {
            return;
        }
        currentNetworkMessage = visible ? text : "";
        networkStatusText.setText(currentNetworkMessage);
        networkStatusText.setCullHint(visible ? Spatial.CullHint.Never : Spatial.CullHint.Always);
        if (visible) {
            networkStatusPulse = 0f;
            networkStatusBaseColor.a = 0.85f;
            networkStatusText.setColor(networkStatusBaseColor);
            float x = (app.getCamera().getWidth() - networkStatusText.getLineWidth()) / 2f;
            float y = (app.getCamera().getHeight() / 2f) + networkStatusText.getLineHeight();
            networkStatusText.setLocalTranslation(x, y, 0);
        }
    }

    private void updateNetworkStatus(float tpf) {
        if (networkStatusText == null) {
            return;
        }
        if (networkStatusText.getCullHint() == Spatial.CullHint.Always) {
            return;
        }
        networkStatusPulse += tpf;
        float pulse = 0.6f + FastMath.sin(networkStatusPulse * 3f) * 0.2f;
        networkStatusBaseColor.a = pulse;
        networkStatusText.setColor(networkStatusBaseColor);
    }

    private void clampPaddleX(Geometry p) {
        float x = p.getLocalTranslation().x;
        x = FastMath.clamp(x, -halfWidth + 1.8f, halfWidth - 1.8f);
        p.setLocalTranslation(x, p.getLocalTranslation().y, p.getLocalTranslation().z);
    }

    private void limitBallSpeed() {
        float max = 26f;
        float min = 6f;
        float horizontal = FastMath.sqrt(ballVel.x * ballVel.x + ballVel.z * ballVel.z);
        if (horizontal < min) {
            float scale = min / Math.max(horizontal, 1e-3f);
            ballVel.x *= scale;
            ballVel.z *= scale;
        } else if (horizontal > max) {
            float scale = max / horizontal;
            ballVel.x *= scale;
            ballVel.z *= scale;
        }
        ballVel.x = FastMath.clamp(ballVel.x, -max, max);
        ballVel.z = FastMath.clamp(ballVel.z, -max, max);
        if (Math.abs(ballVel.y) > max) {
            ballVel.y = ballVel.y >= 0f ? max : -max;
        }
    }

    private void resetBall(boolean towardsEnemy) {
        ball.setLocalTranslation(0, ballRadius, 0);
        ball.setLocalRotation(Quaternion.IDENTITY);
        ballVel.set((random.nextFloat() - 0.5f) * 5f, 0, towardsEnemy ? -6f : 6f);
        playerVelocity = 0f;
        enemyVelocity = 0f;
        bounceBlendDuration = 0f;
        bounceBlendTime = 0f;
        bounceStartVel.set(ballVel);
        bounceTargetVel.set(ballVel);
        networkBallPrimed = false;
    }

    @Override
    protected void onEnable() {
        app.enqueue(() -> {
            if (postProcessor != null && !app.getViewPort().getProcessors().contains(postProcessor)) {
                app.getViewPort().addProcessor(postProcessor);
            }
            app.getRootNode().attachChild(root);
            return null;
        });
    }

    @Override
    protected void onDisable() {
        app.enqueue(() -> {
            root.removeFromParent();
            if (postProcessor != null) {
                app.getViewPort().removeProcessor(postProcessor);
            }
            cameraShakeDuration = 0f;
            cameraShakeElapsed = 0f;
            cameraShakeStrength = 0f;
            app.getCamera().setLocation(cameraBasePos.clone());
            app.getCamera().lookAt(cameraFocusPos, Vector3f.UNIT_Y);
            return null;
        });
        var im = app.getInputManager();
        im.deleteMapping("PL_LEFT");
        im.deleteMapping("PL_RIGHT");
        if (!multiplayer) {
            im.deleteMapping("EN_LEFT");
            im.deleteMapping("EN_RIGHT");
        }
        im.deleteMapping("PAUSE");
        if (hud != null) hud.removeFromParent();
        if (networkStatusText != null) networkStatusText.removeFromParent();
        app.getInputManager().setCursorVisible(true);
        app.getFlyByCamera().setEnabled(false);
    }

    @Override
    protected void cleanup(Application app) {
        if (networkClient != null) {
            networkClient.close();
        }
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "PL_LEFT": movePlayerLeft = isPressed; break;
            case "PL_RIGHT": movePlayerRight = isPressed; break;
            case "EN_LEFT": moveEnemyLeft = isPressed; break;
            case "EN_RIGHT": moveEnemyRight = isPressed; break;
            case "PAUSE":
                if (!isPressed && onPause != null) {
                    onPause.run();
                }
                break;
        }
        if (multiplayer && ("PL_LEFT".equals(name) || "PL_RIGHT".equals(name))) {
            sendNetworkDirection();
        }
    }

    public void resume() {
        movePlayerLeft = movePlayerRight = moveEnemyLeft = moveEnemyRight = false;
        if (multiplayer) {
            sendNetworkDirection();
        }
    }

    private void sendNetworkDirection() {
        if (networkClient == null) return;
        int dir = (movePlayerRight ? 1 : 0) - (movePlayerLeft ? 1 : 0);
        networkClient.sendDirection(dir);
    }

    private boolean applyNetworkState() {
        if (networkClient == null) {
            return false;
        }
        var state = networkClient.getWorldState();
        if (state == null) {
            if (!networkClient.isRunning()) {
                setNetworkStatus("Verbindung verloren", true);
            } else {
                setNetworkStatus("Warte auf Mitspieler...", true);
            }
            return false;
        }
        networkRole = networkClient.getRole();

        float myX = networkRole == NetworkClient.Role.FRONT ? state.frontX() : state.backX();
        float oppX = networkRole == NetworkClient.Role.FRONT ? state.backX() : state.frontX();
        float ballZServer = state.ballZ();
        float localBallZ = networkRole == NetworkClient.Role.FRONT ? ballZServer : -ballZServer;
        boolean firstSnapshot = !networkReady;
        if (!networkReady) {
            networkReady = true;
            lastClientStatus = networkClient.getServerStatus();
            setNetworkStatus("", false);
        }

        networkPlayerTarget.set(myX, playerPaddle.getLocalTranslation().y, playerPaddle.getLocalTranslation().z);
        networkEnemyTarget.set(oppX, enemyPaddle.getLocalTranslation().y, enemyPaddle.getLocalTranslation().z);
        networkBallTarget.set(state.ballX(), ballRadius, localBallZ);

        if (firstSnapshot) {
            playerPaddle.setLocalTranslation(networkPlayerTarget);
            enemyPaddle.setLocalTranslation(networkEnemyTarget);
            ball.setLocalTranslation(networkBallTarget);
            lastNetworkBallZ = localBallZ;
            lastNetworkBallX = state.ballX();
            lastNetworkDeltaZ = 0f;
            lastNetworkDeltaX = 0f;
            networkBallPrimed = false;
        }

        float deltaZ = localBallZ - lastNetworkBallZ;
        float deltaX = state.ballX() - lastNetworkBallX;
        if (networkReady && networkBallPrimed) {
            if (Math.abs(deltaZ) > 0.02f && Math.abs(lastNetworkDeltaZ) > 0.02f
                    && Math.signum(deltaZ) != Math.signum(lastNetworkDeltaZ)
                    && Math.abs(localBallZ) > halfDepth - 0.9f) {
                boolean playerSide = localBallZ > 0f;
                Vector3f impactPos = networkBallTarget.clone();
                spawnImpactEffect(impactPos, playerSide ? playerColor : enemyColor);
                triggerCameraShake(playerSide ? 0.14f : 0.12f, 0.18f);
                playBounceSound();
            }
            if (Math.abs(deltaX) > 0.02f && Math.abs(lastNetworkDeltaX) > 0.02f
                    && Math.signum(deltaX) != Math.signum(lastNetworkDeltaX)
                    && Math.abs(state.ballX()) > halfWidth - 0.9f) {
                Vector3f impactPos = networkBallTarget.clone();
                spawnImpactEffect(impactPos, state.ballX() > 0f ? playerColor : enemyColor);
                triggerCameraShake(0.1f, 0.16f);
                playBounceSound();
            }
        }
        lastNetworkDeltaZ = deltaZ;
        lastNetworkBallZ = localBallZ;
        lastNetworkDeltaX = deltaX;
        lastNetworkBallX = state.ballX();
        networkBallPrimed = true;

        int prevPlayer = scorePlayer;
        int prevEnemy = scoreEnemy;
        if (networkRole == NetworkClient.Role.FRONT) {
            scorePlayer = state.scoreFront();
            scoreEnemy = state.scoreBack();
        } else {
            scorePlayer = state.scoreBack();
            scoreEnemy = state.scoreFront();
        }

        if (scorePlayer > prevPlayer) {
            spawnGoalEffect(true);
            triggerCameraShake(0.22f, 0.32f);
            playGoalSound();
        } else if (scoreEnemy > prevEnemy) {
            spawnGoalEffect(false);
            triggerCameraShake(0.22f, 0.32f);
            playGoalSound();
        }

        if (!networkClient.isRunning()) {
            setNetworkStatus("Gegner getrennt", true);
        }
        return true;
    }

    private void smoothNetworkSpatials(float tpf) {
        float smoothing = FastMath.clamp(tpf * 14f, 0f, 1f);
        interpolateSpatial(playerPaddle, networkPlayerTarget, smoothing);
        interpolateSpatial(enemyPaddle, networkEnemyTarget, smoothing);
        interpolateSpatial(ball, networkBallTarget, FastMath.clamp(tpf * 18f, 0f, 1f));
    }

    private void interpolateSpatial(Geometry geom, Vector3f target, float alpha) {
        if (geom == null) {
            return;
        }
        if (alpha >= 1f) {
            geom.setLocalTranslation(target);
            return;
        }
        Vector3f current = geom.getLocalTranslation();
        current.interpolateLocal(target, alpha);
        geom.setLocalTranslation(current);
    }

    private void pollNetworkStatus() {
        if (networkClient == null || networkStatusText == null) {
            return;
        }
        String status = networkClient.getServerStatus();
        if (status == null) {
            return;
        }
        if (networkReady && status.startsWith("Match gefunden")) {
            lastClientStatus = status;
            if (!currentNetworkMessage.isEmpty()) {
                setNetworkStatus("", false);
            }
            return;
        }
        if (!status.equals(lastClientStatus)) {
            lastClientStatus = status;
            if (status.isBlank()) {
                setNetworkStatus("", false);
            } else {
                setNetworkStatus(status, true);
            }
        }
    }

    /** Simple follower that leaves a fading trail by sampling owner's transform with delay. */
    private static class TrailFollowControl extends AbstractControl {
        private final Spatial owner;
        private final float delay;
        private float acc = 0f;
        public TrailFollowControl(Spatial owner, float delay) { this.owner = owner; this.delay = delay; }
        @Override protected void controlUpdate(float tpf) {
            acc += tpf;
            if (acc >= delay) {
                spatial.setLocalTranslation(owner.getLocalTranslation());
                spatial.setLocalRotation(owner.getLocalRotation());
                acc = 0f;
            }
        }
        @Override protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
    }

    private void handlePaddleBounce(Geometry paddle, boolean playerSide) {
        float diff = ball.getLocalTranslation().x - paddle.getLocalTranslation().x;
        float offset = FastMath.clamp(diff / 1.4f, -1f, 1f);
        float incomingSpeed = FastMath.abs(ballVel.z);
        float targetSpeed = FastMath.clamp(incomingSpeed * 1.04f + 0.4f, 6.5f, 22f);
        float horizontalAim = FastMath.interpolateLinear(0.42f, ballVel.x, offset * targetSpeed * 0.75f);
        float verticalBoost = (playerSide ? 6.1f : 5.6f) + FastMath.abs(offset) * 2.4f;
        float targetZ = playerSide ? -targetSpeed : targetSpeed;
        scheduleBallResponse(horizontalAim, verticalBoost, targetZ, 0.26f);

        float clampedZ = playerSide ? halfDepth - 0.62f : -halfDepth + 0.62f;
        ball.setLocalTranslation(ball.getLocalTranslation().x, ball.getLocalTranslation().y, clampedZ);

        triggerCameraShake(playerSide ? 0.16f : 0.14f, 0.22f);
        spawnImpactEffect(ball.getLocalTranslation().clone(), playerSide ? playerColor : enemyColor);
        playBounceSound();
    }

    private void scheduleBallResponse(float targetX, float targetY, float targetZ, float duration) {
        bounceStartVel.set(ballVel);
        bounceTargetVel.set(targetX, targetY, targetZ);
        bounceBlendTime = 0f;
        bounceBlendDuration = FastMath.clamp(duration, 0.12f, 0.42f);
    }

    private static float smootherStep(float t) {
        t = FastMath.clamp(t, 0f, 1f);
        return t * t * t * (t * (t * 6f - 15f) + 10f);
    }

    private void updateCameraShake(float tpf) {
        if (cameraShakeDuration <= 0f) {
            return;
        }
        cameraShakeElapsed += tpf;
        if (cameraShakeElapsed >= cameraShakeDuration) {
            cameraShakeDuration = 0f;
            cameraShakeElapsed = 0f;
            cameraShakeStrength = 0f;
            app.getCamera().setLocation(cameraBasePos.clone());
            return;
        }
        float progress = cameraShakeElapsed / cameraShakeDuration;
        float falloff = (1f - progress);
        cameraShakeOffset.set(
                (random.nextFloat() * 2f - 1f) * cameraShakeStrength * 0.6f * falloff,
                0f,
                (random.nextFloat() * 2f - 1f) * cameraShakeStrength * 0.4f * falloff
        );
        tempCameraPos.set(
                cameraBasePos.x + cameraShakeOffset.x,
                cameraBasePos.y,
                cameraBasePos.z + cameraShakeOffset.z
        );
        app.getCamera().setLocation(tempCameraPos);
    }

    private void triggerCameraShake(float strength, float duration) {
        float remaining = Math.max(cameraShakeDuration - cameraShakeElapsed, 0f);
        cameraShakeStrength = Math.max(cameraShakeStrength, strength);
        cameraShakeDuration = remaining + duration;
        cameraShakeElapsed = 0f;
    }

    private void spawnImpactEffect(Vector3f position, ColorRGBA color) {
        ParticleEmitter impact = new ParticleEmitter("impact", ParticleMesh.Type.Triangle, 18);
        impact.setGravity(0, -18f, 0);
        impact.setLowLife(0.25f);
        impact.setHighLife(0.45f);
        impact.setStartSize(0.3f);
        impact.setEndSize(0.06f);
        impact.setStartColor(color.clone());
        impact.setEndColor(new ColorRGBA(color.r, color.g, color.b, 0f));
        impact.setParticlesPerSec(0);
        impact.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.32f));
        impact.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 5.2f, 0));
        impact.getParticleInfluencer().setVelocityVariation(0.65f);
        impact.setMaterial(ball.getMaterial());
        impact.setLocalTranslation(position);
        impact.addControl(new TimedRemovalControl(0.6f));
        effects.attachChild(impact);
        impact.emitAllParticles();

        ParticleEmitter sparks = new ParticleEmitter("impact-sparks", ParticleMesh.Type.Triangle, 12);
        sparks.setGravity(0, -22f, 0);
        sparks.setLowLife(0.2f);
        sparks.setHighLife(0.32f);
        sparks.setStartSize(0.16f);
        sparks.setEndSize(0.03f);
        sparks.setStartColor(color.clone().mult(1.3f));
        sparks.setEndColor(new ColorRGBA(color.r, color.g, color.b, 0f));
        sparks.setParticlesPerSec(0);
        sparks.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.18f));
        sparks.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 3.8f, 0));
        sparks.getParticleInfluencer().setVelocityVariation(0.85f);
        sparks.setMaterial(ball.getMaterial());
        sparks.setLocalTranslation(position);
        sparks.addControl(new TimedRemovalControl(0.45f));
        effects.attachChild(sparks);
        sparks.emitAllParticles();
    }

    private void spawnGoalEffect(boolean playerScored) {
        float z = playerScored ? -halfDepth : halfDepth;
        ParticleEmitter ring = new ParticleEmitter("goal-ring", ParticleMesh.Type.Triangle, 42);
        ring.setGravity(0, 0, 0);
        ring.setLowLife(0.5f);
        ring.setHighLife(0.82f);
        ring.setStartSize(0.36f);
        ring.setEndSize(0.05f);
        ColorRGBA color = (playerScored ? playerColor : enemyColor).clone();
        ring.setStartColor(color);
        ring.setEndColor(new ColorRGBA(color.r, color.g, color.b, 0f));
        ring.setParticlesPerSec(0);
        ring.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 0, playerScored ? 6f : -6f));
        ring.getParticleInfluencer().setVelocityVariation(0.6f);
        ring.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.25f));
        ring.setMaterial(ball.getMaterial());
        ring.setLocalTranslation(0, ballRadius + 0.1f, z);
        ring.addControl(new TimedRemovalControl(1.1f));
        effects.attachChild(ring);
        ring.emitAllParticles();

        ParticleEmitter burst = new ParticleEmitter("goal-burst", ParticleMesh.Type.Triangle, 48);
        burst.setGravity(0, -12f, 0);
        burst.setLowLife(0.45f);
        burst.setHighLife(0.8f);
        burst.setStartSize(0.26f);
        burst.setEndSize(0.045f);
        burst.setStartColor(color.clone().mult(1.2f));
        burst.setEndColor(new ColorRGBA(color.r, color.g, color.b, 0f));
        burst.setParticlesPerSec(0);
        burst.setShape(new EmitterSphereShape(Vector3f.ZERO, 0.4f));
        burst.getParticleInfluencer().setInitialVelocity(new Vector3f(0, 4.6f, playerScored ? -1.8f : 1.8f));
        burst.getParticleInfluencer().setVelocityVariation(0.95f);
        burst.setMaterial(ball.getMaterial());
        burst.setLocalTranslation(0, ballRadius + 0.1f, z);
        burst.addControl(new TimedRemovalControl(0.9f));
        effects.attachChild(burst);
        burst.emitAllParticles();
    }

    private void playBounceSound() {
        if (bounceSound != null) {
            bounceSound.playInstance();
        }
    }

    private void playGoalSound() {
        if (goalSound != null) {
            goalSound.playInstance();
        }
    }

    private static class TimedRemovalControl extends AbstractControl {
        private final float lifetime;
        private float elapsed = 0f;
        private TimedRemovalControl(float lifetime) { this.lifetime = lifetime; }
        @Override protected void controlUpdate(float tpf) {
            elapsed += tpf;
            if (elapsed >= lifetime && spatial != null) {
                spatial.removeFromParent();
            }
        }
        @Override protected void controlRender(com.jme3.renderer.RenderManager rm, com.jme3.renderer.ViewPort vp) {}
    }
}
