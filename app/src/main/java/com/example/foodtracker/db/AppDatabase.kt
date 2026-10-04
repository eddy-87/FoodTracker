package com.example.foodtracker.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.foodtracker.dao.FoodDao
import com.example.foodtracker.dao.FoodItemDao
import com.example.foodtracker.dao.RecipeDao
import com.example.foodtracker.dao.UserProfileDao
import com.example.foodtracker.dao.WaterContainerDao
import com.example.foodtracker.dao.WaterIntakeDao
import com.example.foodtracker.entity.Food
import com.example.foodtracker.entity.FoodItem
import com.example.foodtracker.entity.Recipe
import com.example.foodtracker.entity.RecipeIngredient
import com.example.foodtracker.entity.UserProfile
import com.example.foodtracker.entity.WaterContainer
import com.example.foodtracker.entity.WaterIntake

@Database(
    entities = [
        Food::class,
        FoodItem::class,
        WaterContainer::class,
        WaterIntake::class,
        Recipe::class,
        RecipeIngredient::class,
        UserProfile::class
    ],
    version = 17,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun foodDao(): FoodDao
    abstract fun foodItemDao(): FoodItemDao
    abstract fun waterContainerDao(): WaterContainerDao
    abstract fun waterIntakeDao(): WaterIntakeDao
    abstract fun recipeDao(): RecipeDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "food_tracker_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys=ON")
                        }
                    })
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}