package net.limitmedia.pong.client;

public final class ClientLauncher {
    private ClientLauncher() {
    }

    public static void main(String[] args) {
        PongClient app = new PongClient();
        app.start();
    }
}
