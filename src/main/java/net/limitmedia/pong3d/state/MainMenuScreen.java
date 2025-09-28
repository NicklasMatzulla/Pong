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
    private int selectedIndex;
    private float time;

    public MainMenuScreen(GameApplication app) {
        this.app = app;
        this.buttons = new MenuButton[] {
                new MenuButton("Starte Solo Match", app::startSoloMatch),
                new MenuButton("Beenden", app::requestExit)
        };
    }

    @Override
    public void onEnter() {
        AmbientAudioEngine audio = app.getAudioEngine();
        if (audio != null) {
            audio.playAmbientLoop();
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
        float buttonWidth = Math.min(width * 0.45f, 480f);
        float buttonHeight = 64f;
        float startY = height * 0.55f;
        float spacing = 20f;
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
        Draw.rect(0, 0, width, height, 0.05f, 0.07f, 0.09f, 1f);
        Draw.rect(0, 0, width, height * 0.35f, 0.12f, 0.16f, 0.22f, 0.9f);
        Draw.rect(0, height * 0.65f, width, height * 0.35f, 0.08f, 0.1f, 0.13f, 0.95f);

        float orbRadius = 180f + 40f * (float) Math.sin(time * 0.6f);
        float orbX = width * 0.25f + (float) Math.sin(time * 0.4f) * 120f;
        float orbY = height * 0.32f + (float) Math.cos(time * 0.35f) * 60f;
        Draw.circle(orbX, orbY, orbRadius, 0.18f, 0.45f, 0.75f, 0.45f, 48);
        Draw.circle(width - orbX, height - orbY, orbRadius * 0.75f, 0.35f, 0.6f, 0.85f, 0.35f, 48);

        float cardWidth = Math.min(width * 0.6f, 820f);
        float cardHeight = Math.min(height * 0.35f, 320f);
        float cardX = (width - cardWidth) * 0.5f;
        float cardY = height * 0.15f;
        Draw.rect(cardX, cardY, cardWidth, cardHeight, 0.12f, 0.16f, 0.2f, 0.9f);
        Draw.rect(cardX, cardY, cardWidth, 4f, 0.3f, 0.6f, 0.95f, 1f);
        Draw.rect(cardX, cardY + cardHeight - 6f, cardWidth, 6f, 0.2f, 0.4f, 0.7f, 0.9f);

        Draw.text("PONG NEXT", cardX + 40f, cardY + 70f, 2.8f, 0.78f, 0.86f, 1f, 1f);
        Draw.text("Futuristisches Paddle Duel mit maximaler Smoothness", cardX + 40f, cardY + 120f, 1.6f, 0.75f, 0.85f, 1f, 0.9f);
        Draw.text("Bewege dich mit A/D oder den Pfeiltasten, Enter bestätigt.", cardX + 40f, cardY + 170f, 1.4f, 0.65f, 0.75f, 0.9f, 0.8f);

        for (int i = 0; i < buttons.length; i++) {
            MenuButton button = buttons[i];
            boolean selected = i == selectedIndex;
            float baseAlpha = selected ? 0.9f : 0.7f;
            float accent = selected ? glow : 0.35f;
            Draw.rect(button.x - 4f, button.y - 4f, button.width + 8f, button.height + 8f, 0.08f, 0.1f, 0.14f, baseAlpha * 0.55f);
            Draw.rect(button.x, button.y, button.width, button.height,
                    0.16f + accent * 0.2f,
                    0.32f + accent * 0.25f,
                    0.5f + accent * 0.2f,
                    baseAlpha);
            Draw.text(button.label, button.x + 24f, button.y + button.height / 2f + 10f,
                    2f, 0.9f, 0.95f, 1f, selected ? 1f : 0.8f);
        }

        Draw.text("Links-Klick oder Enter", width - 280f, height - 40f, 1.2f, 0.6f, 0.75f, 0.9f, 0.7f);
        Draw.text("ESC minimiert nicht mehr, genieße das Menü!", 40f, height - 40f, 1.2f, 0.6f, 0.75f, 0.9f, 0.7f);
    }

    private static final class MenuButton {
        private final String label;
        private final Runnable action;
        private float x;
        private float y;
        private float width;
        private float height;

        private MenuButton(String label, Runnable action) {
            this.label = label;
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
