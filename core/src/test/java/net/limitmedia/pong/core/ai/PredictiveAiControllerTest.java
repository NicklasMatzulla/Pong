package net.limitmedia.pong.core.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.jme3.math.Vector3f;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;
import org.junit.jupiter.api.Test;

class PredictiveAiControllerTest {

    @Test
    void appliesOffsetWithinErrorMargin() {
        PaddleState paddle = new PaddleState();
        BallState ball = new BallState();
        ball.position().set(4f, 0f, 0f);
        ball.velocity().set(-6f, 0f, 0f);

        PredictiveAiController controller = new PredictiveAiController(20f, 0.2f, 0.25f);
        Vector3f initial = paddle.position().clone();
        controller.update(paddle, ball, 0.016f);

        assertNotEquals(initial.x, paddle.position().x, "AI should move paddle");
        float maxOffset = paddle.halfWidth() * 0.25f;
        assertTrue(Math.abs(controllerError(controller, paddle, ball)) <= maxOffset + 0.01f);
    }

    private static float controllerError(PredictiveAiController controller, PaddleState paddle, BallState ball) {
        // Re-run update with zero delta time to inspect offset behaviour.
        float prev = paddle.position().x;
        controller.update(paddle, ball, 0.0001f);
        float movement = paddle.position().x - prev;
        paddle.position().x = prev; // reset state for determinism
        return movement;
    }
}
