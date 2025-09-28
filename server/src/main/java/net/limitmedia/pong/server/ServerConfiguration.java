package net.limitmedia.pong.server;

import java.nio.file.Path;

public final class ServerConfiguration {
    private int port = 5050;
    private int tickRate = 120;
    private Path persistenceFile = Path.of("server-state.json");

    public int port() {
        return port;
    }

    public int tickRate() {
        return tickRate;
    }

    public Path persistenceFile() {
        return persistenceFile;
    }
}
