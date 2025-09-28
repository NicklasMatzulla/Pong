package net.limitmedia.pong3d.game;

public final class MatchState {
    public float playerX;
    public float opponentX;
    public float ballX;
    public float ballY;
    public float ballVX;
    public float ballVY;
    public float ballSpeed;

    public int playerScore;
    public int opponentScore;
    public int rallyCount;
    public int bestRally;

    public boolean waitingForServe;
    public boolean playerServeTurn;
    public float serveCountdown;
    public boolean matchOver;
    public boolean playerWon;
    public float matchTime;

    public void reset() {
        playerX = 0f;
        opponentX = 0f;
        ballX = 0f;
        ballY = 0f;
        ballVX = 0f;
        ballVY = 0f;
        ballSpeed = MatchConstants.BALL_BASE_SPEED;
        playerScore = 0;
        opponentScore = 0;
        rallyCount = 0;
        bestRally = 0;
        waitingForServe = true;
        playerServeTurn = true;
        serveCountdown = 2.3f;
        matchOver = false;
        playerWon = false;
        matchTime = 0f;
    }

    public void copyFrom(MatchState other) {
        playerX = other.playerX;
        opponentX = other.opponentX;
        ballX = other.ballX;
        ballY = other.ballY;
        ballVX = other.ballVX;
        ballVY = other.ballVY;
        ballSpeed = other.ballSpeed;
        playerScore = other.playerScore;
        opponentScore = other.opponentScore;
        rallyCount = other.rallyCount;
        bestRally = other.bestRally;
        waitingForServe = other.waitingForServe;
        playerServeTurn = other.playerServeTurn;
        serveCountdown = other.serveCountdown;
        matchOver = other.matchOver;
        playerWon = other.playerWon;
        matchTime = other.matchTime;
    }
}
