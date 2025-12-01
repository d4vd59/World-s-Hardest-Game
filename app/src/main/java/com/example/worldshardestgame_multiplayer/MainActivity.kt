package com.example.worldshardestgame_multiplayer

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.view.MotionEvent
import android.widget.ImageButton
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private lateinit var gameView: GameView
    private lateinit var gameManager: GameManager
    private lateinit var playerNameText: TextView
    private lateinit var timerText: TextView
    private lateinit var statsText: TextView
    private lateinit var firebaseManager: FirebaseManager // HIER
    private var gameId: String? = null // HIER
    private var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        gameView = findViewById(R.id.gameView)
        playerNameText = findViewById(R.id.playerNameText)
        timerText = findViewById(R.id.timerText)
        statsText = findViewById(R.id.statsText)

        setupDPad()
        showPlayerSelectionDialog()
    }

    private fun setupDPad() {
        findViewById<ImageButton>(R.id.btnUp).setOnTouchListener { _, event ->
            gameView.handleDpadInput(0, -1, event.action)
            true
        }

        findViewById<ImageButton>(R.id.btnDown).setOnTouchListener { _, event ->
            gameView.handleDpadInput(0, 1, event.action)
            true
        }

        findViewById<ImageButton>(R.id.btnLeft).setOnTouchListener { _, event ->
            gameView.handleDpadInput(-1, 0, event.action)
            true
        }

        findViewById<ImageButton>(R.id.btnRight).setOnTouchListener { _, event ->
            gameView.handleDpadInput(1, 0, event.action)
            true
        }
    }

    private fun startGame(playerCount: Int) {
        gameManager = GameManager(playerCount)

        // Callbacks setzen
        gameView.onLevelCompleted = {
            gameManager.onLevelCompleted()
            showNextPlayerDialog()
        }

        gameView.onPlayerDied = {
            gameManager.onPlayerDied()
            showNextPlayerDialog()
        }

        gameView.onTimeUpdate = { elapsedTime ->
            updateTimer(elapsedTime)
        }

        // Erstes Level starten
        startPlayerTurn()
    }

    private fun startPlayerTurn() {
        val currentPlayer = gameManager.getCurrentPlayer()

        playerNameText.text = currentPlayer.name
        updateStats()

        gameView.startLevel()
        startCountdown(gameManager.getTimeLimit())
    }



    private fun startCountdown(timeLimit: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeLimit, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                timerText.text = "Zeit: ${formatTime(millisUntilFinished)}"
            }

            override fun onFinish() {
                gameView.stopLevel()
                gameManager.onTimeExpired()
                showNextPlayerDialog()
            }
        }.start()
    }

    private fun updateTimer(elapsedTime: Long) {
        // Nur zur Anzeige der verstrichenen Zeit
    }

    private fun updateStats() {
        val player = gameManager.getCurrentPlayer()
        statsText.text = "Level: ${player.levelsCompleted} | Tode: ${player.deaths}"
    }

    private fun showNextPlayerDialog() {
        countDownTimer?.cancel()

        val leaderboard = gameManager.getLeaderboard()
        val message = buildString {
            append("Aktueller Stand:\n\n")
            leaderboard.forEachIndexed { index, player ->
                append("${index + 1}. ${player.name}\n")
                append("   Level: ${player.levelsCompleted}, Tode: ${player.deaths}\n\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Spielerwechsel")
            .setMessage(message)
            .setPositiveButton("Weiter") { _, _ ->
                if (gameManager.isGameOver()) {
                    showGameOverDialog()
                } else {
                    startPlayerTurn()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun showGameOverDialog() {
        val winner = gameManager.getWinner()
        AlertDialog.Builder(this)
            .setTitle("Spiel beendet!")
            .setMessage("Gewinner: ${winner.name}\nLevel: ${winner.levelsCompleted}\nTode: ${winner.deaths}")
            .setPositiveButton("Neues Spiel") { _, _ ->
                showPlayerSelectionDialog()
            }
            .setNegativeButton("Beenden") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }
    private fun showLocalPlayerCountDialog() {
        val options = arrayOf("2 Spieler", "3 Spieler", "4 Spieler")
        AlertDialog.Builder(this)
            .setTitle("Spieleranzahl wählen")
            .setItems(options) { _, which ->
                startGame(which + 2)
            }
            .setCancelable(false)
            .show()
    }

    private fun joinOnlineGame(gameId: String) {
        firebaseManager = FirebaseManager()
        firebaseManager.joinGame(gameId, 1) {
            this.gameId = gameId
            Toast.makeText(this, "Spiel beigetreten", Toast.LENGTH_SHORT).show()
            startGame(2)
        }
    }


    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000) / 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }

    private fun showPlayerSelectionDialog() {
        val options = arrayOf("Lokal (2-4 Spieler)", "Online erstellen", "Online beitreten")
        AlertDialog.Builder(this)
            .setTitle("Spielmodus wählen")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLocalPlayerCountDialog()
                    1 -> createOnlineGame()
                    2 -> showJoinGameDialog()
                }
            }
            .setCancelable(false)
            .show()
    }

    private fun createOnlineGame() {
        firebaseManager = FirebaseManager()
        firebaseManager.createGame(2) { id ->
            gameId = id
            Toast.makeText(this, "Spiel-ID: $id", Toast.LENGTH_LONG).show()
            startGame(2)
        }
    }

    private fun showJoinGameDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Spiel-ID eingeben")
            .setView(input)
            .setPositiveButton("Beitreten") { _, _ ->
                val id = input.text.toString()
                joinOnlineGame(id)
            }
            .show()
    }

}
