package com.example.worldshardestgame_multiplayer

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.Button
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var networkGameManager: NetworkGameManager
    private lateinit var firebaseManager: FirebaseManager
    private lateinit var playerNameText: TextView
    private lateinit var timerText: TextView
    private lateinit var statsText: TextView
    private lateinit var btnReady: Button

    private var gameId: String = ""
    private var playerId: String = ""
    private var playerName: String = ""
    private var countDownTimer: CountDownTimer? = null
    private var lastPositionUpdate = 0L
    private var levelStartTime = 0L
    private var presenceTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Intent-Daten holen
        gameId = intent.getStringExtra("GAME_ID") ?: run {
            Toast.makeText(this, "Fehler: Keine Game-ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        playerId = intent.getStringExtra("PLAYER_ID") ?: run {
            Toast.makeText(this, "Fehler: Keine Player-ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        playerName = intent.getStringExtra("PLAYER_NAME") ?: "Spieler"

        // Views initialisieren
        gameView = findViewById(R.id.gameView)
        playerNameText = findViewById(R.id.playerNameText)
        timerText = findViewById(R.id.timerText)
        statsText = findViewById(R.id.statsText)
        btnReady = findViewById(R.id.btnReady)

        playerNameText.text = playerName

        // Firebase und Network Manager initialisieren
        firebaseManager = FirebaseManager()
        networkGameManager = NetworkGameManager(firebaseManager, gameId, playerId)

        // GameView konfigurieren
        gameView.setMyPlayerId(playerId)

        setupDPad()
        setupGameCallbacks()
        setupNetworkCallbacks()
        setupReadyButton()

        // Starte Presence Heartbeat (alle 10 Sekunden)
        startPresenceHeartbeat()

        // Lobby-Modus
        showLobbyScreen()
    }

    private fun setupDPad() {
        findViewById<Button>(R.id.btnUp).setOnTouchListener { _, event ->
            gameView.handleDpadInput(0, -1, event.action)
            true
        }

        findViewById<Button>(R.id.btnDown).setOnTouchListener { _, event ->
            gameView.handleDpadInput(0, 1, event.action)
            true
        }

        findViewById<Button>(R.id.btnLeft).setOnTouchListener { _, event ->
            gameView.handleDpadInput(-1, 0, event.action)
            true
        }

        findViewById<Button>(R.id.btnRight).setOnTouchListener { _, event ->
            gameView.handleDpadInput(1, 0, event.action)
            true
        }
    }

    private fun setupGameCallbacks() {
        gameView.onLevelCompleted = {
            val elapsedTime = System.currentTimeMillis() - levelStartTime
            networkGameManager.onLevelCompleted(elapsedTime)

            // Starte nächstes Level automatisch
            levelStartTime = System.currentTimeMillis()
            gameView.startLevel()
        }

        gameView.onPlayerDied = {
            networkGameManager.onPlayerDied()

            // Stoppe Level erstmal komplett
            gameView.stopLevel()

            // Respawn automatisch - Level neu starten mit kleinem Delay
            gameView.postDelayed({
                levelStartTime = System.currentTimeMillis()
                gameView.startLevel()
            }, 100) // 100ms Delay um Race Conditions zu vermeiden
        }

        gameView.onPositionChanged = { x, y ->
            // Position throttled updaten (max alle 100ms)
            val now = System.currentTimeMillis()
            if (now - lastPositionUpdate > 100) {
                networkGameManager.updatePosition(x, y)
                lastPositionUpdate = now
            }
        }
    }

    private fun setupNetworkCallbacks() {
        networkGameManager.setOnGameStateChanged { gameState ->
            runOnUiThread {
                updateGameState(gameState)
            }
        }

        networkGameManager.setOnPlayerPositionsChanged { positions ->
            runOnUiThread {
                gameView.updateOtherPlayers(positions)
            }
        }
    }

    private fun setupReadyButton() {
        btnReady.setOnClickListener {
            networkGameManager.setReady(true)
            btnReady.isEnabled = false
            btnReady.text = "Bereit ✓"
        }
    }

    private fun showLobbyScreen() {
        btnReady.visibility = Button.VISIBLE
        playerNameText.text = "$playerName in Lobby"
        timerText.text = "Bereit?"
        statsText.text = "Warte auf weitere Spieler..."
    }

    private fun updateGameState(gameState: GameStateData) {
        when (gameState.status) {
            "waiting" -> {
                val readyCount = gameState.players.count { it.isReady }
                val playerCount = gameState.players.size
                statsText.text = "Spieler in Lobby: $playerCount | bereit: $readyCount"

                if (networkGameManager.allPlayersReady() && playerId == gameState.hostId) {
                    networkGameManager.startGame()
                }
            }
            "playing" -> {
                btnReady.visibility = Button.GONE

                // Alle spielen gleichzeitig - zeige eigene Stats
                val myPlayer = gameState.players.find { it.playerId == playerId }
                if (myPlayer != null) {
                    // Aktuelles Level des Spielers (levelsCompleted + 1, da 0-basiert)
                    val currentPlayerLevel = myPlayer.levelsCompleted + 1

                    playerNameText.text = "$playerName - Level $currentPlayerLevel"
                    statsText.text = "Geschaffte Level: ${myPlayer.levelsCompleted} | Tode: ${myPlayer.deaths}"
                    timerText.text = "Level $currentPlayerLevel"

                    // Setze das korrekte Level in GameView basierend auf Spieler-Fortschritt
                    if (gameView.currentLevel != currentPlayerLevel) {
                        gameView.currentLevel = currentPlayerLevel
                        // Stoppe und starte Level neu wenn sich das Level geändert hat
                        if (gameView.isGameRunning) {
                            gameView.stopLevel()
                        }
                    }
                }

                // Starte mein Spiel wenn noch nicht gestartet
                if (!gameView.isGameRunning) {
                    levelStartTime = System.currentTimeMillis()
                    gameView.startLevel()
                }

                // Prüfe ob jemand gewonnen hat
                if (networkGameManager.isGameOver()) {
                    showGameOverDialog()
                }
            }
            "finished" -> {
                showGameOverDialog()
            }
        }
    }

    // Kein Timer mehr - alle spielen gleichzeitig ohne Zeitlimit

    private fun showGameOverDialog() {
        countDownTimer?.cancel()
        gameView.stopLevel()

        val winner = networkGameManager.getWinner()
        val leaderboard = networkGameManager.getLeaderboard()

        val message = buildString {
            append("🏆 Gewinner: ${winner?.name}\n")
            append("Level: ${winner?.levelsCompleted}\n")
            append("Tode: ${winner?.deaths}\n\n")
            append("Rangliste:\n")
            leaderboard.forEachIndexed { index, player ->
                append("${index + 1}. ${player.name} - ")
                append("L${player.levelsCompleted} T${player.deaths}\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Spiel beendet!")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Startet einen Heartbeat, der alle 10 Sekunden die Presence aktualisiert
     */
    private fun startPresenceHeartbeat() {
        presenceTimer = object : CountDownTimer(Long.MAX_VALUE, 10000) {
            override fun onTick(millisUntilFinished: Long) {
                firebaseManager.updatePlayerPresence(gameId, playerId)
            }

            override fun onFinish() {
                // Wird nie aufgerufen da Long.MAX_VALUE
            }
        }.start()
    }

    override fun onPause() {
        super.onPause()
        // Markiere Spieler als offline wenn App in den Hintergrund geht
        firebaseManager.gamesRef.child(gameId).child("players").child(playerId)
            .child("online").setValue(false)
    }

    override fun onResume() {
        super.onResume()
        // Markiere Spieler als online wenn App wieder aktiv wird
        firebaseManager.gamesRef.child(gameId).child("players").child(playerId)
            .child("online").setValue(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        presenceTimer?.cancel()
        networkGameManager.leaveGame()

        // Wenn der Host die App verlässt, lösche alle Einladungen für diese Lobby
        if (playerId == "player_1") {
            firebaseManager.deleteInvitationsForGame(gameId)
        }
    }

    override fun onBackPressed() {
        // Zeige Bestätigungs-Dialog beim Verlassen
        AlertDialog.Builder(this)
            .setTitle("Lobby verlassen?")
            .setMessage("Möchtest du die Lobby wirklich verlassen?")
            .setPositiveButton("Ja") { _, _ ->
                super.onBackPressed()
            }
            .setNegativeButton("Nein", null)
            .show()
    }

}
