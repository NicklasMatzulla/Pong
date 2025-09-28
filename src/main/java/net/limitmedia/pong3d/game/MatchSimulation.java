package net.limitmedia.pong3d.game;

import java.util.Random;

public final class MatchSimulation {
    private final MatchState state = new MatchState();
    private final MatchFrame frame = new MatchFrame();
    private final Random random = new Random();

    private int tick;
    private int lastCountdownStep = Integer.MAX_VALUE;

    public MatchSimulation() {
        reset(true);
    }

    public MatchState getState() {
        return state;
    }

    public MatchFrame update(float deltaTime, float playerAxis, float opponentAxis,
                              boolean playerServeBoost, boolean opponentServeBoost) {
        frame.setPlayerPaddleImpact(false);
        frame.setOpponentPaddleImpact(false);
        frame.setWallImpact(false);
        frame.setGoalScored(false);
        frame.setPlayerScored(false);
        frame.setCountdownChanged(false);
        frame.setCountdownValue(state.waitingForServe ? Math.max(0, (int) Math.ceil(state.serveCountdown)) : 0);
        frame.setTick(++tick);

        if (state.matchOver) {
            state.matchTime += deltaTime;
            frame.getState().copyFrom(state);
            return frame;
        }

        state.matchTime += deltaTime;

        updatePaddle(true, playerAxis, deltaTime);
        updatePaddle(false, opponentAxis, deltaTime);

        if (playerServeBoost && state.waitingForServe && state.playerServeTurn) {
            state.serveCountdown = Math.min(state.serveCountdown, 0.25f);
        } else if (opponentServeBoost && state.waitingForServe && !state.playerServeTurn) {
            state.serveCountdown = Math.min(state.serveCountdown, 0.25f);
        }

        if (state.waitingForServe) {
            state.serveCountdown -= deltaTime;
            followServeAnchor(deltaTime);

            int step = Math.max(0, (int) Math.ceil(state.serveCountdown));
            if (step < lastCountdownStep) {
                frame.setCountdownChanged(true);
                frame.setCountdownValue(step);
            }
            lastCountdownStep = step;

            if (state.serveCountdown <= 0f) {
                launchServe();
            }
            frame.getState().copyFrom(state);
            return frame;
        }

        updateBall(deltaTime);
        frame.getState().copyFrom(state);
        return frame;
    }

    public void reset(boolean playerServe) {
        state.reset();
        prepareServe(playerServe, true);
        tick = 0;
    }

    public void prepareServe(boolean playerServe, boolean freshStart) {
        state.waitingForServe = true;
        state.playerServeTurn = playerServe;
        state.serveCountdown = freshStart ? 2.3f : 1.6f;
        lastCountdownStep = (int) Math.ceil(state.serveCountdown) + 1;
        state.ballSpeed = MatchConstants.BALL_BASE_SPEED;
        state.ballVX = 0f;
        state.ballVY = 0f;
        float anchorY = playerServe ? playerLaneY() + paddleHalfHeight() + MatchConstants.BALL_RADIUS + 0.35f
                : opponentLaneY() - paddleHalfHeight() - MatchConstants.BALL_RADIUS - 0.35f;
        float anchorX = playerServe ? state.playerX : state.opponentX;
        state.ballX = clamp(anchorX, -arenaHalfWidth() + MatchConstants.BALL_RADIUS,
                arenaHalfWidth() - MatchConstants.BALL_RADIUS);
        state.ballY = anchorY;
    }

    private void launchServe() {
        state.waitingForServe = false;
        float horizontal = (random.nextFloat() * 2f - 1f) * 3f;
        state.ballVX = clamp(horizontal, -MatchConstants.BALL_BASE_SPEED * 0.8f, MatchConstants.BALL_BASE_SPEED * 0.8f);
        state.ballVY = state.playerServeTurn ? state.ballSpeed : -state.ballSpeed;
    }

    private void updatePaddle(boolean player, float axis, float deltaTime) {
        float speed = MatchConstants.BALL_BASE_SPEED * 1.4f;
        float desired = player ? state.playerX : state.opponentX;
        desired += clamp(axis, -1f, 1f) * speed * deltaTime;
        desired = clamp(desired, -arenaHalfWidth() + paddleHalfWidth(), arenaHalfWidth() - paddleHalfWidth());
        float smoothed = smoothDamp(player ? state.playerX : state.opponentX, desired, deltaTime,
                MatchConstants.PADDLE_SMOOTH);
        if (player) {
            state.playerX = smoothed;
        } else {
            state.opponentX = smoothed;
        }
    }

    private void followServeAnchor(float deltaTime) {
        float anchorY = state.playerServeTurn
                ? playerLaneY() + paddleHalfHeight() + MatchConstants.BALL_RADIUS + 0.35f
                : opponentLaneY() - paddleHalfHeight() - MatchConstants.BALL_RADIUS - 0.35f;
        float anchorX = state.playerServeTurn ? state.playerX : state.opponentX;
        state.ballX = smoothDamp(state.ballX,
                clamp(anchorX, -arenaHalfWidth() + MatchConstants.BALL_RADIUS,
                        arenaHalfWidth() - MatchConstants.BALL_RADIUS),
                deltaTime, 16f);
        state.ballY = smoothDamp(state.ballY, anchorY, deltaTime, 16f);
    }

    private void updateBall(float deltaTime) {
        state.ballX += state.ballVX * deltaTime;
        state.ballY += state.ballVY * deltaTime;

        float maxX = arenaHalfWidth() - MatchConstants.BALL_RADIUS;
        if (state.ballX < -maxX) {
            state.ballX = -maxX;
            state.ballVX = Math.abs(state.ballVX) * 0.9f;
            frame.setWallImpact(true);
        } else if (state.ballX > maxX) {
            state.ballX = maxX;
            state.ballVX = -Math.abs(state.ballVX) * 0.9f;
            frame.setWallImpact(true);
        }

        float opponentY = opponentLaneY();
        float playerY = playerLaneY();

        if (state.ballY - MatchConstants.BALL_RADIUS <= playerY + paddleHalfHeight()
                && state.ballY + MatchConstants.BALL_RADIUS > playerY
                && Math.abs(state.ballX - state.playerX) <= paddleHalfWidth() + MatchConstants.BALL_RADIUS) {
            state.ballY = playerY + paddleHalfHeight() + MatchConstants.BALL_RADIUS;
            reflectFromPaddle(state.playerX, true);
            frame.setPlayerPaddleImpact(true);
        } else if (state.ballY + MatchConstants.BALL_RADIUS >= opponentY - paddleHalfHeight()
                && state.ballY - MatchConstants.BALL_RADIUS < opponentY
                && Math.abs(state.ballX - state.opponentX) <= paddleHalfWidth() + MatchConstants.BALL_RADIUS) {
            state.ballY = opponentY - paddleHalfHeight() - MatchConstants.BALL_RADIUS;
            reflectFromPaddle(state.opponentX, false);
            frame.setOpponentPaddleImpact(true);
        }

        if (state.ballY > arenaHalfHeight()) {
            frame.setGoalScored(true);
            frame.setPlayerScored(true);
            handleScore(true);
        } else if (state.ballY < -arenaHalfHeight()) {
            frame.setGoalScored(true);
            frame.setPlayerScored(false);
            handleScore(false);
        }
    }

    private void reflectFromPaddle(float paddleX, boolean playerBounce) {
        float offset = (state.ballX - paddleX) / paddleHalfWidth();
        float targetVX = offset * 8f;
        state.ballVX = lerp(state.ballVX, targetVX, 0.55f);
        state.ballSpeed = Math.min(state.ballSpeed * 1.05f + 0.45f, MatchConstants.BALL_MAX_SPEED);
        state.ballVY = (playerBounce ? -1f : 1f) * state.ballSpeed;
        state.rallyCount++;
        state.bestRally = Math.max(state.bestRally, state.rallyCount);
    }

    private void handleScore(boolean playerScored) {
        state.rallyCount = 0;
        if (playerScored) {
            state.playerScore++;
        } else {
            state.opponentScore++;
        }
        if (state.playerScore >= MatchConstants.WIN_SCORE || state.opponentScore >= MatchConstants.WIN_SCORE) {
            state.matchOver = true;
            state.playerWon = state.playerScore > state.opponentScore;
            state.waitingForServe = false;
            state.ballVX = 0f;
            state.ballVY = 0f;
            return;
        }
        prepareServe(!state.playerServeTurn, false);
    }

    private static float smoothDamp(float current, float target, float deltaTime, float smooth) {
        float lambda = smooth * deltaTime;
        return lerp(current, target, 1f - (float) Math.exp(-lambda));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * clamp(t, 0f, 1f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
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
}
