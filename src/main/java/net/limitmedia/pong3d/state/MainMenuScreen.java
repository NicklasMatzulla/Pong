package net.limitmedia.pong3d.state;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.engine.Draw;
import net.limitmedia.pong3d.engine.GameApplication;
import net.limitmedia.pong3d.engine.Input;
import net.limitmedia.pong3d.engine.Screen;
import net.limitmedia.pong3d.ui.SpringUiTheme;
import net.limitmedia.pong3d.ui.UiColor;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

public final class MainMenuScreen implements Screen {
    private final GameApplication app;
    private final MenuButton[] buttons;
    private final AmbientAudioEngine audio;
    private final String statusMessage;
    private final JoinDialog joinDialog;
    private int selectedIndex;
    private float time;

    public MainMenuScreen(GameApplication app, String statusMessage) {
        this.app = app;
        this.audio = app.getAudioEngine();
        this.statusMessage = statusMessage;
        this.joinDialog = new JoinDialog();
        this.buttons = new MenuButton[] {
                new MenuButton("Einzelspieler", "Starte das Solo Match mit smoother Physik", app::startSoloMatch),
                new MenuButton("Multiplayer hosten", "Erstelle einen Server und lade Spieler ein", app::startNetworkHost),
                new MenuButton("Multiplayer beitreten", "Verbinde dich mit einer bestehenden Session", joinDialog::open),
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
        if (joinDialog.update(deltaTime, input)) {
            return;
        }
        handleSelection(input);
    }

    private void handleSelection(Input input) {
        if (joinDialog.isVisible()) {
            return;
        }
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
        float glow = 0.45f + 0.25f * (float) Math.sin(time * 0.7f);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();

        fillRect(0f, 0f, width, height, SpringUiTheme.CANVAS);
        fillRect(0f, 0f, width, height, withAlpha(SpringUiTheme.CANVAS_GLOW, 0.58f));
        for (int i = 0; i < 7; i++) {
            float bandHeight = height / 9.5f;
            float offset = (float) Math.sin(time * 0.32f + i * 0.75f) * 26f;
            float y = i * bandHeight * 0.88f + offset;
            float alpha = 0.06f + i * 0.035f;
            float r = 0.08f + glow * 0.1f + i * 0.015f;
            float g = 0.12f + glow * 0.18f + i * 0.02f;
            float b = 0.18f + glow * 0.24f + i * 0.024f;
            Draw.rect(0f, y, width, bandHeight, r, g, b, alpha);
        }

        float orbRadius = 220f + 36f * (float) Math.sin(time * 0.54f);
        float orbX = width * 0.24f + (float) Math.sin(time * 0.33f) * 140f;
        float orbY = height * 0.3f + (float) Math.cos(time * 0.27f) * 90f;
        fillCircle(orbX, orbY, orbRadius, withAlpha(SpringUiTheme.ORB_PRIMARY, 0.32f));
        fillCircle(width - orbX, height - orbY, orbRadius * 0.72f, withAlpha(SpringUiTheme.ORB_SECONDARY, 0.24f));

        float cardWidth = Math.min(width * 0.7f, 860f);
        float cardHeight = Math.min(height * 0.52f, 520f);
        float cardX = (width - cardWidth) * 0.5f;
        float cardY = height * 0.16f;
        fillRect(cardX - 18f, cardY - 18f, cardWidth + 36f, cardHeight + 36f, withAlpha(SpringUiTheme.BUTTON_OUTLINE, 0.6f));
        fillRect(cardX, cardY, cardWidth, cardHeight, SpringUiTheme.CARD);
        fillRect(cardX, cardY, cardWidth, 6f, SpringUiTheme.CARD_ACCENT);
        fillRect(cardX + 26f, cardY + 94f, cardWidth - 52f, cardHeight - 148f, withAlpha(SpringUiTheme.CANVAS_GLOW, 0.92f));
        float haloRadius = 90f + 18f * (float) Math.sin(time * 0.66f);
        fillCircle(cardX + cardWidth - 140f, cardY + 126f, haloRadius, withAlpha(SpringUiTheme.ORB_SECONDARY, 0.42f));

        Draw.text("PONG VELOCITY 3D", cardX + 48f, cardY + 80f, 3.2f,
                SpringUiTheme.TEXT_PRIMARY.r(), SpringUiTheme.TEXT_PRIMARY.g(), SpringUiTheme.TEXT_PRIMARY.b(), 1f);
        Draw.text("Geflutet mit Vaadin-Tiefen, volumetrischem Licht und einer reaktiven 3D-Arena", cardX + 48f, cardY + 140f, 1.6f,
                SpringUiTheme.TEXT_SECONDARY.r(), SpringUiTheme.TEXT_SECONDARY.g(), SpringUiTheme.TEXT_SECONDARY.b(), SpringUiTheme.TEXT_SECONDARY.a());
        Draw.text("Steuere das Paddle mit A/D oder den Pfeiltasten. Enter startet die holografische Arena.", cardX + 48f, cardY + 188f,
                1.35f, SpringUiTheme.TEXT_MUTED.r(), SpringUiTheme.TEXT_MUTED.g(), SpringUiTheme.TEXT_MUTED.b(), SpringUiTheme.TEXT_MUTED.a());
        Draw.text("Synchrone Multiplayer-Matches, geschmeidige Rebounds und kamerageführte Lichtspiele warten auf dich.", cardX + 48f,
                cardY + 226f, 1.28f, SpringUiTheme.TEXT_MUTED.r(), SpringUiTheme.TEXT_MUTED.g(), SpringUiTheme.TEXT_MUTED.b(), 0.78f);

        for (int i = 0; i < buttons.length; i++) {
            renderButton(buttons[i], i == selectedIndex, glow);
        }

        Draw.text("LWJGL 3 • Frame pacing stabilisiert • Keine Fullscreen-Pflicht", width * 0.5f - 250f, height - 60f, 1.2f, SpringUiTheme.TEXT_MUTED.r(), SpringUiTheme.TEXT_MUTED.g(), SpringUiTheme.TEXT_MUTED.b(), 0.76f);
        if (statusMessage != null && !statusMessage.isEmpty()) {
            float statusX = width * 0.5f - Math.min(320f, statusMessage.length() * 7.2f);
            Draw.text(statusMessage, statusX, height - 92f, 1.25f, SpringUiTheme.TEXT_PRIMARY.r(), SpringUiTheme.TEXT_PRIMARY.g(), SpringUiTheme.TEXT_PRIMARY.b(), 0.88f);
        }
        Draw.text("Limit Media Labs 2025", width * 0.5f - 120f, height - 32f, 1.1f, SpringUiTheme.TEXT_MUTED.r(), SpringUiTheme.TEXT_MUTED.g(), SpringUiTheme.TEXT_MUTED.b(), 0.62f);

        if (joinDialog.isVisible()) {
            joinDialog.render(width, height);
        }

        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
    }
    private void renderButton(MenuButton button, boolean selected, float glow) {
        float pulse = selected ? (0.65f + 0.35f * (float) Math.sin(time * 3.2f)) : 0.32f;
        UiColor base = selected ? SpringUiTheme.BUTTON_SELECTED : SpringUiTheme.BUTTON_IDLE;
        float alpha = selected ? 0.94f : 0.78f;
        fillRect(button.x - 8f, button.y - 8f, button.width + 16f, button.height + 16f, withAlpha(SpringUiTheme.BUTTON_OUTLINE, alpha * 0.45f));
        Draw.rect(button.x, button.y, button.width, button.height,
                clamp(base.r() + glow * 0.05f + pulse * 0.08f),
                clamp(base.g() + glow * 0.08f + pulse * 0.1f),
                clamp(base.b() + glow * 0.12f + pulse * 0.12f),
                alpha);
        Draw.text(button.label, button.x + 30f, button.y + button.height / 2f + 6f, 2.0f, SpringUiTheme.TEXT_PRIMARY.r(), SpringUiTheme.TEXT_PRIMARY.g(), SpringUiTheme.TEXT_PRIMARY.b(), 1f);
        float descAlpha = selected ? 0.9f : 0.76f;
        Draw.text(button.description, button.x + 30f, button.y + button.height / 2f + 34f, 1.2f, SpringUiTheme.TEXT_SECONDARY.r(), SpringUiTheme.TEXT_SECONDARY.g(), SpringUiTheme.TEXT_SECONDARY.b(), descAlpha);
    }
    private static void fillRect(float x, float y, float width, float height, UiColor color) {
        Draw.rect(x, y, width, height, color.r(), color.g(), color.b(), color.a());
    }

    private static void fillCircle(float x, float y, float radius, UiColor color) {
        Draw.circle(x, y, radius, color.r(), color.g(), color.b(), color.a(), 64);
    }

    private static UiColor withAlpha(UiColor color, float alpha) {
        return color.withAlpha(alpha);
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

    private final class JoinDialog {
        private boolean visible;
        private final StringBuilder host = new StringBuilder("127.0.0.1");
        private float pulse;

        void open() {
            visible = true;
            pulse = 0f;
            if (host.length() == 0) {
                host.append("127.0.0.1");
            }
        }

        boolean update(float deltaTime, Input input) {
            if (!visible) {
                return false;
            }
            pulse += deltaTime;
            String typed = input.consumeTypedChars();
            if (!typed.isEmpty()) {
                for (int i = 0; i < typed.length(); i++) {
                    char c = typed.charAt(i);
                    if (isAllowed(c)) {
                        if (host.length() < 64) {
                            host.append(c);
                        }
                    }
                }
            }
            if (input.wasPressed(GLFW.GLFW_KEY_BACKSPACE) && host.length() > 0) {
                host.setLength(host.length() - 1);
            }
            if (input.wasPressed(GLFW.GLFW_KEY_ESCAPE)) {
                visible = false;
                return true;
            }
            if ((input.wasPressed(GLFW.GLFW_KEY_ENTER) || input.wasPressed(GLFW.GLFW_KEY_KP_ENTER)) && host.length() > 0) {
                String target = host.toString().trim();
                if (target.isEmpty()) {
                    target = "127.0.0.1";
                }
                visible = false;
                app.startNetworkJoin(target);
                return true;
            }
            return true;
        }

        void render(int width, int height) {
            if (!visible) {
                return;
            }
            float overlayWidth = Math.min(width * 0.6f, 520f);
            float overlayHeight = 240f;
            float x = (width - overlayWidth) * 0.5f;
            float y = height * 0.28f;

            fillRect(0f, 0f, width, height, withAlpha(SpringUiTheme.CANVAS, 0.72f));
            fillRect(x - 12f, y - 12f, overlayWidth + 24f, overlayHeight + 24f, withAlpha(SpringUiTheme.BUTTON_OUTLINE, 0.7f));
            fillRect(x, y, overlayWidth, overlayHeight, SpringUiTheme.CARD);
            fillRect(x, y, overlayWidth, 4f, SpringUiTheme.CARD_ACCENT);
            Draw.text("Serveradresse", x + 36f, y + 66f, 1.9f, SpringUiTheme.TEXT_PRIMARY.r(), SpringUiTheme.TEXT_PRIMARY.g(), SpringUiTheme.TEXT_PRIMARY.b(), 1f);
            Draw.text("Bestätige mit Enter. Mit ESC schließt du das Feld.", x + 36f, y + 104f, 1.2f, SpringUiTheme.TEXT_SECONDARY.r(), SpringUiTheme.TEXT_SECONDARY.g(), SpringUiTheme.TEXT_SECONDARY.b(), SpringUiTheme.TEXT_SECONDARY.a());

            float fieldX = x + 32f;
            float fieldY = y + 126f;
            float fieldWidth = overlayWidth - 64f;
            float fieldHeight = 68f;
            fillRect(fieldX - 4f, fieldY - 4f, fieldWidth + 8f, fieldHeight + 8f, withAlpha(SpringUiTheme.BUTTON_OUTLINE, 0.6f));
            fillRect(fieldX, fieldY, fieldWidth, fieldHeight, withAlpha(SpringUiTheme.CANVAS_GLOW, 0.96f));
            fillRect(fieldX, fieldY, fieldWidth, 2f, SpringUiTheme.CARD_ACCENT);
            String textValue = host.length() == 0 ? "127.0.0.1" : host.toString();
            boolean caret = ((int) (pulse * 2f)) % 2 == 0;
            String display = caret ? textValue + "_" : textValue + " ";
            Draw.text(display, fieldX + 16f, fieldY + 46f, 1.6f, SpringUiTheme.TEXT_PRIMARY.r(), SpringUiTheme.TEXT_PRIMARY.g(), SpringUiTheme.TEXT_PRIMARY.b(), 1f);
        }
        boolean isVisible() {
            return visible;
        }

        private boolean isAllowed(char c) {
            return Character.isDigit(c) || Character.isLetter(c) || c == '.' || c == '-' || c == ':';
        }
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
