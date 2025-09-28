package net.limitmedia.pong.core.gameplay;

import net.limitmedia.pong.core.config.GameConfig;
import net.limitmedia.pong.core.physics.ArenaDimensions;

/**
 * Predefined arena profiles that bundle geometry and physics tuning presets.
 */
public enum ArenaProfile {
    CLASSIC(ArenaDimensions.COMPETITIVE,
            new PhysicsTuning(0.92f, 0.96f, 1.8f, 0.08f, 0.65f, 6.5f, 42f, 0.55f)),
    EXTENDED(ArenaDimensions.COMPETITIVE.lengthen(6f).widen(2f),
            new PhysicsTuning(0.9f, 0.94f, 1.6f, 0.075f, 0.6f, 6.5f, 40f, 0.5f)),
    VERTICAL(new ArenaDimensions(16f, 11f, 40f),
            new PhysicsTuning(0.94f, 0.97f, 2.05f, 0.07f, 0.55f, 7.5f, 44f, 0.6f));

    private final ArenaDimensions dimensions;
    private final PhysicsTuning tuning;

    ArenaProfile(ArenaDimensions dimensions, PhysicsTuning tuning) {
        this.dimensions = dimensions;
        this.tuning = tuning;
    }

    public ArenaDimensions dimensions() {
        return dimensions;
    }

    public PhysicsTuning tuning() {
        return tuning;
    }

    public static ArenaProfile from(GameConfig.GameplaySettings.ArenaProfileType type) {
        return switch (type) {
            case EXTENDED -> EXTENDED;
            case VERTICAL -> VERTICAL;
            default -> CLASSIC;
        };
    }
}
