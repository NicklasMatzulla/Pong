package net.limitmedia.pong3d.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight client to talk to the {@link MasterServer}. It keeps the last world state received
 * from the server and exposes helper methods for the game to query.
 */
public final class NetworkClient implements AutoCloseable {

    public enum Role { FRONT, BACK }

    public record WorldState(long timestamp, float frontX, float backX, float ballX, float ballZ,
                              int scoreFront, int scoreBack) {}

    private final Socket socket;
    private final PrintWriter writer;
    private final BufferedReader reader;
    private final AtomicReference<WorldState> worldState = new AtomicReference<>();
    private volatile Role role = Role.FRONT;
    private volatile boolean running = true;
    private final AtomicReference<String> serverStatus = new AtomicReference<>("Verbinde...");

    public NetworkClient(String host, int port, String name) throws IOException {
        this.socket = new Socket(host, port);
        this.writer = new PrintWriter(socket.getOutputStream(), true);
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer.println("HELLO " + name);
        startReadLoop();
    }

    private void startReadLoop() {
        Thread t = new Thread(() -> {
            try {
                String line;
                while (running && (line = reader.readLine()) != null) {
                    handleLine(line.trim());
                }
            } catch (IOException ignored) {
            } finally {
                running = false;
            }
        }, "PongClient-Reader");
        t.setDaemon(true);
        t.start();
    }

    private void handleLine(String line) {
        if (line.isEmpty()) return;
        if (line.startsWith("STATE")) {
            String[] parts = line.split(" ");
            if (parts.length == 8) {
                try {
                    long time = Long.parseLong(parts[1]);
                    float frontX = Float.parseFloat(parts[2]);
                    float backX = Float.parseFloat(parts[3]);
                    float ballX = Float.parseFloat(parts[4]);
                    float ballZ = Float.parseFloat(parts[5]);
                    int scoreFront = Integer.parseInt(parts[6]);
                    int scoreBack = Integer.parseInt(parts[7]);
                    worldState.set(new WorldState(time, frontX, backX, ballX, ballZ, scoreFront, scoreBack));
                } catch (NumberFormatException ignored) {
                }
            }
            serverStatus.compareAndSet("Suche nach Mitspielern...", "Match gefunden");
        } else if (line.startsWith("WAITING")) {
            serverStatus.set("Suche nach Mitspielern...");
        } else if (line.startsWith("START")) {
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                if ("FRONT".equalsIgnoreCase(parts[1])) {
                    role = Role.FRONT;
                } else if ("BACK".equalsIgnoreCase(parts[1])) {
                    role = Role.BACK;
                }
            }
            if (role == Role.FRONT) {
                serverStatus.set("Match gefunden • Du spielst vorne");
            } else {
                serverStatus.set("Match gefunden • Du spielst hinten");
            }
        } else if (line.startsWith("END")) {
            running = false;
            String reason = line.length() > 4 ? line.substring(4).trim() : "";
            if (reason.isEmpty()) {
                serverStatus.set("Match beendet");
            } else {
                serverStatus.set("Verbindung beendet: " + reason);
            }
        }
    }

    public Role getRole() {
        return role;
    }

    public WorldState getWorldState() {
        return worldState.get();
    }

    public boolean isRunning() {
        return running;
    }

    public void sendDirection(int direction) {
        int dir = Math.max(-1, Math.min(1, direction));
        writer.println("MOVE " + dir);
    }

    public String getServerStatus() {
        return serverStatus.get();
    }

    @Override
    public void close() {
        running = false;
        try {
            writer.println("QUIT");
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
