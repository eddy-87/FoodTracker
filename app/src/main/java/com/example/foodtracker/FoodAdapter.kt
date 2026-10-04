package com.example.foodtracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.entity.Food
import com.google.android.material.card.MaterialCardView

class FoodAdapter(
    private var items: List<Food>,
    private val onFoodClick: (Food) -> Unit
) : RecyclerView.Adapter<FoodAdapter.FoodViewHolder>() {

    class FoodViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sectionHeaderContainer: View = view.findViewById(R.id.sectionHeaderContainer)
        val headerText: TextView = view.findViewById(R.id.sectionHeader)
        val cardMealIcon: MaterialCardView = view.findViewById(R.id.cardMealIcon)
        val ivMealIcon: ImageView = view.findViewById(R.id.ivMealIcon)
        val nameText: TextView = view.findViewById(R.id.foodNameText)
        val quantityText: TextView = view.findViewById(R.id.quantityText)
        val caloriesText: TextView = view.findViewById(R.id.caloriesText)
        val mealColorStripe: View = view.findViewById(R.id.mealColorStripe)
        val tvMacP: TextView = view.findViewById(R.id.tvMacP)
        val tvMacC: TextView = view.findViewById(R.id.tvMacC)
        val tvMacF: TextView = view.findViewById(R.id.tvMacF)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_log, parent, false)
        return FoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: FoodViewHolder, position: Int) {
        val item = items[position]

        val isFirstItem = position == 0
        val isDifferentMeal = if (!isFirstItem) item.mealType != items[position - 1].mealType else true
        val mealColor = getMealColor(item.mealType)

        if (isFirstItem || isDifferentMeal) {
            holder.sectionHeaderContainer.visibility = View.VISIBLE
            holder.headerText.text = item.mealType.uppercase()
            holder.headerText.setTextColor(mealColor)
            holder.ivMealIcon.setImageResource(getMealIcon(item.mealType))
            holder.cardMealIcon.setCardBackgroundColor(
                Color.argb(40, Color.red(mealColor), Color.green(mealColor), Color.blue(mealColor))
            )
        } else {
            holder.sectionHeaderContainer.visibility = View.GONE
        }

        holder.mealColorStripe.setBackgroundColor(mealColor)
        holder.nameText.text = item.name
        holder.quantityText.text = "${item.quantity}g"
        holder.caloriesText.text = "${item.calories} kcal"
        holder.tvMacP.text = "P: ${item.protein.toInt()}g"
        holder.tvMacC.text = "C: ${item.carbs.toInt()}g"
        holder.tvMacF.text = "F: ${item.fat.toInt()}g"

        holder.itemView.setOnClickListener { onFoodClick(item) }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<Food>) {
        items = newItems.sortedBy { getMealPriority(it.mealType) }
        notifyDataSetChanged()
    }

    private fun getMealPriority(mealType: String): Int {
        return when (mealType.lowercase()) {
            "mic dejun", "breakfast" -> 1
            "prânz", "lunch" -> 2
            "cină", "dinner" -> 3
            else -> 4
        }
    }

    private fun getMealColor(mealType: String): Int {
        return when (mealType.lowercase()) {
            "mic dejun", "breakfast" -> Color.parseColor("#F57C00")
            "prânz", "lunch" -> Color.parseColor("#4CAF50")
            "cină", "dinner" -> Color.parseColor("#2196F3")
            else -> Color.parseColor("#F44336")
        }
    }

    private fun getMealIcon(mealType: String): Int {
        return when (mealType.lowercase()) {
            "mic dejun", "breakfast" -> R.drawable.ic_breakfast
            "prânz", "lunch" -> R.drawable.ic_lunch
            "cină", "dinner" -> R.drawable.ic_dinner
            else -> R.drawable.ic_snack
        }
    }
}
