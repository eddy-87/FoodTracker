package com.example.foodtracker.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.foodtracker.entity.WaterIntake

@Dao
interface WaterIntakeDao {
    @Query("SELECT * FROM water_intake WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getLogsForDate(startTime: Long, endTime: Long): List<WaterIntake>

    @Insert
    suspend fun insert(intake: WaterIntake)

    @Delete
    suspend fun delete(intake: WaterIntake)

    @Query("SELECT SUM(amountMl) FROM water_intake WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalWaterForDate(startTime: Long, endTime: Long): Int?
}