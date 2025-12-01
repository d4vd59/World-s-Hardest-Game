package com.example.worldshardestgame_multiplayer

import com.google.firebase.database.*

class FirebaseManager {
    private val database = FirebaseDatabase.getInstance()
    private val gamesRef = database.getReference("games")

    fun createGame(playerCount: Int, onGameCreated: (String) -> Unit) {
        val gameId = gamesRef.push().key ?: return

        val gameData = mapOf(
            "playerCount" to playerCount,
            "players" to mapOf(
                "1" to mapOf(
                    "name" to "Spieler 1",
                    "levelsCompleted" to 0,
                    "deaths" to 0
                )
            ),
            "currentLevel" to 1,
            "status" to "waiting"
        )

        gamesRef.child(gameId).setValue(gameData)
            .addOnSuccessListener {
                onGameCreated(gameId)
            }
    }

    fun joinGame(gameId: String, playerId: Int, onJoined: () -> Unit) {
        val playerData = mapOf(
            "name" to "Spieler $playerId",
            "levelsCompleted" to 0,
            "deaths" to 0
        )

        gamesRef.child(gameId).child("players").child(playerId.toString())
            .setValue(playerData)
            .addOnSuccessListener {
                onJoined()
            }
    }

    fun updatePlayerProgress(gameId: String, playerId: Int, levelsCompleted: Int, deaths: Int) {
        gamesRef.child(gameId).child("players").child(playerId.toString()).updateChildren(
            mapOf(
                "levelsCompleted" to levelsCompleted,
                "deaths" to deaths
            )
        )
    }

    fun listenToGame(gameId: String, onUpdate: (Map<String, Any>) -> Unit) {
        gamesRef.child(gameId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.value as? Map<String, Any> ?: return
                onUpdate(data)
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
