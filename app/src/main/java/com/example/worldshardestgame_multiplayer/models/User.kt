package com.example.worldshardestgame_multiplayer.models

data class User(
    val userId: String = "",
    val username: String = "",
    val passwordHash: String = "",
    val salt: String = "",
    val createdAt: Long = 0,
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0
)

