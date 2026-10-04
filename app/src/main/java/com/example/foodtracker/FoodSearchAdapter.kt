package com.example.foodtracker.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.R
import com.example.foodtracker.entity.FoodItem

class FoodSearchAdapter(
    private var items: List<FoodItem>,
    private val onFoodClick: (FoodItem) -> Unit,
    private val onFoodLongClick: ((FoodItem) -> Unit)? = null
) : RecyclerView.Adapter<FoodSearchAdapter.SearchViewHolder>() {

    class SearchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.foodNameText)
        val calMainText: TextView = view.findViewById(R.id.caloriesMainText)
        val calKcalLabel: TextView = view.findViewById(R.id.tvKcalLabel)
        val proteinText: TextView = view.findViewById(R.id.proteinText)
        val carbsText: TextView = view.findViewById(R.id.carbsText)
        val fatText: TextView = view.findViewById(R.id.fatText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_search, parent, false)
        return SearchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = items[position]

        holder.nameText.text = item.name
        holder.calMainText.text = item.caloriesPer100g.toString()
        holder.proteinText.text = "P${item.proteinPer100g.toInt()}"
        holder.carbsText.text = "C${item.carbsPer100g.toInt()}"
        holder.fatText.text = "G${item.fatPer100g.toInt()}"

        holder.itemView.setOnClickListener { onFoodClick(item) }
        holder.itemView.setOnLongClickListener {
            onFoodLongClick?.invoke(item)
            true
        }
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<FoodItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
