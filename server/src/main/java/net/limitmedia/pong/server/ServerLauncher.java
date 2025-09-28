package net.limitmedia.pong.server;

public final class ServerLauncher {
    private ServerLauncher() {
    }

    public static void main(String[] args) throws Exception {
        PongServer server = new PongServer(new ServerConfiguration());
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
