package net.limitmedia.pong.core.physics;

import static org.junit.jupiter.api.Assertions.*;

import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;
import net.limitmedia.pong.core.physics.PhysicsEngine.CollisionEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhysicsEngineTest {
    private BallState ball;
    private PaddleState left;
    private PaddleState right;
    private List<CollisionEvent> collisions;
    private PhysicsEngine engine;

    @BeforeEach
    void setUp() {
        ball = new BallState().setPosition(new Vector3f()).setVelocity(new Vector3f(0, 0, 8));
        left = new PaddleState();
        right = new PaddleState();
        right.position().set(0, 0, 0);
        collisions = new ArrayList<>();
        engine = new PhysicsEngine(ArenaDimensions.COMPETITIVE, 0.9f, 0.95f, 2.2f, collisions::add);
    }

    @Test
    void bouncesOffRightPaddle() {
        ball.position().set(0, 0, 17.5f);
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(ball.velocity().z < 0);
        assertFalse(collisions.isEmpty());
    }

    @Test
    void appliesSpinFromHorizontalOffset() {
        ball.position().set(0.8f, 0f, 17.5f);
        right.position().set(0, 0, 0);
        engine.simulate(ball, left, right, 0.016f);
        assertNotEquals(0f, ball.velocity().x);
    }

    @Test
    void appliesLiftFromVerticalOffset() {
        ball.position().set(0f, 1.5f, 17.5f);
        engine.simulate(ball, left, right, 0.016f);
        assertNotEquals(0f, ball.velocity().y);
    }

    @Test
    void bouncesOffWall() {
        ball.position().set(ArenaDimensions.COMPETITIVE.width(), 0, 0);
        ball.velocity().set(6, 0, 0);
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(ball.velocity().x < 0);
    }

    @Test
    void dampensVelocitySlightly() {
        float prev = ball.velocity().length();
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(ball.velocity().length() <= prev);
    }

    @Test
    void triggersCollisionEventForWall() {
        ball.position().set(ArenaDimensions.COMPETITIVE.width(), 0, 0);
        ball.velocity().set(6, 0, 0);
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(collisions.stream().anyMatch(c -> c.type() == CollisionEvent.Type.WALL));
    }

    @Test
    void triggersCollisionEventForPaddle() {
        ball.position().set(0, 0, 17.5f);
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(collisions.stream().anyMatch(c -> c.type() == CollisionEvent.Type.PADDLE));
    }

    @Test
    void preventsPaddlePenetration() {
        ball.position().set(0, 0, 17.5f);
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(Math.abs(ball.position().z) < ArenaDimensions.COMPETITIVE.depth() / 2f);
    }

    @Test
    void spinInfluenceGraduallyZeroesOut() {
        ball.spin().set(3, 0, 0);
        float before = ball.velocity().x;
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(ball.velocity().x > before);
    }

    @Test
    void paddleVelocityImpactsBounce() {
        right.velocity().z = 3f;
        ball.position().set(0, 0, 17.5f);
        engine.simulate(ball, left, right, 0.016f);
        assertTrue(ball.velocity().z > -7f);
    }
}
