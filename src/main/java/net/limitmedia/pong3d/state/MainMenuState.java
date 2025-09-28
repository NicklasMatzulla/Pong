package net.limitmedia.pong3d.state;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.audio.AudioNode;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
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

import net.limitmedia.pong3d.audio.ProceduralAudioFactory;
import net.limitmedia.pong3d.ui.VaadinPalette;

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
    private Node panel;
    private Geometry panelFill;
    private Geometry panelSheen;
    private Geometry panelOutline;
    private float panelWidth;
    private float panelHeight;

    private BitmapFont largeFont;
    private BitmapFont smallFont;

    private final ColorRGBA accentColor = VaadinPalette.ACCENT_PRIMARY.clone();
    private final ColorRGBA titleColor = VaadinPalette.TEXT_HIGH.clone();

    private int selectedIndex = 0;
    private boolean pointerPressed = false;
    private BitmapText taglineText;
    private final List<AccentLayer> accentLayers = new ArrayList<>();
    private float pulseTime = 0f;
    private final ColorRGBA tempColor = new ColorRGBA();
    private Node heroCard;
    private Geometry heroRing;
    private Geometry heroTrail;
    private Geometry heroGlow;
    private final List<FloatingOrb> heroOrbs = new ArrayList<>();
    private float heroRotation = 0f;
    private float heroTime = 0f;
    private float heroCardHeight;
    private final ColorRGBA heroTrailColor = VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.32f);
    private float panelX;
    private float panelY;
    private float buttonColumnX;
    private float buttonColumnCenterY;
    private final Quaternion heroRotationQuat = new Quaternion();
    private AudioNode menuMusic;

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

        Geometry backdrop = createGradientQuad(width, height,
                VaadinPalette.withAlpha(VaadinPalette.DARK_SURFACE, pauseMenu ? 0.48f : 0.96f),
                VaadinPalette.withAlpha(VaadinPalette.DARK_SURFACE_VARIANT, pauseMenu ? 0.5f : 0.98f),
                VaadinPalette.withAlpha(VaadinPalette.MID_SURFACE, pauseMenu ? 0.52f : 0.99f),
                VaadinPalette.withAlpha(VaadinPalette.DARK_SURFACE_VARIANT, pauseMenu ? 0.48f : 0.96f));
        backdrop.setLocalTranslation(0, 0, -1f);
        gui.attachChild(backdrop);

        buildAccentLayers(width, height);
        pulseTime = 0f;
        heroTime = 0f;
        heroRotation = 0f;
        heroOrbs.clear();

        float stageWidth = width * 0.88f;
        float stageHeight = height * 0.76f;
        Geometry stageShadow = createGradientQuad(stageWidth, stageHeight,
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, pauseMenu ? 0.18f : 0.32f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, pauseMenu ? 0.12f : 0.24f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, pauseMenu ? 0.28f : 0.4f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, pauseMenu ? 0.2f : 0.32f));
        stageShadow.setLocalTranslation((width - stageWidth) / 2f, (height - stageHeight) / 2f, -0.42f);
        gui.attachChild(stageShadow);

        Geometry stageGlow = createGradientQuad(stageWidth, stageHeight,
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.15f), pauseMenu ? 0.08f : 0.14f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.1f), pauseMenu ? 0.05f : 0.1f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.25f), pauseMenu ? 0.12f : 0.18f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.2f), pauseMenu ? 0.1f : 0.16f));
        stageGlow.setLocalTranslation((width - stageWidth) / 2f, (height - stageHeight) / 2f, -0.4f);
        gui.attachChild(stageGlow);

        panel = createGlassPanel(width * 0.7f, height * 0.6f);
        panelX = (width - panelWidth) / 2f;
        panelY = (height - panelHeight) / 2f;
        panel.setLocalTranslation(panelX, panelY, 0.1f);
        gui.attachChild(panel);

        float buttonAreaWidth = panelWidth * 0.4f;
        float buttonAreaHeight = panelHeight - 120f;
        float buttonAreaX = panelX + panelWidth * 0.5f;
        float buttonAreaY = panelY + 72f;
        Geometry buttonShadow = createGradientQuad(buttonAreaWidth + 80f, buttonAreaHeight + 80f,
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.28f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.22f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.34f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.28f));
        buttonShadow.setLocalTranslation(buttonAreaX - panelX - 40f, buttonAreaY - panelY - 40f, 0.08f);
        panel.attachChild(buttonShadow);
        Geometry buttonBackdrop = createGradientQuad(buttonAreaWidth, buttonAreaHeight,
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.16f), 0.86f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.1f), 0.82f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.32f), 0.9f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.24f), 0.88f));
        buttonBackdrop.setLocalTranslation(buttonAreaX - panelX, buttonAreaY - panelY, 0.12f);
        panel.attachChild(buttonBackdrop);

        heroCard = createHeroCard(panelWidth * 0.42f, panelHeight - 96f);
        heroCard.setLocalTranslation(46f, panelHeight - heroCardHeight - 48f, 0.15f);
        panel.attachChild(heroCard);

        float contentX = panelX + panelWidth * 0.55f;
        float headingTop = panelY + panelHeight - 72f;

        titleText = new BitmapText(largeFont, false);
        titleText.setSize(pauseMenu ? 60f : 72f);
        titleText.setText(pauseMenu ? "PAUSE" : "PONG // NEO");
        titleText.setColor(titleColor);
        titleText.setLocalTranslation(contentX, headingTop, 0.2f);
        gui.attachChild(titleText);

        subtitleText = new BitmapText(smallFont, false);
        subtitleText.setSize(32f);
        subtitleText.setText(pauseMenu ? "Mach eine kurze Verschnaufpause" : "Bereit für die nächste Dimension?");
        subtitleText.setColor(VaadinPalette.withAlpha(VaadinPalette.TEXT_MEDIUM, 0.94f));
        subtitleText.setLocalTranslation(contentX, headingTop - 56f, 0.2f);
        gui.attachChild(subtitleText);

        taglineText = new BitmapText(smallFont, false);
        taglineText.setSize(24f);
        taglineText.setText(pauseMenu ? "Bereit, wenn du es bist" : "Synthwave Championship Edition");
        taglineText.setColor(VaadinPalette.withAlpha(VaadinPalette.TEXT_SUBTLE, 0.9f));
        taglineText.setLocalTranslation(contentX, headingTop - 92f, 0.2f);
        gui.attachChild(taglineText);

        buttonColumnX = buttonAreaX + buttonAreaWidth / 2f;
        buttonColumnCenterY = buttonAreaY + buttonAreaHeight / 2f;

        hintText = new BitmapText(smallFont, false);
        hintText.setSize(20f);
        hintText.setText(pauseMenu ? "ENTER fortsetzen  •  ESC zurück" : "ENTER starten  •  Klicke oder nutze A/D");
        hintText.setColor(VaadinPalette.withAlpha(VaadinPalette.TEXT_SUBTLE, 0.92f));
        float hintBaseline = panelY + 36f;
        hintText.setLocalTranslation(buttonColumnX - (hintText.getLineWidth() / 2f), hintBaseline, 0.2f);
        gui.attachChild(hintText);

        createButtons();
        updateButtonStates(-1);

        if (!pauseMenu) {
            menuMusic = ProceduralAudioFactory.createMenuLoop();
            gui.attachChild(menuMusic);
            menuMusic.play();
        }

        var input = app.getInputManager();
        input.addMapping("MENU_UP", new KeyTrigger(KeyInput.KEY_UP), new KeyTrigger(KeyInput.KEY_W));
        input.addMapping("MENU_DOWN", new KeyTrigger(KeyInput.KEY_DOWN), new KeyTrigger(KeyInput.KEY_S));
        input.addMapping("MENU_ACCEPT", new KeyTrigger(KeyInput.KEY_RETURN), new KeyTrigger(KeyInput.KEY_NUMPADENTER), new KeyTrigger(KeyInput.KEY_SPACE));
        input.addMapping("MENU_BACK", new KeyTrigger(KeyInput.KEY_ESCAPE));
        input.addMapping("MENU_CLICK", new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        input.addListener(this, "MENU_UP", "MENU_DOWN", "MENU_ACCEPT", "MENU_BACK", "MENU_CLICK");
    }

    private void createButtons() {
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

        float buttonHeight = 74f;
        float spacing = 28f;
        float totalHeight = entries.size() * buttonHeight + Math.max(entries.size() - 1, 0) * spacing;
        float startY = buttonColumnCenterY + totalHeight / 2f;

        for (int i = 0; i < entries.size(); i++) {
            MenuButton button = entries.get(i);
            float y = startY - (i * (buttonHeight + spacing)) - buttonHeight;
            float x = buttonColumnX - (button.getWidth() / 2f);
            button.setLocalTranslation(x, y, 0.2f);
            gui.attachChild(button);
            buttons.add(button);
        }

        if (selectedIndex >= buttons.size()) {
            selectedIndex = Math.max(0, buttons.size() - 1);
        }
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
        pulseTime += tpf;
        updateAccentLayers();
        updatePanelSheen();
        updateHeroCard(tpf);
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
                if (!isPressed && pauseMenu && onPlaySingle != null) {
                    onPlaySingle.run();
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
            if (menuMusic != null) {
                menuMusic.stop();
                menuMusic.removeFromParent();
                menuMusic = null;
            }
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
        accentLayers.clear();
        heroOrbs.clear();
        heroCard = null;
        heroRing = null;
        heroTrail = null;
        heroGlow = null;
        heroRotation = 0f;
        heroTime = 0f;
        if (menuMusic != null) {
            menuMusic.stop();
            menuMusic.removeFromParent();
            menuMusic = null;
        }
    }

    private Node createHeroCard(float width, float height) {
        heroCardHeight = height;
        heroOrbs.clear();

        Node container = new Node("hero-card");

        Geometry base = createGradientQuad(width, height,
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.2f), 0.9f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.16f), 0.9f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.38f), 0.94f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.32f), 0.94f));
        base.setLocalTranslation(0, 0, -0.08f);
        container.attachChild(base);

        Geometry border = new Geometry("hero-border", new Quad(width, height));
        Material borderMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        borderMat.setColor("Color", VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.24f));
        borderMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        border.setMaterial(borderMat);
        border.setQueueBucket(RenderQueue.Bucket.Gui);
        border.setLocalTranslation(0, 0, -0.09f);
        container.attachChild(border);

        float centerX = width * 0.5f;
        float centerY = height * 0.55f;
        float ballRadius = Math.min(width, height) * 0.24f;

        heroGlow = createCircle(ballRadius * 1.55f,
                new ColorRGBA(0.18f, 0.45f, 1f, 0.22f),
                new ColorRGBA(0.18f, 0.45f, 1f, 0f));
        heroGlow.setLocalTranslation(centerX, centerY, 0.05f);
        container.attachChild(heroGlow);

        Geometry ball = createCircle(ballRadius,
                new ColorRGBA(0.96f, 0.98f, 1f, 1f),
                new ColorRGBA(0.34f, 0.82f, 1f, 0.9f));
        ball.setLocalTranslation(centerX, centerY, 0.2f);
        container.attachChild(ball);

        heroRing = createRing(ballRadius * 1.2f, ballRadius * 1.42f,
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.12f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY, 0.46f));
        heroRing.setLocalTranslation(centerX, centerY, 0.25f);
        container.attachChild(heroRing);

        Node trailNode = new Node("hero-trail-node");
        float trailWidth = width * 0.78f;
        float trailHeight = height * 0.18f;
        heroTrailColor.set(VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.34f));
        heroTrail = new Geometry("hero-trail", new Quad(trailWidth, trailHeight));
        Material trailMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        trailMat.setColor("Color", heroTrailColor.clone());
        trailMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        heroTrail.setMaterial(trailMat);
        heroTrail.setQueueBucket(RenderQueue.Bucket.Gui);
        heroTrail.setLocalTranslation(-trailWidth / 2f, -trailHeight / 2f, 0f);
        trailNode.attachChild(heroTrail);
        trailNode.setLocalTranslation(centerX, centerY - ballRadius * 0.75f, 0.12f);
        trailNode.rotate(0f, 0f, FastMath.DEG_TO_RAD * 18f);
        container.attachChild(trailNode);

        heroOrbs.add(createOrb(container, centerX - ballRadius * 1.65f, centerY + ballRadius * 0.3f,
                ballRadius * 0.42f, 1.35f, ballRadius * 0.35f, 0f, 0.08f,
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.92f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY, 0.18f)));
        heroOrbs.add(createOrb(container, centerX + ballRadius * 1.4f, centerY - ballRadius * 0.6f,
                ballRadius * 0.35f, 1.6f, ballRadius * 0.42f, FastMath.PI * 0.5f, 0.1f,
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_SECONDARY, 0.88f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.2f)));
        heroOrbs.add(createOrb(container, centerX - ballRadius * 0.4f, centerY - ballRadius * 1.4f,
                ballRadius * 0.28f, 1.85f, ballRadius * 0.3f, FastMath.PI, 0.12f,
                VaadinPalette.withAlpha(VaadinPalette.TEXT_HIGH, 0.78f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_SECONDARY, 0.16f)));

        Geometry scanline = createGradientQuad(width * 0.85f, 4f,
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY, 0f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.55f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY, 0f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.55f));
        scanline.setLocalTranslation(width * 0.08f, height * 0.32f, 0.18f);
        container.attachChild(scanline);

        return container;
    }

    private FloatingOrb createOrb(Node parent, float x, float y, float radius, float speed, float amplitude,
                                  float phase, float scalePulse, ColorRGBA inner, ColorRGBA outer) {
        Geometry orb = createCircle(radius, inner, outer);
        orb.setLocalTranslation(x, y, 0.18f);
        parent.attachChild(orb);
        return new FloatingOrb(orb, new Vector2f(x, y), amplitude, speed, phase, scalePulse);
    }

    private Geometry createCircle(float radius, ColorRGBA centerColor, ColorRGBA edgeColor) {
        int samples = 64;
        float[] positions = new float[(samples + 2) * 3];
        float[] colors = new float[(samples + 2) * 4];
        short[] indices = new short[samples * 3];

        positions[0] = 0f;
        positions[1] = 0f;
        positions[2] = 0f;
        putColor(colors, 0, centerColor);

        for (int i = 0; i <= samples; i++) {
            float angle = FastMath.TWO_PI * i / samples;
            float x = FastMath.cos(angle) * radius;
            float y = FastMath.sin(angle) * radius;
            int posIndex = (i + 1) * 3;
            positions[posIndex] = x;
            positions[posIndex + 1] = y;
            positions[posIndex + 2] = 0f;
            putColor(colors, (i + 1) * 4, edgeColor);
        }

        for (int i = 0; i < samples; i++) {
            indices[i * 3] = 0;
            indices[i * 3 + 1] = (short) (i + 1);
            indices[i * 3 + 2] = (short) (i + 2);
        }

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(indices));
        mesh.updateBound();

        Geometry geom = new Geometry("circle", mesh);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        return geom;
    }

    private Geometry createRing(float innerRadius, float outerRadius, ColorRGBA innerColor, ColorRGBA outerColor) {
        int samples = 64;
        float[] positions = new float[samples * 2 * 3];
        float[] colors = new float[samples * 2 * 4];
        short[] indices = new short[samples * 6];

        for (int i = 0; i < samples; i++) {
            float angle = FastMath.TWO_PI * i / samples;
            float cos = FastMath.cos(angle);
            float sin = FastMath.sin(angle);
            int innerIndex = i * 6;
            int outerIndex = innerIndex + 3;

            positions[innerIndex] = cos * innerRadius;
            positions[innerIndex + 1] = sin * innerRadius;
            positions[innerIndex + 2] = 0f;
            positions[outerIndex] = cos * outerRadius;
            positions[outerIndex + 1] = sin * outerRadius;
            positions[outerIndex + 2] = 0f;

            putColor(colors, i * 8, innerColor);
            putColor(colors, i * 8 + 4, outerColor);
        }

        for (int i = 0; i < samples; i++) {
            int next = (i + 1) % samples;
            short innerCurrent = (short) (i * 2);
            short outerCurrent = (short) (innerCurrent + 1);
            short innerNext = (short) (next * 2);
            short outerNext = (short) (innerNext + 1);
            int base = i * 6;
            indices[base] = innerCurrent;
            indices[base + 1] = outerCurrent;
            indices[base + 2] = outerNext;
            indices[base + 3] = innerCurrent;
            indices[base + 4] = outerNext;
            indices[base + 5] = innerNext;
        }

        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(positions));
        mesh.setBuffer(VertexBuffer.Type.Color, 4, BufferUtils.createFloatBuffer(colors));
        mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createShortBuffer(indices));
        mesh.updateBound();

        Geometry geom = new Geometry("ring", mesh);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        return geom;
    }

    private void updateHeroCard(float tpf) {
        if (heroCard == null) {
            return;
        }
        heroTime += tpf;
        heroRotation += tpf * 0.9f;
        if (heroRing != null) {
            heroRotationQuat.fromAngleAxis(heroRotation, Vector3f.UNIT_Z);
            heroRing.setLocalRotation(heroRotationQuat);
        }
        if (heroGlow != null) {
            float glowScale = 1f + 0.05f * FastMath.sin(heroTime * 1.4f);
            heroGlow.setLocalScale(glowScale);
        }
        if (heroTrail != null) {
            float alpha = 0.26f + 0.14f * FastMath.sin(heroTime * 1.6f);
            heroTrailColor.a = FastMath.clamp(alpha, 0.12f, 0.45f);
            Material mat = heroTrail.getMaterial();
            if (mat != null) {
                mat.setColor("Color", heroTrailColor);
            }
        }
        for (FloatingOrb orb : heroOrbs) {
            orb.update(heroTime);
        }
    }

    private Node createGlassPanel(float width, float height) {
        panelWidth = Math.max(480f, width);
        panelHeight = Math.max(340f, height);
        float outlinePadding = 18f;

        Node container = new Node("menu-panel");

        Geometry panelShadow = createGradientQuad(panelWidth + outlinePadding * 2.4f, panelHeight + outlinePadding * 2.4f,
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.22f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.18f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.32f),
                VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.28f));
        panelShadow.setLocalTranslation(-outlinePadding * 1.2f, -outlinePadding * 1.2f, -0.32f);
        container.attachChild(panelShadow);

        panelOutline = new Geometry("panel-outline", new Quad(panelWidth + outlinePadding, panelHeight + outlinePadding));
        Material outlineMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        outlineMat.setColor("Color", VaadinPalette.withAlpha(accentColor, 0.28f));
        panelOutline.setMaterial(outlineMat);
        panelOutline.setQueueBucket(RenderQueue.Bucket.Gui);
        panelOutline.setLocalTranslation(-outlinePadding / 2f, -outlinePadding / 2f, -0.05f);
        container.attachChild(panelOutline);

        panelFill = createGradientQuad(panelWidth, panelHeight,
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.18f), 0.9f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.12f), 0.92f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.38f), 0.95f),
                VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.22f), 0.93f));
        panelFill.setLocalTranslation(0, 0, 0);
        container.attachChild(panelFill);

        panelSheen = createGradientQuad(panelWidth, panelHeight,
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.06f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY, 0.08f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_SECONDARY, 0.16f),
                VaadinPalette.withAlpha(VaadinPalette.ACCENT_PRIMARY_SOFT, 0.1f));
        panelSheen.setLocalTranslation(0, 0, 0.02f);
        container.attachChild(panelSheen);

        Geometry topEdge = new Geometry("panel-top-edge", new Quad(panelWidth, 6f));
        Material edgeMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        edgeMat.setColor("Color", VaadinPalette.withAlpha(accentColor, 0.48f));
        topEdge.setMaterial(edgeMat);
        topEdge.setQueueBucket(RenderQueue.Bucket.Gui);
        topEdge.setLocalTranslation(0, panelHeight - 6f, 0.04f);
        container.attachChild(topEdge);

        return container;
    }

    private final class MenuButton extends Node {
        private final float width = 420f;
        private final float height = 74f;
        private final Runnable action;
        private final Geometry background;
        private final Geometry glow;
        private final Geometry shadow;
        private final BitmapText label;
        private final Geometry accentBar;
        private final BitmapText icon;
        private final Geometry selectionHalo;
        private final ColorRGBA baseTop = VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.35f), 0.96f);
        private final ColorRGBA baseBottom = VaadinPalette.withAlpha(VaadinPalette.elevateSurface(0.12f), 0.98f);
        private final ColorRGBA hoverTop = VaadinPalette.mix(baseTop, VaadinPalette.withAlpha(accentColor, baseTop.a), 0.45f);
        private final ColorRGBA hoverBottom = VaadinPalette.mix(baseBottom, VaadinPalette.withAlpha(accentColor, baseBottom.a), 0.45f);
        private final ColorRGBA pressTop = VaadinPalette.mix(baseTop, VaadinPalette.withAlpha(accentColor, baseTop.a), 0.7f);
        private final ColorRGBA pressBottom = VaadinPalette.mix(baseBottom, VaadinPalette.withAlpha(accentColor, baseBottom.a), 0.7f);
        private final ColorRGBA glowColor = VaadinPalette.withAlpha(accentColor.clone(), 0.7f);
        private final ColorRGBA labelBase = VaadinPalette.TEXT_HIGH.clone();
        private final ColorRGBA labelHighlight = VaadinPalette.TEXT_HIGH.clone();
        private final ColorRGBA accentBarColor = VaadinPalette.withAlpha(accentColor.clone(), 0.24f);
        private final VertexBuffer colorBuffer;
        private final Material glowMat;
        private final Material accentMat;
        private final Material selectionMat;

        private boolean hovered;
        private boolean pressed;
        private boolean selected;

        private MenuButton(String text, Runnable action) {
            this.action = action;

            shadow = new Geometry("shadow", new Quad(width + 18f, height + 18f));
            Material shadowMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            shadowMat.setColor("Color", VaadinPalette.withAlpha(VaadinPalette.TINT_SHADOW, 0.32f));
            shadow.setMaterial(shadowMat);
            shadow.setLocalTranslation(-9f, -9f, -0.25f);
            shadow.setQueueBucket(RenderQueue.Bucket.Gui);
            attachChild(shadow);

            selectionHalo = new Geometry("halo", new Quad(width + 12f, height + 12f));
            selectionMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            selectionMat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Additive);
            selectionMat.setColor("Color", VaadinPalette.withAlpha(accentColor, 0f));
            selectionHalo.setMaterial(selectionMat);
            selectionHalo.setQueueBucket(RenderQueue.Bucket.Gui);
            selectionHalo.setLocalTranslation(-6f, -6f, -0.1f);
            attachChild(selectionHalo);

            background = createGradientQuad(width, height, baseBottom, baseBottom, baseTop, baseTop);
            colorBuffer = background.getMesh().getBuffer(VertexBuffer.Type.Color);
            attachChild(background);

            glow = new Geometry("glow", new Quad(width, height));
            glowMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            glowColor.a = 0f;
            glowMat.setColor("Color", glowColor);
            glow.setMaterial(glowMat);
            glow.setQueueBucket(RenderQueue.Bucket.Gui);
            glow.setLocalTranslation(0, 0, 0.05f);
            attachChild(glow);

            icon = new BitmapText(smallFont, false);
            icon.setSize(30f);
            icon.setText("›");
            icon.setColor(VaadinPalette.withAlpha(accentColor, 0.92f));
            icon.setLocalTranslation(24f, (height + icon.getLineHeight()) / 2f - 2f, 0.1f);
            attachChild(icon);

            label = new BitmapText(smallFont, false);
            label.setSize(30);
            label.setText(text);
            label.setColor(labelBase);
            float textWidth = label.getLineWidth();
            float textHeight = label.getLineHeight();
            label.setLocalTranslation(64f + (width - 88f - textWidth) / 2f, (height + textHeight) / 2f, 0.1f);
            attachChild(label);

            accentBar = new Geometry("accent-bar", new Quad(width, 4f));
            accentMat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
            accentMat.setColor("Color", accentBarColor);
            accentBar.setMaterial(accentMat);
            accentBar.setQueueBucket(RenderQueue.Bucket.Gui);
            accentBar.setLocalTranslation(0, height - 6f, 0.11f);
            accentBar.setLocalScale(1f, 1.5f, 1f);
            attachChild(accentBar);
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
            updateGradient(top, bottom);
            float glowAlpha = selected ? 0.4f : hovered ? 0.25f : 0f;
            glowColor.a = glowAlpha;
            glowMat.setColor("Color", glowColor);
            accentBarColor.a = selected ? 0.95f : hovered ? 0.55f : 0.2f;
            accentMat.setColor("Color", accentBarColor);
            label.setColor((selected || hovered) ? labelHighlight : labelBase);
            icon.setColor((selected || hovered)
                    ? labelHighlight
                    : VaadinPalette.withAlpha(accentColor, 0.92f));
            float haloAlpha = selected ? 0.3f : hovered ? 0.16f : 0f;
            selectionMat.setColor("Color", VaadinPalette.withAlpha(accentColor, haloAlpha));
        }

        private void updateGradient(ColorRGBA top, ColorRGBA bottom) {
            if (colorBuffer == null) {
                return;
            }
            FloatBuffer buffer = (FloatBuffer) colorBuffer.getData();
            if (buffer == null) {
                return;
            }
            buffer.rewind();
            putColor(buffer, bottom);
            putColor(buffer, bottom);
            putColor(buffer, top);
            putColor(buffer, top);
            buffer.flip();
            colorBuffer.updateData(buffer);
        }

        private void putColor(FloatBuffer buffer, ColorRGBA color) {
            buffer.put(color.r).put(color.g).put(color.b).put(color.a);
        }
    }

    private static final class FloatingOrb {
        private final Geometry geom;
        private final Vector2f base;
        private final float amplitude;
        private final float speed;
        private final float phase;
        private final float scalePulse;

        private FloatingOrb(Geometry geom, Vector2f base, float amplitude, float speed, float phase, float scalePulse) {
            this.geom = geom;
            this.base = base;
            this.amplitude = amplitude;
            this.speed = speed;
            this.phase = phase;
            this.scalePulse = scalePulse;
        }

        private void update(float time) {
            float offset = FastMath.sin(time * speed + phase) * amplitude;
            float scale = 1f + FastMath.sin(time * speed * 1.2f + phase) * scalePulse;
            geom.setLocalTranslation(base.x, base.y + offset, geom.getLocalTranslation().z);
            geom.setLocalScale(scale);
        }
    }

    private void buildAccentLayers(float width, float height) {
        accentLayers.clear();
        addAccentLayer(width * 0.06f, height * 0.48f, width * 0.36f, height * 0.46f,
                VaadinPalette.ACCENT_PRIMARY_SOFT.clone(), 0.3f, 0.22f, 0.65f);
        addAccentLayer(width * 0.52f, height * 0.06f, width * 0.42f, height * 0.34f,
                VaadinPalette.mix(VaadinPalette.ACCENT_PRIMARY_SOFT, VaadinPalette.ACCENT_SECONDARY, 0.4f), 0.26f, 0.28f, 0.85f);
        addAccentLayer(width * 0.14f, height * 0.12f, width * 0.28f, height * 0.22f,
                VaadinPalette.mix(VaadinPalette.ACCENT_SECONDARY, VaadinPalette.TEXT_HIGH, 0.2f), 0.24f, 0.35f, 1.4f);
    }

    private void addAccentLayer(float x, float y, float w, float h, ColorRGBA baseColor, float baseAlpha, float amplitude, float speed) {
        Geometry geom = new Geometry("accent-layer", new Quad(w, h));
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        ColorRGBA color = baseColor.clone();
        color.a = baseAlpha;
        mat.setColor("Color", color);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        geom.setLocalTranslation(x, y, -0.35f);
        gui.attachChild(geom);
        accentLayers.add(new AccentLayer(geom, color, baseAlpha, amplitude, speed, FastMath.nextRandomFloat() * FastMath.TWO_PI));
    }

    private void updateAccentLayers() {
        for (AccentLayer layer : accentLayers) {
            float alpha = layer.baseAlpha + FastMath.sin((pulseTime * layer.speed) + layer.phase) * layer.amplitude;
            layer.color.a = FastMath.clamp(alpha, 0.05f, 0.85f);
            layer.geom.getMaterial().setColor("Color", layer.color);
        }
    }

    private void updatePanelSheen() {
        if (panelSheen == null) {
            return;
        }
        Material mat = panelSheen.getMaterial();
        if (mat == null || mat.getParam("Color") == null) {
            return;
        }
        tempColor.set((ColorRGBA) mat.getParam("Color").getValue());
        float pulse = 0.12f + (FastMath.sin(pulseTime * 1.4f) * 0.06f);
        tempColor.a = FastMath.clamp(pulse, 0.05f, 0.22f);
        mat.setColor("Color", tempColor);
        if (titleText != null) {
            float glow = 0.82f + 0.1f * FastMath.sin(pulseTime * 1.1f);
            titleColor.set(glow, 0.95f, 1f, 1f);
            titleText.setColor(titleColor);
        }
    }

    private Geometry createGradientQuad(float width, float height, ColorRGBA bottomLeft, ColorRGBA bottomRight, ColorRGBA topLeft, ColorRGBA topRight) {
        Mesh mesh = new Mesh();
        mesh.setBuffer(VertexBuffer.Type.Position, 3, new float[]{
                0f, 0f, 0f,
                width, 0f, 0f,
                0f, height, 0f,
                width, height, 0f
        });
        mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, new float[]{
                0f, 0f,
                1f, 0f,
                0f, 1f,
                1f, 1f
        });
        mesh.setBuffer(VertexBuffer.Type.Index, 3, new short[]{0, 1, 2, 2, 1, 3});
        FloatBuffer colors = BufferUtils.createFloatBuffer(16);
        putColor(colors, bottomLeft);
        putColor(colors, bottomRight);
        putColor(colors, topLeft);
        putColor(colors, topRight);
        colors.flip();
        mesh.setBuffer(VertexBuffer.Type.Color, 4, colors);
        mesh.updateBound();
        Geometry geom = new Geometry("gradient", mesh);
        Material mat = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        mat.setBoolean("VertexColor", true);
        mat.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        geom.setMaterial(mat);
        geom.setQueueBucket(RenderQueue.Bucket.Gui);
        return geom;
    }

    private void putColor(float[] array, int index, ColorRGBA color) {
        array[index] = color.r;
        array[index + 1] = color.g;
        array[index + 2] = color.b;
        array[index + 3] = color.a;
    }

    private void putColor(FloatBuffer buffer, ColorRGBA color) {
        buffer.put(color.r).put(color.g).put(color.b).put(color.a);
    }

    private static final class AccentLayer {
        final Geometry geom;
        final ColorRGBA color;
        final float baseAlpha;
        final float amplitude;
        final float speed;
        final float phase;

        AccentLayer(Geometry geom, ColorRGBA color, float baseAlpha, float amplitude, float speed, float phase) {
            this.geom = geom;
            this.color = color;
            this.baseAlpha = baseAlpha;
            this.amplitude = amplitude;
            this.speed = speed;
            this.phase = phase;
        }
    }
}
