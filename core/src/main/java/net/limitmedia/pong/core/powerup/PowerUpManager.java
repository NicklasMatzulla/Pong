package net.limitmedia.pong.core.powerup;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.limitmedia.pong.core.physics.ArenaDimensions;

public final class PowerUpManager {
    private final Random random = new Random();
    private final Map<PowerUpType, Duration> durations = new EnumMap<>(PowerUpType.class);
    private final List<PowerUp> active = new ArrayList<>();
    private Duration spawnInterval = Duration.ofSeconds(20);
    private Instant lastSpawn = Instant.EPOCH;

    public PowerUpManager() {
        durations.put(PowerUpType.SPEED_BOOST, Duration.ofSeconds(6));
        durations.put(PowerUpType.PADDLE_GROW, Duration.ofSeconds(10));
        durations.put(PowerUpType.PADDLE_SHRINK, Duration.ofSeconds(10));
        durations.put(PowerUpType.MULTI_BALL, Duration.ofSeconds(5));
        durations.put(PowerUpType.SLOW_FIELD, Duration.ofSeconds(7));
    }

    public void setSpawnInterval(Duration interval) {
        spawnInterval = interval;
    }

    public List<PowerUp> activePowerUps() {
        return List.copyOf(active);
    }

    public PowerUp maybeSpawn(ArenaDimensions arena, Instant now) {
        if (Duration.between(lastSpawn, now).compareTo(spawnInterval) < 0) {
            return null;
        }
        lastSpawn = now;
        PowerUpType type = PowerUpType.values()[random.nextInt(PowerUpType.values().length)];
        float x = FastMath.nextRandomFloat() * arena.width() - arena.width() / 2f;
        float y = FastMath.nextRandomFloat() * arena.height() - arena.height() / 2f;
        Vector3f position = new Vector3f(x, y, 0f);
        PowerUp powerUp = new PowerUp(type, position, now);
        active.add(powerUp);
        return powerUp;
    }

    public boolean consume(PowerUp powerUp) {
        return active.remove(powerUp);
    }

    public void purgeExpired(Instant now) {
        active.removeIf(pu -> Duration.between(pu.spawnedAt(), now).compareTo(durations.getOrDefault(pu.type(), Duration.ZERO)) > 0);
    }
}
