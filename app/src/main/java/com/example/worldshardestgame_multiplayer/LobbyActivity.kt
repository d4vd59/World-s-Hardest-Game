package com.example.worldshardestgame_multiplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class LobbyActivity : AppCompatActivity() {

    private lateinit var firebaseManager: FirebaseManager
    private lateinit var btnCreateGame: Button
    private lateinit var btnJoinGame: Button
    private lateinit var etPlayerName: EditText
    private lateinit var etLobbyName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)

        firebaseManager = FirebaseManager()

        etPlayerName = findViewById(R.id.etPlayerName)
        etLobbyName = findViewById(R.id.etLobbyName)
        btnCreateGame = findViewById(R.id.btnCreateGame)
        btnJoinGame = findViewById(R.id.btnJoinGame)

        btnCreateGame.setOnClickListener {
            createGame()
        }

        btnJoinGame.setOnClickListener {
            showJoinGameDialog()
        }
    }

    private fun createGame() {
        val playerName = etPlayerName.text.toString().trim()
        val lobbyName = etLobbyName.text.toString().trim()

        if (playerName.isEmpty()) {
            Toast.makeText(this, "Bitte gib deinen Namen ein", Toast.LENGTH_SHORT).show()
            return
        }

        if (lobbyName.isEmpty()) {
            Toast.makeText(this, "Bitte gib einen Lobby-Namen ein", Toast.LENGTH_SHORT).show()
            return
        }

        btnCreateGame.isEnabled = false
        Toast.makeText(this, "Prüfe Lobby-Name...", Toast.LENGTH_SHORT).show()

        // Erst prüfen ob Lobby-Name bereits existiert
        firebaseManager.checkLobbyNameExists(
            lobbyName = lobbyName,
            onResult = { exists ->
                runOnUiThread {
                    if (exists) {
                        Toast.makeText(this, "Lobby '$lobbyName' existiert bereits", Toast.LENGTH_LONG).show()
                        btnCreateGame.isEnabled = true
                    } else {
                        // Lobby erstellen
                        firebaseManager.createGame(
                            hostName = playerName,
                            lobbyName = lobbyName,
                            onGameCreated = { gameId ->
                                runOnUiThread {
                                    Toast.makeText(this, "Lobby '$lobbyName' erstellt!", Toast.LENGTH_LONG).show()
                                    startMainActivity(gameId, "player_1", playerName)
                                }
                            },
                            onError = { error ->
                                runOnUiThread {
                                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                                    btnCreateGame.isEnabled = true
                                }
                            }
                        )
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    btnCreateGame.isEnabled = true
                }
            }
        )
    }

    private fun showJoinGameDialog() {
        val playerName = etPlayerName.text.toString().trim()
        val lobbyName = etLobbyName.text.toString().trim()

        if (playerName.isEmpty()) {
            Toast.makeText(this, "Bitte gib deinen Namen ein", Toast.LENGTH_SHORT).show()
            return
        }

        if (lobbyName.isEmpty()) {
            Toast.makeText(this, "Bitte gib einen Lobby-Namen ein", Toast.LENGTH_SHORT).show()
            return
        }

        btnJoinGame.isEnabled = false
        Toast.makeText(this, "Suche Lobby '$lobbyName'...", Toast.LENGTH_SHORT).show()

        // Direkt über Lobby-Namen joinen
        firebaseManager.joinGameByLobbyName(
            lobbyName = lobbyName,
            playerName = playerName,
            onSuccess = { gameId, playerId ->
                runOnUiThread {
                    Toast.makeText(this, "Lobby '$lobbyName' beigetreten!", Toast.LENGTH_SHORT).show()
                    startMainActivity(gameId, playerId, playerName)
                }
            },
            onError = { error ->
                runOnUiThread {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                    btnJoinGame.isEnabled = true
                }
            }
        )
    }

    private fun startMainActivity(gameId: String, playerId: String, playerName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("GAME_ID", gameId)
            putExtra("PLAYER_ID", playerId)
            putExtra("PLAYER_NAME", playerName)
        }
        startActivity(intent)
        finish()
    }
}

