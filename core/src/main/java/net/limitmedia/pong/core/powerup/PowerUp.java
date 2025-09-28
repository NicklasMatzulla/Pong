package net.limitmedia.pong.core.powerup;

import com.jme3.math.Vector3f;
import java.time.Instant;

public record PowerUp(PowerUpType type, Vector3f position, Instant spawnedAt) {
}
