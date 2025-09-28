package net.limitmedia.pong3d.engine;

import net.limitmedia.pong3d.audio.AmbientAudioEngine;
import net.limitmedia.pong3d.state.GameScreen;
import net.limitmedia.pong3d.state.MainMenuScreen;
import org.lwjgl.Version;
import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

public final class GameApplication implements Runnable {
    private static final int INITIAL_WIDTH = 1280;
    private static final int INITIAL_HEIGHT = 720;

    private long window;
    private int width = INITIAL_WIDTH;
    private int height = INITIAL_HEIGHT;
    private boolean running;

    private Screen currentScreen;
    private final Input input = new Input();
    private AmbientAudioEngine audioEngine;

    private double lastTime;

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
        setScreen(new MainMenuScreen(this));
    }

    public void requestExit() {
        running = false;
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
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_ANY_PROFILE);

        window = GLFW.glfwCreateWindow(width, height, "Pong Velocity", 0, 0);
        if (window == 0L) {
            throw new IllegalStateException("Failed to create GLFW window");
        }

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);

        GL.createCapabilities();

        audioEngine = new AmbientAudioEngine();

        input.register(window);
        GLFW.glfwSetFramebufferSizeCallback(window, (w, newWidth, newHeight) -> {
            width = Math.max(1, newWidth);
            height = Math.max(1, newHeight);
            GL11.glViewport(0, 0, width, height);
        });

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        setScreen(new MainMenuScreen(this));
        running = true;
        lastTime = GLFW.glfwGetTime();
    }

    private void loop() {
        while (running && !GLFW.glfwWindowShouldClose(window)) {
            double now = GLFW.glfwGetTime();
            float delta = (float) (now - lastTime);
            lastTime = now;

            input.beginFrame();

            if (currentScreen != null) {
                currentScreen.update(delta);
            }

            GL11.glClearColor(0f, 0f, 0f, 1f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0, width, height, 0.0, -1.0, 1.0);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glLoadIdentity();

            if (currentScreen != null) {
                currentScreen.render();
            }

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    private void shutdown() {
        if (currentScreen != null) {
            currentScreen.onExit();
            currentScreen = null;
        }
        if (audioEngine != null) {
            audioEngine.close();
            audioEngine = null;
        }
        Callbacks.glfwFreeCallbacks(window);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
    }
}
