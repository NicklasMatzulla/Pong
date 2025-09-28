package net.limitmedia.pong3d.ui;

import com.jme3.math.ColorRGBA;

/**
 * Utility palette inspired by Vaadin's Lumo design system so we can paint
 * in-game overlays and backgrounds with cohesive modern tones without
 * pulling in the full UI toolkit.
 */
public final class VaadinPalette {

    public static final ColorRGBA DARK_SURFACE = new ColorRGBA(0.11f, 0.13f, 0.16f, 1f);
    public static final ColorRGBA DARK_SURFACE_VARIANT = new ColorRGBA(0.16f, 0.18f, 0.22f, 1f);
    public static final ColorRGBA MID_SURFACE = new ColorRGBA(0.2f, 0.22f, 0.27f, 1f);
    public static final ColorRGBA LIGHT_SURFACE = new ColorRGBA(0.26f, 0.29f, 0.34f, 1f);

    public static final ColorRGBA ACCENT_PRIMARY = new ColorRGBA(0f, 0.71f, 0.94f, 1f);
    public static final ColorRGBA ACCENT_PRIMARY_SOFT = new ColorRGBA(0.24f, 0.8f, 0.98f, 1f);
    public static final ColorRGBA ACCENT_SECONDARY = new ColorRGBA(0.38f, 0.76f, 0.91f, 1f);

    public static final ColorRGBA TEXT_HIGH = new ColorRGBA(0.94f, 0.97f, 1f, 1f);
    public static final ColorRGBA TEXT_MEDIUM = new ColorRGBA(0.78f, 0.86f, 0.94f, 1f);
    public static final ColorRGBA TEXT_SUBTLE = new ColorRGBA(0.64f, 0.72f, 0.82f, 1f);

    private VaadinPalette() {
    }

    public static ColorRGBA withAlpha(ColorRGBA color, float alpha) {
        return new ColorRGBA(color.r, color.g, color.b, alpha);
    }
}
