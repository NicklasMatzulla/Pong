package net.limitmedia.pong.core.audio;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioMixerTest {
    @Test
    void resolveGainHonoursMasterAndDuck() {
        AudioMixer mixer = new AudioMixer();
        mixer.setVolume(AudioMixer.Bus.MASTER, 0.5f);
        mixer.setVolume(AudioMixer.Bus.MUSIC, 0.8f);
        mixer.setDuck(AudioMixer.Bus.MUSIC, 0.25f);

        assertEquals(0.5f * 0.8f * 0.25f, mixer.resolveGain(AudioMixer.Bus.MUSIC), 1e-6f);
    }

    @Test
    void duckHandleRestoresPreviousValue() {
        AudioMixer mixer = new AudioMixer();
        mixer.setDuck(AudioMixer.Bus.SFX, 0.4f);

        try (AudioMixer.DuckHandle ignored = mixer.pushDuck(AudioMixer.Bus.SFX, 0.1f)) {
            assertEquals(0.1f, mixer.getDuck(AudioMixer.Bus.SFX));
        }

        assertEquals(0.4f, mixer.getDuck(AudioMixer.Bus.SFX));
    }
}
