package com.example.foodtracker

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.example.foodtracker.entity.WaterContainer
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ContainerOptionsBottomSheet(
    private val container: WaterContainer,
    private val onDelete: (WaterContainer) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, containerView: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_container_options, containerView, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Eliminăm marginile albe implicite
        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

        view.findViewById<TextView>(R.id.tvSheetTitle).text = container.name

        val btnDelete = view.findViewById<View>(R.id.layoutDeleteContainer)

        // Acum poți șterge absolut orice recipient, inclusiv cele din fabrică
        btnDelete.setOnClickListener {
            onDelete(container)
            dismiss()
        }
    }
}