package com.example.worldshardestgame_multiplayer

class GameManager(private val playerCount: Int) {
    private val gameState = GameState(
        players = List(playerCount) { Player(it + 1, "Spieler ${it + 1}") }
    )

    private val timeLimits = listOf(
        180000L, // 3 Minuten
        60000L,  // 1 Minute
        30000L   // 30 Sekunden
    )

    fun onLevelCompleted() {
        val currentPlayer = gameState.getCurrentPlayer()
        val elapsedTime = System.currentTimeMillis() - gameState.levelStartTime

        currentPlayer.levelsCompleted++
        currentPlayer.totalTime += elapsedTime

        val completedCount = currentPlayer.levelsCompleted - 1
        gameState.timeLimit = if (completedCount < timeLimits.size) {
            timeLimits[completedCount]
        } else {
            30000L
        }

        gameState.nextPlayer()
        startNextLevel()
    }

    fun onPlayerDied() {
        gameState.getCurrentPlayer().deaths++
        gameState.nextPlayer()
        startNextLevel()
    }

    fun onTimeExpired() {
        gameState.nextPlayer()
        startNextLevel()
    }

    private fun startNextLevel() {
        val leaderboard = gameState.getLeaderboard()
        gameState.currentLevel = leaderboard.first().levelsCompleted + 1
        gameState.levelStartTime = System.currentTimeMillis()
    }

    fun getCurrentPlayer() = gameState.getCurrentPlayer()
    fun getLeaderboard() = gameState.getLeaderboard()
    fun getTimeLimit() = gameState.timeLimit
    fun getWinner() = gameState.getLeaderboard().first()
    fun isGameOver() = gameState.players.all { it.levelsCompleted >= 5 }
}

data class Player(
    val id: Int,
    val name: String,
    var levelsCompleted: Int = 0,
    var deaths: Int = 0,
    var totalTime: Long = 0
) : Comparable<Player> {
    override fun compareTo(other: Player): Int {
        return when {
            this.levelsCompleted != other.levelsCompleted -> other.levelsCompleted - this.levelsCompleted
            this.deaths != other.deaths -> this.deaths - other.deaths
            else -> (this.totalTime - other.totalTime).toInt()
        }
    }
}

data class GameState(
    val players: List<Player>,
    var currentPlayerIndex: Int = 0,
    var currentLevel: Int = 1,
    var levelStartTime: Long = 0,
    var timeLimit: Long = 180000L
) {
    fun getCurrentPlayer() = players[currentPlayerIndex]

    fun nextPlayer() {
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size
    }

    fun getLeaderboard() = players.sortedBy { it }
}
