package net.limitmedia.pong.core.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class ThemeLoaderTest {

    @Test
    void loadsDefaultThemeWhenNameMissing() {
        ThemeDefinition theme = ThemeLoader.load("missing-theme");
        assertNotNull(theme);
        assertEquals("neon", theme.name());
        assertEquals("ui/themes/neon.css", theme.ui().stylesheet());
    }

    @Test
    void loadsNeonThemePalette() {
        ThemeDefinition theme = ThemeLoader.load("neon");
        assertNotNull(theme);
        assertEquals("#1b2231", theme.arena().floorDiffuse());
        assertEquals("#4cd3ff", theme.ball().glow());
        assertEquals(0.85f, theme.effects().shakeMultiplier(), 0.001f);
    }
}
