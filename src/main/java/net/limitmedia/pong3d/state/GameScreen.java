package net.limitmedia.pong3d.state;

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
    private static final float BALL_RADIUS = 0.6f;
    private static final float BALL_BASE_SPEED = 9f;
    private static final float BALL_MAX_SPEED = 18f;
    private static final float SHAKE_BASE = 10f;

    private final GameApplication app;
    private final Input input;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private float time;
    private float playerX;
    private float opponentX;
    private float ballX;
    private float ballY;
    private float ballVX;
    private float ballVY;
    private float ballSpeed;
    private int playerScore;
    private int opponentScore;

    private float shakeTimer;
    private float shakeDuration;
    private float shakeSeed;
    private float shakeStrength;

    public GameScreen(GameApplication app) {
        this.app = app;
        this.input = app.getInput();
    }

    @Override
    public void onEnter() {
        time = 0f;
        playerX = 0f;
        opponentX = 0f;
        playerScore = 0;
        opponentScore = 0;
        shakeStrength = 0f;
        resetBall(true);
    }

    @Override
    public void onExit() {
        particles.clear();
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;
        handleInput(deltaTime);
        updateBall(deltaTime);
        updateParticles(deltaTime);

        if (input.wasPressed(GLFW.GLFW_KEY_ESCAPE)) {
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

        float targetX = ballX + ballVX * 0.35f;
        targetX = clamp(targetX, -arenaHalfWidth() + paddleHalfWidth(), arenaHalfWidth() - paddleHalfWidth());
        opponentX = smoothDamp(opponentX, targetX, deltaTime, AI_SMOOTH);
    }

    private void updateBall(float deltaTime) {
        ballX += ballVX * deltaTime;
        ballY += ballVY * deltaTime;

        float maxX = arenaHalfWidth() - BALL_RADIUS;
        if (ballX < -maxX) {
            ballX = -maxX;
            ballVX = Math.abs(ballVX) * 0.92f;
            triggerBounceEffect(ballX, ballY, 0.18f, 0.65f, 0.95f);
        } else if (ballX > maxX) {
            ballX = maxX;
            ballVX = -Math.abs(ballVX) * 0.92f;
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
            resetBall(true);
        } else if (ballY < -arenaHalfHeight()) {
            opponentScore++;
            triggerGoalEffect(false);
            resetBall(false);
        }

        if (shakeTimer > 0f) {
            shakeTimer = Math.max(0f, shakeTimer - deltaTime);
        }
    }

    private void reflectFromPaddle(float paddleX, boolean playerBounce) {
        float offset = (ballX - paddleX) / (paddleHalfWidth());
        float targetVX = offset * 8f;
        ballVX = lerp(ballVX, targetVX, 0.6f);
        ballSpeed = Math.min(ballSpeed * 1.05f + 0.4f, BALL_MAX_SPEED);
        ballVY = (playerBounce ? -1f : 1f) * ballSpeed;
        spawnParticles(ballX, ballY, playerBounce);
        startShake(0.35f, SHAKE_BASE * (playerBounce ? 1f : 0.9f));
    }

    private void triggerGoalEffect(boolean playerScored) {
        spawnParticles(ballX, ballY, playerScored);
        startShake(0.55f, SHAKE_BASE * 1.4f);
    }

    private void triggerBounceEffect(float bx, float by, float r, float g, float b) {
        particles.add(new Particle(bx, by, r, g, b, 0.8f, 0.5f + random.nextFloat() * 0.5f, 0.35f));
        startShake(0.22f, SHAKE_BASE * 0.6f);
    }

    private void startShake(float duration, float strength) {
        shakeDuration = duration;
        shakeTimer = duration;
        shakeSeed = random.nextFloat() * 1000f;
        shakeStrength = strength;
    }

    private void resetBall(boolean playerServe) {
        ballX = 0f;
        ballY = playerServe ? playerLaneY() + 1.5f : opponentLaneY() - 1.5f;
        ballSpeed = BALL_BASE_SPEED;
        ballVX = random.nextBoolean() ? 1f : -1f;
        ballVX *= 2.5f;
        ballVY = playerServe ? ballSpeed : -ballSpeed;
        particles.clear();
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

    @Override
    public void render() {
        int width = app.getWidth();
        int height = app.getHeight();
        float scale = Math.min(width / ARENA_WIDTH, height / ARENA_HEIGHT);
        float originX = (width - ARENA_WIDTH * scale) * 0.5f;
        float originY = (height - ARENA_HEIGHT * scale) * 0.5f;

        Draw.rect(0, 0, width, height, 0.06f, 0.07f, 0.09f, 1f);
        Draw.rect(0, 0, width, height, 0.05f, 0.08f, 0.12f, 0.35f);

        float backdropPulse = 0.35f + 0.1f * (float) Math.sin(time * 0.75f);
        for (int i = 0; i < 4; i++) {
            float stripAlpha = 0.18f + 0.05f * i;
            Draw.rect(originX + i * 28f, originY, ARENA_WIDTH * scale - i * 56f, ARENA_HEIGHT * scale,
                    0.08f + i * 0.02f,
                    0.12f + backdropPulse * 0.1f,
                    0.16f + backdropPulse * 0.2f,
                    stripAlpha);
        }

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
        Draw.rect(courtX, courtY, ARENA_WIDTH * scale, ARENA_HEIGHT * scale, 0.09f, 0.1f, 0.12f, 0.6f);
        Draw.rect(courtX + 6f, courtY + 6f, ARENA_WIDTH * scale - 12f, ARENA_HEIGHT * scale - 12f, 0.11f, 0.12f, 0.16f, 0.95f);

        float midY = courtY + ARENA_HEIGHT * scale / 2f;
        Draw.rect(courtX + ARENA_WIDTH * scale * 0.25f, midY - 2f, ARENA_WIDTH * scale * 0.5f, 4f, 0.3f, 0.6f, 0.95f, 0.4f);

        Draw.rect(screenX(playerX, originX, scale) - paddleHalfWidth() * scale,
                screenY(playerLaneY(), originY, scale) - PADDLE_HEIGHT * scale,
                PADDLE_WIDTH * scale,
                PADDLE_HEIGHT * scale,
                0.4f, 0.7f, 1f, 0.9f);

        Draw.rect(screenX(opponentX, originX, scale) - paddleHalfWidth() * scale,
                screenY(opponentLaneY(), originY, scale),
                PADDLE_WIDTH * scale,
                PADDLE_HEIGHT * scale,
                0.35f, 0.58f, 0.96f, 0.85f);

        Draw.circle(screenX(ballX, originX, scale), screenY(ballY, originY, scale), BALL_RADIUS * scale,
                0.9f, 0.95f, 1f, 1f, 36);
        Draw.circle(screenX(ballX, originX, scale), screenY(ballY, originY, scale), BALL_RADIUS * scale * 1.8f,
                0.45f, 0.75f, 1f, 0.25f, 36);

        for (Particle particle : particles) {
            float alpha = particle.alpha;
            Draw.circle(screenX(particle.x, originX, scale), screenY(particle.y, originY, scale),
                    particle.radius * scale, particle.r, particle.g, particle.b, alpha, 24);
        }

        Draw.text(String.format("%02d", playerScore), originX + ARENA_WIDTH * scale - 120f, originY + ARENA_HEIGHT * scale + 60f,
                2.4f, 0.8f, 0.9f, 1f, 0.95f);
        Draw.text(String.format("%02d", opponentScore), originX + 40f, originY - 60f, 2.4f, 0.7f, 0.82f, 1f, 0.8f);
        Draw.text("ESC öffnet das moderne Menü", originX + 20f, originY + ARENA_HEIGHT * scale + 100f,
                1.4f, 0.6f, 0.75f, 0.9f, 0.7f);
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
        for (int i = 0; i < 8; i++) {
            float radius = 0.6f + random.nextFloat() * 0.8f;
            float speed = 6f + random.nextFloat() * 6f;
            float life = 0.4f + random.nextFloat() * 0.35f;
            float r = warm ? 0.8f : 0.3f + random.nextFloat() * 0.2f;
            float g = warm ? 0.55f + random.nextFloat() * 0.3f : 0.6f + random.nextFloat() * 0.2f;
            float b = warm ? 0.3f + random.nextFloat() * 0.2f : 0.9f;
            particles.add(new Particle(x, y, r, g, b, radius, speed, life));
        }
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

}
