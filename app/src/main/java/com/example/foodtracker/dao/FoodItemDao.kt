package com.example.foodtracker.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodtracker.entity.FoodItem

@Dao
interface FoodItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FoodItem>)

    @androidx.room.Update
    suspend fun update(item: FoodItem)

    // MODIFICARE: Ordonăm alfabetic (A-Z) direct din baza de date
    @Query("SELECT * FROM food_items ORDER BY lastUsed DESC, name ASC")
    suspend fun getAll(): List<FoodItem>

    // Aceasta rămâne ca rezervă, dar vom folosi filtrarea în memorie (mai rapidă)
    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%'")
    suspend fun searchFoods(query: String): List<FoodItem>

    // Metoda necesară pentru scanarea codului de bare (dacă nu o aveai deja)
    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun getFoodByBarcode(barcode: String): FoodItem?

    @Delete
    suspend fun delete(item: FoodItem)
}