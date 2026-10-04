package com.example.foodtracker.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.foodtracker.entity.Food

@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: Food)

    @Update
    suspend fun update(food: Food)

    @Delete
    suspend fun delete(food: Food)

    @Query("SELECT * FROM food_log WHERE date >= :startDate AND date <= :endDate")
    suspend fun getFoodsByDate(startDate: Long, endDate: Long): List<Food>
}