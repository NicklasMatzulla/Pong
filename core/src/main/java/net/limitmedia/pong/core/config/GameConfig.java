package net.limitmedia.pong.core.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Central configuration data class that mirrors user editable YAML/JSON structures.
 * The config is immutable and can be hot reloaded while the game runs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameConfig(
        @JsonProperty("video") VideoSettings video,
        @JsonProperty("audio") AudioSettings audio,
        @JsonProperty("gameplay") GameplaySettings gameplay,
        @JsonProperty("accessibility") AccessibilitySettings accessibility,
        @JsonProperty("locale") Locale locale
) {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    static {
        YAML_MAPPER.findAndRegisterModules();
    }

    public GameConfig {
        Objects.requireNonNull(video, "video");
        Objects.requireNonNull(audio, "audio");
        Objects.requireNonNull(gameplay, "gameplay");
        Objects.requireNonNull(accessibility, "accessibility");
        locale = locale == null ? Locale.getDefault() : locale;
    }

    public static GameConfig load(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return YAML_MAPPER.readValue(in, GameConfig.class);
        }
    }

    public static GameConfig loadDefault() {
        try (InputStream in = GameConfig.class.getResourceAsStream("/config/default.yml")) {
            if (in == null) {
                throw new IllegalStateException("Missing default configuration");
            }
            return YAML_MAPPER.readValue(in, GameConfig.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read default configuration", ex);
        }
    }

    public GameConfig withLocale(Locale newLocale) {
        return new GameConfig(video, audio, gameplay, accessibility, newLocale);
    }

    public record VideoSettings(
            @JsonProperty("width") int width,
            @JsonProperty("height") int height,
            @JsonProperty("fullscreen") boolean fullscreen,
            @JsonProperty("vsync") boolean vsync,
            @JsonProperty("fpsCap") int fpsCap,
            @JsonProperty("postProcessing") boolean postProcessing,
            @JsonProperty("shadowQuality") ShadowQuality shadowQuality,
            @JsonProperty("antialiasing") Antialiasing antialiasing
    ) {
        public VideoSettings {
            width = Math.max(width, 640);
            height = Math.max(height, 480);
            fpsCap = Math.max(fpsCap, 30);
            shadowQuality = shadowQuality == null ? ShadowQuality.MEDIUM : shadowQuality;
            antialiasing = antialiasing == null ? Antialiasing.FXAA : antialiasing;
        }

        public enum ShadowQuality { LOW, MEDIUM, HIGH }

        public enum Antialiasing { FXAA, MSAA4X, MSAA8X }
    }

    public record AudioSettings(
            @JsonProperty("master") float master,
            @JsonProperty("music") float music,
            @JsonProperty("sfx") float sfx,
            @JsonProperty("ui") float ui,
            @JsonProperty("ducking") float ducking
    ) {
        public AudioSettings {
            master = clamp(master);
            music = clamp(music);
            sfx = clamp(sfx);
            ui = clamp(ui);
            ducking = clamp(ducking);
        }

        private static float clamp(float value) {
            return Math.min(1f, Math.max(0f, value));
        }
    }

    public record GameplaySettings(
            @JsonProperty("botDifficulty") BotDifficulty botDifficulty,
            @JsonProperty("enableSpin") boolean enableSpin,
            @JsonProperty("powerUps") boolean powerUps,
            @JsonProperty("maxScore") int maxScore,
            @JsonProperty("tickRate") int tickRate,
            @JsonProperty("arenaProfile") ArenaProfileType arenaProfile,
            @JsonProperty("cameraStyle") CameraStyle cameraStyle,
            @JsonProperty("aiErrorMargin") float aiErrorMargin,
            @JsonProperty("ballTrail") boolean ballTrail,
            @JsonProperty("physics") PhysicsSettings physics
    ) {
        public GameplaySettings {
            botDifficulty = botDifficulty == null ? BotDifficulty.MEDIUM : botDifficulty;
            maxScore = Math.max(maxScore, 3);
            tickRate = Math.max(30, Math.min(240, tickRate));
            arenaProfile = arenaProfile == null ? ArenaProfileType.CLASSIC : arenaProfile;
            cameraStyle = cameraStyle == null ? CameraStyle.TOP_DOWN : cameraStyle;
            physics = physics == null ? PhysicsSettings.defaults() : physics;
            aiErrorMargin = Math.max(0f, Math.min(0.5f, aiErrorMargin));
        }

        public enum BotDifficulty { EASY, MEDIUM, HARD }

        public enum ArenaProfileType { CLASSIC, EXTENDED, VERTICAL }

        public enum CameraStyle { TOP_DOWN, ANGLED, COURTSIDE }

        public record PhysicsSettings(
                @JsonProperty("wallRestitution") float wallRestitution,
                @JsonProperty("paddleRestitution") float paddleRestitution,
                @JsonProperty("spinInfluence") float spinInfluence,
                @JsonProperty("linearDamping") float linearDamping,
                @JsonProperty("spinDamping") float spinDamping,
                @JsonProperty("minSpeed") float minSpeed,
                @JsonProperty("maxSpeed") float maxSpeed,
                @JsonProperty("paddleControl") float paddleControl
        ) {
            public PhysicsSettings {
                wallRestitution = clamp(wallRestitution, 0.5f, 1.1f);
                paddleRestitution = clamp(paddleRestitution, 0.5f, 1.1f);
                spinInfluence = Math.max(0f, spinInfluence);
                linearDamping = clamp(linearDamping, 0f, 1f);
                spinDamping = clamp(spinDamping, 0f, 1f);
                minSpeed = Math.max(2f, minSpeed);
                maxSpeed = Math.max(minSpeed, maxSpeed);
                paddleControl = clamp(paddleControl, 0f, 2f);
            }

            public static PhysicsSettings defaults() {
                return new PhysicsSettings(0.92f, 0.96f, 1.8f, 0.08f, 0.65f, 6.5f, 42f, 0.55f);
            }

            private static float clamp(float value, float min, float max) {
                return Math.max(min, Math.min(max, value));
            }
        }
    }

    public record AccessibilitySettings(
            @JsonProperty("colorBlindMode") ColorBlindMode colorBlindMode,
            @JsonProperty("uiScale") float uiScale,
            @JsonProperty("screenShake") float screenShake
    ) {
        public AccessibilitySettings {
            colorBlindMode = colorBlindMode == null ? ColorBlindMode.OFF : colorBlindMode;
            uiScale = Math.max(0.75f, Math.min(2.0f, uiScale));
            screenShake = Math.max(0f, Math.min(1f, screenShake));
        }

        public enum ColorBlindMode { OFF, DEUTERANOPIA, PROTANOPIA, TRITANOPIA }
    }
}
