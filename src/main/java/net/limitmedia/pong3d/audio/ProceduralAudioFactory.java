package net.limitmedia.pong3d.audio;

import com.jme3.audio.AudioBuffer;
import com.jme3.audio.AudioNode;
import com.jme3.util.BufferUtils;

import java.nio.ByteBuffer;
import java.util.function.DoubleUnaryOperator;

/**
 * Generates lightweight procedural audio clips so the game can ship without binary assets.
 */
public final class ProceduralAudioFactory {

    private static final int SAMPLE_RATE = 44100;
    private static final float TAU = (float) (Math.PI * 2.0);

    private static final AudioBuffer BOUNCE_BUFFER = createBounceBuffer();
    private static final AudioBuffer GOAL_BUFFER = createGoalBuffer();
    private static final AudioBuffer MENU_BUFFER = createMenuBuffer();

    private ProceduralAudioFactory() {
    }

    public static AudioNode createBounceSound() {
        return configure(new AudioNode(BOUNCE_BUFFER, null), false, 0.5f);
    }

    public static AudioNode createGoalSound() {
        return configure(new AudioNode(GOAL_BUFFER, null), false, 0.6f);
    }

    public static AudioNode createMenuLoop() {
        AudioNode node = configure(new AudioNode(MENU_BUFFER, null), true, 0.28f);
        node.setTimeOffset(0f);
        return node;
    }

    private static AudioNode configure(AudioNode node, boolean looping, float volume) {
        node.setLooping(looping);
        node.setPositional(false);
        node.setVolume(volume);
        return node;
    }

    private static AudioBuffer createBounceBuffer() {
        final float duration = 0.24f;
        DoubleUnaryOperator generator = t -> {
            double normalized = Math.min(1.0, t / duration);
            double envelope = Math.pow(1.0 - normalized, 2.4);
            double freq = 680.0 - normalized * 420.0;
            double angle = TAU * freq * t;
            double body = Math.sin(angle) + 0.35 * Math.sin(angle * 2.0);
            double click = 0.22 * Math.sin(TAU * 1200.0 * t) * Math.exp(-12.0 * normalized);
            return envelope * (body * 0.7 + click);
        };
        return createBuffer(duration, generator);
    }

    private static AudioBuffer createGoalBuffer() {
        final float duration = 0.72f;
        DoubleUnaryOperator generator = t -> {
            double normalized = Math.min(1.0, t / duration);
            double sweep = 320.0 + normalized * 860.0;
            double angle = TAU * sweep * t;
            double harmony = Math.sin(angle) + 0.4 * Math.sin(TAU * (sweep * 1.5) * t);
            double shimmer = 0.18 * Math.sin(TAU * 40.0 * t);
            double envelope = Math.pow(1.0 - normalized, 2.0);
            double attack = Math.min(1.0, t * 9.0);
            return (harmony * 0.6 + shimmer) * envelope * attack;
        };
        return createBuffer(duration, generator);
    }

    private static AudioBuffer createMenuBuffer() {
        final float duration = 2.4f;
        DoubleUnaryOperator generator = t -> {
            double normalized = Math.min(1.0, t / duration);
            double baseFreq = 110.0;
            double leadFreq = 440.0;
            double counterFreq = 660.0;
            double pad = squareWave(baseFreq, t) * 0.35 + squareWave(baseFreq * 2.0, t) * 0.18;
            double lead = triangleWave(leadFreq, t) * 0.28 + triangleWave(leadFreq * 0.5, t) * 0.14;
            double counter = triangleWave(counterFreq, t) * 0.12;
            double wobble = Math.sin(TAU * 0.5 * t) * 0.04;
            double body = pad + lead + counter + wobble;
            double fade = Math.min(Math.min(normalized / 0.12, 1.0), (duration - t) / 0.12);
            return body * fade;
        };
        return createBuffer(duration, generator);
    }

    private static double squareWave(double frequency, double t) {
        return Math.signum(Math.sin(TAU * frequency * t));
    }

    private static double triangleWave(double frequency, double t) {
        double period = 1.0 / frequency;
        double local = t % period;
        double normalized = local / period;
        return 4.0 * Math.abs(normalized - 0.5) - 1.0;
    }

    private static AudioBuffer createBuffer(float duration, DoubleUnaryOperator generator) {
        int samples = Math.max(1, (int) Math.ceil(duration * SAMPLE_RATE));
        ByteBuffer data = BufferUtils.createByteBuffer(samples * 2);
        for (int i = 0; i < samples; i++) {
            double t = i / (double) SAMPLE_RATE;
            double sample = generator.applyAsDouble(t);
            sample = Math.max(-1.0, Math.min(1.0, sample));
            short pcm = (short) Math.round(sample * Short.MAX_VALUE);
            data.putShort(pcm);
        }
        data.flip();
        AudioBuffer buffer = new AudioBuffer();
        buffer.setupFormat(1, 16, SAMPLE_RATE);
        buffer.updateData(data);
        return buffer;
    }
}
