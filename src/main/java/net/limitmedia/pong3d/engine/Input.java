package net.limitmedia.pong3d.engine;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

import java.util.Arrays;

public final class Input {
    private final boolean[] keys = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private final boolean[] pressed = new boolean[GLFW.GLFW_KEY_LAST + 1];
    private final boolean[] released = new boolean[GLFW.GLFW_KEY_LAST + 1];

    private double mouseX;
    private double mouseY;
    private boolean mouseDown;
    private boolean mouseClicked;

    public void register(long window) {
        GLFW.glfwSetKeyCallback(window, keyCallback);
        GLFW.glfwSetCursorPosCallback(window, cursorCallback);
        GLFW.glfwSetMouseButtonCallback(window, mouseCallback);
    }

    public void beginFrame() {
        Arrays.fill(pressed, false);
        Arrays.fill(released, false);
        mouseClicked = false;
    }

    public boolean isKeyDown(int key) {
        return key >= 0 && key < keys.length && keys[key];
    }

    public boolean wasPressed(int key) {
        return key >= 0 && key < pressed.length && pressed[key];
    }

    public boolean wasReleased(int key) {
        return key >= 0 && key < released.length && released[key];
    }

    public double getMouseX() {
        return mouseX;
    }

    public double getMouseY() {
        return mouseY;
    }

    public boolean isMouseDown() {
        return mouseDown;
    }

    public boolean wasMouseClicked() {
        return mouseClicked;
    }

    private final GLFWKeyCallbackI keyCallback = (window, key, scancode, action, mods) -> {
        if (key < 0 || key >= keys.length) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            keys[key] = true;
            pressed[key] = true;
        } else if (action == GLFW.GLFW_RELEASE) {
            keys[key] = false;
            released[key] = true;
        }
    };

    private final GLFWCursorPosCallbackI cursorCallback = (window, xPos, yPos) -> {
        mouseX = xPos;
        mouseY = yPos;
    };

    private final GLFWMouseButtonCallbackI mouseCallback = (window, button, action, mods) -> {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            mouseDown = true;
        } else if (action == GLFW.GLFW_RELEASE) {
            mouseDown = false;
            mouseClicked = true;
        }
    };
}
