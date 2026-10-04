package com.example.foodtracker.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.BarcodeScannerActivity
import com.example.foodtracker.R
import com.example.foodtracker.adapter.FoodSearchAdapter
import com.example.foodtracker.db.AppDatabase
import com.example.foodtracker.entity.FoodItem
import com.example.foodtracker.entity.Recipe
import com.example.foodtracker.entity.RecipeIngredient
import com.example.foodtracker.entity.cleanForSearch
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.RadioGroup

data class IngredientEntry(val food: FoodItem, var quantity: Int)

class CreateRecipeFragment : Fragment() {

    private lateinit var db: AppDatabase
    private val ingredients = mutableListOf<IngredientEntry>()
    private lateinit var ingredientsAdapter: IngredientEntryAdapter

    private lateinit var tvRecCal: TextView
    private lateinit var tvRecProt: TextView
    private lateinit var tvRecCarb: TextView
    private lateinit var tvRecFat: TextView
    private lateinit var tvRecTotalWeight: TextView
    private lateinit var etRecipeName: EditText

    private var fullFoodList: List<FoodItem> = emptyList()
    private lateinit var searchAdapter: FoodSearchAdapter

    private val barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrEmpty()) {
                handleScannedIngredient(barcode)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_create_recipe, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = AppDatabase.getDatabase(requireContext())

        tvRecCal = view.findViewById(R.id.tvRecCal)
        tvRecProt = view.findViewById(R.id.tvRecProt)
        tvRecCarb = view.findViewById(R.id.tvRecCarb)
        tvRecFat = view.findViewById(R.id.tvRecFat)
        tvRecTotalWeight = view.findViewById(R.id.tvRecTotalWeight)
        etRecipeName = view.findViewById(R.id.etRecipeName)

        view.findViewById<ImageButton>(R.id.btnBackRecipe).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val rvIngredients = view.findViewById<RecyclerView>(R.id.rvIngredients)
        rvIngredients.layoutManager = LinearLayoutManager(context)
        ingredientsAdapter = IngredientEntryAdapter(ingredients) { ingToRemove ->
            ingredients.remove(ingToRemove)
            ingredientsAdapter.notifyDataSetChanged()
            updateLiveStats()
        }
        rvIngredients.adapter = ingredientsAdapter

        view.findViewById<View>(R.id.btnAddIngredient).setOnClickListener {
            showSearchIngredientDialog()
        }

        val btnSave = view.findViewById<View>(R.id.btnSaveRecipe)
        btnSave.setOnClickListener {
            saveFinalRecipe()
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(btnSave) { v, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val extraBottom = (24 * resources.displayMetrics.density).toInt()
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = navBar.bottom + extraBottom
            v.layoutParams = params
            insets
        }

        loadAllFood()
    }

    private fun updateLiveStats() {
        val totalWeight = ingredients.sumOf { it.quantity }
        tvRecTotalWeight.text = "${totalWeight}g total"

        if (totalWeight == 0) {
            tvRecCal.text = "0 kcal"; tvRecProt.text = "P: 0g"; tvRecCarb.text = "C: 0g"; tvRecFat.text = "F: 0g"
            return
        }

        var tCal = 0.0; var tProt = 0.0; var tCarb = 0.0; var tFat = 0.0

        for (ing in ingredients) {
            val ratio = ing.quantity / 100.0
            tCal += ing.food.caloriesPer100g * ratio
            tProt += ing.food.proteinPer100g * ratio
            tCarb += ing.food.carbsPer100g * ratio
            tFat += ing.food.fatPer100g * ratio
        }

        val per100Cal = ((tCal / totalWeight) * 100).toInt()
        val per100Prot = ((tProt / totalWeight) * 100).toInt()
        val per100Carb = ((tCarb / totalWeight) * 100).toInt()
        val per100Fat = ((tFat / totalWeight) * 100).toInt()

        tvRecCal.text = "$per100Cal kcal"
        tvRecProt.text = "P: ${per100Prot}g"
        tvRecCarb.text = "C: ${per100Carb}g"
        tvRecFat.text = "F: ${per100Fat}g"
    }

    private fun loadAllFood() {
        lifecycleScope.launch {
            fullFoodList = withContext(Dispatchers.IO) { db.foodItemDao().getAll() }
        }
    }

    // Noul meniu complet pentru Căutare / Adăugare
    private fun showSearchIngredientDialog() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_search_ingredient, null)
        bottomSheetDialog.setContentView(view)

        // Forțăm deschiderea pe tot ecranul (mai lat, fără margini)
        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
        BottomSheetBehavior.from(bottomSheet!!).state = BottomSheetBehavior.STATE_EXPANDED
        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)

        val etSearch = view.findViewById<EditText>(R.id.etSearchIng)
        val btnScan = view.findViewById<ImageButton>(R.id.btnScanIng)
        val btnCreate = view.findViewById<View>(R.id.btnCreateNewIngLocally)
        val rvSearch = view.findViewById<RecyclerView>(R.id.rvIngSearch)

        rvSearch.layoutManager = LinearLayoutManager(context)
        searchAdapter = FoodSearchAdapter(fullFoodList, onFoodClick = { selectedFood ->
            bottomSheetDialog.dismiss()
            showQuantityForIngredient(selectedFood)
        })
        rvSearch.adapter = searchAdapter

        // Funcția de search funcțională
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val q = s.toString().cleanForSearch()
                val filtered = if (q.isEmpty()) fullFoodList else fullFoodList.filter { it.name.cleanForSearch().contains(q) }
                searchAdapter.updateList(filtered)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnScan.setOnClickListener {
            val intent = Intent(requireContext(), BarcodeScannerActivity::class.java)
            barcodeLauncher.launch(intent)
            bottomSheetDialog.dismiss()
        }

        // Butonul de a crea produs direct de aici
        btnCreate.setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateCustomFoodDialog(null)
        }

        bottomSheetDialog.show()
    }

    // Funcția care adaugă în DB și după te dă să alegi cantitatea
    private fun showCreateCustomFoodDialog(scannedBarcode: String?) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_custom_food, null)
        bottomSheetDialog.setContentView(view)
        (view.parent as View).setBackgroundColor(Color.TRANSPARENT)
        bottomSheetDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val nameInput = view.findViewById<EditText>(R.id.etCustomName)
        val rgUnit = view.findViewById<RadioGroup>(R.id.rgUnit)
        val etPortionSize = view.findViewById<EditText>(R.id.etPortionSize)
        val calInput = view.findViewById<EditText>(R.id.etCustomCal)
        val protInput = view.findViewById<EditText>(R.id.etCustomProt)
        val carbInput = view.findViewById<EditText>(R.id.etCustomCarb)
        val fatInput = view.findViewById<EditText>(R.id.etCustomFat)

        view.findViewById<TextView>(R.id.btnCancelCustom).setOnClickListener { bottomSheetDialog.dismiss() }

        view.findViewById<TextView>(R.id.btnSaveCustom).setOnClickListener {
            if (nameInput.text.isNotEmpty() && calInput.text.isNotEmpty()) {
                val unit = if (rgUnit.checkedRadioButtonId == R.id.rbMl) "ml" else "g"
                val portion = etPortionSize.text.toString().toIntOrNull() ?: 0

                val newItem = FoodItem(
                    name = nameInput.text.toString(),
                    caloriesPer100g = calInput.text.toString().toIntOrNull() ?: 0,
                    proteinPer100g = protInput.text.toString().toDoubleOrNull() ?: 0.0,
                    carbsPer100g = carbInput.text.toString().toDoubleOrNull() ?: 0.0,
                    fatPer100g = fatInput.text.toString().toDoubleOrNull() ?: 0.0,
                    barcode = scannedBarcode,
                    unit = unit,
                    portionSize = portion
                )
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.foodItemDao().insertAll(listOf(newItem)) }
                    loadAllFood()
                    Toast.makeText(context, "Creat și adăugat!", Toast.LENGTH_SHORT).show()
                    bottomSheetDialog.dismiss()
                    showQuantityForIngredient(newItem)
                }
            } else {
                Toast.makeText(context, "Introdu măcar Nume și Calorii!", Toast.LENGTH_SHORT).show()
            }
        }
        bottomSheetDialog.show()
    }

    private fun handleScannedIngredient(barcode: String) {
        lifecycleScope.launch {
            val foodFound = withContext(Dispatchers.IO) { db.foodItemDao().getFoodByBarcode(barcode) }
            if (foodFound != null) {
                showQuantityForIngredient(foodFound)
            } else {
                Toast.makeText(context, "Cod necunoscut. Haide să-l creăm!", Toast.LENGTH_LONG).show()
                showCreateCustomFoodDialog(barcode)
            }
        }
    }

    private fun showQuantityForIngredient(food: FoodItem) {
        val view = layoutInflater.inflate(R.layout.dialog_quantity, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val title = view.findViewById<TextView>(R.id.tvDialogTitle)
        val spinner = view.findViewById<Spinner>(R.id.spinnerMeasurement)
        val input = view.findViewById<EditText>(R.id.etQuantityInput)
        val tvUnitLabel = view.findViewById<TextView>(R.id.tvUnitLabel)
        val tvCal = view.findViewById<TextView>(R.id.tvLiveCalories)
        val tvProt = view.findViewById<TextView>(R.id.tvProt)
        val tvCarb = view.findViewById<TextView>(R.id.tvCarb)
        val tvFat = view.findViewById<TextView>(R.id.tvFat)

        title.text = food.name

        val options = mutableListOf(if (food.unit == "ml") "Mililitri (ml)" else "Grame (g)")
        if (food.portionSize > 0) {
            options.add("Porție (${food.portionSize}${food.unit})")
        }

        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_dark, options)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item_dark)
        spinner.adapter = spinnerAdapter

        if (food.portionSize > 0) {
            spinner.setSelection(1)
        }

        var isPortionSelected = false

        fun updateDialogLiveStats() {
            var qty = input.text.toString().toIntOrNull() ?: 0
            if (isPortionSelected) qty *= food.portionSize
            val ratio = qty / 100.0

            tvCal.text = "${(food.caloriesPer100g * ratio).toInt()} kcal"
            tvProt.text = "${(food.proteinPer100g * ratio).toInt()}g"
            tvCarb.text = "${(food.carbsPer100g * ratio).toInt()}g"
            tvFat.text = "${(food.fatPer100g * ratio).toInt()}g"
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                isPortionSelected = position == 1
                input.setText(if (isPortionSelected) "1" else "100")
                input.setSelection(input.text.length)
                tvUnitLabel.text = if (isPortionSelected) "porții" else food.unit
                updateDialogLiveStats()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateDialogLiveStats() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        view.findViewById<TextView>(R.id.btnCancelQty).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnAddQty).setOnClickListener {
            var qty = input.text.toString().toIntOrNull() ?: 0
            if (qty > 0) {
                if (isPortionSelected) qty *= food.portionSize
                ingredients.add(IngredientEntry(food, qty))
                ingredientsAdapter.notifyDataSetChanged()
                updateLiveStats()
                dialog.dismiss()
            }
        }
        dialog.show()
        input.requestFocus()
    }

    private fun saveFinalRecipe() {
        val name = etRecipeName.text.toString().trim()
        if (name.isEmpty()) { Toast.makeText(context, "Dă un nume rețetei!", Toast.LENGTH_SHORT).show(); return }
        if (ingredients.isEmpty()) { Toast.makeText(context, "Adaugă măcar un ingredient!", Toast.LENGTH_SHORT).show(); return }

        val totalWeight = ingredients.sumOf { it.quantity }
        var tCal = 0.0; var tProt = 0.0; var tCarb = 0.0; var tFat = 0.0
        for (ing in ingredients) {
            val ratio = ing.quantity / 100.0
            tCal += ing.food.caloriesPer100g * ratio
            tProt += ing.food.proteinPer100g * ratio
            tCarb += ing.food.carbsPer100g * ratio
            tFat += ing.food.fatPer100g * ratio
        }

        val cal100 = ((tCal / totalWeight) * 100).toInt()
        val prot100 = (tProt / totalWeight) * 100
        val carb100 = (tCarb / totalWeight) * 100
        val fat100 = (tFat / totalWeight) * 100

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                // Salvăm în tabelele relaționale
                val recipe = Recipe(
                    name = name,
                    caloriesPer100g = cal100,
                    proteinPer100g = prot100,
                    carbsPer100g = carb100,
                    fatPer100g = fat100,
                    totalWeightG = totalWeight
                )
                val recipeId = db.recipeDao().insertRecipe(recipe).toInt()
                for (ing in ingredients) {
                    db.recipeDao().insertIngredient(
                        RecipeIngredient(
                            recipeId = recipeId,
                            foodItemId = ing.food.id,
                            quantityGrams = ing.quantity
                        )
                    )
                }

                // Salvăm și în food_items ca să apară în lista de alimente
                val recipeFood = FoodItem(
                    name = "$name (Rețetă)",
                    caloriesPer100g = cal100,
                    proteinPer100g = prot100,
                    carbsPer100g = carb100,
                    fatPer100g = fat100,
                    lastUsed = System.currentTimeMillis()
                )
                db.foodItemDao().insertAll(listOf(recipeFood))
            }
            Toast.makeText(context, "Rețeta a fost salvată!", Toast.LENGTH_LONG).show()
            parentFragmentManager.popBackStack()
        }
    }

    inner class IngredientEntryAdapter(
        private val items: List<IngredientEntry>,
        private val onRemove: (IngredientEntry) -> Unit
    ) : RecyclerView.Adapter<IngredientEntryAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.tvIngName)
            val details: TextView = view.findViewById(R.id.tvIngDetails)
            val btnRemove: ImageButton = view.findViewById(R.id.btnRemoveIng)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recipe_ingredient, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ing = items[position]
            holder.name.text = ing.food.name
            val cal = (ing.food.caloriesPer100g * (ing.quantity / 100.0)).toInt()
            holder.details.text = "${ing.quantity}g • $cal kcal"
            holder.btnRemove.setOnClickListener { onRemove(ing) }
        }
        override fun getItemCount() = items.size
    }
}