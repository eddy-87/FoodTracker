package com.example.foodtracker.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "water_containers")
data class WaterContainer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,     // ex: "Pahar mic", "Sticla mea"
    val amountMl: Int,    // ex: 250, 500, 320
    val isDefault: Boolean = false // ca să știm dacă e creat de sistem sau de user
)