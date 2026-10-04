package com.example.foodtracker.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.foodtracker.entity.Recipe
import com.example.foodtracker.entity.RecipeIngredient

@Dao
interface RecipeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: Recipe): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIngredient(ingredient: RecipeIngredient)

    @Query("SELECT * FROM recipes ORDER BY createdAt DESC")
    suspend fun getAllRecipes(): List<Recipe>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun getIngredientsForRecipe(recipeId: Int): List<RecipeIngredient>

    @Delete
    suspend fun deleteRecipe(recipe: Recipe)
}
