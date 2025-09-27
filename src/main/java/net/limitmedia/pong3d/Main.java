package net.limitmedia.pong3d;

import com.jme3.app.SimpleApplication;
import com.jme3.system.AppSettings;
import net.limitmedia.pong3d.state.GameState;
import net.limitmedia.pong3d.state.MainMenuState;
import net.limitmedia.pong3d.net.NetworkClient;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import java.io.IOException;

public class Main extends SimpleApplication {

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
            stateManager.attach(new MainMenuState(this, this::startSinglePlayerGame, this::startMultiplayerGame, this::quit));
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
            stateManager.attach(new MainMenuState(this, this::resumeGame, null, this::quit, true));
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
    private void quit() {
        enqueue(() -> { stop(); return null; });
    }
}