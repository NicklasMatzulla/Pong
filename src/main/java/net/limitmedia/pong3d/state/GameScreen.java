package net.limitmedia.pong3d.state;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.engine.Draw;
import net.limitmedia.pong3d.engine.GameApplication;
import net.limitmedia.pong3d.engine.Input;
import net.limitmedia.pong3d.engine.Screen;
import net.limitmedia.pong3d.game.MatchConstants;
import net.limitmedia.pong3d.game.MatchFrame;
import net.limitmedia.pong3d.game.MatchSimulation;
import net.limitmedia.pong3d.game.MatchState;
import net.limitmedia.pong3d.network.MatchSnapshot;
import net.limitmedia.pong3d.network.NetworkClient;
import net.limitmedia.pong3d.network.NetworkServer;
import org.lwjgl.glfw.GLFW;

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
        float glow = 0.4f + 0.2f * (float) Math.sin(time * 0.8f);

        Draw.rect(0, 0, width, height, 0.03f, 0.04f, 0.06f, 1f);
        Draw.rect(0, 0, width, height, 0.06f, 0.07f, 0.12f, 0.55f);
        for (int i = 0; i < 7; i++) {
            float bandHeight = height / 10f;
            float y = i * bandHeight * 0.9f + (float) Math.sin(time * 0.3f + i) * 18f;
            float alpha = 0.06f + i * 0.04f;
            Draw.rect(0, y, width, bandHeight, 0.08f + glow * 0.1f + i * 0.02f,
                    0.12f + glow * 0.2f + i * 0.025f,
                    0.18f + glow * 0.3f + i * 0.03f, alpha);
        }

        float shakeX = 0f;
        float shakeY = 0f;
        if (shakeTimer > 0f && shakeDuration > 0f) {
            float strength = shakeStrength * (shakeTimer / shakeDuration);
            shakeX = (float) Math.sin(time * 26f + shakeSeed) * strength;
            shakeY = (float) Math.cos(time * 32f + shakeSeed * 0.6f) * strength;
        }

        renderArena(width, height, glow, shakeX, shakeY);

        renderHUD(width, height);
    }

    private void renderArena(int width, int height, float glow, float shakeX, float shakeY) {
        float arenaPxWidth = width * 0.72f;
        float arenaPxHeight = height * 0.82f;
        float arenaX = (width - arenaPxWidth) * 0.5f + shakeX;
        float arenaY = (height - arenaPxHeight) * 0.55f + shakeY;

        Draw.rect(arenaX - 26f, arenaY - 24f, arenaPxWidth + 52f, arenaPxHeight + 48f, 0.05f, 0.06f, 0.09f, 0.75f);
        Draw.rect(arenaX - 12f, arenaY - 12f, arenaPxWidth + 24f, arenaPxHeight + 24f, 0.08f, 0.09f, 0.13f, 0.92f);
        Draw.rect(arenaX, arenaY, arenaPxWidth, arenaPxHeight, 0.09f, 0.1f, 0.15f, 0.95f);

        float laneThickness = arenaPxHeight / MatchConstants.ARENA_HEIGHT * 0.8f;
        Draw.rect(arenaX, arenaY + arenaPxHeight * 0.5f - laneThickness * 0.5f, arenaPxWidth, laneThickness,
                0.12f, 0.14f, 0.2f, 0.45f);

        renderTrail(arenaX, arenaY, arenaPxWidth, arenaPxHeight);
        renderBall(arenaX, arenaY, arenaPxWidth, arenaPxHeight);
        renderPaddles(arenaX, arenaY, arenaPxWidth, arenaPxHeight);
        renderParticles(arenaX, arenaY, arenaPxWidth, arenaPxHeight);
    }

    private void renderHUD(int width, int height) {
        float hudWidth = width * 0.38f;
        float hudHeight = 120f;
        float hudX = (width - hudWidth) * 0.5f;
        float hudY = height * 0.04f;

        Draw.rect(hudX, hudY, hudWidth, hudHeight, 0.08f, 0.1f, 0.14f, 0.85f);
        Draw.rect(hudX, hudY, hudWidth, 4f, 0.32f, 0.62f, 0.92f, 1f);

        Draw.text(String.valueOf(renderState.playerScore), hudX + 48f, hudY + 72f, 2.8f, 0.9f, 0.96f, 1f, 1f);
        Draw.text(String.valueOf(renderState.opponentScore), hudX + hudWidth - 132f, hudY + 72f, 2.8f, 0.9f, 0.96f, 1f, 1f);
        Draw.text("Rally: " + renderState.rallyCount + "  •  Best: " + renderState.bestRally,
                hudX + 52f, hudY + 108f, 1.2f, 0.72f, 0.82f, 0.95f, 0.82f);

        if (statusTimer > 0f) {
            Draw.text(statusText, hudX + hudWidth * 0.5f - statusText.length() * 8f,
                    hudY + 36f, 1.6f, 0.88f, 0.94f, 1f, statusTimer / statusDuration);
        }
    }

    private void renderTrail(float arenaX, float arenaY, float arenaPxWidth, float arenaPxHeight) {
        if (trailCount <= 1) {
            return;
        }
        for (int i = 0; i < trailCount; i++) {
            TrailSample sample = trail[(trailCursor - i + TRAIL_SAMPLES) % TRAIL_SAMPLES];
            float alpha = 1f - (i / (float) trailCount);
            float radius = BALL_RADIUS() * arenaPxWidth / MatchConstants.ARENA_WIDTH * (0.8f + alpha * 0.4f);
            float x = arenaX + (sample.x + arenaHalfWidth()) / MatchConstants.ARENA_WIDTH * arenaPxWidth;
            float y = arenaY + arenaPxHeight - (sample.y + arenaHalfHeight()) / MatchConstants.ARENA_HEIGHT * arenaPxHeight;
            Draw.circle(x, y, radius, 0.2f + alpha * 0.2f, 0.45f + alpha * 0.3f, 0.75f + alpha * 0.2f, alpha * 0.4f, 24);
        }
    }

    private void renderBall(float arenaX, float arenaY, float arenaPxWidth, float arenaPxHeight) {
        float x = arenaX + (renderState.ballX + arenaHalfWidth()) / MatchConstants.ARENA_WIDTH * arenaPxWidth;
        float y = arenaY + arenaPxHeight - (renderState.ballY + arenaHalfHeight()) / MatchConstants.ARENA_HEIGHT * arenaPxHeight;
        float radius = BALL_RADIUS() * arenaPxWidth / MatchConstants.ARENA_WIDTH;
        Draw.circle(x, y, radius * 1.4f, 0.16f, 0.35f, 0.68f, 0.32f, 36);
        Draw.circle(x, y, radius, 0.85f, 0.92f, 1f, 1f, 48);
    }

    private void renderPaddles(float arenaX, float arenaY, float arenaPxWidth, float arenaPxHeight) {
        float paddleWidthPx = MatchConstants.PADDLE_WIDTH / MatchConstants.ARENA_WIDTH * arenaPxWidth;
        float paddleHeightPx = MatchConstants.PADDLE_HEIGHT / MatchConstants.ARENA_HEIGHT * arenaPxHeight;

        float playerX = arenaX + (renderState.playerX + arenaHalfWidth()) / MatchConstants.ARENA_WIDTH * arenaPxWidth - paddleWidthPx / 2f;
        float playerY = arenaY + arenaPxHeight - (playerLaneY() + arenaHalfHeight()) / MatchConstants.ARENA_HEIGHT * arenaPxHeight - paddleHeightPx;
        float opponentX = arenaX + (renderState.opponentX + arenaHalfWidth()) / MatchConstants.ARENA_WIDTH * arenaPxWidth - paddleWidthPx / 2f;
        float opponentY = arenaY + arenaPxHeight - (opponentLaneY() + arenaHalfHeight()) / MatchConstants.ARENA_HEIGHT * arenaPxHeight;

        Draw.rect(playerX - 4f, playerY - 4f, paddleWidthPx + 8f, paddleHeightPx + 8f, 0.06f, 0.08f, 0.12f, 0.8f);
        Draw.rect(playerX, playerY, paddleWidthPx, paddleHeightPx, 0.82f, 0.92f, 1f, 0.92f);

        Draw.rect(opponentX - 4f, opponentY - 4f, paddleWidthPx + 8f, paddleHeightPx + 8f, 0.06f, 0.08f, 0.12f, 0.8f);
        Draw.rect(opponentX, opponentY, paddleWidthPx, paddleHeightPx, 0.62f, 0.74f, 0.92f, 0.92f);
    }

    private void renderParticles(float arenaX, float arenaY, float arenaPxWidth, float arenaPxHeight) {
        for (Particle particle : particles) {
            float x = arenaX + (particle.x + arenaHalfWidth()) / MatchConstants.ARENA_WIDTH * arenaPxWidth;
            float y = arenaY + arenaPxHeight - (particle.y + arenaHalfHeight()) / MatchConstants.ARENA_HEIGHT * arenaPxHeight;
            float radius = particle.radius * arenaPxWidth / MatchConstants.ARENA_WIDTH;
            Draw.circle(x, y, radius, particle.r, particle.g, particle.b, particle.alpha, 18);
        }
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
