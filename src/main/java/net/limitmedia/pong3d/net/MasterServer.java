package net.limitmedia.pong3d.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple master server that pairs clients and simulates a Pong match. The server keeps authoritative
 * state for the ball and both paddles and broadcasts the world state to both players ~60 times per second.
 *
 * <p>Protocol:</p>
 * <ul>
 *   <li>Client connects and sends: {@code HELLO <name>}</li>
 *   <li>Server responds with {@code WAITING} until a match is available.</li>
 *   <li>When a second player joins the queue the server sends {@code START FRONT} to one client and
 *       {@code START BACK} to the other.</li>
 *   <li>During the game the client can send {@code MOVE -1|0|1}. The server sends {@code STATE <time> <frontX> <backX> <ballX> <ballZ> <scoreFront> <scoreBack>}.</li>
 *   <li>On disconnect the server sends {@code END <reason>} to the remaining peer.</li>
 * </ul>
 */
public final class MasterServer implements AutoCloseable {

    public static void main(String[] args) throws Exception {
        int port = 6000;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        try (MasterServer server = new MasterServer(port)) {
            System.out.println("[MasterServer] Listening on port " + port);
            server.start();
        }
    }

    private final ServerSocket serverSocket;
    private final ExecutorService acceptPool = Executors.newCachedThreadPool();
    private final List<ClientConnection> waitingClients = new CopyOnWriteArrayList<>();
    private final List<GameSession> runningSessions = new CopyOnWriteArrayList<>();

    public MasterServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setSoTimeout(1000);
    }

    public void start() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                acceptPool.submit(() -> handleNewClient(socket));
            } catch (SocketTimeoutException ignore) {
                // periodic check whether the socket has been closed
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
            cleanupSessions();
        }
    }

    private void handleNewClient(Socket socket) {
        try {
            ClientConnection client = new ClientConnection(socket);
            System.out.println("[MasterServer] New connection from " + socket.getRemoteSocketAddress());
            client.send("WELCOME " + Instant.now().toString());

            String hello = client.reader.readLine();
            if (hello == null || !hello.startsWith("HELLO")) {
                client.send("END bad-handshake");
                client.close();
                return;
            }

            client.name = hello.length() > 6 ? hello.substring(6) : "Anonymous";
            client.send("WAITING");
            client.startReadLoop();
            waitingClients.add(client);
            tryStartMatch();
        } catch (IOException e) {
            System.err.println("[MasterServer] Failed to accept client: " + e.getMessage());
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private synchronized void tryStartMatch() {
        if (waitingClients.size() < 2) {
            return;
        }
        ClientConnection front = waitingClients.remove(0);
        ClientConnection back = waitingClients.remove(0);
        GameSession session = new GameSession(front, back);
        runningSessions.add(session);
        acceptPool.submit(session);
    }

    private void cleanupSessions() {
        runningSessions.removeIf(GameSession::isFinished);
    }

    @Override
    public void close() throws IOException {
        for (GameSession session : new ArrayList<>(runningSessions)) {
            session.stop();
        }
        runningSessions.clear();
        for (ClientConnection client : waitingClients) {
            client.send("END shutdown");
            client.close();
        }
        waitingClients.clear();
        serverSocket.close();
        acceptPool.shutdownNow();
        try {
            if (!acceptPool.awaitTermination(2, TimeUnit.SECONDS)) {
                acceptPool.shutdownNow();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class GameSession implements Runnable {
        private static final float HALF_WIDTH = 8f;
        private static final float HALF_DEPTH = 4.5f;
        private static final float PADDLE_SPEED = 12f;
        private static final float BALL_RADIUS = 0.25f;
        private static final float MAX_BALL_SPEED = 22f;
        private static final float MIN_BALL_SPEED = 4f;
        private static final float TICK_RATE = 1f / 60f;

        private final ClientConnection front;
        private final ClientConnection back;
        private volatile boolean running = true;
        private final AtomicInteger frontDir = new AtomicInteger();
        private final AtomicInteger backDir = new AtomicInteger();

        private float frontX = 0f;
        private float backX = 0f;
        private float ballX = 0f;
        private float ballZ = 0f;
        private float velX = 5f;
        private float velZ = -6f;
        private int scoreFront = 0;
        private int scoreBack = 0;

        GameSession(ClientConnection front, ClientConnection back) {
            this.front = front;
            this.back = back;
            front.role = Role.FRONT;
            back.role = Role.BACK;
            front.setSession(this, frontDir);
            back.setSession(this, backDir);
        }

        @Override
        public void run() {
            System.out.println("[MasterServer] Starting match: " + front.name + " vs " + back.name);
            front.send("START FRONT");
            back.send("START BACK");
            broadcastState(System.nanoTime());
            long last = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                float dt = (now - last) / 1_000_000_000f;
                if (dt < TICK_RATE) {
                    try {
                        Thread.sleep(Math.max(0, (long)((TICK_RATE - dt) * 1000))); // coarse sleep
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    now = System.nanoTime();
                    dt = (now - last) / 1_000_000_000f;
                }
                last = now;
                update(dt);
                broadcastState(now);
            }
            endSession("ended");
        }

        private void update(float tpf) {
            float dirFront = frontDir.get();
            float dirBack = backDir.get();

            frontX += dirFront * PADDLE_SPEED * tpf;
            backX += dirBack * PADDLE_SPEED * tpf;
            frontX = clamp(frontX, -HALF_WIDTH + 1.8f, HALF_WIDTH - 1.8f);
            backX = clamp(backX, -HALF_WIDTH + 1.8f, HALF_WIDTH - 1.8f);

            ballX += velX * tpf;
            ballZ += velZ * tpf;

            if (ballX > HALF_WIDTH - BALL_RADIUS && velX > 0) velX *= -1;
            if (ballX < -HALF_WIDTH + BALL_RADIUS && velX < 0) velX *= -1;

            if (ballZ > (HALF_DEPTH - 0.6f) && velZ > 0) {
                if (Math.abs(ballX - frontX) <= 1.4f) {
                    velZ *= -1.07f;
                    velX += (ballX - frontX) * 1.0f;
                    limitBallSpeed();
                }
            }
            if (ballZ < (-HALF_DEPTH + 0.6f) && velZ < 0) {
                if (Math.abs(ballX - backX) <= 1.4f) {
                    velZ *= -1.07f;
                    velX += (ballX - backX) * 1.0f;
                    limitBallSpeed();
                }
            }

            if (ballZ > HALF_DEPTH + 0.35f) {
                scoreBack++;
                resetBall(false);
            } else if (ballZ < -HALF_DEPTH - 0.35f) {
                scoreFront++;
                resetBall(true);
            }
        }

        private void broadcastState(long timestamp) {
            String msg = String.format(java.util.Locale.US,
                    "STATE %d %.4f %.4f %.4f %.4f %d %d",
                    timestamp, frontX, backX, ballX, ballZ, scoreFront, scoreBack);
            if (!front.send(msg)) {
                stopWithReason(front, back, "front-disconnected");
            }
            if (!back.send(msg)) {
                stopWithReason(back, front, "back-disconnected");
            }
        }

        private void stopWithReason(ClientConnection failed, ClientConnection other, String reason) {
            running = false;
            other.send("END " + reason);
            failed.close();
            other.close();
        }

        private void resetBall(boolean towardsBack) {
            ballX = 0f;
            ballZ = 0f;
            velX = 0f;
            velZ = towardsBack ? -6f : 6f;
        }

        private void limitBallSpeed() {
            float absX = Math.abs(velX);
            float absZ = Math.abs(velZ);
            if (absX < MIN_BALL_SPEED) velX = Math.copySign(MIN_BALL_SPEED, velX);
            if (absZ < MIN_BALL_SPEED) velZ = Math.copySign(MIN_BALL_SPEED, velZ);
            if (absX > MAX_BALL_SPEED) velX = Math.copySign(MAX_BALL_SPEED, velX);
            if (absZ > MAX_BALL_SPEED) velZ = Math.copySign(MAX_BALL_SPEED, velZ);
        }

        private float clamp(float v, float min, float max) {
            return Math.max(min, Math.min(max, v));
        }

        void onMove(ClientConnection client, int direction) {
            if (client == front) {
                frontDir.set(direction);
            } else if (client == back) {
                backDir.set(direction);
            }
        }

        void onDisconnect(ClientConnection client) {
            if (!running) {
                return;
            }
            running = false;
            ClientConnection other = client == front ? back : front;
            other.send("END opponent-left");
            other.close();
        }

        boolean isFinished() {
            return !running;
        }

        void stop() {
            running = false;
            front.close();
            back.close();
        }

        private void endSession(String reason) {
            front.send("END " + reason);
            back.send("END " + reason);
            front.close();
            back.close();
        }
    }

    private static final class ClientConnection implements AutoCloseable {
        private final Socket socket;
        private final PrintWriter writer;
        private final BufferedReader reader;
        private volatile boolean closed;
        private volatile Role role = Role.FRONT;
        private volatile GameSession session;
        private volatile AtomicInteger direction;
        private String name = "Anonymous";

        ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.writer = new PrintWriter(socket.getOutputStream(), true);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        }

        void setSession(GameSession session, AtomicInteger direction) {
            this.session = session;
            this.direction = direction;
        }

        void startReadLoop() {
            Thread t = new Thread(() -> {
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        handleLine(line.trim());
                    }
                } catch (IOException ignored) {
                } finally {
                    closed = true;
                    if (session != null) {
                        session.onDisconnect(this);
                    }
                    close();
                }
            }, "ClientReader-" + socket.getRemoteSocketAddress());
            t.setDaemon(true);
            t.start();
        }

        private void handleLine(String line) {
            if (line.isEmpty()) {
                return;
            }
            if (line.startsWith("MOVE")) {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    try {
                        int dir = Integer.parseInt(parts[1]);
                        dir = Math.max(-1, Math.min(1, dir));
                        if (direction != null) {
                            direction.set(dir);
                        }
                        if (session != null) {
                            session.onMove(this, dir);
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else if (line.equals("PING")) {
                send("PONG");
            } else if (line.startsWith("QUIT")) {
                close();
            }
        }

        boolean send(String msg) {
            if (closed) return false;
            writer.println(Objects.requireNonNull(msg));
            return !writer.checkError();
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public enum Role { FRONT, BACK }
}
