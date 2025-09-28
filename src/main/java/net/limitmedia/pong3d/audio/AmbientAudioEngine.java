package net.limitmedia.pong3d.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;

import java.nio.ShortBuffer;
public final class AmbientAudioEngine implements AutoCloseable {
    private static final int SAMPLE_RATE = 44_100;

    private long device;
    private long context;
    private int ambientBuffer;
    private int ambientSource;
    private boolean loopPlaying;

    public AmbientAudioEngine() {
        device = ALC10.alcOpenDevice((java.nio.ByteBuffer) null);
        if (device == 0L) {
            throw new IllegalStateException("Unable to open default audio device");
        }
        context = ALC10.alcCreateContext(device, (int[]) null);
        if (context == 0L) {
            ALC10.alcCloseDevice(device);
            throw new IllegalStateException("Unable to create OpenAL context");
        }
        ALC10.alcMakeContextCurrent(context);
        ALCCapabilities caps = ALC.createCapabilities(device);
        AL.createCapabilities(caps);
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new IllegalStateException("Failed to initialise OpenAL state: " + error);
        }
    }

    public void playAmbientLoop() {
        if (loopPlaying) {
            return;
        }
        if (ambientBuffer == 0) {
            ambientBuffer = AL10.alGenBuffers();
            ShortBuffer buffer = generateAmbientMelody(16f);
            AL10.alBufferData(ambientBuffer, AL10.AL_FORMAT_MONO16, buffer, SAMPLE_RATE);
        }
        ambientSource = AL10.alGenSources();
        AL10.alSourcei(ambientSource, AL10.AL_BUFFER, ambientBuffer);
        AL10.alSourcef(ambientSource, AL10.AL_GAIN, 0.38f);
        AL10.alSourcei(ambientSource, AL10.AL_LOOPING, AL10.AL_TRUE);
        AL10.alSourcePlay(ambientSource);
        loopPlaying = true;
    }

    public void stopAmbientLoop() {
        if (!loopPlaying) {
            return;
        }
        AL10.alSourceStop(ambientSource);
        AL10.alDeleteSources(ambientSource);
        ambientSource = 0;
        loopPlaying = false;
    }

    private ShortBuffer generateAmbientMelody(float seconds) {
        int totalSamples = (int) (seconds * SAMPLE_RATE);
        ShortBuffer data = BufferUtils.createShortBuffer(totalSamples);
        float[] padFrequencies = {196.0f, 261.63f, 329.63f};
        float[][] chordProgression = {
                {261.63f, 329.63f, 392.00f},
                {293.66f, 369.99f, 440.00f},
                {246.94f, 329.63f, 415.30f},
                {261.63f, 349.23f, 523.25f}
        };
        float[] leadNotes = {523.25f, 587.33f, 659.25f, 698.46f, 783.99f, 659.25f, 587.33f, 523.25f};
        float leadNoteDuration = 0.75f;
        float chordDuration = 4.0f;
        float shimmerFreq = 880.0f;

        for (int i = 0; i < totalSamples; i++) {
            float time = i / (float) SAMPLE_RATE;
            int chordIndex = (int) (time / chordDuration) % chordProgression.length;
            float chordPhaseTime = time % chordDuration;
            float chordEnvelope = 0.5f + 0.5f * (float) Math.sin(Math.PI * chordPhaseTime / chordDuration);

            float sampleValue = 0f;

            for (float padFreq : padFrequencies) {
                sampleValue += 0.12f * slowSine(time, padFreq * 0.5f);
            }

            for (float voiceFreq : chordProgression[chordIndex]) {
                sampleValue += 0.18f * smoothSine(time, voiceFreq);
            }
            sampleValue *= chordEnvelope;

            int leadIndex = (int) ((time / leadNoteDuration) % leadNotes.length);
            float leadPhaseTime = time % leadNoteDuration;
            float leadEnv = (float) Math.sin(Math.PI * (leadPhaseTime / leadNoteDuration));
            sampleValue += 0.22f * smoothSine(time, leadNotes[leadIndex]) * leadEnv;

            float shimmer = 0.05f * smoothSine(time, shimmerFreq);
            sampleValue += shimmer;

            float noise = 0.02f * (float) Math.sin(2.0 * Math.PI * 0.5f * time);
            sampleValue += noise;

            sampleValue = Math.max(-1f, Math.min(1f, sampleValue));
            short pcm = (short) (sampleValue * Short.MAX_VALUE);
            data.put(pcm);
        }
        data.flip();
        return data;
    }

    private float smoothSine(float time, float frequency) {
        return (float) Math.sin(2.0 * Math.PI * frequency * time);
    }

    private float slowSine(float time, float frequency) {
        return (float) Math.sin(2.0 * Math.PI * frequency * time);
    }

    @Override
    public void close() {
        stopAmbientLoop();
        if (ambientBuffer != 0) {
            AL10.alDeleteBuffers(ambientBuffer);
            ambientBuffer = 0;
        }
        if (context != 0L) {
            ALC10.alcMakeContextCurrent(0L);
            ALC10.alcDestroyContext(context);
            context = 0L;
        }
        if (device != 0L) {
            ALC10.alcCloseDevice(device);
            device = 0L;
        }
    }
}
