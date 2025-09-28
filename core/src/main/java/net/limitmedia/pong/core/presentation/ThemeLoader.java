package net.limitmedia.pong.core.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility for loading {@link ThemeDefinition} instances from packaged
 * resources or external files. Themes are cached in-memory for the lifetime of
 * the application to avoid unnecessary disk I/O.
 */
public final class ThemeLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.findAndRegisterModules();
    }

    private ThemeLoader() {
    }

    public static ThemeDefinition load(String themeName) {
        if (themeName == null || themeName.isBlank()) {
            return loadDefault();
        }
        String normalised = themeName.toLowerCase(Locale.ROOT).replace(' ', '-');
        String resourcePath = "/themes/" + normalised + ".json";
        try (InputStream in = ThemeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return loadDefault();
            }
            return MAPPER.readValue(in, ThemeDefinition.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load theme '" + themeName + "'", ex);
        }
    }

    public static ThemeDefinition loadFromFile(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream in = Files.newInputStream(path)) {
            return MAPPER.readValue(in, ThemeDefinition.class);
        }
    }

    public static ThemeDefinition loadDefault() {
        try (InputStream in = ThemeLoader.class.getResourceAsStream("/themes/neon.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing built-in theme 'neon'");
            }
            return MAPPER.readValue(in, ThemeDefinition.class);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load default theme", ex);
        }
    }
}
