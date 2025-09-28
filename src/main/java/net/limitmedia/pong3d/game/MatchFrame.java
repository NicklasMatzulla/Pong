package net.limitmedia.pong3d.game;

public final class MatchFrame {
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

    public void copyFrom(MatchFrame other) {
        state.copyFrom(other.state);
        tick = other.tick;
        playerPaddleImpact = other.playerPaddleImpact;
        opponentPaddleImpact = other.opponentPaddleImpact;
        wallImpact = other.wallImpact;
        goalScored = other.goalScored;
        playerScored = other.playerScored;
        countdownChanged = other.countdownChanged;
        countdownValue = other.countdownValue;
    }
}
