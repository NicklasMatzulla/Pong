package net.limitmedia.pong3d;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import net.limitmedia.pong3d.state.GameState;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import net.limitmedia.pong3d.state.MainMenuState;
import net.limitmedia.pong3d.net.NetworkClient;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import java.io.IOException;

public class Main extends SimpleApplication {

    private final boolean fullscreen = false;

    public static void main(String[] args) {
        AppSettings s = new AppSettings(true);
        s.setTitle("Pong 3D");
        s.setVSync(true);
        s.setSamples(4);
        s.setResolution(1280, 720);
        Main app = new Main();
        app.setSettings(s);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        flyCam.setEnabled(true);
        flyCam.setMoveSpeed(20f);
        flyCam.setRotationSpeed(2.5f);
        inputManager.setCursorVisible(true);
        setPauseOnLostFocus(false);
        // Menü anhängen im Renderthread
        enqueue(() -> {
            stateManager.attach(new MainMenuState(this, this::startSinglePlayerGame, this::startMultiplayerGame, this::toggleFullscreen, this::quit));
            return null;
        });
    }

    private void startSinglePlayerGame() {
        enqueue(() -> {
            detachMenu();
            if (stateManager.getState(GameState.class) == null) {
                attachGameState(new GameState(this, this::openPauseMenu));
            }
            return null;
        });
    }

    private void startMultiplayerGame() {
        String target = JOptionPane.showInputDialog(null, "Server (host:port)", System.getProperty("pong.server", "localhost:6000"));
        if (target == null || target.isBlank()) {
            return;
        }
        String host = target;
        int port = 6000;
        int idx = target.lastIndexOf(':');
        if (idx > 0 && idx < target.length() - 1) {
            host = target.substring(0, idx);
            try {
                port = Integer.parseInt(target.substring(idx + 1));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(null, "Ungültiger Port: " + target.substring(idx + 1));
                return;
            }
        }
        String playerName = System.getProperty("user.name", "Spieler");
        final String connectHost = host;
        final int connectPort = port;
        final String connectName = playerName;
        new Thread(() -> {
            try {
                NetworkClient client = new NetworkClient(connectHost, connectPort, connectName);
                enqueue(() -> {
                    detachMenu();
                    if (stateManager.getState(GameState.class) == null) {
                        attachGameState(new GameState(this, this::openPauseMenu, client));
                    }
                    return null;
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Verbindung fehlgeschlagen: " + e.getMessage()));
            }
        }, "Pong-Connect").start();
    }

    private void openPauseMenu() {
        if (stateManager.getState(MainMenuState.class) == null) {
            stateManager.attach(new MainMenuState(this, this::resumeGame, null, this::toggleFullscreen, this::quit, true));
        }
    }

    private void attachGameState(GameState gameState) {
        stateManager.attach(gameState);
    }

    private void detachMenu() {
        MainMenuState menu = stateManager.getState(MainMenuState.class);
        if (menu != null) {
            stateManager.detach(menu);
        }
    }

    private void resumeGame() {
        enqueue(() -> {
            GameState gs = stateManager.getState(GameState.class);
            if (gs != null) gs.resume();
            MainMenuState menu = stateManager.getState(MainMenuState.class);
            if (menu != null) stateManager.detach(menu);
            return null;
        });
    }
    private int[] getDesktopResolution() {
        // GLFW: echte Monitorauflösung
        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor != 0L) {
            GLFWVidMode mode = GLFW.glfwGetVideoMode(monitor);
            if (mode != null) {
                return new int[]{ mode.width(), mode.height(), mode.refreshRate() };
            }
        }
        // Fallback: AWT
        try {
            java.awt.DisplayMode dm = java.awt.GraphicsEnvironment
                    .getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDisplayMode();
            return new int[]{ dm.getWidth(), dm.getHeight(), Math.max(dm.getRefreshRate(), 60) };
        } catch (Throwable ignored) { }
        return new int[]{ 1280, 720, 60 };
    }

    private void toggleFullscreen() {
        enqueue(() -> {
            AppSettings s = (AppSettings) getContext().getSettings().clone();

            boolean toFullscreen = !s.isFullscreen();
            int[] desk = getDesktopResolution();

            if (toFullscreen) {
                // native Auflösung + Refresh
                s.setResolution(desk[0], desk[1]);
                s.setFrequency(desk[2]);
                s.setFullscreen(true);
            } else {
                // Windowed: 80% der Auflösung, mindestens 1280x720
                int w = Math.max(1280, (int)(desk[0] * 0.8));
                int h = Math.max(720,  (int)(desk[1] * 0.8));
                s.setFullscreen(false);
                s.setResolution(w, h);
            }

            setSettings(s);
            restart();
            return null;
        });
    }

    private void quit() {
        enqueue(() -> { stop(); return null; });
    }
}