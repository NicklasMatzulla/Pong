package net.limitmedia.pong3d.network;

import net.limitmedia.pong3d.game.MatchState;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetworkClient implements Closeable {
    public static final int DEFAULT_PORT = 5050;

    private static final byte MSG_HANDSHAKE = 1;
    private static final byte MSG_SNAPSHOT = 2;
    private static final byte MSG_INPUT = 3;

    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Thread readerThread;
    private final Object snapshotLock = new Object();
    private final MatchSnapshot snapshot = new MatchSnapshot();
    private final AtomicBoolean running = new AtomicBoolean(true);

    private volatile int playerIndex = -1;
    private volatile boolean handshakeComplete;
    private volatile String failureReason;

    private NetworkClient(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
        this.readerThread = new Thread(this::readLoop, "NetworkClient-Reader");
        this.readerThread.setDaemon(true);
        this.readerThread.start();
    }

    public static NetworkClient connect(String host, int port, int timeoutMillis) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        return new NetworkClient(socket);
    }

    public static NetworkClient connect(String host, int port) throws IOException {
        return connect(host, port, 4000);
    }

    private void readLoop() {
        try {
            while (running.get()) {
                byte type;
                try {
                    type = input.readByte();
                } catch (IOException ex) {
                    if (running.get()) {
                        failureReason = ex.getMessage();
                    }
                    break;
                }
                if (type == MSG_HANDSHAKE) {
                    playerIndex = input.readInt();
                    handshakeComplete = true;
                } else if (type == MSG_SNAPSHOT) {
                    readSnapshot();
                } else {
                    // Unknown message, skip gracefully
                }
            }
        } catch (IOException ex) {
            if (running.get()) {
                failureReason = ex.getMessage();
            }
        } finally {
            running.set(false);
            closeQuietly();
        }
    }

    private void readSnapshot() throws IOException {
        MatchState state = snapshot.getState();
        synchronized (snapshotLock) {
            snapshot.setTick(input.readInt());
            state.playerX = input.readFloat();
            state.opponentX = input.readFloat();
            state.ballX = input.readFloat();
            state.ballY = input.readFloat();
            state.ballVX = input.readFloat();
            state.ballVY = input.readFloat();
            state.ballSpeed = input.readFloat();
            state.playerScore = input.readInt();
            state.opponentScore = input.readInt();
            state.rallyCount = input.readInt();
            state.bestRally = input.readInt();
            state.waitingForServe = input.readBoolean();
            state.playerServeTurn = input.readBoolean();
            state.serveCountdown = input.readFloat();
            state.matchOver = input.readBoolean();
            state.playerWon = input.readBoolean();
            state.matchTime = input.readFloat();
            snapshot.setPlayerPaddleImpact(input.readBoolean());
            snapshot.setOpponentPaddleImpact(input.readBoolean());
            snapshot.setWallImpact(input.readBoolean());
            snapshot.setGoalScored(input.readBoolean());
            snapshot.setPlayerScored(input.readBoolean());
            snapshot.setCountdownChanged(input.readBoolean());
            snapshot.setCountdownValue(input.readInt());
        }
    }

    public void sendInput(float axis, boolean serveBoost) {
        if (!running.get()) {
            return;
        }
        synchronized (output) {
            try {
                output.writeByte(MSG_INPUT);
                output.writeFloat(axis);
                output.writeBoolean(serveBoost);
                output.flush();
            } catch (IOException ex) {
                failureReason = ex.getMessage();
                running.set(false);
                closeQuietly();
            }
        }
    }

    public MatchSnapshot copySnapshot(MatchSnapshot target) {
        if (target == null) {
            target = new MatchSnapshot();
        }
        synchronized (snapshotLock) {
            target.copyFrom(snapshot);
        }
        return target;
    }

    public boolean isHandshakeComplete() {
        return handshakeComplete;
    }

    public int getPlayerIndex() {
        return playerIndex;
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getFailureReason() {
        return failureReason;
    }

    @Override
    public void close() {
        running.set(false);
        closeQuietly();
    }

    private void closeQuietly() {
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
    }
}
