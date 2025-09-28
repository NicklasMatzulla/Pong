package net.limitmedia.pong3d.engine;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;

public final class Input {
    private static final int KEY_COUNT = GLFW.GLFW_KEY_LAST + 1;

    private final boolean[] keys = new boolean[KEY_COUNT];
    private final boolean[] pressed = new boolean[KEY_COUNT];
    private final boolean[] released = new boolean[KEY_COUNT];
    private final int[] dirtyKeys = new int[KEY_COUNT];
    private int dirtyCount;

    private double mouseX;
    private double mouseY;
    private boolean mouseDown;
    private boolean mouseClicked;
    private final StringBuilder typedChars = new StringBuilder();

    public void register(long window) {
        GLFW.glfwSetKeyCallback(window, keyCallback);
        GLFW.glfwSetCursorPosCallback(window, cursorCallback);
        GLFW.glfwSetMouseButtonCallback(window, mouseCallback);
        GLFW.glfwSetCharCallback(window, charCallback);
    }

    public void beginFrame() {
        for (int i = 0; i < dirtyCount; i++) {
            int key = dirtyKeys[i];
            pressed[key] = false;
            released[key] = false;
        }
        dirtyCount = 0;
        mouseClicked = false;
        typedChars.setLength(0);
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

    public String consumeTypedChars() {
        String result = typedChars.toString();
        typedChars.setLength(0);
        return result;
    }

    private final GLFWKeyCallbackI keyCallback = (window, key, scancode, action, mods) -> {
        if (key < 0 || key >= KEY_COUNT) {
            return;
        }
        if (action == GLFW.GLFW_PRESS) {
            keys[key] = true;
            pressed[key] = true;
            markDirty(key);
        } else if (action == GLFW.GLFW_RELEASE) {
            keys[key] = false;
            released[key] = true;
            markDirty(key);
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

    private final GLFWCharCallbackI charCallback = (window, codepoint) -> {
        if (codepoint == 0L) {
            return;
        }
        if (Character.isISOControl((int) codepoint)) {
            return;
        }
        typedChars.appendCodePoint((int) codepoint);
    };

    private void markDirty(int key) {
        for (int i = 0; i < dirtyCount; i++) {
            if (dirtyKeys[i] == key) {
                return;
            }
        }
        dirtyKeys[dirtyCount++] = key;
    }
}
