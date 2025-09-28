package net.limitmedia.pong.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class GameConfigTest {

    @Test
    void loadDefaultProvidesThemeAndLocale() {
        GameConfig config = GameConfig.loadDefault();

        assertNotNull(config, "Default configuration should load");
        assertEquals("neon", config.presentation().theme(), "Default theme should be neon");
        assertEquals(1920, config.video().width());
        assertEquals("en-US", config.locale().toLanguageTag(), "Locale should map to en-US");
    }
}
