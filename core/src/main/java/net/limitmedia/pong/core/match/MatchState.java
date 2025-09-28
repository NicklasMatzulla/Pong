package net.limitmedia.pong.core.match;

import com.jme3.math.Vector3f;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Objects;
import net.limitmedia.pong.core.physics.BallState;
import net.limitmedia.pong.core.physics.PaddleState;

/**
 * Authoritative simulation snapshot that can be shared between server and client.
 */
public final class MatchState {
    private final BallState ball;
    private final PaddleState leftPaddle;
    private final PaddleState rightPaddle;
    private final Score score;
    private final Duration elapsed;
    private final ArrayDeque<MatchEvent> events;

    public MatchState(BallState ball, PaddleState leftPaddle, PaddleState rightPaddle,
                      Score score, Duration elapsed, ArrayDeque<MatchEvent> events) {
        this.ball = Objects.requireNonNull(ball);
        this.leftPaddle = Objects.requireNonNull(leftPaddle);
        this.rightPaddle = Objects.requireNonNull(rightPaddle);
        this.score = Objects.requireNonNull(score);
        this.elapsed = Objects.requireNonNull(elapsed);
        this.events = Objects.requireNonNull(events);
    }

    public BallState ball() {
        return ball;
    }

    public PaddleState leftPaddle() {
        return leftPaddle;
    }

    public PaddleState rightPaddle() {
        return rightPaddle;
    }

    public Score score() {
        return score;
    }

    public Duration elapsed() {
        return elapsed;
    }

    public ArrayDeque<MatchEvent> events() {
        return events;
    }

    public static MatchState initial() {
        BallState ball = new BallState().setPosition(new Vector3f()).setVelocity(new Vector3f());
        PaddleState left = new PaddleState();
        PaddleState right = new PaddleState();
        return new MatchState(ball, left, right, new Score(0, 0), Duration.ZERO, new ArrayDeque<>());
    }

    public record Score(int left, int right) implements java.io.Serializable {
        public Score addLeft() {
            return new Score(left + 1, right);
        }

        public Score addRight() {
            return new Score(left, right + 1);
        }
    }

    public record MatchEvent(Type type, Instant timestamp, Vector3f position) {
        public enum Type { GOAL_LEFT, GOAL_RIGHT, POWER_UP_SPAWN, POWER_UP_CONSUMED }
    }
}
