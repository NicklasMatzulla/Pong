package net.limitmedia.pong.client.input;

import com.jme3.input.InputManager;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.AnalogListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseAxisTrigger;
import com.jme3.math.FastMath;
import net.limitmedia.pong.core.physics.ArenaDimensions;
import net.limitmedia.pong.core.physics.PaddleState;

/**
 * Encapsulates mouse to paddle mapping so input lifecycle can be managed and
 * unit tested independently from the {@code SimpleApplication}.
 */
public final class MousePaddleInput implements AnalogListener, ActionListener, AutoCloseable {
    private final InputManager inputManager;
    private final PaddleState paddle;
    private final ArenaDimensions arena;
    private final Runnable onPause;

    public MousePaddleInput(InputManager inputManager, PaddleState paddle, ArenaDimensions arena, Runnable onPause) {
        this.inputManager = inputManager;
        this.paddle = paddle;
        this.arena = arena;
        this.onPause = onPause;
    }

    public void register() {
        inputManager.addMapping("MoveLeft", new MouseAxisTrigger(MouseInput.AXIS_X, true));
        inputManager.addMapping("MoveRight", new MouseAxisTrigger(MouseInput.AXIS_X, false));
        inputManager.addMapping("MoveUp", new MouseAxisTrigger(MouseInput.AXIS_Y, true));
        inputManager.addMapping("MoveDown", new MouseAxisTrigger(MouseInput.AXIS_Y, false));
        inputManager.addMapping("Pause", new KeyTrigger(com.jme3.input.KeyInput.KEY_ESCAPE));
        inputManager.addListener(this, "MoveLeft", "MoveRight", "MoveUp", "MoveDown");
        inputManager.addListener(this, "Pause");
    }

    @Override
    public void onAnalog(String name, float value, float tpf) {
        float sensitivity = 18f;
        switch (name) {
            case "MoveLeft" -> paddle.position().addLocal(-value * sensitivity, 0, 0);
            case "MoveRight" -> paddle.position().addLocal(value * sensitivity, 0, 0);
            case "MoveUp" -> paddle.position().addLocal(0, value * sensitivity, 0);
            case "MoveDown" -> paddle.position().addLocal(0, -value * sensitivity, 0);
            default -> {
                return;
            }
        }
        clampPaddle();
    }

    @Override
    public void onAction(String name, boolean isPressed, float tpf) {
        if ("Pause".equals(name) && isPressed) {
            onPause.run();
        }
    }

    private void clampPaddle() {
        float maxX = arena.width() / 2f - paddle.halfWidth();
        float maxY = arena.height() / 2f - paddle.halfHeight();
        paddle.position().x = FastMath.clamp(paddle.position().x, -maxX, maxX);
        paddle.position().y = FastMath.clamp(paddle.position().y, -maxY, maxY);
    }

    @Override
    public void close() {
        inputManager.deleteMapping("MoveLeft");
        inputManager.deleteMapping("MoveRight");
        inputManager.deleteMapping("MoveUp");
        inputManager.deleteMapping("MoveDown");
        inputManager.deleteMapping("Pause");
        inputManager.removeListener(this);
    }
}
