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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.BarcodeScannerActivity
import com.example.foodtracker.MainActivity
import com.example.foodtracker.R
import com.example.foodtracker.adapter.FoodSearchAdapter
import com.example.foodtracker.entity.cleanForSearch
import com.example.foodtracker.db.AppDatabase
import com.example.foodtracker.entity.Food
import com.example.foodtracker.entity.FoodItem
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FoodSelectFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: FoodSearchAdapter
    private var mealType: String = ""
    private var selectedDate: Long = 0L

    private var fullFoodList: List<FoodItem> = emptyList()
    private var searchView: SearchView? = null

    private val barcodeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val barcode = result.data?.getStringExtra("SCANNED_BARCODE")
            if (!barcode.isNullOrEmpty()) {
                handleScannedBarcode(barcode)
            }
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_food_select, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())
        mealType = arguments?.getString("MEAL_TYPE") ?: "Gustare"
        selectedDate = arguments?.getLong("SELECTED_DATE") ?: System.currentTimeMillis()

        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        val recyclerView = view.findViewById<RecyclerView>(R.id.selectFoodRecyclerView)
        adapter = FoodSearchAdapter(
            items = emptyList(),
            onFoodClick = { selectedItem -> showQuantityDialog(selectedItem) },
            onFoodLongClick = { selectedItem -> showDeleteFoodItemDialog(selectedItem) }
        )
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter

        loadAllFood()

        searchView = view.findViewById(R.id.searchFoodView)
        searchView?.setIconifiedByDefault(false)

        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView?.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterList(newText ?: "")
                return true
            }
        })

        val createFab = view.findViewById<ExtendedFloatingActionButton>(R.id.createNewFoodFab)
        createFab.text = "Creează"
        createFab.setOnClickListener {
            showCreateOptionsDialog()
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(createFab) { v, insets ->
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            val extraBottom = (24 * resources.displayMetrics.density).toInt()
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.bottomMargin = navBar.bottom + extraBottom
            v.layoutParams = params
            insets
        }

        val btnScan = view.findViewById<android.widget.ImageButton>(R.id.btnScanBarcode)
        btnScan.setOnClickListener {
            barcodeLauncher.launch(Intent(requireContext(), BarcodeScannerActivity::class.java))
        }
    }


    private fun showCreateOptionsDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.bottom_sheet_create_options, null)
        bottomSheetDialog.setContentView(view)

        (view.parent as android.view.View).setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val btnFood = view.findViewById<View>(R.id.btnOptFood)
        val btnRecipe = view.findViewById<View>(R.id.btnOptRecipe)

        btnFood.setOnClickListener {
            bottomSheetDialog.dismiss()
            showCreateCustomFoodDialog(null)
        }

        btnRecipe.setOnClickListener {
            bottomSheetDialog.dismiss()
            openCreateRecipeFragment()
        }

        bottomSheetDialog.show()
    }

    private fun openCreateRecipeFragment() {
        val fragment = CreateRecipeFragment()
        parentFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun handleScannedBarcode(barcode: String) {
        lifecycleScope.launch {
            val foodFound = withContext(Dispatchers.IO) {
                db.foodItemDao().getFoodByBarcode(barcode)
            }

            if (foodFound != null) {
                showQuantityDialog(foodFound)
            } else {
                Toast.makeText(context, "Produs necunoscut. Te rugăm să-l adaugi!", Toast.LENGTH_LONG).show()
                showCreateCustomFoodDialog(barcode)
            }
        }
    }

    private fun loadAllFood() {
        lifecycleScope.launch {
            fullFoodList = withContext(Dispatchers.IO) {
                db.foodItemDao().getAll()
            }
            adapter.updateList(fullFoodList)
        }
    }

    private fun filterList(query: String) {
        val cleanQuery = query.cleanForSearch()

        val filteredList = if (cleanQuery.isEmpty()) {
            fullFoodList
        } else {
            fullFoodList.filter { item ->
                item.name.cleanForSearch().contains(cleanQuery) || item.barcode == cleanQuery
            }
        }
        adapter.updateList(filteredList)
    }

    private fun showCreateCustomFoodDialog(scannedBarcode: String?) {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_custom_food, null)
        bottomSheetDialog.setContentView(view)
        (view.parent as android.view.View).setBackgroundColor(android.graphics.Color.TRANSPARENT)
        bottomSheetDialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val nameInput = view.findViewById<EditText>(R.id.etCustomName)
        val rgUnit = view.findViewById<RadioGroup>(R.id.rgUnit)
        val etPortionSize = view.findViewById<EditText>(R.id.etPortionSize)
        val calInput = view.findViewById<EditText>(R.id.etCustomCal)
        val protInput = view.findViewById<EditText>(R.id.etCustomProt)
        val carbInput = view.findViewById<EditText>(R.id.etCustomCarb)
        val fatInput = view.findViewById<EditText>(R.id.etCustomFat)

        view.findViewById<TextView>(R.id.btnCancelCustom).setOnClickListener {
            bottomSheetDialog.dismiss()
        }

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
                saveNewFoodItem(newItem)
                bottomSheetDialog.dismiss()
            } else {
                Toast.makeText(context, "Introdu măcar Nume și Calorii!", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetDialog.show()
    }

    private fun showDeleteFoodItemDialog(item: FoodItem) {
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(view).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        view.findViewById<android.widget.TextView>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<android.widget.TextView>(R.id.btnConfirmDelete).setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.foodItemDao().delete(item) }
                fullFoodList = fullFoodList.filter { it.id != item.id }
                adapter.updateList(fullFoodList)
                Toast.makeText(context, "${item.name} șters!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveNewFoodItem(item: FoodItem) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    db.foodItemDao().insertAll(listOf(item))
                }
                Toast.makeText(context, "Salvat local!", Toast.LENGTH_SHORT).show()
                loadAllFood()
                showQuantityDialog(item)
            } catch (e: Exception) {
                showQuantityDialog(item)
            }
        }
    }

    private fun showQuantityDialog(item: FoodItem) {
        val view = layoutInflater.inflate(R.layout.dialog_quantity, null)
        val builder = AlertDialog.Builder(requireContext()).setView(view)
        val dialog = builder.create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val title = view.findViewById<TextView>(R.id.tvDialogTitle)
        val spinner = view.findViewById<Spinner>(R.id.spinnerMeasurement)
        val input = view.findViewById<EditText>(R.id.etQuantityInput)
        val tvUnitLabel = view.findViewById<TextView>(R.id.tvUnitLabel)

        val tvCal = view.findViewById<TextView>(R.id.tvLiveCalories)
        val tvProt = view.findViewById<TextView>(R.id.tvProt)
        val tvCarb = view.findViewById<TextView>(R.id.tvCarb)
        val tvFat = view.findViewById<TextView>(R.id.tvFat)

        title.text = item.name

        // Configurare Dropdown (Spinner)
        val options = mutableListOf(if (item.unit == "ml") "Mililitri (ml)" else "Grame (g)")
        if (item.portionSize > 0) {
            options.add("Porție (${item.portionSize}${item.unit})")
        }

        val spinnerAdapter = ArrayAdapter(requireContext(), R.layout.spinner_item_dark, options)
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_item_dark) // Asta colorează lista extinsă
        spinner.adapter = spinnerAdapter

        if (item.portionSize > 0) {
            spinner.setSelection(1)
        }

        var isPortionSelected = false

        fun updateLiveStats() {
            var qty = input.text.toString().toIntOrNull() ?: 0
            if (isPortionSelected) qty *= item.portionSize

            val ratio = qty / 100.0
            val cal = (item.caloriesPer100g * ratio).toInt()
            val p = (item.proteinPer100g * ratio).toInt()
            val c = (item.carbsPer100g * ratio).toInt()
            val f = (item.fatPer100g * ratio).toInt()

            tvCal.text = "$cal kcal"
            tvProt.text = "${p}g"
            tvCarb.text = "${c}g"
            tvFat.text = "${f}g"
        }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                isPortionSelected = position == 1
                input.setText(if (isPortionSelected) "1" else "100")
                input.setSelection(input.text.length)

                // Actualizăm textul de lângă EditText (ex: "g", "ml" sau "porții")
                tvUnitLabel.text = if (isPortionSelected) "porții" else item.unit

                updateLiveStats()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                updateLiveStats()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        view.findViewById<TextView>(R.id.btnCancelQty).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnAddQty).setOnClickListener {
            var qty = input.text.toString().toIntOrNull() ?: 0
            if (qty > 0) {
                if (isPortionSelected) qty *= item.portionSize
                saveFoodLog(item, qty)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun saveFoodLog(item: FoodItem, quantity: Int) {
        lifecycleScope.launch {
            val factor = quantity / 100.0
            val foodLog = Food(
                name = item.name,
                calories = (item.caloriesPer100g * factor).toInt(),
                protein = item.proteinPer100g * factor,
                carbs = item.carbsPer100g * factor,
                fat = item.fatPer100g * factor,
                quantity = quantity,
                date = selectedDate,
                mealType = mealType,
                foodItemId = item.id
            )

            withContext(Dispatchers.IO) {
                db.foodDao().insert(foodLog)
                db.foodItemDao().update(item.copy(lastUsed = System.currentTimeMillis()))
            }
            Toast.makeText(context, "Adăugat!", Toast.LENGTH_SHORT).show()

            (requireActivity() as? MainActivity)?.loadDailyFoodLog()
            parentFragmentManager.popBackStack()
        }
    }
}