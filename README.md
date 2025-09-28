# Pong Velocity

A modular 3D Pong prototype built with jMonkeyEngine, JavaFX, Netty, and OpenAL. The project is split into `core`, `client`, and `server` modules so gameplay, rendering, and networking concerns remain isolated and testable.

## Build & Run

```bash
./gradlew clean build
./gradlew runServer    # launches the dedicated server
./gradlew runClient    # launches the LWJGL client
```

## Known Problems & Fixes

| Problem | Fix |
| --- | --- |
| GLFW/OpenGL context fails on older drivers | Set the driver to support OpenGL 3.3 or let the fallback request 2.1; the client logs an explicit error instead of crashing. |
| Audio assets missing on startup | Audio nodes now fail gracefully and log the missing resource without crashing the game. |
| Multiplayer client waits forever on match start | The master server emits a state broadcast as soon as a match opens so clients leave the waiting screen immediately. |
| Camera shake felt disorienting | Shake intensity is clamped by accessibility settings, and the rig resets automatically when the impulse fades. |

## Testing

Unit tests cover physics rebound behaviour, lag compensation, audio mixing, and the new camera rig helper. Run all tests with:

```bash
./gradlew test
```
