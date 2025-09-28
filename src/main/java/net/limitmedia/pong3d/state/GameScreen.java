package net.limitmedia.pong3d.state;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.engine.Draw;
import net.limitmedia.pong3d.engine.GameApplication;
import net.limitmedia.pong3d.engine.Input;
import net.limitmedia.pong3d.engine.Scene3D;
import net.limitmedia.pong3d.engine.Scene3D.CameraFrame;
import net.limitmedia.pong3d.engine.Screen;
import net.limitmedia.pong3d.game.MatchConstants;
import net.limitmedia.pong3d.game.MatchFrame;
import net.limitmedia.pong3d.game.MatchSimulation;
import net.limitmedia.pong3d.game.MatchState;
import net.limitmedia.pong3d.network.MatchSnapshot;
import net.limitmedia.pong3d.network.NetworkClient;
import net.limitmedia.pong3d.network.NetworkServer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class GameScreen implements Screen {
    private static final int TRAIL_SAMPLES = 28;
    private static final float SHAKE_BASE = 10f;

    public enum Mode {
        LOCAL,
        NETWORK
    }

    private final GameApplication app;
    private final Input input;
    private final AmbientAudioEngine audio;
    private final Mode mode;
    private final NetworkClient networkClient;
    private final NetworkServer networkServer;
    private final MatchSimulation simulation;
    private final MatchState liveState;
    private final MatchState renderState = new MatchState();
    private final MatchState previousState = new MatchState();
    private final MatchSnapshot snapshotBuffer = new MatchSnapshot();
    private final List<Particle> particles = new ArrayList<>();
    private final TrailSample[] trail = new TrailSample[TRAIL_SAMPLES];
    private final Random random = new Random();

    private static final float WORLD_WIDTH = 52f;
    private static final float WORLD_DEPTH = 84f;
    private static final float WALL_HEIGHT = 14f;
    private static final float FLOOR_Y = -5.5f;
    private static final float PADDLE_BASE_Y = 1.6f;
    private static final float PADDLE_HEIGHT_WORLD = 4.8f;

    private float time;
    private float countdownPulse;
    private float statusTimer;
    private float statusDuration;
    private String statusText = "";
    private boolean paused;
    private boolean matchOver;
    private float shakeTimer;
    private float shakeDuration;
    private float shakeSeed;
    private float shakeStrength;
    private float trailTimer;
    private int trailCursor;
    private int trailCount;
    private int lastSnapshotTick;
    private int playerIndex;
    private boolean handshakeComplete;

    public GameScreen(GameApplication app) {
        this(app, Mode.LOCAL, null, null);
    }

    public GameScreen(GameApplication app, Mode mode, NetworkClient networkClient, NetworkServer networkServer) {
        this.app = app;
        this.input = app.getInput();
        this.audio = app.getAudioEngine();
        this.mode = mode;
        this.networkClient = networkClient;
        this.networkServer = networkServer;
        this.simulation = mode == Mode.LOCAL ? new MatchSimulation() : null;
        this.liveState = mode == Mode.LOCAL ? simulation.getState() : new MatchState();
        for (int i = 0; i < trail.length; i++) {
            trail[i] = new TrailSample();
        }
    }

    @Override
    public void onEnter() {
        time = 0f;
        countdownPulse = 0f;
        statusTimer = 0f;
        statusDuration = 0f;
        statusText = "";
        paused = false;
        matchOver = false;
        shakeStrength = 0f;
        shakeTimer = 0f;
        shakeDuration = 0f;
        trailCursor = 0;
        trailCount = 0;
        trailTimer = 0f;
        particles.clear();
        clearTrail();
        if (audio != null) {
            audio.playArenaLoop();
        }
        if (mode == Mode.LOCAL) {
            simulation.reset(true);
            renderState.copyFrom(liveState);
            previousState.copyFrom(liveState);
            setStatus("Dein Aufschlag", 2.0f);
        } else {
            liveState.reset();
            renderState.copyFrom(liveState);
            previousState.copyFrom(liveState);
            handshakeComplete = false;
            playerIndex = 0;
            lastSnapshotTick = -1;
            setStatus("Verbinde mit Server...", 2.4f);
        }
    }

    @Override
    public void onExit() {
        particles.clear();
        clearTrail();
        if (mode == Mode.NETWORK) {
            if (networkClient != null) {
                networkClient.close();
            }
            if (networkServer != null) {
                networkServer.close();
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;
        countdownPulse += deltaTime;
        if (statusTimer > 0f) {
            statusTimer = Math.max(0f, statusTimer - deltaTime);
        }
        if (shakeTimer > 0f) {
            shakeTimer = Math.max(0f, shakeTimer - deltaTime);
        }

        if (mode == Mode.NETWORK) {
            updateNetwork(deltaTime);
        } else {
            updateLocal(deltaTime);
        }
    }

    private void updateLocal(float deltaTime) {
        if (matchOver || liveState.matchOver) {
            matchOver = true;
            updateParticles(deltaTime * 0.5f);
            updateTrail(deltaTime, false);
            handleMatchOverInput();
            return;
        }

        if (input.wasPressed(GLFW.GLFW_KEY_ESCAPE)) {
            paused = !paused;
            setStatus(paused ? "Pause" : "Zurück im Spiel", 1.2f);
        }

        if (paused) {
            if (input.wasPressed(GLFW.GLFW_KEY_ENTER) || input.wasPressed(GLFW.GLFW_KEY_SPACE)) {
                paused = false;
                setStatus("Zurück im Spiel", 1.1f);
            } else if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
                app.returnToMenu();
                return;
            }
            updateParticles(deltaTime * 0.5f);
            updateTrail(deltaTime, false);
            return;
        }

        float axis = computePlayerAxis();
        boolean serveBoost = liveState.waitingForServe && liveState.playerServeTurn && input.wasPressed(GLFW.GLFW_KEY_SPACE);
        float opponentAxis = computeAiAxis(deltaTime);

        MatchFrame frame = simulation.update(deltaTime, axis, opponentAxis, serveBoost, false);
        applyFrame(frame, 0);

        updateParticles(deltaTime);
        updateTrail(deltaTime, !renderState.waitingForServe && !renderState.matchOver);

        if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
            app.returnToMenu();
        }
    }

    private void updateNetwork(float deltaTime) {
        if (networkClient == null) {
            app.showMainMenu("Keine Netzwerkverbindung");
            return;
        }
        String failure = networkClient.getFailureReason();
        if (failure != null) {
            app.showMainMenu("Verbindung getrennt: " + failure);
            return;
        }
        if (!networkClient.isRunning()) {
            app.showMainMenu("Verbindung beendet");
            return;
        }

        if (networkClient.isHandshakeComplete() && !handshakeComplete) {
            handshakeComplete = true;
            playerIndex = Math.max(0, networkClient.getPlayerIndex());
            setStatus(playerIndex == 0 ? "Verbunden als Spieler A" : "Verbunden als Spieler B", 2.0f);
        }

        float axis = computePlayerAxis();
        boolean serveBoost = renderState.waitingForServe && renderState.playerServeTurn && input.wasPressed(GLFW.GLFW_KEY_SPACE);
        networkClient.sendInput(axis, serveBoost);

        MatchSnapshot snapshot = networkClient.copySnapshot(snapshotBuffer);
        if (snapshot.getTick() != 0 && snapshot.getTick() != lastSnapshotTick) {
            lastSnapshotTick = snapshot.getTick();
            applySnapshot(snapshot);
        }

        updateParticles(deltaTime);
        updateTrail(deltaTime, !renderState.waitingForServe && !renderState.matchOver);

        if (renderState.matchOver) {
            matchOver = true;
        }

        if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
            app.returnToMenu();
        }
    }

    private void applyFrame(MatchFrame frame, int localIndex) {
        previousState.copyFrom(renderState);
        renderState.copyFrom(frame.getState());
        matchOver = renderState.matchOver;

        boolean localImpact = localIndex == 0 ? frame.isPlayerPaddleImpact() : frame.isOpponentPaddleImpact();
        boolean remoteImpact = localIndex == 0 ? frame.isOpponentPaddleImpact() : frame.isPlayerPaddleImpact();
        boolean localScored = localIndex == 0 ? frame.isPlayerScored() : !frame.isPlayerScored();

        processEvents(localImpact, remoteImpact, frame.isWallImpact(), frame.isGoalScored(), localScored,
                frame.isCountdownChanged(), frame.getCountdownValue());
    }

    private void applySnapshot(MatchSnapshot snapshot) {
        previousState.copyFrom(renderState);
        if (playerIndex == 0) {
            renderState.copyFrom(snapshot.getState());
        } else {
            mirrorState(snapshot.getState(), renderState);
        }

        boolean localImpact = playerIndex == 0 ? snapshot.isPlayerPaddleImpact() : snapshot.isOpponentPaddleImpact();
        boolean remoteImpact = playerIndex == 0 ? snapshot.isOpponentPaddleImpact() : snapshot.isPlayerPaddleImpact();
        boolean localScored = playerIndex == 0 ? snapshot.isPlayerScored() : !snapshot.isPlayerScored();

        processEvents(localImpact, remoteImpact, snapshot.isWallImpact(), snapshot.isGoalScored(), localScored,
                snapshot.isCountdownChanged(), snapshot.getCountdownValue());
    }

    private void processEvents(boolean localImpact, boolean remoteImpact, boolean wallImpact, boolean goalScored,
                               boolean localScored, boolean countdownChanged, int countdownValue) {
        if (countdownChanged) {
            if (countdownValue > 0) {
                setStatus(String.valueOf(countdownValue), 0.75f);
                if (audio != null) {
                    audio.playCountdownTick();
                }
            } else {
                setStatus("Rally", 1.0f);
                if (audio != null) {
                    audio.playCountdownGo();
                }
            }
        }

        if (wallImpact) {
            triggerWallBounce();
        }
        if (localImpact) {
            triggerPaddleBounce(true);
        } else if (remoteImpact) {
            triggerPaddleBounce(false);
        }
        if (goalScored) {
            triggerGoal(localScored);
        }
        if (renderState.matchOver && !previousState.matchOver) {
            setStatus(renderState.playerWon ? "Du siegst!" : "Neues Match?", 3.2f);
        }
    }

    private void handleMatchOverInput() {
        if (mode == Mode.NETWORK) {
            if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
                app.returnToMenu();
            }
            return;
        }
        if (input.wasPressed(GLFW.GLFW_KEY_ENTER) || input.wasPressed(GLFW.GLFW_KEY_SPACE)) {
            simulation.reset(true);
            renderState.copyFrom(liveState);
            previousState.copyFrom(liveState);
            particles.clear();
            clearTrail();
            matchOver = false;
            setStatus("Dein Aufschlag", 2.0f);
            return;
        }
        if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
            app.returnToMenu();
        }
    }

    private float computePlayerAxis() {
        float axis = 0f;
        if (input.isKeyDown(GLFW.GLFW_KEY_A) || input.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
            axis -= 1f;
        }
        if (input.isKeyDown(GLFW.GLFW_KEY_D) || input.isKeyDown(GLFW.GLFW_KEY_RIGHT)) {
            axis += 1f;
        }
        return clamp(axis, -1f, 1f);
    }

    private float computeAiAxis(float deltaTime) {
        if (liveState.waitingForServe && !liveState.playerServeTurn) {
            return clamp(-liveState.opponentX * 0.6f, -1f, 1f);
        }
        float predicted = liveState.ballX + liveState.ballVX * MatchConstants.AI_PREDICTION;
        float target = clamp(predicted, -arenaHalfWidth() + paddleHalfWidth(), arenaHalfWidth() - paddleHalfWidth());
        float delta = target - liveState.opponentX;
        float maxDelta = MatchConstants.BALL_BASE_SPEED * deltaTime * 1.4f;
        return clamp(delta / Math.max(0.001f, maxDelta), -1f, 1f);
    }

    @Override
    public void render() {
        int width = app.getWidth();
        int height = app.getHeight();
        float glow = 0.38f + 0.24f * (float) Math.sin(time * 0.62f);

        GL11.glClearColor(0.05f, 0.06f, 0.08f, 1f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

        float shakeX = 0f;
        float shakeY = 0f;
        float shakeRoll = 0f;
        if (shakeTimer > 0f && shakeDuration > 0f) {
            float strength = shakeStrength * (shakeTimer / shakeDuration);
            shakeX = (float) Math.sin(time * 26f + shakeSeed) * strength;
            shakeY = (float) Math.cos(time * 32f + shakeSeed * 0.6f) * strength;
            shakeRoll = (float) Math.sin(time * 18f + shakeSeed) * strength * 0.12f;
        }

        float eyeX = shakeX * 0.02f;
        float eyeY = 28f + shakeY * 0.03f;
        float eyeZ = 54f;
        float centerX = 0f;
        float centerY = 5f;
        float centerZ = 0f;

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        Scene3D.applyPerspective(58f, width / (float) height, 0.1f, 240f);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        CameraFrame camera = Scene3D.lookAt(eyeX, eyeY, eyeZ, centerX, centerY, centerZ, 0f, 1f, 0f);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glCullFace(GL11.GL_BACK);

        renderArena3D(glow, camera, shakeRoll);

        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);

        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        renderHUD(width, height);

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }

    private void renderArena3D(float glow, CameraFrame camera, float shakeRoll) {
        GL11.glPushMatrix();
        if (Math.abs(shakeRoll) > 0.0001f) {
            GL11.glRotatef(shakeRoll, 0f, 0f, 1f);
        }

        renderEnvironment(glow);
        renderTrail3D(camera);
        renderBall3D(camera, glow);
        renderPaddles3D(glow);
        renderParticles3D(camera);

        GL11.glPopMatrix();
    }

    private void renderEnvironment(float glow) {
        float floorOuterWidth = WORLD_WIDTH * 1.5f;
        float floorOuterDepth = WORLD_DEPTH * 1.5f;

        Scene3D.drawPlane(0f, FLOOR_Y - 0.4f, 0f, floorOuterWidth, floorOuterDepth,
                0.03f, 0.04f, 0.06f,
                0.06f, 0.07f, 0.1f, 1f);
        Scene3D.drawPlane(0f, FLOOR_Y, 0f, WORLD_WIDTH, WORLD_DEPTH,
                0.12f + glow * 0.08f, 0.15f + glow * 0.1f, 0.2f + glow * 0.12f,
                0.08f + glow * 0.06f, 0.11f + glow * 0.08f, 0.18f + glow * 0.1f, 0.96f);
        Scene3D.drawPlane(0f, FLOOR_Y + 0.12f, 0f, WORLD_WIDTH * 0.96f, 2.0f,
                0.32f + glow * 0.18f, 0.52f + glow * 0.22f, 0.78f + glow * 0.2f,
                0.24f + glow * 0.16f, 0.38f + glow * 0.18f, 0.65f + glow * 0.18f, 0.8f);

        Scene3D.drawBox(0f, FLOOR_Y + WALL_HEIGHT * 0.5f, WORLD_DEPTH * 0.5f + 1.4f,
                WORLD_WIDTH, WALL_HEIGHT, 3f, 0.09f, 0.11f, 0.16f, 0.74f);
        Scene3D.drawBox(0f, FLOOR_Y + WALL_HEIGHT * 0.5f, -WORLD_DEPTH * 0.5f - 1.4f,
                WORLD_WIDTH, WALL_HEIGHT, 3f, 0.09f, 0.11f, 0.16f, 0.74f);
        Scene3D.drawBox(WORLD_WIDTH * 0.5f + 1.4f, FLOOR_Y + WALL_HEIGHT * 0.5f, 0f,
                3f, WALL_HEIGHT, WORLD_DEPTH + 2f, 0.09f, 0.11f, 0.16f, 0.74f);
        Scene3D.drawBox(-WORLD_WIDTH * 0.5f - 1.4f, FLOOR_Y + WALL_HEIGHT * 0.5f, 0f,
                3f, WALL_HEIGHT, WORLD_DEPTH + 2f, 0.09f, 0.11f, 0.16f, 0.74f);

        Scene3D.drawBox(0f, FLOOR_Y + 2.8f, 0f,
                WORLD_WIDTH, 4.4f, 0.7f,
                0.24f + glow * 0.22f, 0.5f + glow * 0.24f, 0.82f + glow * 0.18f, 0.68f);

        Scene3D.drawPlane(0f, FLOOR_Y + WALL_HEIGHT - 0.6f, 0f, WORLD_WIDTH * 1.25f, WORLD_DEPTH * 1.25f,
                0.06f + glow * 0.05f, 0.07f + glow * 0.05f, 0.11f + glow * 0.06f,
                0.12f + glow * 0.08f, 0.13f + glow * 0.08f, 0.18f + glow * 0.1f, 0.42f);
    }

    private void renderTrail3D(CameraFrame camera) {
        if (trailCount <= 1) {
            return;
        }
        for (int i = 0; i < trailCount; i++) {
            TrailSample sample = trail[(trailCursor - i + TRAIL_SAMPLES) % TRAIL_SAMPLES];
            float alpha = 1f - (i / (float) trailCount);
            float size = ballWorldRadius() * (1.4f + alpha * 1.2f);
            float x = toWorldX(sample.x);
            float z = toWorldZ(sample.y);
            float y = computeTrailHeight(sample);
            Scene3D.drawBillboard(x, y, z, size, camera,
                    0.22f + alpha * 0.26f,
                    0.5f + alpha * 0.26f,
                    0.9f + alpha * 0.08f,
                    alpha * 0.48f);
        }
    }

    private void renderBall3D(CameraFrame camera, float glow) {
        float x = toWorldX(renderState.ballX);
        float z = toWorldZ(renderState.ballY);
        float y = computeBallHeight();
        float radius = ballWorldRadius();
        float tintR = 0.75f + glow * 0.22f;
        float tintG = 0.82f + glow * 0.2f;
        float tintB = 0.94f + glow * 0.12f;
        Scene3D.drawSphere(x, y, z, radius, 18, 28, tintR, tintG, tintB, 0.96f);
        Scene3D.drawBillboard(x, y, z, radius * 3.4f, camera,
                0.26f + glow * 0.25f,
                0.58f + glow * 0.22f,
                0.95f,
                0.32f);
    }

    private void renderPaddles3D(float glow) {
        float width = paddleWorldWidth();
        float depth = paddleWorldDepth();
        float centerY = PADDLE_BASE_Y + PADDLE_HEIGHT_WORLD * 0.5f;

        float playerX = toWorldX(renderState.playerX);
        float playerZ = toWorldZ(playerLaneY());
        Scene3D.drawBox(playerX, centerY, playerZ, width, PADDLE_HEIGHT_WORLD, depth,
                0.82f + glow * 0.12f, 0.92f + glow * 0.08f, 1f, 0.96f);
        Scene3D.drawBox(playerX, centerY + 0.55f, playerZ, width * 0.92f, PADDLE_HEIGHT_WORLD * 0.35f, depth * 0.82f,
                0.3f + glow * 0.18f, 0.6f + glow * 0.2f, 0.95f, 0.58f);

        float opponentX = toWorldX(renderState.opponentX);
        float opponentZ = toWorldZ(opponentLaneY());
        Scene3D.drawBox(opponentX, centerY, opponentZ, width, PADDLE_HEIGHT_WORLD, depth,
                0.58f + glow * 0.1f, 0.72f + glow * 0.12f, 0.95f, 0.96f);
        Scene3D.drawBox(opponentX, centerY + 0.55f, opponentZ, width * 0.92f, PADDLE_HEIGHT_WORLD * 0.35f, depth * 0.82f,
                0.26f + glow * 0.18f, 0.5f + glow * 0.18f, 0.84f, 0.58f);
    }

    private void renderParticles3D(CameraFrame camera) {
        for (Particle particle : particles) {
            float x = toWorldX(particle.x);
            float z = toWorldZ(particle.y);
            float size = toWorldRadius(particle.radius) * 3.2f;
            float y = PADDLE_BASE_Y + PADDLE_HEIGHT_WORLD * 0.5f + 0.8f + size * 0.12f;
            Scene3D.drawBillboard(x, y, z, size, camera,
                    particle.r, particle.g, particle.b, particle.alpha);
        }
    }

    private void renderHUD(int width, int height) {
        float hudWidth = Math.min(width * 0.46f, 540f);
        float hudHeight = 148f;
        float hudX = (width - hudWidth) * 0.5f;
        float hudY = height * 0.04f;

        Draw.rect(hudX - 16f, hudY - 18f, hudWidth + 32f, hudHeight + 36f, 0.05f, 0.07f, 0.12f, 0.42f);
        Draw.rect(hudX, hudY, hudWidth, hudHeight, 0.08f, 0.1f, 0.15f, 0.86f);
        Draw.rect(hudX, hudY, hudWidth, 6f, 0.32f, 0.6f, 0.94f, 1f);
        Draw.circle(hudX + hudWidth * 0.5f, hudY + hudHeight - 18f, 120f, 0.18f, 0.24f, 0.46f, 0.2f, 48);

        Draw.text(String.valueOf(renderState.playerScore), hudX + 56f, hudY + 80f, 3.2f, 0.92f, 0.97f, 1f, 1f);
        Draw.text(String.valueOf(renderState.opponentScore), hudX + hudWidth - 124f, hudY + 80f, 3.2f, 0.92f, 0.97f, 1f, 1f);
        Draw.text("Rally " + renderState.rallyCount + "  |  Best " + renderState.bestRally,
                hudX + 56f, hudY + 122f, 1.35f, 0.72f, 0.84f, 0.96f, 0.88f);

        if (matchOver) {
            String summary = renderState.playerWon ? "Du siegst!" : "Gegner gewinnt";
            Draw.text(summary, hudX + hudWidth * 0.5f - 72f, hudY + 40f, 1.8f, 0.95f, 0.85f, 0.9f, 0.96f);
            Draw.text("ENTER für Rematch  •  ESC zurück", hudX + hudWidth * 0.5f - 130f, hudY + 134f,
                    1.2f, 0.7f, 0.8f, 0.95f, 0.78f);
        } else if (statusTimer > 0f && statusDuration > 0f) {
            float alpha = statusTimer / statusDuration;
            Draw.text(statusText, hudX + hudWidth * 0.5f - statusText.length() * 7.5f,
                    hudY + 40f, 1.8f, 0.9f, 0.96f, 1f, alpha);
        } else if (paused) {
            Draw.text("Pause", hudX + hudWidth * 0.5f - 36f, hudY + 40f, 1.8f, 0.9f, 0.96f, 1f, 1f);
        } else if (renderState.waitingForServe) {
            Draw.text("Serve in " + String.format("%.1f", Math.max(0f, renderState.serveCountdown)),
                    hudX + hudWidth * 0.5f - 68f, hudY + 40f, 1.6f, 0.9f, 0.96f, 1f, 0.92f);
        }

        if (mode == Mode.NETWORK) {
            String label = handshakeComplete ? "Online" : "Warte auf Spieler...";
            float labelWidth = label.length() * 7f;
            Draw.text(label, hudX + hudWidth - labelWidth - 48f, hudY + 40f, 1.25f,
                    0.64f, 0.78f, 0.96f, 0.86f);
        }
    }

    private float computeBallHeight() {
        float speedRatio = clamp(renderState.ballSpeed / MatchConstants.BALL_MAX_SPEED, 0f, 1f);
        float travel = Math.abs(renderState.ballY) / arenaHalfHeight();
        return PADDLE_BASE_Y + PADDLE_HEIGHT_WORLD * 0.5f + 1.4f
                + (1f - travel) * 2.6f + speedRatio * 1.8f;
    }

    private float computeTrailHeight(TrailSample sample) {
        float speed = (float) Math.sqrt(sample.vx * sample.vx + sample.vy * sample.vy);
        float speedRatio = clamp(speed / MatchConstants.BALL_MAX_SPEED, 0f, 1f);
        return PADDLE_BASE_Y + PADDLE_HEIGHT_WORLD * 0.5f + 1.0f + speedRatio * 1.4f;
    }

    private float toWorldX(float arenaX) {
        return arenaX / arenaHalfWidth() * (WORLD_WIDTH * 0.5f);
    }

    private float toWorldZ(float arenaY) {
        return -(arenaY / arenaHalfHeight()) * (WORLD_DEPTH * 0.5f);
    }

    private float paddleWorldWidth() {
        return MatchConstants.PADDLE_WIDTH / MatchConstants.ARENA_WIDTH * WORLD_WIDTH;
    }

    private float paddleWorldDepth() {
        return MatchConstants.PADDLE_HEIGHT / MatchConstants.ARENA_HEIGHT * WORLD_DEPTH;
    }

    private float ballWorldRadius() {
        return MatchConstants.BALL_RADIUS / MatchConstants.ARENA_WIDTH * WORLD_WIDTH * 1.2f;
    }

    private float toWorldRadius(float arenaUnits) {
        return arenaUnits / MatchConstants.ARENA_WIDTH * WORLD_WIDTH;
    }

    private void updateParticles(float deltaTime) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.update(deltaTime);
            if (particle.alpha <= 0f) {
                iterator.remove();
            }
        }
    }

    private void updateTrail(float deltaTime, boolean active) {
        trailTimer += deltaTime;
        if (!active) {
            return;
        }
        float interval = 0.012f;
        while (trailTimer >= interval) {
            trailTimer -= interval;
            TrailSample sample = trail[trailCursor];
            sample.x = renderState.ballX;
            sample.y = renderState.ballY;
            sample.vx = renderState.ballVX;
            sample.vy = renderState.ballVY;
            trailCursor = (trailCursor + 1) % TRAIL_SAMPLES;
            if (trailCount < TRAIL_SAMPLES) {
                trailCount++;
            }
        }
    }

    private void clearTrail() {
        for (TrailSample sample : trail) {
            sample.x = 0f;
            sample.y = 0f;
            sample.vx = 0f;
            sample.vy = 0f;
        }
        trailCursor = 0;
        trailCount = 0;
        trailTimer = 0f;
    }

    private void triggerWallBounce() {
        particles.add(new Particle(renderState.ballX, renderState.ballY, 0.18f, 0.65f, 0.95f, 0.8f,
                0.5f + random.nextFloat() * 0.5f, 0.35f));
        startShake(0.22f, SHAKE_BASE * 0.6f);
        if (audio != null) {
            audio.playBounce();
        }
    }

    private void triggerPaddleBounce(boolean localPlayer) {
        particles.add(new Particle(renderState.ballX, renderState.ballY,
                localPlayer ? 0.35f : 0.18f,
                localPlayer ? 0.62f : 0.52f,
                localPlayer ? 0.95f : 0.72f,
                0.92f,
                0.6f + random.nextFloat() * 0.4f, 0.42f));
        startShake(0.35f, SHAKE_BASE * (localPlayer ? 1.0f : 0.85f));
        if (audio != null) {
            audio.playBounce();
        }
    }

    private void triggerGoal(boolean localPlayer) {
        particles.add(new Particle(renderState.ballX, renderState.ballY,
                localPlayer ? 0.4f : 0.2f,
                localPlayer ? 0.75f : 0.4f,
                localPlayer ? 0.95f : 0.45f,
                1f,
                0.8f + random.nextFloat() * 0.4f, 0.55f));
        startShake(0.55f, SHAKE_BASE * 1.4f);
        if (audio != null) {
            audio.playGoal(localPlayer);
        }
        setStatus(localPlayer ? "Punkt für dich" : "Punkt für Gegner", 1.6f);
    }

    private void startShake(float duration, float strength) {
        shakeDuration = duration;
        shakeTimer = duration;
        shakeSeed = random.nextFloat() * 1000f;
        shakeStrength = strength;
    }

    private void setStatus(String message, float duration) {
        statusText = message;
        statusDuration = duration;
        statusTimer = duration;
    }

    private static void mirrorState(MatchState source, MatchState target) {
        target.playerX = source.opponentX;
        target.opponentX = source.playerX;
        target.ballX = source.ballX;
        target.ballY = -source.ballY;
        target.ballVX = source.ballVX;
        target.ballVY = -source.ballVY;
        target.ballSpeed = source.ballSpeed;
        target.playerScore = source.opponentScore;
        target.opponentScore = source.playerScore;
        target.rallyCount = source.rallyCount;
        target.bestRally = source.bestRally;
        target.waitingForServe = source.waitingForServe;
        target.playerServeTurn = !source.playerServeTurn;
        target.serveCountdown = source.serveCountdown;
        target.matchOver = source.matchOver;
        target.playerWon = !source.playerWon;
        target.matchTime = source.matchTime;
    }

    private static float arenaHalfWidth() {
        return MatchConstants.ARENA_WIDTH * 0.5f;
    }

    private static float arenaHalfHeight() {
        return MatchConstants.ARENA_HEIGHT * 0.5f;
    }

    private static float paddleHalfWidth() {
        return MatchConstants.PADDLE_WIDTH * 0.5f;
    }

    private static float paddleHalfHeight() {
        return MatchConstants.PADDLE_HEIGHT * 0.5f;
    }

    private static float playerLaneY() {
        return -arenaHalfHeight() + 3.4f;
    }

    private static float opponentLaneY() {
        return arenaHalfHeight() - 3.4f;
    }

    private static float BALL_RADIUS() {
        return MatchConstants.BALL_RADIUS;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Particle {
        float x;
        float y;
        float r;
        float g;
        float b;
        float alpha;
        float radius;
        float life;

        Particle(float x, float y, float r, float g, float b, float alpha, float radius, float life) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.alpha = alpha;
            this.radius = radius;
            this.life = life;
        }

        void update(float delta) {
            life -= delta;
            alpha = Math.max(0f, life / 0.55f);
            radius *= 1.02f;
        }
    }

    private static final class TrailSample {
        float x;
        float y;
        float vx;
        float vy;
    }
}
