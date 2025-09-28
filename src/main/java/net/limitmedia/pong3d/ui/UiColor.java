package net.limitmedia.pong3d.ui;

public record UiColor(float r, float g, float b, float a) {
    public static UiColor rgb(float r, float g, float b) {
        return new UiColor(r, g, b, 1f);
    }

    public UiColor withAlpha(float alpha) {
        return new UiColor(r, g, b, alpha);
    }

    public UiColor scale(float factor) {
        return new UiColor(clamp(r * factor), clamp(g * factor), clamp(b * factor), a);
    }

    public UiColor mix(UiColor other, float t) {
        float inv = 1f - t;
        return new UiColor(
                clamp(r * inv + other.r * t),
                clamp(g * inv + other.g * t),
                clamp(b * inv + other.b * t),
                clamp(a * inv + other.a * t)
        );
    }

    private static float clamp(float value) {
        if (value < 0f) {
            return 0f;
        }
        if (value > 1f) {
            return 1f;
        }
        return value;
    }
}
