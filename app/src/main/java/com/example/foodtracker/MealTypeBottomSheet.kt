package com.example.foodtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView

class MealTypeBottomSheet(
    private val onMealSelected: (MealType) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_meal_type_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialCardView>(R.id.cardBreakfast).setOnClickListener {
            onMealSelected(MealType.Breakfast)
            dismiss()
        }

        view.findViewById<MaterialCardView>(R.id.cardLunch).setOnClickListener {
            onMealSelected(MealType.Lunch)
            dismiss()
        }

        view.findViewById<MaterialCardView>(R.id.cardDinner).setOnClickListener {
            onMealSelected(MealType.Dinner)
            dismiss()
        }

        view.findViewById<MaterialCardView>(R.id.cardSnack).setOnClickListener {
            onMealSelected(MealType.Snack)
            dismiss()
        }

        view.findViewById<View>(R.id.tvCancel).setOnClickListener {
            dismiss()
        }
    }
}