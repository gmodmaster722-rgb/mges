package com.example.data

import kotlinx.coroutines.flow.Flow

class GameStatsRepository(private val gameStatsDao: GameStatsDao) {

    val careerStats: Flow<CareerStats?> = gameStatsDao.getCareerStats()
    val matchHistory: Flow<List<MatchHistory>> = gameStatsDao.getMatchHistory()

    suspend fun saveCareerStats(stats: CareerStats) {
        gameStatsDao.insertCareerStats(stats)
    }

    suspend fun recordMatch(kills: Int, deaths: Int, score: Int, placement: Int, totalPlayers: Int) {
        val outcome = when {
            placement == 1 -> "Victory"
            placement <= totalPlayers / 2 -> "Draw" // Podiums / high ranks
            else -> "Defeat"
        }
        val match = MatchHistory(
            kills = kills,
            deaths = deaths,
            score = score,
            placement = placement,
            totalPlayers = totalPlayers,
            outcome = outcome
        )
        gameStatsDao.insertMatchHistory(match)

        // Also update career stats reactively
        // We will read current career stats in ViewModel and apply updates, then save CareerStats
    }

    suspend fun clearAllHistory() {
        gameStatsDao.clearHistory()
    }
}
