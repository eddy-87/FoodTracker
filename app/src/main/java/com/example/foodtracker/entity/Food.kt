package com.example.foodtracker.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_log",
    foreignKeys = [ForeignKey(
        entity = FoodItem::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("foodItemId")]
)
data class Food(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val calories: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val quantity: Int,
    val date: Long,
    val mealType: String,
    val foodItemId: Int? = null
)