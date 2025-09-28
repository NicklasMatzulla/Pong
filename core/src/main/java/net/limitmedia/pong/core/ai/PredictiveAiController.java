package net.limitmedia.pong.core.ai;

import com.jme3.math.Vector3f;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;

/**
 * Predictive AI that moves paddles by predicting intercept point using linear prediction.
 */
public final class PredictiveAiController {
    private final float maxSpeed;
    private final float anticipation;

    public PredictiveAiController(float maxSpeed, float anticipation) {
        this.maxSpeed = maxSpeed;
        this.anticipation = anticipation;
    }

    public void update(PaddleState paddle, BallState ball, float tpf) {
        Vector3f future = ball.position().add(ball.velocity().mult(anticipation, new Vector3f()), new Vector3f());
        float targetX = future.x;
        float delta = targetX - paddle.position().x;
        float maxStep = maxSpeed * tpf;
        float movement = Math.max(-maxStep, Math.min(maxStep, delta));
        paddle.position().x += movement;
        paddle.velocity().x = movement / tpf;
    }
}
