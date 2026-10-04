package com.example.foodtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.entity.WaterIntake
import java.text.SimpleDateFormat
import java.util.Locale

class WaterLogAdapter(
    private var logs: List<WaterIntake>,
    private val onDeleteClick: (WaterIntake) -> Unit
) : RecyclerView.Adapter<WaterLogAdapter.LogViewHolder>() {

    fun updateData(newLogs: List<WaterIntake>) {
        logs = newLogs
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_water_log, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val item = logs[position]
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        holder.tvTime.text = timeFormat.format(item.timestamp)
        holder.tvAmount.text = "${item.amountMl} ml"

        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
    }

    override fun getItemCount() = logs.size

    class LogViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvAmount: TextView = view.findViewById(R.id.tvAmountLog)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDeleteLog)
    }
}