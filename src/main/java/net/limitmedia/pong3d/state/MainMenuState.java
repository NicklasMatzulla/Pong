package net.limitmedia.pong3d.state;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Quad;

public class MainMenuState extends BaseAppState implements ActionListener {

    private final SimpleApplication app;
    private final Runnable onPlaySingle;
    private final Runnable onPlayOnline;
    private final Runnable onToggleFullscreen;
    private final Runnable onQuit;
    private final boolean isPauseMenu;

    private final Node gui = new Node("MainMenu");
    private BitmapText title, line1, line2, line3, line4, hint;

    public MainMenuState(SimpleApplication app, Runnable onPlaySingle, Runnable onPlayOnline, Runnable onToggleFullscreen, Runnable onQuit) {
        this(app, onPlaySingle, onPlayOnline, onToggleFullscreen, onQuit, false);
    }

    public MainMenuState(SimpleApplication app, Runnable onPlaySingle, Runnable onPlayOnline, Runnable onToggleFullscreen, Runnable onQuit, boolean isPauseMenu) {
        this.app = app;
        this.onPlaySingle = onPlaySingle;
        this.onPlayOnline = onPlayOnline;
        this.onToggleFullscreen = onToggleFullscreen;
        this.onQuit = onQuit;
        this.isPauseMenu = isPauseMenu;
    }

    @Override
    protected void initialize(Application ignored) {
        // Dimmer Background
        Quad q = new Quad(app.getCamera().getWidth(), app.getCamera().getHeight());
        Geometry bg = new Geometry("menu-bg", q);
        Material m = new Material(app.getAssetManager(), "Common/MatDefs/Misc/Unshaded.j3md");
        m.setColor("Color", new ColorRGBA(0,0,0,0.6f));
        bg.setMaterial(m);
        bg.setQueueBucket(RenderQueue.Bucket.Gui);
        bg.setLocalTranslation(0,0,0);
        gui.attachChild(bg);

        BitmapFont font = app.getAssetManager().loadFont("Interface/Fonts/Default.fnt");

        title = new BitmapText(font, false);
        title.setSize(36); title.setText(isPauseMenu ? "PAUSE" : "PONG 3D");
        centerX(title); title.setLocalTranslation(title.getLocalTranslation().x, app.getCamera().getHeight() - 120, 1);
        gui.attachChild(title);

        line1 = new BitmapText(font, false);
        line1.setSize(24); line1.setText(isPauseMenu ? "[1] Weiter" : "[1] Einzelspieler");
        centerX(line1); line1.setLocalTranslation(line1.getLocalTranslation().x, app.getCamera().getHeight() - 200, 1);
        gui.attachChild(line1);

        line2 = new BitmapText(font, false);
        line2.setSize(24);
        if (!isPauseMenu && onPlayOnline != null) {
            line2.setText("[2] Multiplayer verbinden");
        } else {
            line2.setText(isPauseMenu ? "[2] Vollbild umschalten" : "[2] Vollbild umschalten");
        }
        centerX(line2); line2.setLocalTranslation(line2.getLocalTranslation().x, app.getCamera().getHeight() - 240, 1);
        gui.attachChild(line2);

        line3 = new BitmapText(font, false);
        line3.setSize(24);
        if (!isPauseMenu && onPlayOnline != null) {
            line3.setText("[3] Vollbild umschalten");
        } else {
            line3.setText(isPauseMenu ? "[3] Beenden" : "[3] Beenden");
        }
        centerX(line3); line3.setLocalTranslation(line3.getLocalTranslation().x, app.getCamera().getHeight() - 280, 1);
        gui.attachChild(line3);

        if (!isPauseMenu && onPlayOnline != null) {
            line4 = new BitmapText(font, false);
            line4.setSize(24); line4.setText("[4] Beenden");
            centerX(line4); line4.setLocalTranslation(line4.getLocalTranslation().x, app.getCamera().getHeight() - 320, 1);
            gui.attachChild(line4);
        }

        hint = new BitmapText(font, false);
        hint.setSize(18);
        hint.setText(onPlayOnline != null && !isPauseMenu ? "A/D bewegen • ESC Pause • 1/2/3/4 Menü" : "A/D bewegen • ESC Pause • 1/2/3 Menü");
        centerX(hint); hint.setLocalTranslation(hint.getLocalTranslation().x, 60, 1);
        gui.attachChild(hint);

        app.getInputManager().addMapping("M_1", new KeyTrigger(KeyInput.KEY_1));
        app.getInputManager().addMapping("M_2", new KeyTrigger(KeyInput.KEY_2));
        app.getInputManager().addMapping("M_3", new KeyTrigger(KeyInput.KEY_3));
        if (!isPauseMenu && onPlayOnline != null) {
            app.getInputManager().addMapping("M_4", new KeyTrigger(KeyInput.KEY_4));
            app.getInputManager().addListener(this, "M_4");
        }
        app.getInputManager().addListener(this, "M_1", "M_2", "M_3");
    }

    private void centerX(BitmapText t) {
        float x = (app.getCamera().getWidth() - t.getLineWidth())/2f;
        t.setLocalTranslation(new Vector3f(x, t.getLocalTranslation().y, t.getLocalTranslation().z));
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if (isPressed) return;
        switch (name) {
            case "M_1":
                if (onPlaySingle != null) onPlaySingle.run();
                break;
            case "M_2":
                if (!isPauseMenu && onPlayOnline != null) {
                    onPlayOnline.run();
                } else if (onToggleFullscreen != null) {
                    onToggleFullscreen.run();
                }
                break;
            case "M_3":
                if (!isPauseMenu && onPlayOnline != null) {
                    if (onToggleFullscreen != null) onToggleFullscreen.run();
                } else if (onQuit != null) {
                    onQuit.run();
                }
                break;
            case "M_4":
                if (!isPauseMenu && onPlayOnline != null && onQuit != null) {
                    onQuit.run();
                }
                break;
        }
    }

    @Override
    protected void onEnable() {
        app.enqueue(() -> { app.getGuiNode().attachChild(gui); return null; });
    }

    @Override
    protected void onDisable() {
        app.enqueue(() -> { gui.removeFromParent(); return null; });
        app.getInputManager().deleteMapping("M_1");
        app.getInputManager().deleteMapping("M_2");
        app.getInputManager().deleteMapping("M_3");
        if (!isPauseMenu && onPlayOnline != null) {
            app.getInputManager().deleteMapping("M_4");
        }
    }

    @Override protected void cleanup(Application app) { }
}