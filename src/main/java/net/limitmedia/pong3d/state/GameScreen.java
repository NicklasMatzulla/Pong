package net.limitmedia.pong3d.state;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.engine.Draw;
import net.limitmedia.pong3d.engine.GameApplication;
import net.limitmedia.pong3d.engine.Input;
import net.limitmedia.pong3d.engine.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class GameScreen implements Screen {
    private static final float ARENA_WIDTH = 18f;
    private static final float ARENA_HEIGHT = 28f;
    private static final float PADDLE_WIDTH = 6.5f;
    private static final float PADDLE_HEIGHT = 1.2f;
    private static final float PADDLE_SMOOTH = 12f;
    private static final float AI_SMOOTH = 4.5f;
    private static final float AI_PREDICTION = 0.35f;
    private static final float BALL_RADIUS = 0.6f;
    private static final float BALL_BASE_SPEED = 9f;
    private static final float BALL_MAX_SPEED = 18f;
    private static final float SHAKE_BASE = 10f;
    private static final int TRAIL_SAMPLES = 28;
    private static final int WIN_SCORE = 11;

    private final GameApplication app;
    private final Input input;
    private final AmbientAudioEngine audio;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();
    private final TrailSample[] trail = new TrailSample[TRAIL_SAMPLES];

    private float time;
    private float matchTime;
    private float playerX;
    private float opponentX;
    private float ballX;
    private float ballY;
    private float ballVX;
    private float ballVY;
    private float ballSpeed;
    private int playerScore;
    private int opponentScore;
    private int rallyCount;
    private int bestRally;

    private float shakeTimer;
    private float shakeDuration;
    private float shakeSeed;
    private float shakeStrength;

    private boolean waitingForServe;
    private boolean playerServeTurn;
    private float serveCountdown;
    private int countdownLastStep;
    private float countdownPulse;

    private boolean paused;
    private boolean matchOver;

    private float trailTimer;
    private int trailCursor;
    private int trailCount;

    private float statusTimer;
    private float statusDuration;
    private String statusText = "";

    public GameScreen(GameApplication app) {
        this.app = app;
        this.input = app.getInput();
        this.audio = app.getAudioEngine();
    }

    @Override
    public void onEnter() {
        time = 0f;
        matchTime = 0f;
        playerX = 0f;
        opponentX = 0f;
        playerScore = 0;
        opponentScore = 0;
        rallyCount = 0;
        bestRally = 0;
        paused = false;
        matchOver = false;
        shakeStrength = 0f;
        clearTrail();
        particles.clear();
        if (audio != null) {
            audio.playArenaLoop();
        }
        prepareServe(true, true);
    }

    @Override
    public void onExit() {
        particles.clear();
        clearTrail();
        waitingForServe = false;
        matchOver = false;
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;
        countdownPulse += deltaTime;
        if (statusTimer > 0f) {
            statusTimer = Math.max(0f, statusTimer - deltaTime);
        }

        if (matchOver) {
            updateParticles(deltaTime * 0.5f);
            handleMatchOverInput();
            return;
        }

        if (input.wasPressed(GLFW.GLFW_KEY_ESCAPE)) {
            paused = !paused;
            setStatus(paused ? "Pause" : "Zurück im Spiel", 1.3f);
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

        matchTime += deltaTime;

        handleInput(deltaTime);
        updateServeAndBall(deltaTime);
        updateParticles(deltaTime);

        if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
            app.returnToMenu();
        }
    }

    private void handleMatchOverInput() {
        if (input.wasPressed(GLFW.GLFW_KEY_ENTER) || input.wasPressed(GLFW.GLFW_KEY_SPACE)) {
            playerScore = 0;
            opponentScore = 0;
            rallyCount = 0;
            bestRally = 0;
            matchTime = 0f;
            paused = false;
            matchOver = false;
            particles.clear();
            clearTrail();
            prepareServe(true, true);
            return;
        }
        if (input.wasPressed(GLFW.GLFW_KEY_Q)) {
            app.returnToMenu();
        }
    }

    private void handleInput(float deltaTime) {
        float desiredX = playerX;
        if (input.isKeyDown(GLFW.GLFW_KEY_A) || input.isKeyDown(GLFW.GLFW_KEY_LEFT)) {
            desiredX -= BALL_BASE_SPEED * deltaTime * 1.4f;
        }
        if (input.isKeyDown(GLFW.GLFW_KEY_D) || input.isKeyDown(GLFW.GLFW_KEY_RIGHT)) {
            desiredX += BALL_BASE_SPEED * deltaTime * 1.4f;
        }
        desiredX = clamp(desiredX, -arenaHalfWidth() + paddleHalfWidth(), arenaHalfWidth() - paddleHalfWidth());
        playerX = smoothDamp(playerX, desiredX, deltaTime, PADDLE_SMOOTH);

        if (waitingForServe && playerServeTurn && input.wasPressed(GLFW.GLFW_KEY_SPACE)) {
            serveCountdown = Math.min(serveCountdown, 0.25f);
        }

        float targetX;
        if (waitingForServe && !playerServeTurn) {
            targetX = 0f;
        } else {
            targetX = ballX + ballVX * AI_PREDICTION;
        }
        targetX = clamp(targetX, -arenaHalfWidth() + paddleHalfWidth(), arenaHalfWidth() - paddleHalfWidth());
        opponentX = smoothDamp(opponentX, targetX, deltaTime, AI_SMOOTH);
    }

    private void updateServeAndBall(float deltaTime) {
        if (shakeTimer > 0f) {
            shakeTimer = Math.max(0f, shakeTimer - deltaTime);
        }

        if (waitingForServe) {
            serveCountdown -= deltaTime;
            followServeAnchor(deltaTime);
            updateTrail(deltaTime, false);

            int step = Math.max(0, (int) Math.ceil(serveCountdown));
            if (step < countdownLastStep && step > 0) {
                setStatus(String.valueOf(step), 0.75f);
                if (audio != null) {
                    audio.playCountdownTick();
                }
            }
            countdownLastStep = step;

            if (serveCountdown <= 0f) {
                launchServe();
                if (audio != null) {
                    audio.playCountdownGo();
                }
            }
            return;
        }

        ballX += ballVX * deltaTime;
        ballY += ballVY * deltaTime;
        updateTrail(deltaTime, true);

        float maxX = arenaHalfWidth() - BALL_RADIUS;
        if (ballX < -maxX) {
            ballX = -maxX;
            ballVX = Math.abs(ballVX) * 0.9f;
            triggerBounceEffect(ballX, ballY, 0.18f, 0.65f, 0.95f);
        } else if (ballX > maxX) {
            ballX = maxX;
            ballVX = -Math.abs(ballVX) * 0.9f;
            triggerBounceEffect(ballX, ballY, 0.18f, 0.65f, 0.95f);
        }

        float opponentY = opponentLaneY();
        float playerY = playerLaneY();

        if (ballY - BALL_RADIUS <= playerY + paddleHalfHeight() && ballY + BALL_RADIUS > playerY && Math.abs(ballX - playerX) <= paddleHalfWidth() + BALL_RADIUS) {
            ballY = playerY + paddleHalfHeight() + BALL_RADIUS;
            reflectFromPaddle(playerX, true);
        } else if (ballY + BALL_RADIUS >= opponentY - paddleHalfHeight() && ballY - BALL_RADIUS < opponentY && Math.abs(ballX - opponentX) <= paddleHalfWidth() + BALL_RADIUS) {
            ballY = opponentY - paddleHalfHeight() - BALL_RADIUS;
            reflectFromPaddle(opponentX, false);
        }

        if (ballY > arenaHalfHeight()) {
            playerScore++;
            triggerGoalEffect(true);
            handleScoreChange(true);
            return;
        } else if (ballY < -arenaHalfHeight()) {
            opponentScore++;
            triggerGoalEffect(false);
            handleScoreChange(false);
            return;
        }
    }

    private void handleScoreChange(boolean playerScored) {
        rallyCount = 0;
        if (playerScore >= WIN_SCORE || opponentScore >= WIN_SCORE) {
            matchOver = true;
            waitingForServe = false;
            paused = false;
            ballVX = 0f;
            ballVY = 0f;
            setStatus(playerScore > opponentScore ? "Du siegst!" : "Neues Match?", 3.2f);
            return;
        }
        playerServeTurn = !playerServeTurn;
        prepareServe(playerServeTurn, false);
        setStatus(playerScored ? "Punkt für dich" : "Punkt für Gegner", 1.6f);
    }

    private void prepareServe(boolean playerServe, boolean freshStart) {
        waitingForServe = true;
        playerServeTurn = playerServe;
        serveCountdown = freshStart ? 2.3f : 1.6f;
        countdownLastStep = (int) Math.ceil(serveCountdown) + 1;
        countdownPulse = 0f;
        ballSpeed = BALL_BASE_SPEED;
        ballVX = 0f;
        ballVY = 0f;
        clearTrail();
        float anchorY = playerServe ? playerLaneY() + paddleHalfHeight() + BALL_RADIUS + 0.35f : opponentLaneY() - paddleHalfHeight() - BALL_RADIUS - 0.35f;
        float anchorX = playerServe ? playerX : opponentX;
        ballX = clamp(anchorX, -arenaHalfWidth() + BALL_RADIUS, arenaHalfWidth() - BALL_RADIUS);
        ballY = anchorY;
        if (freshStart) {
            setStatus(playerServe ? "Dein Aufschlag" : "Gegner Aufschlag", serveCountdown);
        }
    }

    private void launchServe() {
        waitingForServe = false;
        float horizontal = (random.nextFloat() * 2f - 1f) * 3f;
        ballVX = clamp(horizontal, -BALL_BASE_SPEED * 0.8f, BALL_BASE_SPEED * 0.8f);
        ballVY = playerServeTurn ? ballSpeed : -ballSpeed;
        setStatus("Rally", 1.1f);
    }

    private void followServeAnchor(float deltaTime) {
        float anchorY = playerServeTurn ? playerLaneY() + paddleHalfHeight() + BALL_RADIUS + 0.35f : opponentLaneY() - paddleHalfHeight() - BALL_RADIUS - 0.35f;
        float anchorX = playerServeTurn ? playerX : opponentX;
        ballX = smoothDamp(ballX, clamp(anchorX, -arenaHalfWidth() + BALL_RADIUS, arenaHalfWidth() - BALL_RADIUS), deltaTime, 16f);
        ballY = smoothDamp(ballY, anchorY, deltaTime, 16f);
    }

    private void reflectFromPaddle(float paddleX, boolean playerBounce) {
        float offset = (ballX - paddleX) / (paddleHalfWidth());
        float targetVX = offset * 8f;
        ballVX = lerp(ballVX, targetVX, 0.55f);
        ballSpeed = Math.min(ballSpeed * 1.05f + 0.45f, BALL_MAX_SPEED);
        ballVY = (playerBounce ? -1f : 1f) * ballSpeed;
        rallyCount++;
        bestRally = Math.max(bestRally, rallyCount);
        spawnParticles(ballX, ballY, playerBounce);
        startShake(0.35f, SHAKE_BASE * (playerBounce ? 1f : 0.9f));
        if (audio != null) {
            audio.playBounce();
        }
    }

    private void triggerGoalEffect(boolean playerScored) {
        spawnParticles(ballX, ballY, playerScored);
        startShake(0.55f, SHAKE_BASE * 1.4f);
        if (audio != null) {
            audio.playGoal(playerScored);
        }
    }

    private void triggerBounceEffect(float bx, float by, float r, float g, float b) {
        particles.add(new Particle(bx, by, r, g, b, 0.8f, 0.5f + random.nextFloat() * 0.5f, 0.35f));
        startShake(0.22f, SHAKE_BASE * 0.6f);
        if (audio != null) {
            audio.playBounce();
        }
    }

    private void startShake(float duration, float strength) {
        shakeDuration = duration;
        shakeTimer = duration;
        shakeSeed = random.nextFloat() * 1000f;
        shakeStrength = strength;
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

    private void updateTrail(float deltaTime, boolean emit) {
        if (trailCount > 0) {
            for (int i = 0; i < trailCount; i++) {
                int index = (trailCursor - i + TRAIL_SAMPLES) % TRAIL_SAMPLES;
                TrailSample sample = trail[index];
                if (sample != null) {
                    sample.alpha = Math.max(0f, sample.alpha - deltaTime * 2.2f);
                    sample.radius += deltaTime * 0.4f;
                }
            }
            while (trailCount > 0) {
                TrailSample head = trail[(trailCursor - (trailCount - 1) + TRAIL_SAMPLES) % TRAIL_SAMPLES];
                if (head != null && head.alpha <= 0.01f) {
                    trailCount--;
                } else {
                    break;
                }
            }
        }

        if (!emit) {
            return;
        }

        trailTimer += deltaTime;
        float interval = 1f / 90f;
        while (trailTimer >= interval) {
            trailTimer -= interval;
            trailCursor = (trailCursor + 1) % TRAIL_SAMPLES;
            if (trailCount < TRAIL_SAMPLES) {
                trailCount++;
            }
            TrailSample sample = trail[trailCursor];
            if (sample == null) {
                sample = new TrailSample();
                trail[trailCursor] = sample;
            }
            sample.x = ballX;
            sample.y = ballY;
            sample.alpha = 0.85f;
            sample.radius = BALL_RADIUS * 1.3f;
        }
    }

    private void clearTrail() {
        trailCount = 0;
        trailCursor = 0;
        trailTimer = 0f;
        for (int i = 0; i < trail.length; i++) {
            if (trail[i] != null) {
                trail[i].alpha = 0f;
            }
        }
    }

    @Override
    public void render() {
        int width = app.getWidth();
        int height = app.getHeight();
        float scale = Math.min(width / ARENA_WIDTH, height / ARENA_HEIGHT);
        float originX = (width - ARENA_WIDTH * scale) * 0.5f;
        float originY = (height - ARENA_HEIGHT * scale) * 0.5f;

        renderBackdrop(width, height);

        float offsetX = 0f;
        float offsetY = 0f;
        if (shakeTimer > 0f && shakeDuration > 0f) {
            float t = shakeTimer / shakeDuration;
            float intensity = t * t;
            float phase = time * 60f + shakeSeed;
            float strength = shakeStrength * 0.05f;
            offsetX = (float) Math.sin(phase) * strength * intensity;
            offsetY = (float) Math.cos(phase * 0.8f) * strength * 0.75f * intensity;
        }

        float courtX = originX + offsetX;
        float courtY = originY + offsetY;

        renderCourt(scale, courtX, courtY);
        renderActors(scale, courtX, courtY, originX, originY);
        renderHud(width, height, scale, originX, originY);
        renderStatus(width, height);

        if (paused && !matchOver) {
            renderPauseOverlay(width, height);
        }
        if (matchOver) {
            renderMatchOverOverlay(width, height);
        }
    }

    private void renderBackdrop(int width, int height) {
        float pulse = 0.25f + 0.15f * (float) Math.sin(time * 0.45f);
        Draw.rect(0, 0, width, height, 0.04f, 0.05f, 0.07f, 1f);
        Draw.rect(0, 0, width, height, 0.06f, 0.07f, 0.1f, 0.55f);
        for (int i = 0; i < 6; i++) {
            float bandHeight = height / 8f;
            float y = i * bandHeight * 0.9f + (float) Math.sin(time * 0.2f + i) * 12f;
            float alpha = 0.08f + i * 0.035f;
            Draw.rect(0, y, width, bandHeight, 0.08f + pulse * 0.1f + i * 0.02f, 0.12f + pulse * 0.2f + i * 0.025f, 0.16f + pulse * 0.3f + i * 0.03f, alpha);
        }
        float orbRadius = 200f + 36f * (float) Math.sin(time * 0.6f);
        float orbX = width * 0.28f + (float) Math.sin(time * 0.35f) * 110f;
        float orbY = height * 0.32f + (float) Math.cos(time * 0.28f) * 70f;
        Draw.circle(orbX, orbY, orbRadius, 0.2f, 0.45f, 0.75f, 0.3f, 64);
        Draw.circle(width - orbX, height - orbY, orbRadius * 0.82f, 0.15f, 0.32f, 0.55f, 0.24f, 64);
    }

    private void renderCourt(float scale, float courtX, float courtY) {
        Draw.rect(courtX - 22f, courtY - 22f, ARENA_WIDTH * scale + 44f, ARENA_HEIGHT * scale + 44f, 0.1f, 0.13f, 0.18f, 0.42f);
        Draw.rect(courtX - 10f, courtY - 10f, ARENA_WIDTH * scale + 20f, ARENA_HEIGHT * scale + 20f, 0.08f, 0.1f, 0.14f, 0.55f);
        Draw.rect(courtX, courtY, ARENA_WIDTH * scale, ARENA_HEIGHT * scale, 0.09f, 0.1f, 0.12f, 0.65f);

        for (int i = 0; i < 4; i++) {
            float inset = i * 14f;
            float alpha = 0.18f - i * 0.03f;
            Draw.rect(courtX + inset, courtY + inset, ARENA_WIDTH * scale - inset * 2f, ARENA_HEIGHT * scale - inset * 2f, 0.11f + i * 0.02f, 0.12f + i * 0.03f, 0.16f + i * 0.04f, Math.max(0f, alpha));
        }

        float midY = courtY + ARENA_HEIGHT * scale / 2f;
        Draw.rect(courtX + ARENA_WIDTH * scale * 0.25f, midY - 2f, ARENA_WIDTH * scale * 0.5f, 4f, 0.3f, 0.6f, 0.95f, 0.4f);
        for (int i = 0; i < 5; i++) {
            float lineY = courtY + i * (ARENA_HEIGHT * scale / 4f);
            Draw.rect(courtX + 30f, lineY, ARENA_WIDTH * scale - 60f, 1.5f, 0.18f, 0.32f, 0.55f, 0.18f);
        }
    }

    private void renderActors(float scale, float courtX, float courtY, float originX, float originY) {
        float playerScreenX = screenX(playerX, originX, scale);
        float opponentScreenX = screenX(opponentX, originX, scale);
        float playerScreenY = screenY(playerLaneY(), originY, scale) - PADDLE_HEIGHT * scale;
        float opponentScreenY = screenY(opponentLaneY(), originY, scale);

        for (int i = 0; i < trailCount; i++) {
            int index = (trailCursor - i + TRAIL_SAMPLES) % TRAIL_SAMPLES;
            TrailSample sample = trail[index];
            if (sample == null || sample.alpha <= 0f) {
                continue;
            }
            float alpha = sample.alpha * 0.6f;
            Draw.circle(screenX(sample.x, originX, scale), screenY(sample.y, originY, scale), sample.radius * scale, 0.35f, 0.72f, 1f, alpha, 36);
        }

        Draw.rect(playerScreenX - paddleHalfWidth() * scale, playerScreenY, PADDLE_WIDTH * scale, PADDLE_HEIGHT * scale, 0.4f, 0.7f, 1f, 0.95f);
        Draw.rect(playerScreenX - paddleHalfWidth() * scale, playerScreenY - 6f, PADDLE_WIDTH * scale, 6f, 0.25f, 0.5f, 0.85f, 0.6f);

        Draw.rect(opponentScreenX - paddleHalfWidth() * scale, opponentScreenY, PADDLE_WIDTH * scale, PADDLE_HEIGHT * scale, 0.35f, 0.58f, 0.96f, 0.85f);
        Draw.rect(opponentScreenX - paddleHalfWidth() * scale, opponentScreenY + PADDLE_HEIGHT * scale, PADDLE_WIDTH * scale, 6f, 0.22f, 0.44f, 0.75f, 0.55f);

        float ballScreenX = screenX(ballX, originX, scale);
        float ballScreenY = screenY(ballY, originY, scale);
        Draw.circle(ballScreenX, ballScreenY, BALL_RADIUS * scale, 0.9f, 0.95f, 1f, 1f, 40);
        Draw.circle(ballScreenX, ballScreenY, BALL_RADIUS * scale * 1.8f, 0.45f, 0.75f, 1f, 0.25f + 0.1f * (float) Math.sin(time * 3f + countdownPulse * 2f), 40);

        for (Particle particle : particles) {
            if (particle.alpha <= 0f) {
                continue;
            }
            Draw.circle(screenX(particle.x, originX, scale), screenY(particle.y, originY, scale), particle.radius * scale, particle.r, particle.g, particle.b, particle.alpha, 28);
        }
    }

    private void renderHud(int width, int height, float scale, float originX, float originY) {
        float hudWidth = Math.min(width * 0.65f, 680f);
        float hudX = (width - hudWidth) * 0.5f;
        float hudY = originY - 90f;
        Draw.rect(hudX, hudY, hudWidth, 72f, 0.1f, 0.14f, 0.2f, 0.82f);
        Draw.rect(hudX, hudY, hudWidth, 4f, 0.3f, 0.6f, 0.95f, 1f);

        Draw.text(String.format("%02d", playerScore), hudX + 36f, hudY + 50f, 2.4f, 0.85f, 0.93f, 1f, 1f);
        Draw.text(String.format("%02d", opponentScore), hudX + hudWidth - 130f, hudY + 50f, 2.4f, 0.68f, 0.82f, 1f, 0.92f);

        Draw.text(formatClock(matchTime), hudX + hudWidth * 0.5f - 60f, hudY + 44f, 1.6f, 0.75f, 0.85f, 1f, 0.9f);
        Draw.text(String.format("Rally %02d | Best %02d", rallyCount, bestRally), hudX + 36f, hudY + 70f, 1.2f, 0.62f, 0.75f, 0.95f, 0.75f);

        String serveLabel = playerServeTurn ? "Aufschlag: Du" : "Aufschlag: Gegner";
        Draw.text(serveLabel, hudX + hudWidth - 220f, hudY + 20f, 1.2f, 0.55f, 0.7f, 0.9f, 0.7f);

        Draw.text("ESC Pause  |  Q Menü", originX + ARENA_WIDTH * scale - 220f, originY + ARENA_HEIGHT * scale + 90f, 1.1f, 0.55f, 0.68f, 0.85f, 0.7f);
        Draw.text("Space zum schnellen Aufschlag", originX + 20f, originY + ARENA_HEIGHT * scale + 90f, 1.1f, 0.55f, 0.68f, 0.85f, 0.7f);
    }

    private void renderStatus(int width, int height) {
        if (statusTimer <= 0f || statusText == null || statusText.isEmpty()) {
            return;
        }
        float alpha = Math.max(0f, Math.min(1f, statusTimer / Math.max(0.001f, statusDuration)));
        float scale = 2.4f;
        float textWidth = statusText.length() * 8f * scale;
        float x = width * 0.5f - textWidth * 0.5f;
        float y = height * 0.15f + (float) Math.sin(countdownPulse * 2f) * 4f;
        Draw.text(statusText, x, y, scale, 0.85f, 0.93f, 1f, alpha);
    }

    private void renderPauseOverlay(int width, int height) {
        Draw.rect(0, 0, width, height, 0.02f, 0.03f, 0.05f, 0.65f);
        Draw.text("PAUSE", width * 0.5f - 140f, height * 0.45f, 3.2f, 0.85f, 0.93f, 1f, 1f);
        Draw.text("ENTER/SPACE weiter", width * 0.5f - 200f, height * 0.55f, 1.6f, 0.7f, 0.82f, 1f, 0.9f);
        Draw.text("Q zurück ins Menü", width * 0.5f - 170f, height * 0.6f, 1.4f, 0.6f, 0.75f, 0.9f, 0.75f);
    }

    private void renderMatchOverOverlay(int width, int height) {
        Draw.rect(0, 0, width, height, 0.02f, 0.04f, 0.08f, 0.7f);
        Draw.text(playerScore > opponentScore ? "SIEG" : "MATCH BEENDET", width * 0.5f - 220f, height * 0.45f, 3.2f, 0.85f, 0.93f, 1f, 1f);
        Draw.text("ENTER für Rematch", width * 0.5f - 200f, height * 0.55f, 1.8f, 0.7f, 0.82f, 1f, 0.95f);
        Draw.text("Q Menü", width * 0.5f - 70f, height * 0.62f, 1.6f, 0.6f, 0.75f, 0.9f, 0.8f);
    }

    private void setStatus(String text, float duration) {
        if (text == null) {
            statusText = "";
            statusTimer = 0f;
            statusDuration = 1f;
            return;
        }
        statusText = text;
        statusTimer = duration;
        statusDuration = Math.max(0.1f, duration);
    }

    private float screenX(float worldX, float originX, float scale) {
        return originX + (worldX + arenaHalfWidth()) * scale;
    }

    private float screenY(float worldY, float originY, float scale) {
        return originY + (arenaHalfHeight() - worldY) * scale;
    }

    private float smoothDamp(float current, float target, float deltaTime, float smooth) {
        float factor = 1f - (float) Math.exp(-smooth * deltaTime);
        return current + (target - current) * factor;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float arenaHalfWidth() {
        return ARENA_WIDTH / 2f;
    }

    private float arenaHalfHeight() {
        return ARENA_HEIGHT / 2f;
    }

    private float paddleHalfWidth() {
        return PADDLE_WIDTH / 2f;
    }

    private float paddleHalfHeight() {
        return PADDLE_HEIGHT / 2f;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float playerLaneY() {
        return -arenaHalfHeight() + paddleHalfHeight() + 0.8f;
    }

    private float opponentLaneY() {
        return arenaHalfHeight() - paddleHalfHeight() - 0.8f;
    }

    private void spawnParticles(float x, float y, boolean warm) {
        for (int i = 0; i < 10; i++) {
            float radius = 0.6f + random.nextFloat() * 0.8f;
            float speed = 6f + random.nextFloat() * 6f;
            float life = 0.4f + random.nextFloat() * 0.35f;
            float r = warm ? 0.8f : 0.3f + random.nextFloat() * 0.2f;
            float g = warm ? 0.55f + random.nextFloat() * 0.3f : 0.6f + random.nextFloat() * 0.2f;
            float b = warm ? 0.3f + random.nextFloat() * 0.2f : 0.9f;
            particles.add(new Particle(x, y, r, g, b, radius, speed, life));
        }
    }

    private String formatClock(float seconds) {
        int total = Math.max(0, (int) seconds);
        int mins = total / 60;
        int secs = total % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private static final class Particle {
        float x;
        float y;
        final float r;
        final float g;
        final float b;
        float radius;
        final float speed;
        float alpha = 1f;
        float life;

        Particle(float x, float y, float r, float g, float b, float radius, float speed, float life) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.radius = radius;
            this.speed = speed;
            this.life = life;
        }

        void update(float deltaTime) {
            life -= deltaTime;
            alpha = Math.max(0f, life * 2.2f);
            y += speed * deltaTime * 0.35f;
            radius += deltaTime * 0.4f;
        }
    }

    private static final class TrailSample {
        float x;
        float y;
        float alpha;
        float radius;
    }
}
