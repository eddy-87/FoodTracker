package com.example.foodtracker.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val age: Int = 0,
    val weight: Float = 0f,
    val height: Int = 0,
    val goalWeight: Float = 0f,
    val isMale: Boolean = true,
    val goalType: String = "Maintain",
    val activityLevel: String = "LightActive",
    val isAutoCalc: Boolean = true,
    val calorieGoal: Int = 2000,
    val proteinGoal: Int = 150,
    val carbGoal: Int = 250,
    val fatGoal: Int = 70,
    val stepsGoal: Int = 10000,
    val waterGoal: Int = 2000
)
