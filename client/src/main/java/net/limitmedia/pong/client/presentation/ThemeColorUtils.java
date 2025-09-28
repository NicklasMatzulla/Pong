package net.limitmedia.pong.client.presentation;

import com.jme3.math.ColorRGBA;

/**
 * Helper methods for converting theme colour strings into JME colour objects.
 */
public final class ThemeColorUtils {
    private ThemeColorUtils() {
    }

    public static ColorRGBA fromHex(String hex, float fallbackAlpha) {
        if (hex == null || hex.isBlank()) {
            return new ColorRGBA(1f, 1f, 1f, fallbackAlpha);
        }
        String value = hex.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            int length = value.length();
            if (length == 6) {
                int r = Integer.parseInt(value.substring(0, 2), 16);
                int g = Integer.parseInt(value.substring(2, 4), 16);
                int b = Integer.parseInt(value.substring(4, 6), 16);
                return new ColorRGBA(r / 255f, g / 255f, b / 255f, fallbackAlpha);
            } else if (length == 8) {
                int r = Integer.parseInt(value.substring(0, 2), 16);
                int g = Integer.parseInt(value.substring(2, 4), 16);
                int b = Integer.parseInt(value.substring(4, 6), 16);
                int a = Integer.parseInt(value.substring(6, 8), 16);
                return new ColorRGBA(r / 255f, g / 255f, b / 255f, a / 255f);
            }
        } catch (NumberFormatException ignored) {
        }
        return new ColorRGBA(1f, 1f, 1f, fallbackAlpha);
    }
}
