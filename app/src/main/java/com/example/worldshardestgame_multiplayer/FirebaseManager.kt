package com.example.worldshardestgame_multiplayer

import com.google.firebase.database.*
import android.util.Log

class FirebaseManager {
    private val database = FirebaseDatabase.getInstance("https://world-s-hardest-game-default-rtdb.europe-west1.firebasedatabase.app/")
    val gamesRef = database.getReference("games")

    companion object {
        private const val TAG = "FirebaseManager"
    }

    // Spiel erstellen
    fun createGame(hostName: String, lobbyName: String, onGameCreated: (String) -> Unit, onError: (String) -> Unit) {
        val gameId = gamesRef.push().key
        if (gameId == null) {
            onError("Fehler beim Erstellen der Game-ID")
            return
        }

        val gameData = hashMapOf(
            "gameId" to gameId,
            "lobbyName" to lobbyName,
            "hostId" to "player_1",
            "status" to "waiting", // waiting, playing, finished
            "currentLevel" to 1,
            "currentPlayerIndex" to 0,
            "levelStartTime" to ServerValue.TIMESTAMP,
            "timeLimit" to 180000L,
            "players" to hashMapOf(
                "player_1" to hashMapOf(
                    "playerId" to "player_1",
                    "name" to hostName,
                    "levelsCompleted" to 0,
                    "deaths" to 0,
                    "totalTime" to 0L,
                    "isReady" to false,
                    "x" to 0f,
                    "y" to 0f
                )
            )
        )

        gamesRef.child(gameId).setValue(gameData)
            .addOnSuccessListener {
                Log.d(TAG, "Game created: $gameId")
                onGameCreated(gameId)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating game", e)
                onError("Fehler beim Erstellen des Spiels: ${e.message}")
            }
    }

    // Spiel beitreten
    fun joinGame(gameId: String, playerName: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        gamesRef.child(gameId).get().addOnSuccessListener { snapshot ->
            if (!snapshot.exists()) {
                onError("Spiel nicht gefunden")
                return@addOnSuccessListener
            }

            val players = snapshot.child("players").children.count()
            val playerId = "player_${players + 1}"

            val playerData = hashMapOf(
                "playerId" to playerId,
                "name" to playerName,
                "levelsCompleted" to 0,
                "deaths" to 0,
                "totalTime" to 0L,
                "isReady" to false,
                "x" to 0f,
                "y" to 0f
            )

            gamesRef.child(gameId).child("players").child(playerId).setValue(playerData)
                .addOnSuccessListener {
                    Log.d(TAG, "Joined game: $gameId as $playerId")
                    onSuccess(playerId)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error joining game", e)
                    onError("Fehler beim Beitreten: ${e.message}")
                }
        }.addOnFailureListener { e ->
            onError("Fehler beim Laden des Spiels: ${e.message}")
        }
    }

    // Spieler-Position aktualisieren
    fun updatePlayerPosition(gameId: String, playerId: String, x: Float, y: Float) {
        gamesRef.child(gameId).child("players").child(playerId).updateChildren(
            mapOf("x" to x, "y" to y)
        )
    }

    // Spieler-Fortschritt aktualisieren
    fun updatePlayerProgress(gameId: String, playerId: String, levelsCompleted: Int, deaths: Int, totalTime: Long) {
        gamesRef.child(gameId).child("players").child(playerId).updateChildren(
            mapOf(
                "levelsCompleted" to levelsCompleted,
                "deaths" to deaths,
                "totalTime" to totalTime
            )
        )
    }

    // Spieler bereit markieren
    fun setPlayerReady(gameId: String, playerId: String, isReady: Boolean) {
        gamesRef.child(gameId).child("players").child(playerId).child("isReady").setValue(isReady)
    }

    // Spiel starten
    fun startGame(gameId: String) {
        gamesRef.child(gameId).updateChildren(
            mapOf(
                "status" to "playing",
                "levelStartTime" to ServerValue.TIMESTAMP
            )
        )
    }

    // Game State aktualisieren
    fun updateGameState(gameId: String, currentLevel: Int, currentPlayerIndex: Int, timeLimit: Long) {
        gamesRef.child(gameId).updateChildren(
            mapOf(
                "currentLevel" to currentLevel,
                "currentPlayerIndex" to currentPlayerIndex,
                "timeLimit" to timeLimit,
                "levelStartTime" to ServerValue.TIMESTAMP
            )
        )
    }

    // Auf Game State Änderungen hören
    fun listenToGameState(gameId: String, onUpdate: (GameStateData) -> Unit) {
        gamesRef.child(gameId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val gameStateData = parseGameState(snapshot)
                    onUpdate(gameStateData)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing game state", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Game state listener cancelled", error.toException())
            }
        })
    }

    // Auf Spieler-Positionen hören
    fun listenToPlayerPositions(gameId: String, onUpdate: (Map<String, PlayerPosition>) -> Unit) {
        gamesRef.child(gameId).child("players").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val positions = mutableMapOf<String, PlayerPosition>()
                snapshot.children.forEach { playerSnapshot ->
                    val playerId = playerSnapshot.child("playerId").getValue(String::class.java) ?: return@forEach
                    val x = playerSnapshot.child("x").getValue(Float::class.java) ?: 0f
                    val y = playerSnapshot.child("y").getValue(Float::class.java) ?: 0f
                    val name = playerSnapshot.child("name").getValue(String::class.java) ?: ""
                    positions[playerId] = PlayerPosition(playerId, name, x, y)
                }
                onUpdate(positions)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Player positions listener cancelled", error.toException())
            }
        })
    }

    // Game State parsen
    private fun parseGameState(snapshot: DataSnapshot): GameStateData {
        val players = mutableListOf<PlayerData>()
        snapshot.child("players").children.forEach { playerSnapshot ->
            val player = PlayerData(
                playerId = playerSnapshot.child("playerId").getValue(String::class.java) ?: "",
                name = playerSnapshot.child("name").getValue(String::class.java) ?: "",
                levelsCompleted = playerSnapshot.child("levelsCompleted").getValue(Int::class.java) ?: 0,
                deaths = playerSnapshot.child("deaths").getValue(Int::class.java) ?: 0,
                totalTime = playerSnapshot.child("totalTime").getValue(Long::class.java) ?: 0L,
                isReady = playerSnapshot.child("isReady").getValue(Boolean::class.java) ?: false
            )
            players.add(player)
        }

        return GameStateData(
            gameId = snapshot.child("gameId").getValue(String::class.java) ?: "",
            hostId = snapshot.child("hostId").getValue(String::class.java) ?: "",
            status = snapshot.child("status").getValue(String::class.java) ?: "waiting",
            currentLevel = snapshot.child("currentLevel").getValue(Int::class.java) ?: 1,
            currentPlayerIndex = snapshot.child("currentPlayerIndex").getValue(Int::class.java) ?: 0,
            timeLimit = snapshot.child("timeLimit").getValue(Long::class.java) ?: 180000L,
            players = players
        )
    }

    // Spiel verlassen
    fun leaveGame(gameId: String, playerId: String) {
        gamesRef.child(gameId).child("players").child(playerId).removeValue()
    }

    // Verfügbare Spiele (Lobbys) abrufen
    fun getAvailableGames(onGamesLoaded: (List<GameLobbyInfo>) -> Unit) {
        gamesRef.orderByChild("status").equalTo("waiting").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val games = mutableListOf<GameLobbyInfo>()
                snapshot.children.forEach { gameSnapshot ->
                    val gameId = gameSnapshot.child("gameId").getValue(String::class.java) ?: return@forEach
                    val lobbyName = gameSnapshot.child("lobbyName").getValue(String::class.java) ?: "Unbekannte Lobby"
                    val playerCount = gameSnapshot.child("players").childrenCount.toInt()
                    val hostName = gameSnapshot.child("players").child("player_1").child("name").getValue(String::class.java) ?: "Unbekannt"

                    // Nur Spiele mit weniger als 4 Spielern anzeigen
                    if (playerCount < 4) {
                        games.add(GameLobbyInfo(gameId, lobbyName, hostName, playerCount, 4))
                    }
                }
                onGamesLoaded(games)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error loading games", error.toException())
                onGamesLoaded(emptyList())
            }
        })
    }

    // Spiel beitreten über Lobby-Namen
    fun joinGameByLobbyName(lobbyName: String, playerName: String, onSuccess: (String, String) -> Unit, onError: (String) -> Unit) {
        gamesRef.orderByChild("lobbyName").equalTo(lobbyName).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists() || snapshot.childrenCount == 0L) {
                    onError("Lobby '$lobbyName' nicht gefunden")
                    return
                }

                // Nimm die erste Lobby mit diesem Namen
                val gameSnapshot = snapshot.children.firstOrNull()
                if (gameSnapshot == null) {
                    onError("Lobby '$lobbyName' nicht gefunden")
                    return
                }

                val status = gameSnapshot.child("status").getValue(String::class.java)
                if (status != "waiting") {
                    onError("Lobby '$lobbyName' hat bereits gestartet")
                    return
                }

                val playerCount = gameSnapshot.child("players").childrenCount.toInt()
                if (playerCount >= 4) {
                    onError("Lobby '$lobbyName' ist voll")
                    return
                }

                val gameId = gameSnapshot.child("gameId").getValue(String::class.java) ?: ""
                joinGame(gameId, playerName, onSuccess = { playerId ->
                    onSuccess(gameId, playerId)
                }, onError = onError)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error finding lobby", error.toException())
                onError("Fehler beim Suchen der Lobby: ${error.message}")
            }
        })
    }

    // Prüfen ob Lobby-Name bereits existiert
    fun checkLobbyNameExists(lobbyName: String, onResult: (Boolean) -> Unit, onError: ((String) -> Unit)? = null) {
        gamesRef.orderByChild("lobbyName").equalTo(lobbyName).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Prüfe ob es eine Lobby mit diesem Namen gibt, die noch "waiting" ist
                val exists = snapshot.children.any {
                    it.child("status").getValue(String::class.java) == "waiting"
                }
                onResult(exists)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Error checking lobby name", error.toException())

                // Spezielle Behandlung für Permission denied
                if (error.code == DatabaseError.PERMISSION_DENIED) {
                    onError?.invoke("Firebase-Berechtigung fehlt! Bitte aktiviere Lese-/Schreibrechte in der Firebase Console.")
                } else {
                    onError?.invoke("Fehler beim Prüfen der Lobby: ${error.message}")
                }

                // Fallback: Lobby existiert nicht annehmen
                onResult(false)
            }
        })
    }
}

// Data Classes
data class GameStateData(
    val gameId: String,
    val hostId: String,
    val status: String,
    val currentLevel: Int,
    val currentPlayerIndex: Int,
    val timeLimit: Long,
    val players: List<PlayerData>
)

data class PlayerData(
    val playerId: String,
    val name: String,
    val levelsCompleted: Int,
    val deaths: Int,
    val totalTime: Long,
    val isReady: Boolean
)

data class PlayerPosition(
    val playerId: String,
    val name: String,
    val x: Float,
    val y: Float
)

data class GameLobbyInfo(
    val gameId: String,
    val lobbyName: String,
    val hostName: String,
    val currentPlayers: Int,
    val maxPlayers: Int
)
