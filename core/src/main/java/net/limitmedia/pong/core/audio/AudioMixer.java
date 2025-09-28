package net.limitmedia.pong.core.audio;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mixes logical audio buses and exposes convenience helpers for UI bindings.
 */
public final class AudioMixer {
    public enum Bus { MASTER, MUSIC, SFX, UI }

    private final Map<Bus, Float> volumes = new EnumMap<>(Bus.class);

    public AudioMixer() {
        for (Bus bus : Bus.values()) {
            volumes.put(bus, 1f);
        }
    }

    public void setVolume(Bus bus, float value) {
        volumes.put(bus, clamp(value));
    }

    public float getVolume(Bus bus) {
        return volumes.getOrDefault(bus, 1f);
    }

    public float resolveGain(Bus bus) {
        return getVolume(Bus.MASTER) * getVolume(bus);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
