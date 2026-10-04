package com.example.foodtracker.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodtracker.entity.WaterContainer

@Dao
interface WaterContainerDao {
    @Query("SELECT * FROM water_containers")
    suspend fun getAllContainers(): List<WaterContainer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(container: WaterContainer)

    @Delete
    suspend fun delete(container: WaterContainer)
}