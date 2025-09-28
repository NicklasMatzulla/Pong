package net.limitmedia.pong.core.net;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import net.limitmedia.pong.core.match.MatchState;

/**
 * Transfer object used by the Netty pipeline. Encodes full state required for prediction.
 */
public record NetworkFrame(
        UUID matchId,
        long tick,
        Instant serverTime,
        float[] ballPosition,
        float[] ballVelocity,
        float[] leftPosition,
        float[] rightPosition,
        MatchState.Score score,
        float pingEstimateMs
) implements Serializable {
    public NetworkFrame {
        ballPosition = ballPosition.clone();
        ballVelocity = ballVelocity.clone();
        leftPosition = leftPosition.clone();
        rightPosition = rightPosition.clone();
    }
}
