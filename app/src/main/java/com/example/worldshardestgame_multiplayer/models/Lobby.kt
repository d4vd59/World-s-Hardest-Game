package com.example.worldshardestgame_multiplayer.models

data class Lobby(
    val gameId: String = "",
    val lobbyName: String = "",
    val hostUserId: String = "",
    val hostUsername: String = "",
    val isPublic: Boolean = true,
    val maxPlayers: Int = 4,
    val currentPlayers: Int = 0,
    val status: String = "waiting", // waiting, playing, finished
    val currentLevel: Int = 1,
    val createdAt: Long = 0,
    val invitedUserIds: List<String> = emptyList(),
    val playerUsernames: Map<String, String> = emptyMap() // playerId -> username
)

