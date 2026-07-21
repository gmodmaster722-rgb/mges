package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface GameStatsDao {
    @Query("SELECT * FROM career_stats WHERE id = 1")
    fun getCareerStats(): Flow<CareerStats?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareerStats(stats: CareerStats)

    @Query("SELECT * FROM match_history ORDER BY timestamp DESC")
    fun getMatchHistory(): Flow<List<MatchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchHistory(history: MatchHistory)

    @Query("DELETE FROM match_history")
    suspend fun clearHistory()
}
