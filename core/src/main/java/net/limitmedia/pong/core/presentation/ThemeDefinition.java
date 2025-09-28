package net.limitmedia.pong.core.presentation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Serializable description of a presentation theme. The record is intentionally
 * engine-agnostic so it can be shared between the server, client, and external
 * tooling.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThemeDefinition(
        @JsonProperty("name") String name,
        @JsonProperty("arena") Arena arena,
        @JsonProperty("paddles") Paddles paddles,
        @JsonProperty("ball") Ball ball,
        @JsonProperty("effects") Effects effects,
        @JsonProperty("ui") UiTheme ui,
        @JsonProperty("audio") AudioTheme audio
) {
    public ThemeDefinition {
        arena = arena == null ? Arena.defaults() : arena;
        paddles = paddles == null ? Paddles.defaults() : paddles;
        ball = ball == null ? Ball.defaults() : ball;
        effects = effects == null ? Effects.defaults() : effects;
        ui = ui == null ? UiTheme.defaults() : ui;
        audio = audio == null ? AudioTheme.defaults() : audio;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Arena(
            @JsonProperty("floorDiffuse") String floorDiffuse,
            @JsonProperty("floorAmbient") String floorAmbient,
            @JsonProperty("boundsColor") String boundsColor,
            @JsonProperty("backgroundColor") String backgroundColor,
            @JsonProperty("auroraColor") String auroraColor,
            @JsonProperty("auroraOpacity") float auroraOpacity,
            @JsonProperty("auroraSpeed") float auroraSpeed
    ) {
        public Arena {
            floorDiffuse = fallbackColor(floorDiffuse, "#1e2432");
            floorAmbient = fallbackColor(floorAmbient, "#10141d");
            boundsColor = fallbackColor(boundsColor, "#3a4b63aa");
            backgroundColor = fallbackColor(backgroundColor, "#050608");
            auroraColor = fallbackColor(auroraColor, "#33a7ff");
            auroraOpacity = clamp(auroraOpacity, 0f, 1f, 0.32f);
            auroraSpeed = clamp(auroraSpeed, 0f, 4f, 0.35f);
        }

        private static float clamp(float value, float min, float max, float fallback) {
            if (Float.isNaN(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static String fallbackColor(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public static Arena defaults() {
            return new Arena("#1e2432", "#10141d", "#3a4b63aa", "#050608", "#33a7ff", 0.32f, 0.35f);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Paddles(
            @JsonProperty("leftColor") String leftColor,
            @JsonProperty("rightColor") String rightColor,
            @JsonProperty("halfWidth") float halfWidth,
            @JsonProperty("halfHeight") float halfHeight,
            @JsonProperty("depth") float depth
    ) {
        public Paddles {
            leftColor = fallbackColor(leftColor, "#4ab7ff");
            rightColor = fallbackColor(rightColor, "#ff6ad5");
            halfWidth = clampSize(halfWidth, 0.5f, 4f, 1.2f);
            halfHeight = clampSize(halfHeight, 1f, 6f, 2.4f);
            depth = clampSize(depth, 0.1f, 1f, 0.25f);
        }

        private static float clampSize(float value, float min, float max, float fallback) {
            if (value <= 0f || Float.isNaN(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static String fallbackColor(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public static Paddles defaults() {
            return new Paddles("#4ab7ff", "#ff6ad5", 1.2f, 2.4f, 0.25f);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Ball(
            @JsonProperty("diffuse") String diffuse,
            @JsonProperty("ambient") String ambient,
            @JsonProperty("glow") String glow,
            @JsonProperty("trailColor") String trailColor,
            @JsonProperty("radius") float radius,
            @JsonProperty("trailWidth") float trailWidth
    ) {
        public Ball {
            diffuse = fallbackColor(diffuse, "#d8e2ff");
            ambient = fallbackColor(ambient, "#35405c");
            glow = fallbackColor(glow, "#4cd3ff");
            trailColor = fallbackColor(trailColor, "#4cd3ff66");
            radius = clampSize(radius, 0.2f, 2f, 0.6f);
            trailWidth = clampSize(trailWidth, 0.5f, 8f, 2.5f);
        }

        private static float clampSize(float value, float min, float max, float fallback) {
            if (value <= 0f || Float.isNaN(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static String fallbackColor(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public static Ball defaults() {
            return new Ball("#d8e2ff", "#35405c", "#4cd3ff", "#4cd3ff66", 0.6f, 2.5f);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Effects(
            @JsonProperty("trailFade") float trailFade,
            @JsonProperty("hueShiftSpeed") float hueShiftSpeed,
            @JsonProperty("shakeMultiplier") float shakeMultiplier,
            @JsonProperty("glowShader") String glowShader,
            @JsonProperty("motionBlurShader") String motionBlurShader
    ) {
        public Effects {
            trailFade = clamp(trailFade, 0f, 1f, 0.45f);
            hueShiftSpeed = clamp(hueShiftSpeed, 0f, 3f, 0.3f);
            shakeMultiplier = clamp(shakeMultiplier, 0.1f, 2f, 1f);
            glowShader = fallback(glowShader, "shaders/glow.frag");
            motionBlurShader = fallback(motionBlurShader, "shaders/motion_blur.frag");
        }

        private static float clamp(float value, float min, float max, float fallback) {
            if (Float.isNaN(value) || value <= 0f) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        }

        private static String fallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public static Effects defaults() {
            return new Effects(0.45f, 0.3f, 1f, "shaders/glow.frag", "shaders/motion_blur.frag");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UiTheme(
            @JsonProperty("stylesheet") String stylesheet,
            @JsonProperty("fontFamily") String fontFamily,
            @JsonProperty("accentColor") String accentColor,
            @JsonProperty("iconSet") String iconSet
    ) {
        public UiTheme {
            stylesheet = fallback(stylesheet, "ui/themes/neon.css");
            fontFamily = fallback(fontFamily, "Inter");
            accentColor = fallback(accentColor, "#4cd3ff");
            iconSet = fallback(iconSet, "assets/ui/icons/neon");
        }

        private static String fallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public static UiTheme defaults() {
            return new UiTheme("ui/themes/neon.css", "Inter", "#4cd3ff", "assets/ui/icons/neon");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AudioTheme(
            @JsonProperty("menuLoop") String menuLoop,
            @JsonProperty("matchLoop") String matchLoop,
            @JsonProperty("bounce") String bounce,
            @JsonProperty("goal") String goal,
            @JsonProperty("powerUp") String powerUp,
            @JsonProperty("countdown") String countdown
    ) {
        public AudioTheme {
            menuLoop = fallback(menuLoop, "assets/audio/menu/lofi_menu.ogg");
            matchLoop = fallback(matchLoop, "assets/audio/match/cyber_match.ogg");
            bounce = fallback(bounce, "assets/audio/sfx/bounce_soft.ogg");
            goal = fallback(goal, "assets/audio/sfx/goal_swell.ogg");
            powerUp = fallback(powerUp, "assets/audio/sfx/power_up.ogg");
            countdown = fallback(countdown, "assets/audio/voice/countdown.ogg");
        }

        private static String fallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value.trim();
        }

        public static AudioTheme defaults() {
            return new AudioTheme(
                    "assets/audio/menu/lofi_menu.ogg",
                    "assets/audio/match/cyber_match.ogg",
                    "assets/audio/sfx/bounce_soft.ogg",
                    "assets/audio/sfx/goal_swell.ogg",
                    "assets/audio/sfx/power_up.ogg",
                    "assets/audio/voice/countdown.ogg");
        }
    }
}
