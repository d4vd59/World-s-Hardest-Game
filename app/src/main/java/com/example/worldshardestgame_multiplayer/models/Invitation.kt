package com.example.worldshardestgame_multiplayer.models

data class Invitation(
    val invitationId: String = "",
    val gameId: String = "",
    val lobbyName: String = "",
    val fromUserId: String = "",
    val fromUsername: String = "",
    val toUserId: String = "",
    val toUsername: String = "",
    val status: String = "pending", // pending, accepted, declined
    val timestamp: Long = 0
)

