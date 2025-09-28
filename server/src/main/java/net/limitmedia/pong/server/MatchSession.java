package net.limitmedia.pong.server;

import com.jme3.math.Vector3f;
import java.time.Instant;
import java.util.UUID;
import net.limitmedia.pong.core.match.MatchState;
import net.limitmedia.pong.core.net.NetworkFrame;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;
import net.limitmedia.pong.core.physics.PhysicsEngine;

public final class MatchSession {
    private final UUID id = UUID.randomUUID();
    private final PhysicsEngine physics;
    private final BallState ball = new BallState();
    private final PaddleState left = new PaddleState();
    private final PaddleState right = new PaddleState();
    private MatchState.Score score = new MatchState.Score(0, 0);
    private long tick;

    public MatchSession(PhysicsEngine physics) {
        this.physics = physics;
        ball.setVelocity(new Vector3f(0, 0, 8f));
    }

    public void update(float tpf) {
        physics.simulate(ball, left, right, tpf);
        tick++;
    }

    public NetworkFrame toNetworkFrame(float pingEstimate) {
        return new NetworkFrame(id, tick, Instant.now(),
                vector(ball.position()), vector(ball.velocity()),
                vector(left.position()), vector(right.position()),
                score, pingEstimate);
    }

    public MatchState.Score score() {
        return score;
    }

    private static float[] vector(com.jme3.math.Vector3f v) {
        return new float[] { v.x, v.y, v.z };
    }
}
