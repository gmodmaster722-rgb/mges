package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "career_stats")
data class CareerStats(
    @PrimaryKey val id: Int = 1,
    val username: String = "Player_1",
    val wins: Int = 0,
    val losses: Int = 0,
    val totalKills: Int = 0,
    val totalDeaths: Int = 0,
    val highscore: Int = 0,
    val accuracy: Float = 0f,
    val bulletsFired: Int = 0,
    val bulletsHit: Int = 0
)

@Entity(tableName = "match_history")
data class MatchHistory(
    @PrimaryKey(autoGenerate = true) val matchId: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val kills: Int,
    val deaths: Int,
    val score: Int,
    val placement: Int,
    val totalPlayers: Int,
    val outcome: String // "Victory" or "Defeat" or "Draw"
)
