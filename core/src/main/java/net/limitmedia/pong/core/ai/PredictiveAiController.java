package net.limitmedia.pong.core.ai;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.Random;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;

/**
 * Predictive AI that moves paddles by predicting intercept point using linear prediction.
 */
public final class PredictiveAiController {
    private final float maxSpeed;
    private final float anticipation;
    private final float errorMargin;
    private final Random random = new Random();
    private float wobbleTimer;
    private float aimOffset;

    public PredictiveAiController(float maxSpeed, float anticipation, float errorMargin) {
        this.maxSpeed = maxSpeed;
        this.anticipation = anticipation;
        this.errorMargin = FastMath.clamp(errorMargin, 0f, 0.5f);
    }

    public void update(PaddleState paddle, BallState ball, float tpf) {
        wobbleTimer += tpf;
        if (wobbleTimer >= 0.4f) {
            wobbleTimer = 0f;
            aimOffset = (random.nextFloat() * 2f - 1f) * errorMargin * paddle.halfWidth();
        }
        Vector3f future = ball.position().add(ball.velocity().mult(anticipation, new Vector3f()), new Vector3f());
        float targetX = future.x + aimOffset;
        float delta = targetX - paddle.position().x;
        float maxStep = maxSpeed * tpf;
        float movement = Math.max(-maxStep, Math.min(maxStep, delta));
        paddle.position().x += movement;
        paddle.velocity().x = movement / tpf;
    }
}
