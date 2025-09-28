package net.limitmedia.pong.core.audio;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mixes logical audio buses and exposes convenience helpers for UI bindings.
 */
public final class AudioMixer {
    public enum Bus { MASTER, MUSIC, SFX, UI }

    private final Map<Bus, Float> volumes = new EnumMap<>(Bus.class);
    private final Map<Bus, Float> ducking = new EnumMap<>(Bus.class);

    public AudioMixer() {
        for (Bus bus : Bus.values()) {
            volumes.put(bus, 1f);
            ducking.put(bus, 1f);
        }
    }

    public void setVolume(Bus bus, float value) {
        volumes.put(bus, clamp(value));
    }

    public float getVolume(Bus bus) {
        return volumes.getOrDefault(bus, 1f);
    }

    public float resolveGain(Bus bus) {
        return getVolume(Bus.MASTER) * getVolume(bus) * ducking.getOrDefault(bus, 1f);
    }

    public void setDuck(Bus bus, float multiplier) {
        ducking.put(bus, clamp(multiplier));
    }

    public float getDuck(Bus bus) {
        return ducking.getOrDefault(bus, 1f);
    }

    public DuckHandle pushDuck(Bus bus, float multiplier) {
        float previous = getDuck(bus);
        setDuck(bus, multiplier);
        return () -> setDuck(bus, previous);
    }

    public interface DuckHandle extends AutoCloseable {
        @Override
        void close();
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
