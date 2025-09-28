package net.limitmedia.pong3d.network;

import net.limitmedia.pong3d.game.MatchFrame;
import net.limitmedia.pong3d.game.MatchState;

public final class MatchSnapshot {
    private final MatchState state = new MatchState();
    private int tick;
    private boolean playerPaddleImpact;
    private boolean opponentPaddleImpact;
    private boolean wallImpact;
    private boolean goalScored;
    private boolean playerScored;
    private boolean countdownChanged;
    private int countdownValue;

    public MatchState getState() {
        return state;
    }

    public int getTick() {
        return tick;
    }

    public void setTick(int tick) {
        this.tick = tick;
    }

    public boolean isPlayerPaddleImpact() {
        return playerPaddleImpact;
    }

    public void setPlayerPaddleImpact(boolean playerPaddleImpact) {
        this.playerPaddleImpact = playerPaddleImpact;
    }

    public boolean isOpponentPaddleImpact() {
        return opponentPaddleImpact;
    }

    public void setOpponentPaddleImpact(boolean opponentPaddleImpact) {
        this.opponentPaddleImpact = opponentPaddleImpact;
    }

    public boolean isWallImpact() {
        return wallImpact;
    }

    public void setWallImpact(boolean wallImpact) {
        this.wallImpact = wallImpact;
    }

    public boolean isGoalScored() {
        return goalScored;
    }

    public void setGoalScored(boolean goalScored) {
        this.goalScored = goalScored;
    }

    public boolean isPlayerScored() {
        return playerScored;
    }

    public void setPlayerScored(boolean playerScored) {
        this.playerScored = playerScored;
    }

    public boolean isCountdownChanged() {
        return countdownChanged;
    }

    public void setCountdownChanged(boolean countdownChanged) {
        this.countdownChanged = countdownChanged;
    }

    public int getCountdownValue() {
        return countdownValue;
    }

    public void setCountdownValue(int countdownValue) {
        this.countdownValue = countdownValue;
    }

    public void copyFrom(MatchFrame frame) {
        state.copyFrom(frame.getState());
        tick = frame.getTick();
        playerPaddleImpact = frame.isPlayerPaddleImpact();
        opponentPaddleImpact = frame.isOpponentPaddleImpact();
        wallImpact = frame.isWallImpact();
        goalScored = frame.isGoalScored();
        playerScored = frame.isPlayerScored();
        countdownChanged = frame.isCountdownChanged();
        countdownValue = frame.getCountdownValue();
    }

    public void copyFrom(MatchSnapshot snapshot) {
        state.copyFrom(snapshot.state);
        tick = snapshot.tick;
        playerPaddleImpact = snapshot.playerPaddleImpact;
        opponentPaddleImpact = snapshot.opponentPaddleImpact;
        wallImpact = snapshot.wallImpact;
        goalScored = snapshot.goalScored;
        playerScored = snapshot.playerScored;
        countdownChanged = snapshot.countdownChanged;
        countdownValue = snapshot.countdownValue;
    }
}
