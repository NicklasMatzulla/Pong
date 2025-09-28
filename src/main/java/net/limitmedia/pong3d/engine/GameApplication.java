package net.limitmedia.pong3d.engine;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.network.NetworkClient;
import net.limitmedia.pong3d.network.NetworkServer;
import net.limitmedia.pong3d.state.GameScreen;
import net.limitmedia.pong3d.state.MainMenuScreen;
import org.lwjgl.Version;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;

public final class GameApplication implements Runnable {
    private static final int INITIAL_WIDTH = 1280;
    private static final int INITIAL_HEIGHT = 720;
    private static final int NETWORK_PORT = 5050;

    private long window;
    private int width = INITIAL_WIDTH;
    private int height = INITIAL_HEIGHT;
    private boolean running;

    private Screen currentScreen;
    private final Input input = new Input();
    private AmbientAudioEngine audioEngine;
    private NetworkServer hostedServer;

    private double lastTime;
    private GLFWErrorCallback errorCallback;

    public void run() {
        init();
        loop();
        shutdown();
    }

    public void setScreen(Screen screen) {
        if (currentScreen != null) {
            currentScreen.onExit();
        }
        currentScreen = screen;
        if (currentScreen != null) {
            currentScreen.onEnter();
        }
    }

    public void startSoloMatch() {
        setScreen(new GameScreen(this));
    }

    public void returnToMenu() {
        stopHostedServer();
        showMainMenu(null);
    }

    public void showMainMenu(String statusMessage) {
        setScreen(new MainMenuScreen(this, statusMessage));
    }

    public void requestExit() {
        running = false;
        if (window != MemoryUtil.NULL) {
            GLFW.glfwSetWindowShouldClose(window, true);
        }
    }

    public void startNetworkHost() {
        stopHostedServer();
        try {
            NetworkServer server = new NetworkServer(NETWORK_PORT);
            server.start();
            hostedServer = server;
            NetworkClient client = NetworkClient.connect("127.0.0.1", NETWORK_PORT);
            setScreen(new GameScreen(this, GameScreen.Mode.NETWORK, client, server));
        } catch (Exception ex) {
            stopHostedServer();
            showMainMenu("Serverstart fehlgeschlagen: " + ex.getMessage());
        }
    }

    public void startNetworkJoin(String host) {
        stopHostedServer();
        try {
            NetworkClient client = NetworkClient.connect(host, NETWORK_PORT);
            setScreen(new GameScreen(this, GameScreen.Mode.NETWORK, client, null));
        } catch (Exception ex) {
            showMainMenu("Verbindung fehlgeschlagen: " + ex.getMessage());
        }
    }

    private void stopHostedServer() {
        if (hostedServer != null) {
            hostedServer.close();
            hostedServer = null;
        }
    }

    public Input getInput() {
        return input;
    }

    public AmbientAudioEngine getAudioEngine() {
        return audioEngine;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void init() {
        System.out.println("Starting LWJGL " + Version.getVersion());
        errorCallback = GLFWErrorCallback.create((error, description) -> {
            String message = GLFWErrorCallback.getDescription(description);
            System.err.println("[GLFW] error " + error + ": " + message);
        });
        errorCallback.set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW. Prüfe GPU-Treiber oder installiere die Desktop-Windowing-Komponenten.");
        }

        window = createWindow(3, 3, true);
        if (window == MemoryUtil.NULL) {
            System.err.println("Konnte keinen OpenGL 3.3 Kontext erzeugen – weiche auf 2.1 Kompatibilitätsmodus aus.");
            window = createWindow(2, 1, false);
        }
        if (window == MemoryUtil.NULL) {
            throw new IllegalStateException("Failed to create GLFW window – OpenGL 2.1 Kontext nicht verfügbar.");
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);

        GL.createCapabilities();
        if (!org.lwjgl.opengl.GL.getCapabilities().OpenGL20) {
            throw new IllegalStateException("OpenGL 2.0 wird benötigt, aber nicht gefunden. Aktualisiere die Grafikkartentreiber.");
        }
        GL11.glViewport(0, 0, width, height);

        try {
            audioEngine = new AmbientAudioEngine();
        } catch (RuntimeException | UnsatisfiedLinkError ex) {
            System.err.println("Audio initialisation failed – continuing ohne Sound: " + ex.getMessage());
            audioEngine = null;
        }

        input.register(window);
        GLFW.glfwSetFramebufferSizeCallback(window, (w, newWidth, newHeight) -> {
            width = Math.max(1, newWidth);
            height = Math.max(1, newHeight);
            GL11.glViewport(0, 0, width, height);
        });

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        showMainMenu(null);
        running = true;
        lastTime = GLFW.glfwGetTime();
    }

    private long createWindow(int major, int minor, boolean requestCompatProfile) {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_DOUBLEBUFFER, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, major);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, minor);
        if (major >= 3 && requestCompatProfile) {
            if (Platform.get() == Platform.MACOSX) {
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            } else {
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_COMPAT_PROFILE);
            }
        } else {
            GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_ANY_PROFILE);
            if (Platform.get() == Platform.MACOSX) {
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
            }
        }
        return GLFW.glfwCreateWindow(width, height, "Pong Velocity", MemoryUtil.NULL, MemoryUtil.NULL);
    }

    private void loop() {
        while (running && !GLFW.glfwWindowShouldClose(window)) {
            double now = GLFW.glfwGetTime();
            float delta = (float) (now - lastTime);
            if (delta > 0.12f) {
                delta = 0.12f;
            }
            lastTime = now;

            input.beginFrame();
            GLFW.glfwPollEvents();

            if (audioEngine != null) {
                audioEngine.update();
            }

            if (currentScreen != null) {
                currentScreen.update(delta);
            }

            GL11.glClearColor(0f, 0f, 0f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);

            if (currentScreen != null) {
                currentScreen.render();
            }

            GLFW.glfwSwapBuffers(window);
        }
    }

    private void shutdown() {
        if (currentScreen != null) {
            currentScreen.onExit();
            currentScreen = null;
        }
        stopHostedServer();
        if (audioEngine != null) {
            audioEngine.close();
            audioEngine = null;
        }
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        if (errorCallback != null) {
            errorCallback.free();
            errorCallback = null;
        }
    }
}
