package net.limitmedia.pong.client.audio;

import com.jme3.asset.AssetManager;
import com.jme3.audio.AudioNode;
import com.jme3.math.FastMath;
import com.jme3.scene.Node;
import java.io.Closeable;
import net.limitmedia.pong.core.audio.AudioMixer;
import net.limitmedia.pong.core.config.GameConfig;
import net.limitmedia.pong.core.gameplay.PhysicsTuning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles ambient loops and collision effects for the LWJGL client. The
 * implementation is defensive: missing audio assets no longer crash the game,
 * but get logged once.
 */
public final class ClientAudioController implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(ClientAudioController.class);

    private final AudioMixer mixer;
    private final GameConfig.AudioSettings settings;

    private AudioNode ambience;
    private AudioNode bounce;

    public ClientAudioController(AudioMixer mixer, GameConfig.AudioSettings settings) {
        this.mixer = mixer;
        this.settings = settings;
    }

    public void initialize(AssetManager assets, Node root) {
        ambience = loadLoop(assets, root, "Sound/Effects/Footsteps.ogg", AudioMixer.Bus.MUSIC, 0.25f);
        bounce = loadOneShot(assets, root, "Sound/Effects/Footsteps.ogg", AudioMixer.Bus.SFX);
    }

    public void update(float tpf) {
        float current = mixer.getDuck(AudioMixer.Bus.MUSIC);
        if (current >= 1f - 1e-3f) {
            return;
        }
        float restored = FastMath.clamp(current + tpf * 0.9f, 0f, 1f);
        if (restored != current) {
            mixer.setDuck(AudioMixer.Bus.MUSIC, restored);
            if (ambience != null) {
                ambience.setVolume(mixer.resolveGain(AudioMixer.Bus.MUSIC) * 0.25f);
            }
        }
    }

    public void playBounce(float intensity, PhysicsTuning tuning) {
        if (bounce == null) {
            return;
        }
        float loudness = FastMath.clamp(intensity / tuning.maxSpeed(), 0.25f, 1f);
        bounce.setVolume(mixer.resolveGain(AudioMixer.Bus.SFX) * loudness);
        bounce.playInstance();
        float duck = FastMath.clamp(1f - loudness * settings.ducking(), 0.35f, 1f);
        mixer.setDuck(AudioMixer.Bus.MUSIC, duck);
        if (ambience != null) {
            ambience.setVolume(mixer.resolveGain(AudioMixer.Bus.MUSIC) * 0.25f);
        }
    }

    private AudioNode loadLoop(AssetManager assets, Node root, String path, AudioMixer.Bus bus, float gain) {
        try {
            AudioNode node = new AudioNode(assets, path, false);
            node.setLooping(true);
            node.setPositional(false);
            node.setVolume(mixer.resolveGain(bus) * gain);
            root.attachChild(node);
            return node;
        } catch (Exception ex) {
            LOG.warn("Could not load loop {}: {}", path, ex.getMessage());
            return null;
        }
    }

    private AudioNode loadOneShot(AssetManager assets, Node root, String path, AudioMixer.Bus bus) {
        try {
            AudioNode node = new AudioNode(assets, path, false);
            node.setLooping(false);
            node.setPositional(false);
            node.setVolume(mixer.resolveGain(bus));
            root.attachChild(node);
            return node;
        } catch (Exception ex) {
            LOG.warn("Could not load sfx {}: {}", path, ex.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        if (ambience != null) {
            ambience.stop();
            ambience.removeFromParent();
        }
        if (bounce != null) {
            bounce.removeFromParent();
        }
    }
}
