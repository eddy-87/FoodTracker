package com.example.foodtracker.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "food_items", indices = [Index(value = ["name"], unique = true)])
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val caloriesPer100g: Int,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val barcode: String? = null,
    val lastUsed: Long = 0L,
    val unit: String = "g",
    val portionSize: Int = 0
)