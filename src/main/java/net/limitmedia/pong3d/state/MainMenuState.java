package net.limitmedia.pong3d.state;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.scene.shape.Quad;
import com.jme3.util.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Neon styled main and pause menu with clickable buttons and keyboard navigation.
 */
public class MainMenuState extends BaseAppState implements ActionListener {

    private final SimpleApplication app;
    private final Runnable onPlaySingle;
    private final Runnable onPlayOnline;
    private final Runnable onQuit;
    private final boolean pauseMenu;

    private final Node gui = new Node("MainMenu");
    private final List<MenuButton> buttons = new ArrayList<>();

    private BitmapText titleText;
    private BitmapText subtitleText;
    private BitmapText hintText;
    private Geometry panel;
    private float panelWidth;
    private float panelHeight;

    private BitmapFont largeFont;
    private BitmapFont smallFont;

    private final ColorRGBA accentColor = new ColorRGBA(0.35f, 0.8f, 1f, 1f);

    private int selectedIndex = 0;
    private boolean pointerPressed = false;

    public MainMenuState(SimpleApplication app, Runnable onPlaySingle, Runnable onPlayOnline, Runnable onQuit) {
        this(app, onPlaySingle, onPlayOnline, onQuit, false);
    }

    public MainMenuState(SimpleApplication app, Runnable onPlaySingle, Runnable onPlayOnline, Runnable onQuit, boolean pauseMenu) {
        this.app = app;
        this.onPlaySingle = onPlaySingle;
        this.onPlayOnline = onPlayOnline;
        this.onQuit = onQuit;
        this.pauseMenu = pauseMenu;
    }

    @Override
    protected void initialize(Application ignored) {
        var assetManager = app.getAssetManager();
        largeFont = assetManager.loadFont("Interface/Fonts/Default.fnt");
        smallFont = largeFont;

        float width = app.getCamera().getWidth();
        float height = app.getCamera().getHeight();

        // Dimmed backdrop.
        Quad backdropQuad = new Quad(width, height);
        Geometry backdrop = new Geometry("menu-backdrop", backdropQuad);
        Material backdropMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        backdropMat.setColor("Color", new ColorRGBA(0, 0, 0, pauseMenu ? 0.45f : 0.75f));
        backdrop.setMaterial(backdropMat);
        backdrop.setQueueBucket(RenderQueue.Bucket.Gui);
        backdrop.setLocalTranslation(0, 0, -1f);
        gui.attachChild(backdrop);

        panel = createGlassPanel(width * 0.6f, height * 0.55f);
        float panelX = (width - panelWidth) / 2f;
        float panelY = (height - panelHeight) / 2f;
        panel.setLocalTranslation(panelX, panelY, 0.1f);
        gui.attachChild(panel);

        titleText = new BitmapText(largeFont, false);
        titleText.setSize(64);
        titleText.setText(pauseMenu ? "PAUSE" : "PONG 3D");
        centerText(titleText, height - 140f);
        titleText.setColor(new ColorRGBA(0.9f, 0.95f, 1f, 1f));
        gui.attachChild(titleText);

        subtitleText = new BitmapText(smallFont, false);
        subtitleText.setSize(28);
        subtitleText.setText(pauseMenu ? "Mach eine kurze Verschnaufpause" : "Wähle einen Spielmodus");
        centerText(subtitleText, height - 190f);
        subtitleText.setColor(new ColorRGBA(0.7f, 0.85f, 1f, 0.9f));
        gui.attachChild(subtitleText);

        hintText = new BitmapText(smallFont, false);
        hintText.setSize(20);
        hintText.setText("A/D bewegen • ESC " + (pauseMenu ? "weiter" : "beenden"));
        centerText(hintText, 60f);
        hintText.setColor(new ColorRGBA(0.75f, 0.82f, 0.92f, 0.85f));
        gui.attachChild(hintText);

        createButtons(width, height);
        updateButtonStates(-1);

        var input = app.getInputManager();
        input.addMapping("MENU_UP", new KeyTrigger(KeyInput.KEY_UP), new KeyTrigger(KeyInput.KEY_W));
        input.addMapping("MENU_DOWN", new KeyTrigger(KeyInput.KEY_DOWN), new KeyTrigger(KeyInput.KEY_S));
        input.addMapping("MENU_ACCEPT", new KeyTrigger(KeyInput.KEY_RETURN), new KeyTrigger(KeyInput.KEY_NUMPADENTER), new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping("MENU_BACK", new KeyTrigger(KeyInput.KEY_ESCAPE));
        input.addMapping("MENU_CLICK", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addListener(this, "MENU_UP", "MENU_DOWN", "MENU_ACCEPT", "MENU_BACK", "MENU_CLICK");
    }

    private void createButtons(float width, float height) {
        buttons.clear();

        List<MenuButton> entries = new ArrayList<>();
        if (pauseMenu) {
            if (onPlaySingle != null) {
                entries.add(new MenuButton("Weiter spielen", onPlaySingle));
            }
            if (onQuit != null) {
                entries.add(new MenuButton("Spiel beenden", onQuit));
            }
        } else {
            if (onPlaySingle != null) {
                entries.add(new MenuButton("Einzelspieler starten", onPlaySingle));
            }
            if (onPlayOnline != null) {
                entries.add(new MenuButton("Online spielen", onPlayOnline));
            }
            if (onQuit != null) {
                entries.add(new MenuButton("Beenden", onQuit));
            }
        }

        float buttonHeight = 68f;
        float totalHeight = entries.size() * buttonHeight + (Math.max(entries.size() - 1, 0)) * 18f;
        float startY = height / 2f + totalHeight / 2f;

        for (int i = 0; i < entries.size(); i++) {
            MenuButton button = entries.get(i);
            float y = startY - (i * (buttonHeight + 18f)) - buttonHeight;
            float x = (width - button.getWidth()) / 2f;
            button.setLocalTranslation(x, y, 0.2f);
            gui.attachChild(button);
            buttons.add(button);
        }

        if (selectedIndex >= buttons.size()) {
            selectedIndex = Math.max(0, buttons.size() - 1);
        }
    }

    private void centerText(BitmapText text, float y) {
        float x = (app.getCamera().getWidth() - text.getLineWidth()) / 2f;
        text.setLocalTranslation(new Vector3f(x, y, 0.2f));
    }

    @Override
    public void update(float tpf) {
        if (buttons.isEmpty()) {
            return;
        }
        Vector2f cursor = app.getInputManager().getCursorPosition();
        int pointerIndex = indexAt(cursor);
        if (pointerIndex >= 0 && !pointerPressed) {
            selectedIndex = pointerIndex;
        }
        updateButtonStates(pointerIndex);
    }

    private void updateButtonStates(int pointerIndex) {
        for (int i = 0; i < buttons.size(); i++) {
            MenuButton button = buttons.get(i);
            boolean isPointer = i == pointerIndex;
            button.setHovered(isPointer);
            button.setSelected(i == selectedIndex);
            button.setPressed(pointerPressed && isPointer);
        }
    }

    private int indexAt(Vector2f cursor) {
        for (int i = 0; i < buttons.size(); i++) {
            if (buttons.get(i).contains(cursor)) {
                return i;
            }
        }
        return -1;
    }

    private void triggerSelected() {
        if (buttons.isEmpty()) {
            return;
        }
        buttons.get(selectedIndex).fire();
    }

    private void moveSelection(int delta) {
        if (buttons.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + delta + buttons.size()) % buttons.size();
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        switch (name) {
            case "MENU_UP":
                if (!isPressed) {
                    moveSelection(-1);
                    updateButtonStates(-1);
                }
                break;
            case "MENU_DOWN":
                if (!isPressed) {
                    moveSelection(1);
                    updateButtonStates(-1);
                }
                break;
            case "MENU_ACCEPT":
                if (!isPressed) {
                    triggerSelected();
                }
                break;
            case "MENU_BACK":
                if (!isPressed) {
                    if (pauseMenu && onPlaySingle != null) {
                        onPlaySingle.run();
                    } else if (onQuit != null) {
                        onQuit.run();
                    }
                }
                break;
            case "MENU_CLICK":
                pointerPressed = isPressed;
                int pointerIndex = indexAt(app.getInputManager().getCursorPosition());
                if (isPressed) {
                    if (pointerIndex >= 0) {
                        selectedIndex = pointerIndex;
                        updateButtonStates(pointerIndex);
                    }
                } else {
                    if (pointerIndex >= 0) {
                        selectedIndex = pointerIndex;
                        triggerSelected();
                    }
                    updateButtonStates(pointerIndex);
                }
                break;
        }
    }

    @Override
    protected void onEnable() {
        app.enqueue(() -> {
            app.getGuiNode().attachChild(gui);
            app.getInputManager().setCursorVisible(true);
            return null;
        });
    }

    @Override
    protected void onDisable() {
        app.enqueue(() -> {
            gui.removeFromParent();
            return null;
        });
        var input = app.getInputManager();
        input.deleteMapping("MENU_UP");
        input.deleteMapping("MENU_DOWN");
        input.deleteMapping("MENU_ACCEPT");
        input.deleteMapping("MENU_BACK");
        input.deleteMapping("MENU_CLICK");
        input.removeListener(this);
    }

    @Override
    protected void cleanup(Application app) {
        buttons.clear();
    }

    private Geometry createGlassPanel(float width, float height) {
        panelWidth = Math.max(420f, width);
        panelHeight = Math.max(320f, height);
        Mesh mesh = new Quad(panelWidth, panelHeight);
        Geometry geometry = new Geometry("menu-panel", mesh);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setColor("Color", new ColorRGBA(0.08f, 0.1f, 0.15f, 0.75f));
        geometry.setMaterial(mat);
        geometry.setQueueBucket(RenderQueue.Bucket.Gui);
        return geometry;
    }

    private final class MenuButton extends Node {
        private final float width = 380f;
        private final float height = 68f;
        private final Runnable action;
        private final Geometry background;
        private final Mesh backgroundMesh;
        private final Geometry glow;
        private final Geometry shadow;
        private final BitmapText label;
        private final ColorRGBA baseTop = new ColorRGBA(0.16f, 0.2f, 0.28f, 0.94f);
        private final ColorRGBA baseBottom = new ColorRGBA(0.09f, 0.12f, 0.18f, 0.94f);
        private final ColorRGBA hoverTop = mix(baseTop, accentColor, 0.45f);
        private final ColorRGBA hoverBottom = mix(baseBottom, accentColor, 0.45f);
        private final ColorRGBA pressTop = mix(baseTop, accentColor, 0.7f);
        private final ColorRGBA pressBottom = mix(baseBottom, accentColor, 0.7f);
        private final ColorRGBA glowColor = accentColor.clone();

        private boolean hovered;
        private boolean pressed;
        private boolean selected;

        private MenuButton(String text, Runnable action) {
            this.action = action;

            shadow = new Geometry("shadow", new Quad(width, height));
            Material shadowMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            shadowMat.setColor("Color", new ColorRGBA(0, 0, 0, 0.35f));
            shadow.setMaterial(shadowMat);
            shadow.setLocalTranslation(6f, -6f, -0.2f);
            shadow.setQueueBucket(RenderQueue.Bucket.Gui);
            attachChild(shadow);

            background = createGradientQuad(width, height, baseTop, baseBottom);
            backgroundMesh = background.getMesh();
            attachChild(background);

            glow = new Geometry("glow", new Quad(width, height));
            Material glowMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            glowColor.a = 0f;
            glowMat.setColor("Color", glowColor);
            glow.setMaterial(glowMat);
            glow.setQueueBucket(RenderQueue.Bucket.Gui);
            glow.setLocalTranslation(0, 0, 0.05f);
            attachChild(glow);

            label = new BitmapText(smallFont, false);
            label.setSize(28);
            label.setText(text);
            label.setColor(ColorRGBA.White);
            float textWidth = label.getLineWidth();
            float textHeight = label.getLineHeight();
            label.setLocalTranslation((width - textWidth) / 2f, (height + textHeight) / 2f, 0.1f);
            attachChild(label);
        }

        private Geometry createGradientQuad(float w, float h, ColorRGBA top, ColorRGBA bottom) {
            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[]{
                    0f, 0f, 0f,
                    w, 0f, 0f,
                    0f, h, 0f,
                    w, h, 0f
            });
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[]{
                    0f, 0f,
                    1f, 0f,
                    0f, 1f,
                    1f, 1f
            });
            mesh.setBuffer(VertexBuffer.Type.Index, 3, new short[]{0, 1, 2, 2, 1, 3});
            applyGradient(mesh, top, bottom);
            mesh.updateBound();
            Geometry geom = new Geometry("button-bg", mesh);
            Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            mat.setBoolean("VertexColor", true);
            geom.setMaterial(mat);
            geom.setQueueBucket(RenderQueue.Bucket.Gui);
            return geom;
        }

        private void applyGradient(Mesh mesh, ColorRGBA top, ColorRGBA bottom) {
            float[] colors = new float[]{
                    bottom.r, bottom.g, bottom.b, bottom.a,
                    bottom.r, bottom.g, bottom.b, bottom.a,
                    top.r, top.g, top.b, top.a,
                    top.r, top.g, top.b, top.a
            };
            FloatBuffer buffer = BufferUtils.createFloatBuffer(colors);
            mesh.setBuffer(VertexBuffer.Type.Color, 4, buffer);
            mesh.updateBound();
        }

        public float getWidth() {
            return width;
        }

        public float getHeight() {
            return height;
        }

        public boolean contains(Vector2f cursor) {
            Vector3f world = getWorldTranslation();
            float x = world.x;
            float y = world.y;
            return cursor.x >= x && cursor.x <= x + width && cursor.y >= y && cursor.y <= y + height;
        }

        public void setHovered(boolean hovered) {
            if (this.hovered == hovered) {
                return;
            }
            this.hovered = hovered;
            refresh();
        }

        public void setPressed(boolean pressed) {
            if (this.pressed == pressed) {
                return;
            }
            this.pressed = pressed;
            refresh();
        }

        public void setSelected(boolean selected) {
            if (this.selected == selected) {
                return;
            }
            this.selected = selected;
            glowColor.a = selected ? 0.35f : 0f;
            glow.getMaterial().setColor("Color", glowColor);
            refresh();
        }

        public void fire() {
            if (action != null) {
                action.run();
            }
        }

        private void refresh() {
            ColorRGBA top = baseTop;
            ColorRGBA bottom = baseBottom;
            if (pressed) {
                top = pressTop;
                bottom = pressBottom;
            } else if (hovered || selected) {
                top = hoverTop;
                bottom = hoverBottom;
            }
            applyGradient(backgroundMesh, top, bottom);
        }
    }

    private ColorRGBA mix(ColorRGBA base, ColorRGBA target, float t) {
        ColorRGBA result = base.clone();
        result.interpolateLocal(target, t);
        return result;
    }
}
