package com.example.worldshardestgame_multiplayer.models

data class Friend(
    val userId: String = "",
    val username: String = "",
    val status: String = "offline", // online, offline, in_game
    val addedAt: Long = System.currentTimeMillis()
)

