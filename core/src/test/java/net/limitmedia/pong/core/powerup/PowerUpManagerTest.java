package net.limitmedia.pong.core.powerup;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PowerUpManagerTest {
    private PowerUpManager manager;
    private Instant now;

    @BeforeEach
    void setup() {
        manager = new PowerUpManager();
        manager.setSpawnInterval(Duration.ZERO);
        now = Instant.parse("2024-01-01T00:00:00Z");
    }

    @Test
    void spawnsPowerUpWhenIntervalReached() {
        PowerUp powerUp = manager.maybeSpawn(ArenaDimensions.COMPETITIVE, now);
        assertNotNull(powerUp);
    }

    @Test
    void doesNotSpawnBeforeInterval() {
        manager.setSpawnInterval(Duration.ofSeconds(10));
        manager.maybeSpawn(ArenaDimensions.COMPETITIVE, now);
        assertNull(manager.maybeSpawn(ArenaDimensions.COMPETITIVE, now.plusSeconds(1)));
    }

    @Test
    void tracksActivePowerUps() {
        PowerUp pu = manager.maybeSpawn(ArenaDimensions.COMPETITIVE, now);
        assertTrue(manager.activePowerUps().contains(pu));
    }

    @Test
    void removesConsumedPowerUps() {
        PowerUp pu = manager.maybeSpawn(ArenaDimensions.COMPETITIVE, now);
        assertTrue(manager.consume(pu));
        assertFalse(manager.activePowerUps().contains(pu));
    }

    @Test
    void purgesExpiredPowerUps() {
        PowerUp pu = manager.maybeSpawn(ArenaDimensions.COMPETITIVE, now);
        manager.purgeExpired(now.plus(Duration.ofMinutes(1)));
        assertFalse(manager.activePowerUps().contains(pu));
    }

    @Test
    void activeListIsImmutable() {
        List<PowerUp> list = manager.activePowerUps();
        assertThrows(UnsupportedOperationException.class, () -> list.add(null));
    }
}
