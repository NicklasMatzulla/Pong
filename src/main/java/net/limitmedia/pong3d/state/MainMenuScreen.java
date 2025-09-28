package net.limitmedia.pong3d.state;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.engine.Draw;
import net.limitmedia.pong3d.engine.GameApplication;
import net.limitmedia.pong3d.engine.Input;
import net.limitmedia.pong3d.engine.Screen;
import org.lwjgl.glfw.GLFW;

public final class MainMenuScreen implements Screen {
    private final GameApplication app;
    private final MenuButton[] buttons;
    private final AmbientAudioEngine audio;
    private int selectedIndex;
    private float time;

    public MainMenuScreen(GameApplication app) {
        this.app = app;
        this.audio = app.getAudioEngine();
        this.buttons = new MenuButton[] {
                new MenuButton("Sofort spielen", "Starte das Solo Match mit smoother Physik", app::startSoloMatch),
                new MenuButton("Beenden", "Schließe die holografische Arena", app::requestExit)
        };
    }

    @Override
    public void onEnter() {
        time = 0f;
        selectedIndex = 0;
        if (audio != null) {
            audio.playMenuLoop();
        }
    }

    @Override
    public void onExit() {
    }

    @Override
    public void update(float deltaTime) {
        time += deltaTime;
        Input input = app.getInput();

        layoutButtons();
        handleSelection(input);
    }

    private void handleSelection(Input input) {
        if (input.wasPressed(GLFW.GLFW_KEY_UP) || input.wasPressed(GLFW.GLFW_KEY_W)) {
            selectedIndex = (selectedIndex - 1 + buttons.length) % buttons.length;
        }
        if (input.wasPressed(GLFW.GLFW_KEY_DOWN) || input.wasPressed(GLFW.GLFW_KEY_S)) {
            selectedIndex = (selectedIndex + 1) % buttons.length;
        }

        double mouseX = input.getMouseX();
        double mouseY = input.getMouseY();
        for (int i = 0; i < buttons.length; i++) {
            MenuButton button = buttons[i];
            if (button.contains(mouseX, mouseY)) {
                selectedIndex = i;
                if (input.wasMouseClicked()) {
                    button.activate();
                }
            }
        }

        if (input.wasPressed(GLFW.GLFW_KEY_ENTER) || input.wasPressed(GLFW.GLFW_KEY_SPACE)) {
            buttons[selectedIndex].activate();
        }
    }

    private void layoutButtons() {
        int width = app.getWidth();
        int height = app.getHeight();
        float buttonWidth = Math.min(width * 0.5f, 520f);
        float buttonHeight = 74f;
        float startY = height * 0.6f;
        float spacing = 28f;
        float x = (width - buttonWidth) * 0.5f;
        for (int i = 0; i < buttons.length; i++) {
            float y = startY + i * (buttonHeight + spacing);
            buttons[i].updateBounds(x, y, buttonWidth, buttonHeight);
        }
    }

    @Override
    public void render() {
        int width = app.getWidth();
        int height = app.getHeight();
        float glow = 0.4f + 0.2f * (float) Math.sin(time * 0.8f);

        Draw.rect(0, 0, width, height, 0.03f, 0.04f, 0.06f, 1f);
        Draw.rect(0, 0, width, height, 0.06f, 0.07f, 0.12f, 0.55f);
        for (int i = 0; i < 7; i++) {
            float bandHeight = height / 10f;
            float y = i * bandHeight * 0.9f + (float) Math.sin(time * 0.3f + i) * 18f;
            float alpha = 0.06f + i * 0.04f;
            Draw.rect(0, y, width, bandHeight, 0.08f + glow * 0.1f + i * 0.02f, 0.12f + glow * 0.2f + i * 0.025f, 0.18f + glow * 0.3f + i * 0.03f, alpha);
        }

        float orbRadius = 220f + 40f * (float) Math.sin(time * 0.5f);
        float orbX = width * 0.25f + (float) Math.sin(time * 0.35f) * 120f;
        float orbY = height * 0.32f + (float) Math.cos(time * 0.28f) * 70f;
        Draw.circle(orbX, orbY, orbRadius, 0.18f, 0.45f, 0.75f, 0.32f, 64);
        Draw.circle(width - orbX, height - orbY, orbRadius * 0.7f, 0.3f, 0.6f, 0.95f, 0.26f, 64);

        float cardWidth = Math.min(width * 0.68f, 860f);
        float cardHeight = Math.min(height * 0.52f, 520f);
        float cardX = (width - cardWidth) * 0.5f;
        float cardY = height * 0.16f;
        Draw.rect(cardX, cardY, cardWidth, cardHeight, 0.11f, 0.14f, 0.18f, 0.88f);
        Draw.rect(cardX, cardY, cardWidth, 6f, 0.35f, 0.62f, 0.95f, 1f);
        Draw.rect(cardX + 26f, cardY + 90f, cardWidth - 52f, cardHeight - 140f, 0.09f, 0.11f, 0.15f, 0.92f);
        Draw.circle(cardX + cardWidth - 140f, cardY + 120f, 90f + 20f * (float) Math.sin(time * 0.7f), 0.22f, 0.46f, 0.75f, 0.4f, 48);

        Draw.text("PONG VELOCITY", cardX + 48f, cardY + 80f, 3.1f, 0.82f, 0.9f, 1f, 1f);
        Draw.text("Modernes LWJGL Erlebnis mit Vaadin-inspirierter Eleganz", cardX + 48f, cardY + 140f, 1.6f, 0.75f, 0.85f, 1f, 0.92f);
        Draw.text("Bewege das Paddle mit A/D oder den Pfeiltasten. Enter oder Space startet.", cardX + 48f, cardY + 188f, 1.4f, 0.65f, 0.75f, 0.9f, 0.82f);
        Draw.text("Satte Audioeffekte, sanfte Kamerafahrten und ein futuristisches Stadion erwarten dich.", cardX + 48f, cardY + 226f, 1.3f, 0.6f, 0.72f, 0.92f, 0.78f);

        for (int i = 0; i < buttons.length; i++) {
            renderButton(buttons[i], i == selectedIndex, glow);
        }

        Draw.text("LWJGL 3 • 144Hz bereit • Keine Fullscreen-Pflicht", width * 0.5f - 220f, height - 60f, 1.2f, 0.62f, 0.76f, 0.92f, 0.75f);
        Draw.text("Limit Media Labs 2025", width * 0.5f - 120f, height - 32f, 1.1f, 0.52f, 0.64f, 0.82f, 0.65f);
    }

    private void renderButton(MenuButton button, boolean selected, float glow) {
        float accent = selected ? (0.6f + 0.4f * (float) Math.sin(time * 3f)) : 0.35f;
        float baseAlpha = selected ? 0.92f : 0.75f;
        Draw.rect(button.x - 6f, button.y - 6f, button.width + 12f, button.height + 12f, 0.08f, 0.1f, 0.14f, baseAlpha * 0.5f);
        Draw.rect(button.x, button.y, button.width, button.height,
                0.14f + accent * 0.25f,
                0.28f + accent * 0.3f,
                0.46f + accent * 0.28f,
                baseAlpha);
        Draw.text(button.label, button.x + 30f, button.y + button.height / 2f + 6f, 2.0f, 0.92f, 0.96f, 1f, 1f);
        Draw.text(button.description, button.x + 30f, button.y + button.height / 2f + 34f, 1.2f, 0.75f, 0.85f, 1f, selected ? 0.9f : 0.75f);
    }

    private static final class MenuButton {
        private final String label;
        private final String description;
        private final Runnable action;
        private float x;
        private float y;
        private float width;
        private float height;

        private MenuButton(String label, String description, Runnable action) {
            this.label = label;
            this.description = description;
            this.action = action;
        }

        private void updateBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean contains(double px, double py) {
            return px >= x && px <= x + width && py >= y && py <= y + height;
        }

        private void activate() {
            if (action != null) {
                action.run();
            }
        }
    }
}
