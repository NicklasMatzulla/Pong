package net.limitmedia.pong3d.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;

import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class AmbientAudioEngine implements AutoCloseable {
    private static final int SAMPLE_RATE = 44_100;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private enum LoopType {
        NONE,
        MENU,
        ARENA
    }

    private final List<Integer> transientSources = new ArrayList<>();
    private final Random random = new Random();

    private long device;
    private long context;

    private int menuLoopBuffer;
    private int arenaLoopBuffer;
    private int ambientSource;
    private LoopType currentLoop = LoopType.NONE;

    private int bounceBuffer;
    private int goalBuffer;
    private int countdownTickBuffer;
    private int countdownGoBuffer;

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
        playMenuLoop();
    }

    public void playMenuLoop() {
        playLoop(LoopType.MENU);
    }

    public void playArenaLoop() {
        playLoop(LoopType.ARENA);
    }

    public void stopAmbientLoop() {
        if (currentLoop == LoopType.NONE) {
            return;
        }
        AL10.alSourceStop(ambientSource);
        AL10.alDeleteSources(ambientSource);
        ambientSource = 0;
        currentLoop = LoopType.NONE;
    }

    public void playBounce() {
        ensureEffectBuffers();
        playOneShot(bounceBuffer, 0.45f + random.nextFloat() * 0.15f, 0.95f + random.nextFloat() * 0.1f);
    }

    public void playGoal(boolean playerScored) {
        ensureEffectBuffers();
        float pitch = playerScored ? 1.08f : 0.94f;
        playOneShot(goalBuffer, 0.55f, pitch);
    }

    public void playCountdownTick() {
        ensureEffectBuffers();
        playOneShot(countdownTickBuffer, 0.42f, 1f);
    }

    public void playCountdownGo() {
        ensureEffectBuffers();
        playOneShot(countdownGoBuffer, 0.55f, 1.1f);
    }

    public void update() {
        cleanupFinishedSources(false);
    }

    @Override
    public void close() {
        stopAmbientLoop();
        cleanupFinishedSources(true);
        deleteBuffer(menuLoopBuffer);
        deleteBuffer(arenaLoopBuffer);
        deleteBuffer(bounceBuffer);
        deleteBuffer(goalBuffer);
        deleteBuffer(countdownTickBuffer);
        deleteBuffer(countdownGoBuffer);
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

    private void playLoop(LoopType loopType) {
        if (currentLoop == loopType) {
            return;
        }
        stopAmbientLoop();

        int buffer = ensureLoopBuffer(loopType);
        if (buffer == 0) {
            return;
        }

        ambientSource = AL10.alGenSources();
        AL10.alSourcei(ambientSource, AL10.AL_BUFFER, buffer);
        AL10.alSourcei(ambientSource, AL10.AL_LOOPING, AL10.AL_TRUE);
        AL10.alSourcef(ambientSource, AL10.AL_GAIN, loopType == LoopType.MENU ? 0.34f : 0.4f);
        AL10.alSourcef(ambientSource, AL10.AL_ROLLOFF_FACTOR, 0f);
        AL10.alSourcePlay(ambientSource);
        currentLoop = loopType;
    }

    private int ensureLoopBuffer(LoopType loopType) {
        if (loopType == LoopType.NONE) {
            return 0;
        }
        if (loopType == LoopType.MENU && menuLoopBuffer == 0) {
            menuLoopBuffer = createBuffer(generateLoop(loopType, 18f));
        } else if (loopType == LoopType.ARENA && arenaLoopBuffer == 0) {
            arenaLoopBuffer = createBuffer(generateLoop(loopType, 14f));
        }
        return loopType == LoopType.MENU ? menuLoopBuffer : arenaLoopBuffer;
    }

    private void ensureEffectBuffers() {
        if (bounceBuffer == 0) {
            bounceBuffer = createBuffer(generateBounceSample());
        }
        if (goalBuffer == 0) {
            goalBuffer = createBuffer(generateGoalSample());
        }
        if (countdownTickBuffer == 0) {
            countdownTickBuffer = createBuffer(generateCountdownSample(false));
        }
        if (countdownGoBuffer == 0) {
            countdownGoBuffer = createBuffer(generateCountdownSample(true));
        }
    }

    private void playOneShot(int buffer, float gain, float pitch) {
        int source = AL10.alGenSources();
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcef(source, AL10.AL_GAIN, gain);
        AL10.alSourcef(source, AL10.AL_PITCH, pitch);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
        AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
        AL10.alSourcePlay(source);
        transientSources.add(source);
    }

    private void cleanupFinishedSources(boolean force) {
        Iterator<Integer> iterator = transientSources.iterator();
        while (iterator.hasNext()) {
            int source = iterator.next();
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (force || state != AL10.AL_PLAYING) {
                AL10.alSourceStop(source);
                AL10.alDeleteSources(source);
                iterator.remove();
            }
        }
    }

    private int createBuffer(ShortBuffer data) {
        if (data == null) {
            return 0;
        }
        int buffer = AL10.alGenBuffers();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SAMPLE_RATE);
        return buffer;
    }

    private void deleteBuffer(int buffer) {
        if (buffer != 0) {
            AL10.alDeleteBuffers(buffer);
        }
    }

    private ShortBuffer generateLoop(LoopType loopType, float seconds) {
        int totalSamples = (int) (seconds * SAMPLE_RATE);
        ShortBuffer data = BufferUtils.createShortBuffer(totalSamples);
        for (int i = 0; i < totalSamples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float sample = loopType == LoopType.MENU ? menuSample(t) : arenaSample(t);
            data.put(toPcm(sample));
        }
        data.flip();
        return data;
    }

    private float menuSample(float time) {
        float pad = 0f;
        float[] padFrequencies = {174.0f, 196.0f, 261.63f};
        for (float f : padFrequencies) {
            pad += 0.16f * slowSine(time, f * 0.5f);
        }
        float[][] chords = {
                {261.63f, 329.63f, 392.00f},
                {293.66f, 369.99f, 440.00f},
                {246.94f, 329.63f, 415.30f},
                {261.63f, 349.23f, 523.25f}
        };
        float chordDuration = 4f;
        float chordEnvelope = 0.5f + 0.5f * (float) Math.sin(Math.PI * (time % chordDuration) / chordDuration);
        int chordIndex = (int) (time / chordDuration) % chords.length;
        float voices = 0f;
        for (float voiceFreq : chords[chordIndex]) {
            voices += 0.18f * smoothSine(time, voiceFreq);
        }
        float leadDuration = 0.8f;
        float[] lead = {523.25f, 587.33f, 659.25f, 698.46f, 783.99f, 659.25f, 587.33f, 523.25f};
        int leadIndex = (int) (time / leadDuration) % lead.length;
        float leadPhase = time % leadDuration;
        float leadEnv = (float) Math.sin(Math.PI * (leadPhase / leadDuration));
        float shimmer = 0.05f * smoothSine(time, 880f);
        float lowDrift = 0.03f * smoothSine(time, 55f);
        float sample = (pad + voices) * chordEnvelope + 0.22f * smoothSine(time, lead[leadIndex]) * leadEnv + shimmer + lowDrift;
        return clamp(sample * 0.55f);
    }

    private float arenaSample(float time) {
        float tempo = 1.0f;
        float beat = time * 1.5f * tempo;
        float kick = (float) Math.exp(-6f * (beat - (float) Math.floor(beat)));
        float bass = 0.18f * smoothSine(time, 110f) + 0.14f * smoothSine(time, 220f);
        float arpDuration = 0.5f;
        float[] arp = {329.63f, 392f, 440f, 523.25f};
        int arpIndex = (int) (time / arpDuration) % arp.length;
        float arpPhase = (time % arpDuration) / arpDuration;
        float arpEnv = (float) Math.pow(Math.sin(Math.PI * arpPhase), 1.8f);
        float airy = 0.08f * smoothSine(time, 784f) + 0.05f * smoothSine(time, 932f);
        float percussive = kick * 0.35f + 0.05f * smoothSine(time, 55f);
        float sample = bass + airy + 0.3f * smoothSine(time, arp[arpIndex]) * arpEnv + percussive;
        return clamp(sample * 0.6f);
    }

    private ShortBuffer generateBounceSample() {
        int samples = (int) (SAMPLE_RATE * 0.18f);
        ShortBuffer buffer = BufferUtils.createShortBuffer(samples);
        for (int i = 0; i < samples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float env = (float) Math.exp(-10f * t);
            float tone = smoothSine(t, 420f + random.nextFloat() * 40f);
            float click = (float) Math.exp(-30f * t) * smoothSine(t, 1600f);
            float sample = (tone * 0.7f + click * 0.3f) * env;
            buffer.put(toPcm(sample));
        }
        buffer.flip();
        return buffer;
    }

    private ShortBuffer generateGoalSample() {
        int samples = (int) (SAMPLE_RATE * 0.6f);
        ShortBuffer buffer = BufferUtils.createShortBuffer(samples);
        float[] sweep = {392f, 523.25f, 659.25f, 783.99f};
        for (int i = 0; i < samples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float env = (float) Math.exp(-2.8f * t);
            int idx = Math.min(sweep.length - 1, (int) (t / 0.12f));
            float tone = smoothSine(t, sweep[idx]);
            float sparkle = smoothSine(t, 1200f) * (float) Math.exp(-6f * t);
            float sample = tone * env * 0.7f + sparkle * 0.2f;
            buffer.put(toPcm(sample));
        }
        buffer.flip();
        return buffer;
    }

    private ShortBuffer generateCountdownSample(boolean go) {
        int samples = (int) (SAMPLE_RATE * (go ? 0.35f : 0.22f));
        ShortBuffer buffer = BufferUtils.createShortBuffer(samples);
        float frequency = go ? 660f : 520f;
        for (int i = 0; i < samples; i++) {
            float t = i / (float) SAMPLE_RATE;
            float env = (float) Math.exp(-(go ? 5f : 8f) * t);
            float sample = smoothSine(t, frequency) * env;
            if (!go) {
                sample += smoothSine(t, frequency * 0.5f) * env * 0.3f;
            } else {
                sample += smoothSine(t, frequency * 2f) * env * 0.25f;
            }
            buffer.put(toPcm(sample * 0.8f));
        }
        buffer.flip();
        return buffer;
    }

    private float smoothSine(float time, float frequency) {
        return (float) Math.sin(TWO_PI * frequency * time);
    }

    private float slowSine(float time, float frequency) {
        return (float) Math.sin(TWO_PI * frequency * time);
    }

    private short toPcm(float sample) {
        return (short) (clamp(sample) * Short.MAX_VALUE);
    }

    private float clamp(float value) {
        if (value > 1f) {
            return 1f;
        }
        if (value < -1f) {
            return -1f;
        }
        return value;
    }
}
