package net.limitmedia.pong.core.physics;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.util.function.Consumer;
import net.limitmedia.pong.core.gameplay.PhysicsTuning;

/**
 * Deterministic gameplay physics with spin, acceleration and restitution controls.
 */
public final class PhysicsEngine {
    private final ArenaDimensions arena;
    private final PhysicsTuning tuning;
    private final Consumer<CollisionEvent> collisionListener;

    public PhysicsEngine(ArenaDimensions arena,
                         float wallRestitution,
                         float paddleRestitution,
                         float spinInfluence,
                         Consumer<CollisionEvent> collisionListener) {
        this(arena, new PhysicsTuning(wallRestitution, paddleRestitution, spinInfluence, 0.08f, 0.65f, 6.5f, 42f, 0.55f),
                collisionListener);
    }

    public PhysicsEngine(ArenaDimensions arena,
                         PhysicsTuning tuning,
                         Consumer<CollisionEvent> collisionListener) {
        this.arena = arena;
        this.tuning = tuning;
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
        velocity.multLocal(FastMath.clamp(1f - tpf * tuning.linearDamping(), 0.5f, 1f));
        ball.position().addLocal(velocity.x * tpf, velocity.y * tpf, velocity.z * tpf);
        if (ball.spin().lengthSquared() > FastMath.ZERO_TOLERANCE && tuning.spinInfluence() > 0f) {
            Vector3f curve = ball.spin().mult(tpf * tuning.spinInfluence(), new Vector3f());
            ball.velocity().addLocal(curve);
            ball.spin().multLocal(FastMath.clamp(1f - tpf * tuning.spinDamping(), 0f, 1f));
        }
        clampSpeed(ball.velocity());
    }

    private void handleWallBounce(BallState ball, float tpf) {
        Vector3f position = ball.position();
        Vector3f velocity = ball.velocity();
        float halfWidth = arena.width() * 0.5f;
        float halfHeight = arena.height() * 0.5f;

        if (Math.abs(position.x) > halfWidth) {
            float sign = Math.signum(position.x);
            position.x = sign * halfWidth;
            velocity.x = -velocity.x * tuning.wallRestitution();
            emitCollision(CollisionEvent.Type.WALL, position.clone(), velocity.length(), tpf);
        }
        if (Math.abs(position.y) > halfHeight) {
            float sign = Math.signum(position.y);
            position.y = sign * halfHeight;
            velocity.y = -velocity.y * tuning.wallRestitution();
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
                float newZ = -velocity.z * tuning.paddleRestitution() + paddleVelocity * 0.35f;
                float spin = FastMath.clamp((position.x - paddlePos.x) / paddle.halfWidth(), -1f, 1f);
                float lift = FastMath.clamp((position.y - paddlePos.y) / paddle.halfHeight(), -1f, 1f);
                float impartX = paddle.velocity().x * tuning.paddleControl();
                float impartY = paddle.velocity().y * tuning.paddleControl();
                velocity.set(velocity.x + spin * 4f + impartX,
                        velocity.y + lift * 3f + impartY,
                        newZ);
                if (tuning.spinInfluence() > 0f) {
                    ball.spin().set(lift * 2f + impartX * 0.25f, spin * 1.5f + impartY * 0.2f, 0f);
                } else {
                    ball.spin().set(0, 0, 0);
                }
                position.z = paddleZ + (isLeft ? 0.02f : -0.02f);
                emitCollision(CollisionEvent.Type.PADDLE, position.clone(), velocity.length(), FastMath.abs(spin));
                clampSpeed(velocity);
            }
        }
    }

    private void emitCollision(CollisionEvent.Type type, Vector3f position, float intensity, float detail) {
        if (collisionListener != null) {
            collisionListener.accept(new CollisionEvent(type, position, intensity, detail));
        }
    }

    private void clampSpeed(Vector3f velocity) {
        float speed = velocity.length();
        if (speed < tuning.minSpeed()) {
            velocity.normalizeLocal().multLocal(tuning.minSpeed());
        } else if (speed > tuning.maxSpeed()) {
            velocity.normalizeLocal().multLocal(tuning.maxSpeed());
        }
    }

    public record CollisionEvent(Type type, Vector3f position, float intensity, float detail) {
        public enum Type { WALL, PADDLE }
    }
}
