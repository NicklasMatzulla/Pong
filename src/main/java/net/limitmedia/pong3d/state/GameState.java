
package net.limitmedia.pong3d.state;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Box;
import com.jme3.scene.shape.Sphere;
import com.jme3.effect.ParticleEmitter;
import com.jme3.effect.ParticleMesh;

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
    private final Vector3f ballVel = new Vector3f(5f, 0f, -6f);
    private final float paddleSpeed = 12f;

    private boolean movePlayerLeft, movePlayerRight, moveEnemyLeft, moveEnemyRight;
    private int scorePlayer = 0, scoreEnemy = 0;
    private BitmapText hud;

    private float halfWidth = 8f;
    private float halfDepth = 4.5f;

    private float ballRadius = 0.25f;

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
        // Kamera + Maussteuerung
        app.getFlyByCamera().setEnabled(true);
        app.getFlyByCamera().setDragToRotate(false); // sofortige Maus-Capture
        app.getFlyByCamera().setMoveSpeed(20f);
        app.getInputManager().setCursorVisible(false);
        // Toggle per C
        app.getInputManager().addMapping("CAM_TOGGLE", new KeyTrigger(KeyInput.KEY_C));
        app.getInputManager().addListener(this, "CAM_TOGGLE");

        // Kamera-Startposition
        app.getCamera().setLocation(new Vector3f(0, 12, 18));
        app.getCamera().lookAt(Vector3f.ZERO, Vector3f.UNIT_Y);

        // Licht
        AmbientLight amb = new AmbientLight();
        amb.setColor(new ColorRGBA(0.35f,0.35f,0.4f,1f));
        DirectionalLight sun = new DirectionalLight();
        sun.setDirection(new Vector3f(-0.7f,-1f,-0.3f).normalizeLocal());
        sun.setColor(new ColorRGBA(0.95f,0.95f,0.95f,1f));
        root.addLight(amb);
        root.addLight(sun);

        // Sternen-"Sky": Partikel-Emitter im Hintergrund
        ParticleEmitter stars = new ParticleEmitter("stars", ParticleMesh.Type.Triangle, 1500);
        stars.setGravity(0,0,0);
        stars.setLowLife(4f);
        stars.setHighLife(8f);
        stars.getParticleInfluencer().setInitialVelocity(new Vector3f(0,0,-2f));
        stars.getParticleInfluencer().setVelocityVariation(1f);
        stars.setStartSize(0.03f);
        stars.setEndSize(0.03f);
        stars.setStartColor(ColorRGBA.White);
        stars.setEndColor(new ColorRGBA(1,1,1,0.2f));
        stars.setImagesX(1); stars.setImagesY(1);
        Material starMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        starMat.setColor("Color", ColorRGBA.White);
        stars.setMaterial(starMat);
        stars.setQueueBucket(RenderQueue.Bucket.Sky);
        stars.setLocalTranslation(0, 5f, 0);
        root.attachChild(stars);

        // Materialien (Lighting + Glow)
        Material matBlue = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matBlue.setBoolean("UseMaterialColors", true);
        matBlue.setColor("Diffuse", new ColorRGBA(0.2f,0.6f,1f,1f));
        matBlue.setColor("Specular", new ColorRGBA(0.7f,0.9f,1f,1f));
        matBlue.setColor("GlowColor", new ColorRGBA(0.2f,0.6f,1f,1f));
        matBlue.setFloat("Shininess", 24f);

        Material matRed = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matRed.setBoolean("UseMaterialColors", true);
        matRed.setColor("Diffuse", new ColorRGBA(1f,0.3f,0.9f,1f)); // pink/violett wie im Screenshot
        matRed.setColor("Specular", new ColorRGBA(1f,0.8f,1f,1f));
        matRed.setColor("GlowColor", new ColorRGBA(1f,0.3f,0.9f,1f));
        matRed.setFloat("Shininess", 24f);

        Material matTable = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matTable.setBoolean("UseMaterialColors", true);
        matTable.setColor("Diffuse", new ColorRGBA(0.12f,0.1f,0.05f,1f));
        matTable.setColor("Specular", new ColorRGBA(0.2f,0.2f,0.2f,1f));
        matTable.setFloat("Shininess", 8f);

        Material matBall = new Material(app.getAssetManager(), "Common/MatDefs/Light/Lighting.j3md");
        matBall.setBoolean("UseMaterialColors", true);
        matBall.setColor("Diffuse", ColorRGBA.White);
        matBall.setColor("Specular", new ColorRGBA(1f,1f,1f,1f));
        matBall.setColor("GlowColor", new ColorRGBA(1f,1f,1f,0.6f));
        matBall.setFloat("Shininess", 64f);

        // Tisch
        table = new Geometry("table", new Box(halfWidth+0.2f, 0.1f, halfDepth+0.2f));
        table.setMaterial(matTable);
        table.setShadowMode(RenderQueue.ShadowMode.Receive);
        root.attachChild(table);

        // Schläger (leicht angewinkelt für Perspektive)
        playerPaddle = new Geometry("playerPaddle", new Box(1.8f, 0.4f, 0.2f));
        playerPaddle.setMaterial(matBlue);
        playerPaddle.setLocalTranslation(0, 0.5f, halfDepth - 0.6f);
        playerPaddle.rotate(0, FastMath.DEG_TO_RAD * 8f, 0);

        enemyPaddle = new Geometry("enemyPaddle", new Box(1.8f, 0.4f, 0.2f));
        enemyPaddle.setMaterial(matRed);
        enemyPaddle.setLocalTranslation(0, 0.5f, -halfDepth + 0.6f);
        enemyPaddle.rotate(0, -FastMath.DEG_TO_RAD * 8f, 0);

        // Ball
        Sphere sph = new Sphere(24, 24, ballRadius);
        ball = new Geometry("ball", sph);
        ball.setMaterial(matBall);
        ball.setLocalTranslation(0, ballRadius, 0);

        root.attachChild(playerPaddle);
        root.attachChild(enemyPaddle);
        root.attachChild(ball);

        // Trails: einfache Fake-Trails via nachziehenden Geometrien (leicht skalierte Boxen)
        attachTrail(playerPaddle, matBlue);
        attachTrail(enemyPaddle, matRed);

        // HUD
        var font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");
        hud = new BitmapText(font, false);
        hud.setSize(32);
        app.getGuiNode().attachChild(hud);
        updateHud();

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

        // Postprocessing: Bloom + FXAA (Glow sichtbar)
        FilterPostProcessor fpp = new FilterPostProcessor(app.getAssetManager());
        BloomFilter bloom = new BloomFilter(BloomFilter.GlowMode.Scene);
        bloom.setBloomIntensity(0.6f);
        fpp.addFilter(bloom);
        fpp.addFilter(new FXAAFilter());
        app.getViewPort().addProcessor(fpp);

        // Start-Richtung
        Random r = new Random();
        float signZ = r.nextBoolean() ? -1f : 1f;
        ballVel.set( (r.nextFloat()-0.5f)*6f, 0f, signZ * (5f + r.nextFloat()*2f));

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
        if (multiplayer) {
            applyNetworkState();
            updateHud();
            return;
        }
        // Player movement X
        float dxPlayer = (movePlayerRight ? 1 : 0) - (movePlayerLeft ? 1 : 0);
        playerPaddle.move(dxPlayer * paddleSpeed * tpf, 0, 0);
        clampPaddleX(playerPaddle);

        // Enemy movement: AI unless overridden
        if (moveEnemyLeft || moveEnemyRight) {
            float dxEnemy = (moveEnemyRight ? 1 : 0) - (moveEnemyLeft ? 1 : 0);
            enemyPaddle.move(dxEnemy * paddleSpeed * tpf, 0, 0);
            clampPaddleX(enemyPaddle);
        } else {
            float diff = ball.getLocalTranslation().x - enemyPaddle.getLocalTranslation().x;
            float step = FastMath.clamp(diff, -paddleSpeed * 0.7f * tpf, paddleSpeed * 0.7f * tpf);
            enemyPaddle.move(step, 0, 0);
            clampPaddleX(enemyPaddle);
        }

        // Ball
        ball.move(ballVel.x * tpf, 0, ballVel.z * tpf);

        // Spin
        float speed = FastMath.sqrt(ballVel.x*ballVel.x + ballVel.z*ballVel.z);
        if (speed > 1e-4f) {
            float angular = speed / ballRadius;
            Vector3f axis = new Vector3f(ballVel.z, 0, -ballVel.x).normalizeLocal();
            Quaternion q = new Quaternion().fromAngleAxis(angular * tpf, axis);
            ball.rotate(q);
        }

        // Walls X
        if (ball.getLocalTranslation().x > halfWidth - ballRadius && ballVel.x > 0) ballVel.x *= -1;
        if (ball.getLocalTranslation().x < -halfWidth + ballRadius && ballVel.x < 0) ballVel.x *= -1;

        // Paddles Z
        if (ball.getLocalTranslation().z > (halfDepth - 0.6f) && ballVel.z > 0) {
            if (Math.abs(ball.getLocalTranslation().x - playerPaddle.getLocalTranslation().x) <= 1.4f) {
                ballVel.z *= -1.07f;
                ballVel.x += (ball.getLocalTranslation().x - playerPaddle.getLocalTranslation().x) * 1.0f;
                limitBallSpeed();
            }
        }
        if (ball.getLocalTranslation().z < (-halfDepth + 0.6f) && ballVel.z < 0) {
            if (Math.abs(ball.getLocalTranslation().x - enemyPaddle.getLocalTranslation().x) <= 1.4f) {
                ballVel.z *= -1.07f;
                ballVel.x += (ball.getLocalTranslation().x - enemyPaddle.getLocalTranslation().x) * 1.0f;
                limitBallSpeed();
            }
        }

        // Goals
        if (ball.getLocalTranslation().z > halfDepth + 0.35f) {
            scoreEnemy++; resetBall(false);
        } else if (ball.getLocalTranslation().z < -halfDepth - 0.35f) {
            scorePlayer++; resetBall(true);
        }

        updateHud();
    }

    private void updateHud() {
        if (hud == null) return;
        String txt = scorePlayer + " : " + scoreEnemy;
        if (!txt.equals(hud.getText())) hud.setText(txt);
        float x = (app.getCamera().getWidth()/2f) - (hud.getLineWidth()/2f);
        float y = app.getCamera().getHeight() - 20f;
        hud.setLocalTranslation(x, y, 0);
    }

    private void clampPaddleX(Geometry p) {
        float x = p.getLocalTranslation().x;
        x = FastMath.clamp(x, -halfWidth + 1.8f, halfWidth - 1.8f);
        p.setLocalTranslation(x, p.getLocalTranslation().y, p.getLocalTranslation().z);
    }

    private void limitBallSpeed() {
        float max = 22f, min = 4.0f;
        if (Math.abs(ballVel.x) < min) ballVel.x = FastMath.sign(ballVel.x) * min;
        if (Math.abs(ballVel.z) < min) ballVel.z = FastMath.sign(ballVel.z) * min;
        ballVel.x = FastMath.clamp(ballVel.x, -max, max);
        ballVel.z = FastMath.clamp(ballVel.z, -max, max);
    }

    private void resetBall(boolean towardsEnemy) {
        ball.setLocalTranslation(0, ballRadius, 0);
        ball.setLocalRotation(Quaternion.IDENTITY);
        ballVel.set(0, 0, towardsEnemy ? -6f : 6f);
    }

    @Override
    protected void onEnable() {
        app.enqueue(() -> { app.getRootNode().attachChild(root); return null; });
    }

    @Override
    protected void onDisable() {
        app.enqueue(() -> { root.removeFromParent(); return null; });
        var im = app.getInputManager();
        im.deleteMapping("PL_LEFT");
        im.deleteMapping("PL_RIGHT");
        if (!multiplayer) {
            im.deleteMapping("EN_LEFT");
            im.deleteMapping("EN_RIGHT");
        }
        im.deleteMapping("PAUSE");
        im.deleteMapping("CAM_TOGGLE");
        if (hud != null) hud.removeFromParent();
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
            case "CAM_TOGGLE":
                if (!isPressed) {
                    boolean enabled = app.getFlyByCamera().isDragToRotate();
                    app.getFlyByCamera().setDragToRotate(!enabled);
                    app.getInputManager().setCursorVisible(enabled);
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

    private void applyNetworkState() {
        if (networkClient == null) return;
        var state = networkClient.getWorldState();
        if (state == null) return;
        networkRole = networkClient.getRole();

        float myX = networkRole == NetworkClient.Role.FRONT ? state.frontX() : state.backX();
        float oppX = networkRole == NetworkClient.Role.FRONT ? state.backX() : state.frontX();
        float ballZServer = state.ballZ();
        float localBallZ = networkRole == NetworkClient.Role.FRONT ? ballZServer : -ballZServer;

        playerPaddle.setLocalTranslation(myX, playerPaddle.getLocalTranslation().y, playerPaddle.getLocalTranslation().z);
        enemyPaddle.setLocalTranslation(oppX, enemyPaddle.getLocalTranslation().y, enemyPaddle.getLocalTranslation().z);
        ball.setLocalTranslation(state.ballX(), ball.getLocalTranslation().y, localBallZ);

        if (networkRole == NetworkClient.Role.FRONT) {
            scorePlayer = state.scoreFront();
            scoreEnemy = state.scoreBack();
        } else {
            scorePlayer = state.scoreBack();
            scoreEnemy = state.scoreFront();
        }
    }

    /** Simple follower that leaves a fading trail by sampling owner's transform with delay. */
    private static class TrailFollowControl extends com.jme3.scene.control.AbstractControl {
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
}
