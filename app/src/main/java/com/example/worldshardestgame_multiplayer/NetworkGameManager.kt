package com.example.worldshardestgame_multiplayer

import android.util.Log

class NetworkGameManager(
    private val firebaseManager: FirebaseManager,
    private val gameId: String,
    private val playerId: String
) {
    private var gameStateData: GameStateData? = null
    private var onGameStateChanged: ((GameStateData) -> Unit)? = null
    private var onPlayerPositionsChanged: ((Map<String, PlayerPosition>) -> Unit)? = null

    companion object {
        private const val TAG = "NetworkGameManager"
    }

    private var previousGameState: GameStateData? = null

    init {
        // Auf Game State Änderungen hören
        firebaseManager.listenToGameState(gameId) { data ->
            val oldState = gameStateData
            gameStateData = data
            onGameStateChanged?.invoke(data)
            Log.d(TAG, "Game state updated: Level ${data.currentLevel}, Status ${data.status}")

            // Host: Automatischer Player-Switch wenn Spieler Level geschafft hat
            checkForLevelCompletion(oldState, data)
        }

        // Auf Spieler-Positionen hören
        firebaseManager.listenToPlayerPositions(gameId) { positions ->
            onPlayerPositionsChanged?.invoke(positions)
        }
    }

    // Host prüft ob jemand gewonnen hat (alle spielen gleichzeitig)
    private fun checkForLevelCompletion(oldState: GameStateData?, newState: GameStateData) {
        // Nur Host prüft Gewinnbedingung
        if (newState.hostId != playerId) return

        // Nur im playing-Modus
        if (newState.status != "playing") return

        // Prüfe ob jemand genug Levels geschafft hat um zu gewinnen
        val winner = newState.players.find { it.levelsCompleted >= 5 }
        if (winner != null && newState.status != "finished") {
            Log.d(TAG, "Host detected game over! Winner: ${winner.name}")
            firebaseManager.updateGameState(gameId, newState.currentLevel, 0, 0L)

            // Spiel beenden
            val updates = mapOf<String, Any>("status" to "finished")
            firebaseManager.gamesRef.child(gameId).updateChildren(updates)
        }
    }

    // Spieler bereit markieren
    fun setReady(isReady: Boolean) {
        firebaseManager.setPlayerReady(gameId, playerId, isReady)
    }

    // Spiel starten (nur Host)
    fun startGame() {
        val state = gameStateData ?: return
        if (state.hostId == playerId) {
            firebaseManager.startGame(gameId)
        }
    }

    // Position updaten
    fun updatePosition(x: Float, y: Float) {
        firebaseManager.updatePlayerPosition(gameId, playerId, x, y)
    }

    // Level abgeschlossen - alle spielen gleichzeitig
    fun onLevelCompleted(elapsedTime: Long) {
        val state = gameStateData ?: return
        val currentPlayer = state.players.find { it.playerId == playerId } ?: return

        val newLevelsCompleted = currentPlayer.levelsCompleted + 1
        val newTotalTime = currentPlayer.totalTime + elapsedTime

        Log.d(TAG, "Player $playerId completed level! New count: $newLevelsCompleted")

        // Progress updaten - alle spielen parallel
        firebaseManager.updatePlayerProgress(
            gameId,
            playerId,
            newLevelsCompleted,
            currentPlayer.deaths,
            newTotalTime
        )
    }

    // Spieler gestorben - alle spielen gleichzeitig
    fun onPlayerDied() {
        val state = gameStateData ?: return
        val currentPlayer = state.players.find { it.playerId == playerId } ?: return

        val newDeaths = currentPlayer.deaths + 1

        Log.d(TAG, "Player $playerId died! New death count: $newDeaths")

        // Progress updaten - alle spielen parallel
        firebaseManager.updatePlayerProgress(
            gameId,
            playerId,
            currentPlayer.levelsCompleted,
            newDeaths,
            currentPlayer.totalTime
        )
    }

    // Alle spielen gleichzeitig - keine Turns mehr

    // Leaderboard
    fun getLeaderboard(): List<PlayerData> {
        return gameStateData?.players?.sortedWith(
            compareByDescending<PlayerData> { it.levelsCompleted }
                .thenBy { it.deaths }
                .thenBy { it.totalTime }
        ) ?: emptyList()
    }

    // Sind alle bereit?
    fun allPlayersReady(): Boolean {
        return gameStateData?.players?.all { it.isReady } ?: false
    }

    // Spiel zu Ende?
    fun isGameOver(): Boolean {
        return gameStateData?.players?.any { it.levelsCompleted >= 5 } ?: false
    }

    // Gewinner
    fun getWinner(): PlayerData? {
        return getLeaderboard().firstOrNull()
    }

    // Callbacks
    fun setOnGameStateChanged(callback: (GameStateData) -> Unit) {
        onGameStateChanged = callback
    }

    fun setOnPlayerPositionsChanged(callback: (Map<String, PlayerPosition>) -> Unit) {
        onPlayerPositionsChanged = callback
    }

    // Game State
    fun getGameState(): GameStateData? = gameStateData

    // Spiel verlassen
    fun leaveGame() {
        firebaseManager.leaveGame(gameId, playerId) { success ->
            if (success) {
                Log.d(TAG, "Successfully left game $gameId")
            } else {
                Log.e(TAG, "Failed to leave game $gameId")
            }
        }
    }
}

