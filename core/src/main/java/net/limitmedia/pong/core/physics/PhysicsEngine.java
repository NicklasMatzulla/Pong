package net.limitmedia.pong.core.physics;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.function.Consumer;

/**
 * Deterministic gameplay physics with spin, acceleration and restitution controls.
 */
public final class PhysicsEngine {
    private final ArenaDimensions arena;
    private final float wallRestitution;
    private final float paddleRestitution;
    private final float spinInfluence;
    private final Consumer<CollisionEvent> collisionListener;

    public PhysicsEngine(ArenaDimensions arena,
                         float wallRestitution,
                         float paddleRestitution,
                         float spinInfluence,
                         Consumer<CollisionEvent> collisionListener) {
        this.arena = arena;
        this.wallRestitution = wallRestitution;
        this.paddleRestitution = paddleRestitution;
        this.spinInfluence = spinInfluence;
        this.collisionListener = collisionListener;
    }

    public void simulate(BallState ball, PaddleState left, PaddleState right, float tpf) {
        integrate(ball, tpf);
        handleWallBounce(ball, tpf);
        handlePaddleBounce(ball, left, true);
        handlePaddleBounce(ball, right, false);
    }

    private void integrate(BallState ball, float tpf) {
        Vector3f velocity = ball.velocity();
        velocity.multLocal(FastMath.clamp(1f - tpf * 0.1f, 0.8f, 1f));
        ball.position().addLocal(velocity.x * tpf, velocity.y * tpf, velocity.z * tpf);
        if (ball.spin().lengthSquared() > FastMath.ZERO_TOLERANCE) {
            Vector3f curve = ball.spin().mult(tpf * spinInfluence);
            ball.velocity().addLocal(curve);
        }
    }

    private void handleWallBounce(BallState ball, float tpf) {
        Vector3f position = ball.position();
        Vector3f velocity = ball.velocity();
        float halfWidth = arena.width() * 0.5f;
        float halfHeight = arena.height() * 0.5f;

        if (Math.abs(position.x) > halfWidth) {
            float sign = Math.signum(position.x);
            position.x = sign * halfWidth;
            velocity.x = -velocity.x * wallRestitution;
            emitCollision(CollisionEvent.Type.WALL, position.clone(), velocity.length(), tpf);
        }
        if (Math.abs(position.y) > halfHeight) {
            float sign = Math.signum(position.y);
            position.y = sign * halfHeight;
            velocity.y = -velocity.y * wallRestitution;
            emitCollision(CollisionEvent.Type.WALL, position.clone(), velocity.length(), tpf);
        }
    }

    private void handlePaddleBounce(BallState ball, PaddleState paddle, boolean isLeft) {
        Vector3f position = ball.position();
        Vector3f velocity = ball.velocity();
        Vector3f paddlePos = paddle.position();

        float halfDepth = arena.depth() * 0.5f;
        float paddleZ = isLeft ? -halfDepth + paddle.halfWidth() : halfDepth - paddle.halfWidth();
        float distanceZ = position.z - paddleZ;

        if ((isLeft && distanceZ < 0f && velocity.z < 0f) || (!isLeft && distanceZ > 0f && velocity.z > 0f)) {
            float withinX = Math.abs(position.x - paddlePos.x) - paddle.halfWidth();
            float withinY = Math.abs(position.y - paddlePos.y) - paddle.halfHeight();
            if (withinX <= 0f && withinY <= 0f) {
                float paddleVelocity = paddle.velocity().z;
                float newZ = -velocity.z * paddleRestitution + paddleVelocity * 0.5f;
                float spin = FastMath.clamp((position.x - paddlePos.x) / paddle.halfWidth(), -1f, 1f);
                float lift = FastMath.clamp((position.y - paddlePos.y) / paddle.halfHeight(), -1f, 1f);
                velocity.set(velocity.x + spin * 4f, velocity.y + lift * 3f, newZ);
                ball.spin().set(lift * 2f, spin * 1.5f, 0f);
                position.z = paddleZ + (isLeft ? 0.02f : -0.02f);
                emitCollision(CollisionEvent.Type.PADDLE, position.clone(), velocity.length(), FastMath.abs(spin));
            }
        }
    }

    private void emitCollision(CollisionEvent.Type type, Vector3f position, float intensity, float detail) {
        if (collisionListener != null) {
            collisionListener.accept(new CollisionEvent(type, position, intensity, detail));
        }
    }

    public record CollisionEvent(Type type, Vector3f position, float intensity, float detail) {
        public enum Type { WALL, PADDLE }
    }
}
