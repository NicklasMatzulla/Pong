package net.limitmedia.pong3d.engine;

public interface Screen {
    void onEnter();

    void onExit();

    void update(float deltaTime);

    void render();
}
