package net.limitmedia.pong3d.network;

import net.limitmedia.pong3d.game.MatchFrame;
import net.limitmedia.pong3d.game.MatchSimulation;
import net.limitmedia.pong3d.game.MatchState;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetworkServer implements Closeable {
    private static final byte MSG_HANDSHAKE = 1;
    private static final byte MSG_SNAPSHOT = 2;
    private static final byte MSG_INPUT = 3;

    private final int port;
    private final MatchSimulation simulation = new MatchSimulation();
    private final AtomicBoolean running = new AtomicBoolean();
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread simulationThread;

    private final ClientHandler[] clients = new ClientHandler[2];
    private final float[] axes = new float[2];
    private final boolean[] serveBoost = new boolean[2];

    public NetworkServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (running.get()) {
            return;
        }
        serverSocket = new ServerSocket(port);
        running.set(true);
        acceptThread = new Thread(this::acceptLoop, "NetworkServer-Accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        simulationThread = new Thread(this::simulationLoop, "NetworkServer-Sim");
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                int slot = assignClient(socket);
                if (slot >= 0) {
                    ClientHandler handler = new ClientHandler(slot, socket);
                    synchronized (clients) {
                        clients[slot] = handler;
                    }
                    handler.start();
                    sendHandshake(handler);
                    if (bothConnected()) {
                        simulation.reset(true);
                        synchronized (axes) {
                            axes[0] = 0f;
                            axes[1] = 0f;
                        }
                        synchronized (serveBoost) {
                            serveBoost[0] = false;
                            serveBoost[1] = false;
                        }
                    }
                } else {
                    socket.close();
                }
            } catch (SocketException ex) {
                if (running.get()) {
                    ex.printStackTrace();
                }
                break;
            } catch (IOException ex) {
                if (running.get()) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private int assignClient(Socket socket) {
        synchronized (clients) {
            for (int i = 0; i < clients.length; i++) {
                if (clients[i] == null || !clients[i].isRunning()) {
                    clients[i] = null;
                    synchronized (axes) {
                        axes[i] = 0f;
                    }
                    synchronized (serveBoost) {
                        serveBoost[i] = false;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    private void sendHandshake(ClientHandler handler) {
        try {
            handler.output.writeByte(MSG_HANDSHAKE);
            handler.output.writeInt(handler.index);
            handler.output.flush();
        } catch (IOException ex) {
            handler.shutdown();
        }
    }

    private boolean bothConnected() {
        synchronized (clients) {
            return clients[0] != null && clients[1] != null && clients[0].isRunning() && clients[1].isRunning();
        }
    }

    private void simulationLoop() {
        long lastTime = System.nanoTime();
        while (running.get()) {
            boolean active = bothConnected();
            long now = System.nanoTime();
            float delta = (now - lastTime) / 1_000_000_000f;
            if (delta > 0.05f) {
                delta = 0.05f;
            }
            if (active) {
                float playerAxis;
                float opponentAxis;
                synchronized (axes) {
                    playerAxis = axes[0];
                    opponentAxis = axes[1];
                }
                MatchFrame step = simulation.update(delta, playerAxis, opponentAxis, consumeServeBoost(0), consumeServeBoost(1));
                broadcast(step);
            }
            lastTime = now;
            try {
                Thread.sleep(active ? 6L : 20L);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private boolean consumeServeBoost(int index) {
        synchronized (serveBoost) {
            boolean boost = serveBoost[index];
            serveBoost[index] = false;
            return boost;
        }
    }

    private void broadcast(MatchFrame matchFrame) {
        MatchState state = matchFrame.getState();
        synchronized (clients) {
            for (ClientHandler handler : clients) {
                if (handler != null && handler.isRunning()) {
                    try {
                        DataOutputStream out = handler.output;
                        out.writeByte(MSG_SNAPSHOT);
                        out.writeInt(matchFrame.getTick());
                        out.writeFloat(state.playerX);
                        out.writeFloat(state.opponentX);
                        out.writeFloat(state.ballX);
                        out.writeFloat(state.ballY);
                        out.writeFloat(state.ballVX);
                        out.writeFloat(state.ballVY);
                        out.writeFloat(state.ballSpeed);
                        out.writeInt(state.playerScore);
                        out.writeInt(state.opponentScore);
                        out.writeInt(state.rallyCount);
                        out.writeInt(state.bestRally);
                        out.writeBoolean(state.waitingForServe);
                        out.writeBoolean(state.playerServeTurn);
                        out.writeFloat(state.serveCountdown);
                        out.writeBoolean(state.matchOver);
                        out.writeBoolean(state.playerWon);
                        out.writeFloat(state.matchTime);
                        out.writeBoolean(matchFrame.isPlayerPaddleImpact());
                        out.writeBoolean(matchFrame.isOpponentPaddleImpact());
                        out.writeBoolean(matchFrame.isWallImpact());
                        out.writeBoolean(matchFrame.isGoalScored());
                        out.writeBoolean(matchFrame.isPlayerScored());
                        out.writeBoolean(matchFrame.isCountdownChanged());
                        out.writeInt(matchFrame.getCountdownValue());
                        out.flush();
                    } catch (IOException ex) {
                        handler.shutdown();
                    }
                }
            }
        }
    }

    @Override
    public void close() {
        if (!running.getAndSet(false)) {
            return;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        synchronized (clients) {
            for (int i = 0; i < clients.length; i++) {
                if (clients[i] != null) {
                    clients[i].shutdown();
                    clients[i] = null;
                }
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        if (simulationThread != null) {
            simulationThread.interrupt();
        }
    }

    private final class ClientHandler implements Runnable {
        private final int index;
        private final Socket socket;
        private final DataInputStream input;
        private final DataOutputStream output;
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private Thread thread;

        private ClientHandler(int index, Socket socket) throws IOException {
            this.index = index;
            this.socket = socket;
            this.input = new DataInputStream(socket.getInputStream());
            this.output = new DataOutputStream(socket.getOutputStream());
        }

        private void start() {
            thread = new Thread(this, "NetworkServer-Client-" + index);
            thread.setDaemon(true);
            thread.start();
        }

        @Override
        public void run() {
            try {
                while (alive.get() && running.get()) {
                    byte type = input.readByte();
                    if (type == MSG_INPUT) {
                        float axis = input.readFloat();
                        boolean boost = input.readBoolean();
                        synchronized (axes) {
                            axes[index] = axis;
                        }
                        synchronized (serveBoost) {
                            serveBoost[index] = serveBoost[index] || boost;
                        }
                    }
                }
            } catch (IOException ex) {
                // connection lost
            } finally {
                shutdown();
            }
        }

        private boolean isRunning() {
            return alive.get();
        }

        private void shutdown() {
            if (!alive.getAndSet(false)) {
                return;
            }
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            try {
                input.close();
            } catch (IOException ignored) {
            }
            try {
                output.close();
            } catch (IOException ignored) {
            }
            synchronized (clients) {
                if (clients[index] == this) {
                    clients[index] = null;
                }
            }
            synchronized (axes) {
                axes[index] = 0f;
            }
            synchronized (serveBoost) {
                serveBoost[index] = false;
            }
        }
    }
}
