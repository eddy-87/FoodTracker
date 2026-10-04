package com.example.foodtracker

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.entity.WaterContainer
import com.google.android.material.card.MaterialCardView

class WaterContainerAdapter(
    private var containers: List<WaterContainer>,
    private val onContainerClick: (WaterContainer) -> Unit,
    private val onAddClick: () -> Unit,
    private val onLongClick: (WaterContainer) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD = 1
    }

    fun updateData(newContainers: List<WaterContainer>) {
        containers = newContainers
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = containers.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position < containers.size) TYPE_ITEM else TYPE_ADD
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_water_container, parent, false)
            ContainerViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_water_add, parent, false)
            AddViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ContainerViewHolder) {
            val item = containers[position]
            holder.bind(item)
        } else if (holder is AddViewHolder) {
            holder.itemView.setOnClickListener { onAddClick() }
        }
    }

    inner class ContainerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvContainerName)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvContainerAmount)
        private val card: MaterialCardView = itemView.findViewById(R.id.cardContainer)

        fun bind(item: WaterContainer) {
            tvName.text = item.name
            tvAmount.text = "${item.amountMl} ml"

            // Toate cardurile vor avea același gri
            card.setCardBackgroundColor(Color.parseColor("#2C2C2E"))

            itemView.setOnClickListener { onContainerClick(item) }
            itemView.setOnLongClickListener {
                onLongClick(item)
                true
            }
        }
    }

    inner class AddViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}