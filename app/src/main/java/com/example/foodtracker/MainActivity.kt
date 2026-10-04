package com.example.foodtracker

import android.app.AlertDialog
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.foodtracker.db.AppDatabase
import com.example.foodtracker.entity.Food
import com.example.foodtracker.entity.FoodItem
import com.example.foodtracker.entity.WaterContainer
import com.example.foodtracker.entity.WaterIntake
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt
import com.example.foodtracker.ui.FoodSelectFragment

class MainActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    // Adapters
    private lateinit var adapter: FoodAdapter
    private lateinit var waterAdapter: WaterContainerAdapter
    private lateinit var waterLogAdapter: WaterLogAdapter

    // UI Elements - Food
    private lateinit var tvTotalCalories: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProtein: TextView
    private lateinit var tvCarbs: TextView
    private lateinit var tvFat: TextView
    private lateinit var labelProtein: TextView
    private lateinit var labelCarbs: TextView
    private lateinit var labelFat: TextView

    // UI Elements - Navigation
    private lateinit var tvDateDisplay: TextView
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton

    // UI Elements - Food empty state
    private lateinit var emptyFoodState: android.view.View

    // UI Elements - Water
    private lateinit var tvWaterCount: TextView
    private lateinit var waterProgressBar: ProgressBar
    private lateinit var rvWaterContainers: RecyclerView
    private lateinit var rvWaterHistory: RecyclerView

    // UI Elements - Pași
    private lateinit var tvStepsCount: TextView
    private lateinit var tvStepsCalories: TextView
    private lateinit var stepsProgressBar: ProgressBar
    private lateinit var btnEditSteps: ImageButton

    // Logic Variables
    private var waterGoal = 2000
    private val currentCalendar = Calendar.getInstance()
    private var searchJob: Job? = null

    // Goals
    private var calorieGoal = 2000
    private var proteinGoal = 150
    private var carbGoal = 200
    private var fatGoal = 70

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = AppDatabase.getDatabase(this)

        // AICI APELĂM FUNCȚIA CARE ADAUGĂ ALIMENTELE
        checkAndSeedDatabase()

        // Initializare UI Mâncare și Navigare
        tvTotalCalories = findViewById(R.id.textTotalCalories)
        progressBar = findViewById(R.id.caloriesProgressBar)
        tvProtein = findViewById(R.id.textProtein)
        tvCarbs = findViewById(R.id.textCarbs)
        tvFat = findViewById(R.id.textFat)
        labelProtein = findViewById(R.id.labelProtein)
        labelCarbs = findViewById(R.id.labelCarbs)
        labelFat = findViewById(R.id.labelFat)
        tvDateDisplay = findViewById(R.id.textDateDisplay)
        btnPrev = findViewById(R.id.btnPrevDay)
        btnNext = findViewById(R.id.btnNextDay)

        // Initializare UI Apă
        emptyFoodState = findViewById(R.id.emptyFoodState)
        tvWaterCount = findViewById(R.id.tvWaterCount)
        waterProgressBar = findViewById(R.id.waterProgressBar)
        rvWaterContainers = findViewById(R.id.rvWaterContainers)
        rvWaterHistory = findViewById(R.id.rvWaterHistory)

        // Initializare UI Pași
        tvStepsCount = findViewById(R.id.tvStepsCount)
        tvStepsCalories = findViewById(R.id.tvStepsCalories)
        stepsProgressBar = findViewById(R.id.stepsProgressBar)
        btnEditSteps = findViewById(R.id.btnEditSteps)
        btnEditSteps.setOnClickListener { showManualStepsDialog() }

        loadUserGoals()

        // Setup Butoane Navigare Data
        btnPrev.setOnClickListener { changeDate(-1) }
        btnNext.setOnClickListener { changeDate(1) }
        tvDateDisplay.setOnClickListener { showDatePicker() }

        // Setup Butoane Sus (Profil / Stats)
        findViewById<ImageButton>(R.id.btnProfile).setOnClickListener {
            startActivity(android.content.Intent(this, ProfileActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btnStats).setOnClickListener {
            startActivity(android.content.Intent(this, StatsActivity::class.java))
        }

        // --- SETUP LISTA MÂNCARE ---
        val recyclerView = findViewById<RecyclerView>(R.id.foodRecyclerView)
        adapter = FoodAdapter(emptyList()) { foodClicked -> showEditFoodDialog(foodClicked) }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // --- SETUP LISTE APĂ ---
        setupWaterUI()

        // --- SETUP FAB ---
        val fab = findViewById<ExtendedFloatingActionButton>(R.id.addFoodFab)
        fab.setOnClickListener {
            MealTypeBottomSheet { mealType -> openFoodSelect(mealType) }.show(supportFragmentManager, "MealTypeBottomSheet")
        }

        updateDateDisplay()
        loadDailyFoodLog()
        loadDailyWater()
        loadTodaySteps()
    }

    override fun onResume() {
        super.onResume()
        loadUserGoals()
        loadDailyWater()
        loadDailyFoodLog()
        loadTodaySteps()
    }

    private fun setupWaterUI() {
        rvWaterContainers.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        waterAdapter = WaterContainerAdapter(
            containers = emptyList(),
            onContainerClick = { container -> addWaterLog(container.amountMl) },
            onAddClick = { showAddContainerDialog() },
            onLongClick = { container ->
                ContainerOptionsBottomSheet(container) { containerToDelete ->
                    deleteCustomContainer(containerToDelete)
                }.show(supportFragmentManager, "ContainerOptions")
            }
        )
        rvWaterContainers.adapter = waterAdapter
        loadWaterContainers()

        rvWaterHistory.layoutManager = LinearLayoutManager(this)
        waterLogAdapter = WaterLogAdapter(emptyList()) { logToDelete -> deleteWaterLog(logToDelete) }
        rvWaterHistory.adapter = waterLogAdapter
    }

    private fun loadDailyWater() {
        lifecycleScope.launch {
            val (start, end) = getStartEndForCurrentDate()
            val logs = withContext(Dispatchers.IO) { db.waterIntakeDao().getLogsForDate(start, end) }
            waterLogAdapter.updateData(logs)

            val total = withContext(Dispatchers.IO) { db.waterIntakeDao().getTotalWaterForDate(start, end) ?: 0 }
            tvWaterCount.text = "$total / $waterGoal ml"
            waterProgressBar.progress = ((total.toFloat() / waterGoal) * 100).toInt().coerceAtMost(100)
        }
    }

    private fun addWaterLog(amount: Int) {
        lifecycleScope.launch {
            val timestamp = if (isToday(currentCalendar)) {
                System.currentTimeMillis()
            } else {
                currentCalendar.clone().let {
                    (it as Calendar).set(Calendar.HOUR_OF_DAY, 12)
                    it.set(Calendar.MINUTE, 0)
                    it.timeInMillis
                }
            }
            val log = WaterIntake(amountMl = amount, timestamp = timestamp)
            withContext(Dispatchers.IO) { db.waterIntakeDao().insert(log) }
            loadDailyWater()
            Toast.makeText(this@MainActivity, "+$amount ml", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteWaterLog(log: WaterIntake) {
        val view = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        // Fundal transparent ca să se vadă colțurile rotunjite
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        view.findViewById<TextView>(R.id.btnCancelDelete).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnConfirmDelete).setOnClickListener {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { db.waterIntakeDao().delete(log) }
                loadDailyWater()
                Toast.makeText(this@MainActivity, "Șters!", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun loadWaterContainers() {
        lifecycleScope.launch {
            val containers = withContext(Dispatchers.IO) { db.waterContainerDao().getAllContainers() }
            waterAdapter.updateData(containers)
        }
    }

    private fun saveNewContainer(name: String, amount: Int) {
        lifecycleScope.launch {
            val newContainer = WaterContainer(name = name, amountMl = amount, isDefault = false)
            withContext(Dispatchers.IO) { db.waterContainerDao().insert(newContainer) }
            loadWaterContainers()
        }
    }

    private fun deleteCustomContainer(container: WaterContainer) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.waterContainerDao().delete(container) }
            loadWaterContainers()
        }
    }

    private fun showAddContainerDialog() {
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_water, null)
        bottomSheetDialog.setContentView(view)

        (view.parent as android.view.View).setBackgroundColor(android.graphics.Color.TRANSPARENT)

        val etName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWaterName)
        val etAmount = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etWaterAmount)
        val btnSave = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveWater)
        view.findViewById<android.widget.TextView>(R.id.btnCancelAddContainer).setOnClickListener { bottomSheetDialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etName.text?.toString() ?: ""
            val amountStr = etAmount.text?.toString() ?: ""
            if (name.isNotEmpty() && amountStr.isNotEmpty()) {
                val amt = amountStr.toIntOrNull()
                if (amt != null && amt > 0) {
                    saveNewContainer(name, amt)
                    bottomSheetDialog.dismiss()
                }
            } else {
                Toast.makeText(this, "Completează ambele câmpuri", Toast.LENGTH_SHORT).show()
            }
        }
        bottomSheetDialog.show()
    }

    private fun getStartEndForCurrentDate(): Pair<Long, Long> {
        val startCal = currentCalendar.clone() as Calendar
        startCal.set(Calendar.HOUR_OF_DAY, 0)
        startCal.set(Calendar.MINUTE, 0)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        val endCal = startCal.clone() as Calendar
        endCal.add(Calendar.DAY_OF_YEAR, 1)
        endCal.add(Calendar.MILLISECOND, -1)

        return Pair(startCal.timeInMillis, endCal.timeInMillis)
    }

    private fun isToday(cal: Calendar): Boolean {
        val today = Calendar.getInstance()
        return cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }

    fun loadDailyFoodLog() {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            val (start, end) = getStartEndForCurrentDate()

            val foods = withContext(Dispatchers.IO) { db.foodDao().getFoodsByDate(start, end) }

            val totalCal = foods.sumOf { it.calories }
            val totalProt = foods.sumOf { it.protein }
            val totalCarbs = foods.sumOf { it.carbs }
            val totalFat = foods.sumOf { it.fat }

            tvTotalCalories.text = "$totalCal / $calorieGoal kcal"
            progressBar.max = calorieGoal
            progressBar.progress = totalCal

            val goalType = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                .getString("GOAL_TYPE", "Maintain") ?: "Maintain"
            val overCalorieColor = if (goalType == "Gain")
                android.graphics.Color.parseColor("#4CAF50")  // verde = surplus dorit la masă
            else
                android.graphics.Color.parseColor("#F44336")  // roșu = depășire nedorită
            if (totalCal > calorieGoal) {
                progressBar.progressDrawable.setTint(overCalorieColor)
            } else {
                progressBar.progressDrawable.setTint(getColor(R.color.purple_500))
            }

            tvProtein.text = "${totalProt.roundToInt()} / ${proteinGoal}g"
            tvCarbs.text = "${totalCarbs.roundToInt()} / ${carbGoal}g"
            tvFat.text = "${totalFat.roundToInt()} / ${fatGoal}g"

            val green = android.graphics.Color.parseColor("#00C853")
            val protColor = if (totalProt >= proteinGoal) green else android.graphics.Color.parseColor("#2196F3")
            val carbColor = if (totalCarbs >= carbGoal) green else android.graphics.Color.parseColor("#FF9800")
            val fatColor  = if (totalFat  >= fatGoal)  green else android.graphics.Color.parseColor("#F44336")

            tvProtein.setTextColor(protColor);  labelProtein.setTextColor(protColor)
            tvCarbs.setTextColor(carbColor);    labelCarbs.setTextColor(carbColor)
            tvFat.setTextColor(fatColor);       labelFat.setTextColor(fatColor)

            adapter.updateData(foods)
            emptyFoodState.visibility = if (foods.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun deleteFood(food: Food) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { db.foodDao().delete(food) }
            loadDailyFoodLog()
            Toast.makeText(this@MainActivity, "Șters!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditFoodDialog(food: Food) {
        val view = layoutInflater.inflate(R.layout.dialog_quantity, null)
        val dialog = AlertDialog.Builder(this).setView(view).create()

        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        val title = view.findViewById<TextView>(R.id.tvDialogTitle)
        val input = view.findViewById<EditText>(R.id.etQuantityInput)
        val tvCal = view.findViewById<TextView>(R.id.tvLiveCalories)
        val tvProt = view.findViewById<TextView>(R.id.tvProt)
        val tvCarb = view.findViewById<TextView>(R.id.tvCarb)
        val tvFat = view.findViewById<TextView>(R.id.tvFat)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancelQty)
        val btnAdd = view.findViewById<TextView>(R.id.btnAddQty)

        title.text = "Editează: ${food.name}"
        input.setText(food.quantity.toString())
        input.setSelection(input.text.length)

        val calPer100g = if (food.quantity > 0) (food.calories.toDouble() / food.quantity) * 100 else 0.0
        val protPer100g = if (food.quantity > 0) (food.protein / food.quantity) * 100 else 0.0
        val carbPer100g = if (food.quantity > 0) (food.carbs / food.quantity) * 100 else 0.0
        val fatPer100g = if (food.quantity > 0) (food.fat / food.quantity) * 100 else 0.0

        fun updateLiveStats(qtyStr: String) {
            val qty = qtyStr.toIntOrNull() ?: 0
            val ratio = qty / 100.0

            val cal = (calPer100g * ratio).toInt()
            val p = (protPer100g * ratio).toInt()
            val c = (carbPer100g * ratio).toInt()
            val f = (fatPer100g * ratio).toInt()

            tvCal.text = "$cal kcal"
            tvProt.text = "${p}g"
            tvCarb.text = "${c}g"
            tvFat.text = "${f}g"
        }

        updateLiveStats(food.quantity.toString())

        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateLiveStats(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnCancel.text = "Șterge"
        btnCancel.setTextColor(android.graphics.Color.parseColor("#F44336"))
        btnCancel.setOnClickListener {
            deleteFood(food)
            dialog.dismiss()
        }

        btnAdd.text = "Actualizează"
        btnAdd.setOnClickListener {
            val newQty = input.text.toString().toIntOrNull()
            if (newQty != null && newQty > 0) {
                updateFoodQuantity(food, newQty)
                dialog.dismiss()
            }
        }

        dialog.show()
        input.requestFocus()
    }

    private fun updateFoodQuantity(food: Food, newQty: Int) {
        lifecycleScope.launch {
            val ratio = newQty.toDouble() / food.quantity.toDouble()
            val newFood = food.copy(
                quantity = newQty,
                calories = (food.calories * ratio).toInt(),
                protein = food.protein * ratio,
                carbs = food.carbs * ratio,
                fat = food.fat * ratio
            )
            withContext(Dispatchers.IO) { db.foodDao().update(newFood) }
            loadDailyFoodLog()
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.MunchDatePicker)
            .setTitleText("Selectează ziua")
            .setSelection(currentCalendar.timeInMillis)
            .build()

        picker.addOnPositiveButtonClickListener { millis ->
            currentCalendar.timeInMillis = millis
            // MaterialDatePicker returnează UTC midnight, corectăm timezone-ul
            val utcOffset = java.util.TimeZone.getDefault().getOffset(millis)
            currentCalendar.timeInMillis = millis + utcOffset
            updateDateDisplay()
            loadDailyFoodLog()
            loadDailyWater()
            loadTodaySteps()
        }

        picker.show(supportFragmentManager, "DatePicker")
    }

    private fun changeDate(days: Int) {
        currentCalendar.add(Calendar.DAY_OF_YEAR, days)
        updateDateDisplay()
        loadDailyFoodLog()
        loadDailyWater()
        loadTodaySteps()
    }

    private fun updateDateDisplay() {
        val today = Calendar.getInstance()

        if (isToday(currentCalendar)) {
            tvDateDisplay.text = "Astăzi"
        } else {
            val yesterday = Calendar.getInstance()
            yesterday.add(Calendar.DAY_OF_YEAR, -1)
            val isYesterday = currentCalendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    currentCalendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)

            if (isYesterday) {
                tvDateDisplay.text = "Ieri"
            } else {
                val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                tvDateDisplay.text = dateFormat.format(currentCalendar.time)
            }
        }
    }

    private fun openFoodSelect(mealType: MealType) {
        val fragment = FoodSelectFragment().apply {
            arguments = Bundle().apply {
                putString("MEAL_TYPE", mealType.name)
                putLong("SELECTED_DATE", currentCalendar.timeInMillis)
            }
        }
        supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun loadUserGoals() {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        calorieGoal = prefs.getInt("CALORIE_GOAL", 2000)
        proteinGoal = prefs.getInt("PROTEIN_GOAL", 150)
        carbGoal = prefs.getInt("CARB_GOAL", 250)
        fatGoal = prefs.getInt("FAT_GOAL", 70)
        waterGoal = prefs.getInt("WATER_GOAL", 2000)
    }

    // ======== PAȘI MANUALI ========

    private fun loadTodaySteps() {
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentCalendar.time)
        val steps = getSharedPreferences("StepPrefs", Context.MODE_PRIVATE)
            .getLong("steps_count_$dateStr", 0L)
        val stepsGoal = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            .getInt("STEPS_GOAL", 10000)
        if (steps > 0) {
            updateStepsUI(steps, stepsGoal)
        } else {
            val goalFormatted = String.format(Locale.getDefault(), "%,d", stepsGoal).replace(",", ".")
            tvStepsCount.text = "0 / $goalFormatted"
            stepsProgressBar.progress = 0
            tvStepsCalories.text = "0 kcal"
        }
    }

    private fun updateStepsUI(steps: Long, stepsGoal: Int = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE).getInt("STEPS_GOAL", 10000)) {
        val calories = calculateStepsCalories(steps)
        val formatted = String.format(Locale.getDefault(), "%,d", steps).replace(",", ".")
        val goalFormatted = String.format(Locale.getDefault(), "%,d", stepsGoal).replace(",", ".")
        tvStepsCount.text = "$formatted / $goalFormatted"
        stepsProgressBar.progress = steps.toInt().coerceAtMost(stepsGoal)
        tvStepsCalories.text = "~ $calories kcal"

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(currentCalendar.time)
        getSharedPreferences("StepPrefs", Context.MODE_PRIVATE).edit()
            .putInt("steps_cals_$dateStr", calories)
            .putLong("steps_count_$dateStr", steps)
            .apply()
    }

    private fun calculateStepsCalories(steps: Long): Int {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val weightKg = prefs.getFloat("USER_WEIGHT", 70f).toDouble()
        val heightCm = prefs.getInt("USER_HEIGHT", 170).toDouble()

        // Lungimea pasului 41.5% din inaltime (mers normal)
        val strideLength = heightCm * 0.415 / 100.0
        // Distanta parcursa un km
        val distanceKm = steps * strideLength / 1000.0
        // Calorii arse = greutate(kg) × distanta(km) × 1.036
        return (weightKg * distanceKm * 1.036).toInt()
    }

    private fun showManualStepsDialog() {
        val dialog = android.app.Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val view = layoutInflater.inflate(R.layout.dialog_manual_steps, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etSteps = view.findViewById<EditText>(R.id.etManualSteps)
        val tvCalPreview = view.findViewById<TextView>(R.id.tvStepCalPreview)
        val btnSave = view.findViewById<android.widget.Button>(R.id.btnSaveSteps)
        val btnCancel = view.findViewById<android.widget.Button>(R.id.btnCancelSteps)

        etSteps.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val steps = s?.toString()?.toLongOrNull() ?: 0L
                val cal = calculateStepsCalories(steps)
                tvCalPreview.text = if (steps > 0) "~ $cal kcal arse" else ""
            }
        })

        btnSave.setOnClickListener {
            val steps = etSteps.text.toString().toLongOrNull()
            if (steps != null && steps >= 0) {
                updateStepsUI(steps)
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Număr invalid", Toast.LENGTH_SHORT).show()
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        etSteps.requestFocus()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    // FUNCȚIA SEPARATĂ CARE ADAUGĂ ALIMENTELE
    private fun checkAndSeedDatabase() {
        lifecycleScope.launch {
            val currentFoods = withContext(Dispatchers.IO) { db.foodItemDao().getAll() }

            if (currentFoods.isEmpty()) {
                withContext(Dispatchers.IO) {
                    db.waterContainerDao().insert(WaterContainer(name = "Pahar", amountMl = 250, isDefault = true))
                    db.waterContainerDao().insert(WaterContainer(name = "Doză", amountMl = 330, isDefault = true))
                    db.waterContainerDao().insert(WaterContainer(name = "Sticlă mică", amountMl = 500, isDefault = true))
                    db.waterContainerDao().insert(WaterContainer(name = "Sticlă mare", amountMl = 750, isDefault = true))

                    val foodList = listOf(
                        FoodItem(name = "Măr Golden", caloriesPer100g = 57, proteinPer100g = 0.3, carbsPer100g = 13.0, fatPer100g = 0.2),
                        FoodItem(name = "Măr Roșu (Starkimson)", caloriesPer100g = 52, proteinPer100g = 0.3, carbsPer100g = 14.0, fatPer100g = 0.2),
                        FoodItem(name = "Măr Verde (Granny Smith)", caloriesPer100g = 58, proteinPer100g = 0.4, carbsPer100g = 13.6, fatPer100g = 0.2),
                        FoodItem(name = "Măr Idared", caloriesPer100g = 50, proteinPer100g = 0.3, carbsPer100g = 12.0, fatPer100g = 0.2),
                        FoodItem(name = "Pară Williams", caloriesPer100g = 57, proteinPer100g = 0.4, carbsPer100g = 15.0, fatPer100g = 0.1),
                        FoodItem(name = "Pară Conference", caloriesPer100g = 60, proteinPer100g = 0.4, carbsPer100g = 15.5, fatPer100g = 0.1),
                        FoodItem(name = "Gutuie", caloriesPer100g = 57, proteinPer100g = 0.4, carbsPer100g = 15.0, fatPer100g = 0.1),
                        FoodItem(name = "Portocală", caloriesPer100g = 47, proteinPer100g = 0.9, carbsPer100g = 12.0, fatPer100g = 0.1),
                        FoodItem(name = "Suc de Portocale (Proaspăt)", caloriesPer100g = 45, proteinPer100g = 0.7, carbsPer100g = 10.4, fatPer100g = 0.2),
                        FoodItem(name = "Mandarină", caloriesPer100g = 53, proteinPer100g = 0.8, carbsPer100g = 13.3, fatPer100g = 0.3),
                        FoodItem(name = "Clementină", caloriesPer100g = 47, proteinPer100g = 0.9, carbsPer100g = 12.0, fatPer100g = 0.2),
                        FoodItem(name = "Grapefruit Alb", caloriesPer100g = 33, proteinPer100g = 0.7, carbsPer100g = 8.4, fatPer100g = 0.1),
                        FoodItem(name = "Grapefruit Roșu", caloriesPer100g = 42, proteinPer100g = 0.8, carbsPer100g = 10.7, fatPer100g = 0.1),
                        FoodItem(name = "Pomelo", caloriesPer100g = 38, proteinPer100g = 0.8, carbsPer100g = 9.6, fatPer100g = 0.0),
                        FoodItem(name = "Lămâie (cu coajă)", caloriesPer100g = 29, proteinPer100g = 1.1, carbsPer100g = 9.0, fatPer100g = 0.3),
                        FoodItem(name = "Lime (Lămâie verde)", caloriesPer100g = 30, proteinPer100g = 0.7, carbsPer100g = 10.5, fatPer100g = 0.2),
                        FoodItem(name = "Struguri Albi", caloriesPer100g = 69, proteinPer100g = 0.7, carbsPer100g = 18.1, fatPer100g = 0.2),
                        FoodItem(name = "Struguri Roșii/Negri", caloriesPer100g = 72, proteinPer100g = 0.6, carbsPer100g = 17.5, fatPer100g = 0.3),
                        FoodItem(name = "Pepene Roșu", caloriesPer100g = 30, proteinPer100g = 0.6, carbsPer100g = 8.0, fatPer100g = 0.2),
                        FoodItem(name = "Pepene Galben (Cantaloupe)", caloriesPer100g = 34, proteinPer100g = 0.8, carbsPer100g = 8.0, fatPer100g = 0.2),
                        FoodItem(name = "Pepene Galben (Galia)", caloriesPer100g = 28, proteinPer100g = 0.5, carbsPer100g = 6.0, fatPer100g = 0.1),
                        FoodItem(name = "Căpșuni", caloriesPer100g = 32, proteinPer100g = 0.7, carbsPer100g = 7.7, fatPer100g = 0.3),
                        FoodItem(name = "Zmeură", caloriesPer100g = 52, proteinPer100g = 1.2, carbsPer100g = 11.9, fatPer100g = 0.7),
                        FoodItem(name = "Afine", caloriesPer100g = 57, proteinPer100g = 0.7, carbsPer100g = 14.5, fatPer100g = 0.3),
                        FoodItem(name = "Mure", caloriesPer100g = 43, proteinPer100g = 1.4, carbsPer100g = 9.6, fatPer100g = 0.5),
                        FoodItem(name = "Coacăze Roșii", caloriesPer100g = 56, proteinPer100g = 1.4, carbsPer100g = 13.8, fatPer100g = 0.2),
                        FoodItem(name = "Coacăze Negre", caloriesPer100g = 63, proteinPer100g = 1.4, carbsPer100g = 15.4, fatPer100g = 0.4),
                        FoodItem(name = "Cireșe", caloriesPer100g = 63, proteinPer100g = 1.1, carbsPer100g = 16.0, fatPer100g = 0.2),
                        FoodItem(name = "Vișine", caloriesPer100g = 50, proteinPer100g = 1.0, carbsPer100g = 12.2, fatPer100g = 0.3),
                        FoodItem(name = "Prune (Bistrițe)", caloriesPer100g = 46, proteinPer100g = 0.7, carbsPer100g = 11.4, fatPer100g = 0.3),
                        FoodItem(name = "Caise", caloriesPer100g = 48, proteinPer100g = 1.4, carbsPer100g = 11.0, fatPer100g = 0.4),
                        FoodItem(name = "Piersici", caloriesPer100g = 39, proteinPer100g = 0.9, carbsPer100g = 9.5, fatPer100g = 0.3),
                        FoodItem(name = "Nectarine", caloriesPer100g = 44, proteinPer100g = 1.1, carbsPer100g = 10.6, fatPer100g = 0.3),
                        FoodItem(name = "Banană", caloriesPer100g = 89, proteinPer100g = 1.1, carbsPer100g = 22.8, fatPer100g = 0.3),
                        FoodItem(name = "Ananas (proaspăt)", caloriesPer100g = 50, proteinPer100g = 0.5, carbsPer100g = 13.1, fatPer100g = 0.1),
                        FoodItem(name = "Ananas (compot)", caloriesPer100g = 80, proteinPer100g = 0.4, carbsPer100g = 20.0, fatPer100g = 0.1),
                        FoodItem(name = "Kiwi", caloriesPer100g = 61, proteinPer100g = 1.1, carbsPer100g = 14.7, fatPer100g = 0.5),
                        FoodItem(name = "Mango", caloriesPer100g = 60, proteinPer100g = 0.8, carbsPer100g = 15.0, fatPer100g = 0.4),
                        FoodItem(name = "Avocado", caloriesPer100g = 160, proteinPer100g = 2.0, carbsPer100g = 8.5, fatPer100g = 14.7),
                        FoodItem(name = "Rodie (semințe)", caloriesPer100g = 83, proteinPer100g = 1.7, carbsPer100g = 18.7, fatPer100g = 1.2),
                        FoodItem(name = "Kaki (Persimmon)", caloriesPer100g = 70, proteinPer100g = 0.6, carbsPer100g = 18.6, fatPer100g = 0.2),
                        FoodItem(name = "Papaya", caloriesPer100g = 43, proteinPer100g = 0.5, carbsPer100g = 10.8, fatPer100g = 0.3),
                        FoodItem(name = "Nucă de Cocos (miez)", caloriesPer100g = 354, proteinPer100g = 3.3, carbsPer100g = 15.0, fatPer100g = 33.5),
                        FoodItem(name = "Prune Uscate", caloriesPer100g = 240, proteinPer100g = 2.2, carbsPer100g = 64.0, fatPer100g = 0.4),
                        FoodItem(name = "Curmale", caloriesPer100g = 282, proteinPer100g = 2.5, carbsPer100g = 75.0, fatPer100g = 0.4),
                        FoodItem(name = "Smochine Uscate", caloriesPer100g = 249, proteinPer100g = 3.3, carbsPer100g = 63.9, fatPer100g = 0.9),
                        FoodItem(name = "Stafide", caloriesPer100g = 299, proteinPer100g = 3.1, carbsPer100g = 79.0, fatPer100g = 0.5),
                        FoodItem(name = "Merișoare Uscate (îndulcite)", caloriesPer100g = 308, proteinPer100g = 0.1, carbsPer100g = 82.0, fatPer100g = 1.4),
                        FoodItem(name = "Caise Uscate", caloriesPer100g = 241, proteinPer100g = 3.4, carbsPer100g = 63.0, fatPer100g = 0.5),
                        FoodItem(name = "Roșii (normale)", caloriesPer100g = 18, proteinPer100g = 0.9, carbsPer100g = 3.9, fatPer100g = 0.2),
                        FoodItem(name = "Roșii Cherry", caloriesPer100g = 27, proteinPer100g = 1.3, carbsPer100g = 5.8, fatPer100g = 0.3),
                        FoodItem(name = "Ardei Gras Roșu", caloriesPer100g = 31, proteinPer100g = 1.0, carbsPer100g = 6.0, fatPer100g = 0.3),
                        FoodItem(name = "Ardei Gras Galben", caloriesPer100g = 27, proteinPer100g = 1.0, carbsPer100g = 6.3, fatPer100g = 0.2),
                        FoodItem(name = "Ardei Gras Verde", caloriesPer100g = 20, proteinPer100g = 0.9, carbsPer100g = 4.6, fatPer100g = 0.2),
                        FoodItem(name = "Ardei Kapia", caloriesPer100g = 26, proteinPer100g = 1.0, carbsPer100g = 6.0, fatPer100g = 0.3),
                        FoodItem(name = "Ardei Iute", caloriesPer100g = 40, proteinPer100g = 1.9, carbsPer100g = 8.8, fatPer100g = 0.4),
                        FoodItem(name = "Vinete (crud)", caloriesPer100g = 25, proteinPer100g = 1.0, carbsPer100g = 5.9, fatPer100g = 0.2),
                        FoodItem(name = "Dovlecel", caloriesPer100g = 17, proteinPer100g = 1.2, carbsPer100g = 3.1, fatPer100g = 0.3),
                        FoodItem(name = "Castraveți", caloriesPer100g = 15, proteinPer100g = 0.7, carbsPer100g = 3.6, fatPer100g = 0.1),
                        FoodItem(name = "Cartofi Albi (cruzi)", caloriesPer100g = 77, proteinPer100g = 2.0, carbsPer100g = 17.5, fatPer100g = 0.1),
                        FoodItem(name = "Cartofi Roșii (cruzi)", caloriesPer100g = 70, proteinPer100g = 1.9, carbsPer100g = 16.0, fatPer100g = 0.1),
                        FoodItem(name = "Cartofi Noi", caloriesPer100g = 75, proteinPer100g = 1.7, carbsPer100g = 17.0, fatPer100g = 0.1),
                        FoodItem(name = "Cartof Dulce", caloriesPer100g = 86, proteinPer100g = 1.6, carbsPer100g = 20.1, fatPer100g = 0.1),
                        FoodItem(name = "Morcov", caloriesPer100g = 41, proteinPer100g = 0.9, carbsPer100g = 9.6, fatPer100g = 0.2),
                        FoodItem(name = "Țelină (Rădăcină)", caloriesPer100g = 42, proteinPer100g = 1.5, carbsPer100g = 9.0, fatPer100g = 0.3),
                        FoodItem(name = "Țelină Apio (Tulpini)", caloriesPer100g = 16, proteinPer100g = 0.7, carbsPer100g = 3.0, fatPer100g = 0.2),
                        FoodItem(name = "Sfeclă Roșie", caloriesPer100g = 43, proteinPer100g = 1.6, carbsPer100g = 9.6, fatPer100g = 0.2),
                        FoodItem(name = "Păstârnac", caloriesPer100g = 75, proteinPer100g = 1.2, carbsPer100g = 18.0, fatPer100g = 0.3),
                        FoodItem(name = "Ridichi Roșii", caloriesPer100g = 16, proteinPer100g = 0.7, carbsPer100g = 3.4, fatPer100g = 0.1),
                        FoodItem(name = "Ridiche Neagră", caloriesPer100g = 19, proteinPer100g = 0.9, carbsPer100g = 4.0, fatPer100g = 0.1),
                        FoodItem(name = "Ceapă Galbenă/Albă", caloriesPer100g = 40, proteinPer100g = 1.1, carbsPer100g = 9.3, fatPer100g = 0.1),
                        FoodItem(name = "Ceapă Roșie", caloriesPer100g = 37, proteinPer100g = 1.1, carbsPer100g = 8.5, fatPer100g = 0.1),
                        FoodItem(name = "Ceapă Verde", caloriesPer100g = 32, proteinPer100g = 1.8, carbsPer100g = 7.3, fatPer100g = 0.2),
                        FoodItem(name = "Usturoi", caloriesPer100g = 149, proteinPer100g = 6.4, carbsPer100g = 33.1, fatPer100g = 0.5),
                        FoodItem(name = "Usturoi Verde", caloriesPer100g = 40, proteinPer100g = 2.0, carbsPer100g = 8.0, fatPer100g = 0.1),
                        FoodItem(name = "Praz", caloriesPer100g = 61, proteinPer100g = 1.5, carbsPer100g = 14.2, fatPer100g = 0.3),
                        FoodItem(name = "Varză Albă", caloriesPer100g = 25, proteinPer100g = 1.3, carbsPer100g = 5.8, fatPer100g = 0.1),
                        FoodItem(name = "Varză Roșie", caloriesPer100g = 31, proteinPer100g = 1.4, carbsPer100g = 7.0, fatPer100g = 0.2),
                        FoodItem(name = "Varză de Bruxelles", caloriesPer100g = 43, proteinPer100g = 3.4, carbsPer100g = 9.0, fatPer100g = 0.3),
                        FoodItem(name = "Conopidă", caloriesPer100g = 25, proteinPer100g = 1.9, carbsPer100g = 5.0, fatPer100g = 0.3),
                        FoodItem(name = "Broccoli", caloriesPer100g = 34, proteinPer100g = 2.8, carbsPer100g = 6.6, fatPer100g = 0.4),
                        FoodItem(name = "Gulie", caloriesPer100g = 27, proteinPer100g = 1.7, carbsPer100g = 6.2, fatPer100g = 0.1),
                        FoodItem(name = "Spanac", caloriesPer100g = 23, proteinPer100g = 2.9, carbsPer100g = 3.6, fatPer100g = 0.4),
                        FoodItem(name = "Salată Verde (clasică)", caloriesPer100g = 15, proteinPer100g = 1.4, carbsPer100g = 2.9, fatPer100g = 0.2),
                        FoodItem(name = "Salată Iceberg", caloriesPer100g = 14, proteinPer100g = 0.9, carbsPer100g = 3.0, fatPer100g = 0.1),
                        FoodItem(name = "Rucola", caloriesPer100g = 25, proteinPer100g = 2.6, carbsPer100g = 3.7, fatPer100g = 0.7),
                        FoodItem(name = "Lobodă", caloriesPer100g = 19, proteinPer100g = 1.7, carbsPer100g = 3.0, fatPer100g = 0.4),
                        FoodItem(name = "Ștevie", caloriesPer100g = 22, proteinPer100g = 2.0, carbsPer100g = 3.2, fatPer100g = 0.6),
                        FoodItem(name = "Urzici", caloriesPer100g = 42, proteinPer100g = 2.7, carbsPer100g = 7.5, fatPer100g = 0.1),
                        FoodItem(name = "Pătrunjel (frunze)", caloriesPer100g = 36, proteinPer100g = 3.0, carbsPer100g = 6.3, fatPer100g = 0.8),
                        FoodItem(name = "Mărar", caloriesPer100g = 43, proteinPer100g = 3.5, carbsPer100g = 7.0, fatPer100g = 1.1),
                        FoodItem(name = "Fasole Verde (păstăi)", caloriesPer100g = 31, proteinPer100g = 1.8, carbsPer100g = 7.0, fatPer100g = 0.2),
                        FoodItem(name = "Fasole Galbenă (păstăi)", caloriesPer100g = 31, proteinPer100g = 1.8, carbsPer100g = 7.0, fatPer100g = 0.2),
                        FoodItem(name = "Mazăre Verde (boabe)", caloriesPer100g = 81, proteinPer100g = 5.4, carbsPer100g = 14.5, fatPer100g = 0.4),
                        FoodItem(name = "Porumb Dulce (boabe)", caloriesPer100g = 86, proteinPer100g = 3.2, carbsPer100g = 19.0, fatPer100g = 1.2),
                        FoodItem(name = "Sparanghel", caloriesPer100g = 20, proteinPer100g = 2.2, carbsPer100g = 3.9, fatPer100g = 0.1),
                        FoodItem(name = "Ciuperci Champignon (albe)", caloriesPer100g = 22, proteinPer100g = 3.1, carbsPer100g = 3.3, fatPer100g = 0.3),
                        FoodItem(name = "Ciuperci Pleurotus", caloriesPer100g = 33, proteinPer100g = 3.3, carbsPer100g = 6.0, fatPer100g = 0.4),
                        FoodItem(name = "Piept de Pui (fără piele)", caloriesPer100g = 110, proteinPer100g = 23.0, carbsPer100g = 0.0, fatPer100g = 1.2),
                        FoodItem(name = "Piept de Pui (cu piele)", caloriesPer100g = 170, proteinPer100g = 21.0, carbsPer100g = 0.0, fatPer100g = 9.0),
                        FoodItem(name = "Pulpe de Pui Superioare (fără piele)", caloriesPer100g = 130, proteinPer100g = 19.5, carbsPer100g = 0.0, fatPer100g = 5.5),
                        FoodItem(name = "Pulpe de Pui Superioare (cu piele)", caloriesPer100g = 210, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 15.0),
                        FoodItem(name = "Ciocănele de Pui (fără piele)", caloriesPer100g = 120, proteinPer100g = 19.0, carbsPer100g = 0.0, fatPer100g = 4.0),
                        FoodItem(name = "Ciocănele de Pui (cu piele)", caloriesPer100g = 160, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 9.5),
                        FoodItem(name = "Aripioare de Pui (cu piele)", caloriesPer100g = 203, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 14.0),
                        FoodItem(name = "Ficăței de Pui", caloriesPer100g = 119, proteinPer100g = 17.0, carbsPer100g = 0.6, fatPer100g = 4.8),
                        FoodItem(name = "Pipote și Inimi de Pui", caloriesPer100g = 110, proteinPer100g = 18.0, carbsPer100g = 0.5, fatPer100g = 3.5),
                        FoodItem(name = "Piept de Curcan (fără piele)", caloriesPer100g = 110, proteinPer100g = 24.0, carbsPer100g = 0.0, fatPer100g = 1.0),
                        FoodItem(name = "Pulpă de Curcan (fără piele)", caloriesPer100g = 140, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 6.0),
                        FoodItem(name = "Carne Tocată de Curcan (slabă)", caloriesPer100g = 150, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 7.0),
                        FoodItem(name = "Rață (piept cu piele)", caloriesPer100g = 200, proteinPer100g = 19.0, carbsPer100g = 0.0, fatPer100g = 14.0),
                        FoodItem(name = "Rață (pulpă)", caloriesPer100g = 220, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 16.0),
                        FoodItem(name = "Mușchiuleț de Porc (slab)", caloriesPer100g = 143, proteinPer100g = 21.0, carbsPer100g = 0.0, fatPer100g = 6.0),
                        FoodItem(name = "Cotlet de Porc (fără grăsime)", caloriesPer100g = 155, proteinPer100g = 22.0, carbsPer100g = 0.0, fatPer100g = 7.0),
                        FoodItem(name = "Ceafă de Porc (grătar)", caloriesPer100g = 260, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 20.0),
                        FoodItem(name = "Coaste de Porc (scăriță)", caloriesPer100g = 280, proteinPer100g = 16.0, carbsPer100g = 0.0, fatPer100g = 23.0),
                        FoodItem(name = "Carne Tocată Porc (medie)", caloriesPer100g = 260, proteinPer100g = 17.0, carbsPer100g = 0.0, fatPer100g = 21.0),
                        FoodItem(name = "Carne Tocată Porc (grasă)", caloriesPer100g = 300, proteinPer100g = 15.0, carbsPer100g = 0.0, fatPer100g = 26.0),
                        FoodItem(name = "Fleică de Porc", caloriesPer100g = 310, proteinPer100g = 15.0, carbsPer100g = 0.0, fatPer100g = 27.0),
                        FoodItem(name = "Ciolan de Porc (afumat)", caloriesPer100g = 290, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 24.0),
                        FoodItem(name = "Slănină / Untură", caloriesPer100g = 800, proteinPer100g = 2.0, carbsPer100g = 0.0, fatPer100g = 88.0),
                        FoodItem(name = "Ficat de Porc", caloriesPer100g = 135, proteinPer100g = 21.0, carbsPer100g = 2.5, fatPer100g = 4.5),
                        FoodItem(name = "Mușchi de Vită (Tenderloin)", caloriesPer100g = 150, proteinPer100g = 22.0, carbsPer100g = 0.0, fatPer100g = 6.0),
                        FoodItem(name = "Antricot de Vită (Ribeye)", caloriesPer100g = 250, proteinPer100g = 19.0, carbsPer100g = 0.0, fatPer100g = 19.0),
                        FoodItem(name = "Vrabioară de Vită (Sirloin)", caloriesPer100g = 180, proteinPer100g = 21.0, carbsPer100g = 0.0, fatPer100g = 10.0),
                        FoodItem(name = "Carne Tocată Vită (slabă 10%)", caloriesPer100g = 176, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 10.0),
                        FoodItem(name = "Carne Tocată Vită (grasă 20%)", caloriesPer100g = 250, proteinPer100g = 17.0, carbsPer100g = 0.0, fatPer100g = 20.0),
                        FoodItem(name = "Ficat de Vită", caloriesPer100g = 135, proteinPer100g = 20.0, carbsPer100g = 3.5, fatPer100g = 4.0),
                        FoodItem(name = "Limbă de Vită", caloriesPer100g = 220, proteinPer100g = 15.0, carbsPer100g = 0.5, fatPer100g = 17.0),
                        FoodItem(name = "Carne de Iepure", caloriesPer100g = 115, proteinPer100g = 22.0, carbsPer100g = 0.0, fatPer100g = 2.5),
                        FoodItem(name = "Carne de Oaie / Miel (medie)", caloriesPer100g = 250, proteinPer100g = 17.0, carbsPer100g = 0.0, fatPer100g = 20.0),
                        FoodItem(name = "Salam de Sibiu", caloriesPer100g = 480, proteinPer100g = 25.0, carbsPer100g = 1.0, fatPer100g = 42.0),
                        FoodItem(name = "Salam Victoria / Șuncă Praga", caloriesPer100g = 150, proteinPer100g = 18.0, carbsPer100g = 1.5, fatPer100g = 8.0),
                        FoodItem(name = "Mușchi File Afumat", caloriesPer100g = 120, proteinPer100g = 20.0, carbsPer100g = 1.0, fatPer100g = 4.0),
                        FoodItem(name = "Cârnați de Porc (proaspeți)", caloriesPer100g = 300, proteinPer100g = 15.0, carbsPer100g = 1.0, fatPer100g = 26.0),
                        FoodItem(name = "Cârnați Cabanos / Oltenești", caloriesPer100g = 350, proteinPer100g = 16.0, carbsPer100g = 1.0, fatPer100g = 31.0),
                        FoodItem(name = "Crenvurști de Pui", caloriesPer100g = 230, proteinPer100g = 12.0, carbsPer100g = 3.0, fatPer100g = 19.0),
                        FoodItem(name = "Crenvurști de Porc", caloriesPer100g = 280, proteinPer100g = 11.0, carbsPer100g = 2.0, fatPer100g = 25.0),
                        FoodItem(name = "Parizer de Pui", caloriesPer100g = 200, proteinPer100g = 11.0, carbsPer100g = 4.0, fatPer100g = 15.0),
                        FoodItem(name = "Bacon / Kaiser", caloriesPer100g = 400, proteinPer100g = 14.0, carbsPer100g = 1.0, fatPer100g = 38.0),
                        FoodItem(name = "Pastramă de Porc", caloriesPer100g = 160, proteinPer100g = 20.0, carbsPer100g = 1.0, fatPer100g = 8.0),
                        FoodItem(name = "Somon (crud)", caloriesPer100g = 208, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 13.0),
                        FoodItem(name = "Somon Afumat", caloriesPer100g = 180, proteinPer100g = 22.0, carbsPer100g = 0.0, fatPer100g = 10.0),
                        FoodItem(name = "Păstrăv", caloriesPer100g = 148, proteinPer100g = 20.0, carbsPer100g = 0.0, fatPer100g = 7.0),
                        FoodItem(name = "Crap", caloriesPer100g = 127, proteinPer100g = 17.0, carbsPer100g = 0.0, fatPer100g = 5.6),
                        FoodItem(name = "Macrou", caloriesPer100g = 205, proteinPer100g = 19.0, carbsPer100g = 0.0, fatPer100g = 14.0),
                        FoodItem(name = "Doradă / Biban de mare", caloriesPer100g = 90, proteinPer100g = 19.0, carbsPer100g = 0.0, fatPer100g = 1.5),
                        FoodItem(name = "Cod / Merluciu", caloriesPer100g = 82, proteinPer100g = 18.0, carbsPer100g = 0.0, fatPer100g = 0.7),
                        FoodItem(name = "Ton (conservă în suc propriu)", caloriesPer100g = 116, proteinPer100g = 26.0, carbsPer100g = 0.0, fatPer100g = 1.0),
                        FoodItem(name = "Ton (conservă în ulei)", caloriesPer100g = 190, proteinPer100g = 25.0, carbsPer100g = 0.0, fatPer100g = 10.0),
                        FoodItem(name = "Sardine (conservă în ulei)", caloriesPer100g = 208, proteinPer100g = 24.0, carbsPer100g = 0.0, fatPer100g = 11.0),
                        FoodItem(name = "Creveți (decorticați)", caloriesPer100g = 99, proteinPer100g = 24.0, carbsPer100g = 0.2, fatPer100g = 0.3),
                        FoodItem(name = "Midii", caloriesPer100g = 86, proteinPer100g = 12.0, carbsPer100g = 3.7, fatPer100g = 2.2),
                        FoodItem(name = "Calamar", caloriesPer100g = 92, proteinPer100g = 15.6, carbsPer100g = 3.1, fatPer100g = 1.4),
                        FoodItem(name = "Lapte de Vacă 1.5%", caloriesPer100g = 44, proteinPer100g = 3.0, carbsPer100g = 4.7, fatPer100g = 1.5),
                        FoodItem(name = "Lapte de Vacă 3.5%", caloriesPer100g = 64, proteinPer100g = 3.3, carbsPer100g = 4.8, fatPer100g = 3.5),
                        FoodItem(name = "Lapte de Capră", caloriesPer100g = 69, proteinPer100g = 3.6, carbsPer100g = 4.5, fatPer100g = 4.1),
                        FoodItem(name = "Iaurt Clasic 3.5%", caloriesPer100g = 61, proteinPer100g = 3.5, carbsPer100g = 4.0, fatPer100g = 3.5),
                        FoodItem(name = "Iaurt Slab 0.1%", caloriesPer100g = 35, proteinPer100g = 4.0, carbsPer100g = 4.5, fatPer100g = 0.1),
                        FoodItem(name = "Iaurt Grecesc 10%", caloriesPer100g = 125, proteinPer100g = 4.0, carbsPer100g = 3.5, fatPer100g = 10.0),
                        FoodItem(name = "Iaurt Grecesc 2%", caloriesPer100g = 70, proteinPer100g = 10.0, carbsPer100g = 3.5, fatPer100g = 2.0),
                        FoodItem(name = "Sana / Kefir", caloriesPer100g = 60, proteinPer100g = 3.2, carbsPer100g = 4.0, fatPer100g = 3.6),
                        FoodItem(name = "Brânză de Vaci (Slabă)", caloriesPer100g = 70, proteinPer100g = 16.0, carbsPer100g = 2.0, fatPer100g = 0.5),
                        FoodItem(name = "Brânză de Vaci (Grasă)", caloriesPer100g = 140, proteinPer100g = 14.0, carbsPer100g = 2.0, fatPer100g = 8.0),
                        FoodItem(name = "Telemea de Vacă (Proaspătă)", caloriesPer100g = 240, proteinPer100g = 16.0, carbsPer100g = 1.0, fatPer100g = 18.0),
                        FoodItem(name = "Telemea de Vacă (Maturată)", caloriesPer100g = 280, proteinPer100g = 18.0, carbsPer100g = 1.0, fatPer100g = 22.0),
                        FoodItem(name = "Telemea de Oaie", caloriesPer100g = 300, proteinPer100g = 17.0, carbsPer100g = 1.0, fatPer100g = 25.0),
                        FoodItem(name = "Telemea de Capră", caloriesPer100g = 270, proteinPer100g = 19.0, carbsPer100g = 1.0, fatPer100g = 21.0),
                        FoodItem(name = "Cașcaval (Dalia, Rucăr etc.)", caloriesPer100g = 330, proteinPer100g = 25.0, carbsPer100g = 1.0, fatPer100g = 26.0),
                        FoodItem(name = "Mozzarella (bloc)", caloriesPer100g = 280, proteinPer100g = 22.0, carbsPer100g = 2.0, fatPer100g = 22.0),
                        FoodItem(name = "Mozzarella (fresca/saramură)", caloriesPer100g = 240, proteinPer100g = 18.0, carbsPer100g = 2.0, fatPer100g = 18.0),
                        FoodItem(name = "Parmesan", caloriesPer100g = 431, proteinPer100g = 38.0, carbsPer100g = 4.0, fatPer100g = 29.0),
                        FoodItem(name = "Brânză de Burduf", caloriesPer100g = 370, proteinPer100g = 22.0, carbsPer100g = 0.0, fatPer100g = 30.0),
                        FoodItem(name = "Brânză Topită", caloriesPer100g = 280, proteinPer100g = 12.0, carbsPer100g = 6.0, fatPer100g = 23.0),
                        FoodItem(name = "Urda", caloriesPer100g = 140, proteinPer100g = 12.0, carbsPer100g = 3.0, fatPer100g = 8.0),
                        FoodItem(name = "Smântână 12%", caloriesPer100g = 135, proteinPer100g = 3.0, carbsPer100g = 4.0, fatPer100g = 12.0),
                        FoodItem(name = "Smântână 20%", caloriesPer100g = 205, proteinPer100g = 2.5, carbsPer100g = 3.5, fatPer100g = 20.0),
                        FoodItem(name = "Smântână 30% (pentru frișcă)", caloriesPer100g = 290, proteinPer100g = 2.0, carbsPer100g = 3.0, fatPer100g = 30.0),
                        FoodItem(name = "Unt 82%", caloriesPer100g = 740, proteinPer100g = 0.9, carbsPer100g = 0.1, fatPer100g = 82.0),
                        FoodItem(name = "Unt 65%", caloriesPer100g = 590, proteinPer100g = 0.5, carbsPer100g = 0.1, fatPer100g = 65.0),
                        FoodItem(name = "Ou de Găină (M - 50g)", caloriesPer100g = 155, proteinPer100g = 13.0, carbsPer100g = 1.1, fatPer100g = 11.0),
                        FoodItem(name = "Albuș de Ou", caloriesPer100g = 52, proteinPer100g = 11.0, carbsPer100g = 0.7, fatPer100g = 0.2),
                        FoodItem(name = "Gălbenuș de Ou", caloriesPer100g = 322, proteinPer100g = 16.0, carbsPer100g = 3.6, fatPer100g = 26.0),
                        FoodItem(name = "Ou de Prepelită", caloriesPer100g = 158, proteinPer100g = 13.0, carbsPer100g = 0.4, fatPer100g = 11.0),
                        FoodItem(name = "Pâine Albă (Feliată)", caloriesPer100g = 265, proteinPer100g = 9.0, carbsPer100g = 49.0, fatPer100g = 3.2),
                        FoodItem(name = "Pâine Integrală / Neagră", caloriesPer100g = 240, proteinPer100g = 13.0, carbsPer100g = 41.0, fatPer100g = 3.5),
                        FoodItem(name = "Pâine Graham", caloriesPer100g = 250, proteinPer100g = 10.0, carbsPer100g = 45.0, fatPer100g = 2.0),
                        FoodItem(name = "Pâine de Secară", caloriesPer100g = 259, proteinPer100g = 9.0, carbsPer100g = 48.0, fatPer100g = 3.3),
                        FoodItem(name = "Pâine Toast (Albă)", caloriesPer100g = 270, proteinPer100g = 8.0, carbsPer100g = 50.0, fatPer100g = 3.0),
                        FoodItem(name = "Baghetă Franțuzească", caloriesPer100g = 280, proteinPer100g = 9.5, carbsPer100g = 55.0, fatPer100g = 1.5),
                        FoodItem(name = "Chiflă (pentru burger)", caloriesPer100g = 290, proteinPer100g = 9.0, carbsPer100g = 52.0, fatPer100g = 5.0),
                        FoodItem(name = "Lipie Libaneză", caloriesPer100g = 270, proteinPer100g = 8.0, carbsPer100g = 55.0, fatPer100g = 1.0),
                        FoodItem(name = "Mămăligă (gata făcută)", caloriesPer100g = 70, proteinPer100g = 2.0, carbsPer100g = 15.0, fatPer100g = 1.0),
                        FoodItem(name = "Covrig (cu susan/mac)", caloriesPer100g = 290, proteinPer100g = 9.0, carbsPer100g = 57.0, fatPer100g = 2.0),
                        FoodItem(name = "Covrigi (uscați/buzău)", caloriesPer100g = 400, proteinPer100g = 11.0, carbsPer100g = 75.0, fatPer100g = 4.0),
                        FoodItem(name = "Sticks-uri / Sărățele", caloriesPer100g = 400, proteinPer100g = 10.0, carbsPer100g = 70.0, fatPer100g = 7.0),
                        FoodItem(name = "Rondele de Orez (Rice Cakes)", caloriesPer100g = 380, proteinPer100g = 8.0, carbsPer100g = 80.0, fatPer100g = 3.0),
                        FoodItem(name = "Merdenea (cu brânză)", caloriesPer100g = 420, proteinPer100g = 8.0, carbsPer100g = 35.0, fatPer100g = 28.0),
                        FoodItem(name = "Orez Alb (crud)", caloriesPer100g = 360, proteinPer100g = 7.0, carbsPer100g = 79.0, fatPer100g = 0.6),
                        FoodItem(name = "Orez Brun/Integral (crud)", caloriesPer100g = 370, proteinPer100g = 7.9, carbsPer100g = 77.0, fatPer100g = 2.9),
                        FoodItem(name = "Orez Basmati (crud)", caloriesPer100g = 350, proteinPer100g = 8.0, carbsPer100g = 75.0, fatPer100g = 0.5),
                        FoodItem(name = "Paste Făinoase (crude)", caloriesPer100g = 370, proteinPer100g = 13.0, carbsPer100g = 75.0, fatPer100g = 1.5),
                        FoodItem(name = "Paste Integrale (crude)", caloriesPer100g = 350, proteinPer100g = 14.0, carbsPer100g = 65.0, fatPer100g = 2.5),
                        FoodItem(name = "Mălai (crud)", caloriesPer100g = 365, proteinPer100g = 9.0, carbsPer100g = 74.0, fatPer100g = 1.0),
                        FoodItem(name = "Griș (crud)", caloriesPer100g = 360, proteinPer100g = 12.0, carbsPer100g = 72.0, fatPer100g = 1.0),
                        FoodItem(name = "Fulgi de Ovăz", caloriesPer100g = 389, proteinPer100g = 16.9, carbsPer100g = 66.0, fatPer100g = 6.9),
                        FoodItem(name = "Cereale Cornflakes (simple)", caloriesPer100g = 370, proteinPer100g = 7.0, carbsPer100g = 84.0, fatPer100g = 0.9),
                        FoodItem(name = "Cereale cu Ciocolată", caloriesPer100g = 390, proteinPer100g = 8.0, carbsPer100g = 78.0, fatPer100g = 4.0),
                        FoodItem(name = "Muesli (cu fructe)", caloriesPer100g = 360, proteinPer100g = 10.0, carbsPer100g = 65.0, fatPer100g = 6.0),
                        FoodItem(name = "Hrișcă (crudă)", caloriesPer100g = 343, proteinPer100g = 13.0, carbsPer100g = 71.0, fatPer100g = 3.4),
                        FoodItem(name = "Quinoa (crudă)", caloriesPer100g = 368, proteinPer100g = 14.0, carbsPer100g = 64.0, fatPer100g = 6.0),
                        FoodItem(name = "Couscous (crud)", caloriesPer100g = 376, proteinPer100g = 13.0, carbsPer100g = 77.0, fatPer100g = 0.6),
                        FoodItem(name = "Orez Alb (fiert)", caloriesPer100g = 130, proteinPer100g = 2.7, carbsPer100g = 28.0, fatPer100g = 0.3),
                        FoodItem(name = "Orez Brun (fiert)", caloriesPer100g = 111, proteinPer100g = 2.6, carbsPer100g = 23.0, fatPer100g = 0.9),
                        FoodItem(name = "Paste (fierte)", caloriesPer100g = 131, proteinPer100g = 5.0, carbsPer100g = 25.0, fatPer100g = 1.1),
                        FoodItem(name = "Cartofi Piure (cu lapte și unt)", caloriesPer100g = 110, proteinPer100g = 2.0, carbsPer100g = 15.0, fatPer100g = 4.5),
                        FoodItem(name = "Cartofi Prăjiți (acasă)", caloriesPer100g = 312, proteinPer100g = 3.4, carbsPer100g = 41.0, fatPer100g = 15.0),
                        FoodItem(name = "Cartofi Prăjiți (Fast Food)", caloriesPer100g = 320, proteinPer100g = 3.5, carbsPer100g = 40.0, fatPer100g = 17.0),
                        FoodItem(name = "Nuci (miez)", caloriesPer100g = 654, proteinPer100g = 15.0, carbsPer100g = 14.0, fatPer100g = 65.0),
                        FoodItem(name = "Migdale (crude)", caloriesPer100g = 579, proteinPer100g = 21.0, carbsPer100g = 22.0, fatPer100g = 50.0),
                        FoodItem(name = "Migdale (prăjite)", caloriesPer100g = 595, proteinPer100g = 21.0, carbsPer100g = 21.0, fatPer100g = 52.0),
                        FoodItem(name = "Arahide / Alune (crude)", caloriesPer100g = 567, proteinPer100g = 26.0, carbsPer100g = 16.0, fatPer100g = 49.0),
                        FoodItem(name = "Arahide (prăjite și sărate)", caloriesPer100g = 585, proteinPer100g = 24.0, carbsPer100g = 21.0, fatPer100g = 50.0),
                        FoodItem(name = "Caju (crud)", caloriesPer100g = 553, proteinPer100g = 18.0, carbsPer100g = 30.0, fatPer100g = 44.0),
                        FoodItem(name = "Fistic (prăjit și sărat)", caloriesPer100g = 560, proteinPer100g = 20.0, carbsPer100g = 27.0, fatPer100g = 45.0),
                        FoodItem(name = "Alune de Pădure", caloriesPer100g = 628, proteinPer100g = 15.0, carbsPer100g = 17.0, fatPer100g = 61.0),
                        FoodItem(name = "Semințe de Floarea Soarelui (miez)", caloriesPer100g = 584, proteinPer100g = 20.0, carbsPer100g = 20.0, fatPer100g = 51.0),
                        FoodItem(name = "Semințe de Dovleac (miez)", caloriesPer100g = 559, proteinPer100g = 30.0, carbsPer100g = 10.0, fatPer100g = 49.0),
                        FoodItem(name = "Semințe de Chia", caloriesPer100g = 486, proteinPer100g = 17.0, carbsPer100g = 42.0, fatPer100g = 31.0),
                        FoodItem(name = "Semințe de In", caloriesPer100g = 534, proteinPer100g = 18.0, carbsPer100g = 29.0, fatPer100g = 42.0),
                        FoodItem(name = "Unt de Arahide (natural)", caloriesPer100g = 588, proteinPer100g = 25.0, carbsPer100g = 20.0, fatPer100g = 50.0),
                        FoodItem(name = "Ulei de Floarea Soarelui", caloriesPer100g = 884, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 100.0),
                        FoodItem(name = "Ulei de Măsline", caloriesPer100g = 884, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 100.0),
                        FoodItem(name = "Ulei de Cocos", caloriesPer100g = 862, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 100.0),
                        FoodItem(name = "Untură de Porc", caloriesPer100g = 900, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 100.0),
                        FoodItem(name = "Margarină", caloriesPer100g = 717, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 81.0),
                        FoodItem(name = "Maioneză", caloriesPer100g = 680, proteinPer100g = 1.0, carbsPer100g = 1.0, fatPer100g = 75.0),
                        FoodItem(name = "Ketchup", caloriesPer100g = 100, proteinPer100g = 1.0, carbsPer100g = 25.0, fatPer100g = 0.1),
                        FoodItem(name = "Muștar", caloriesPer100g = 66, proteinPer100g = 4.0, carbsPer100g = 5.0, fatPer100g = 3.0),
                        FoodItem(name = "Zahăr Alb", caloriesPer100g = 387, proteinPer100g = 0.0, carbsPer100g = 100.0, fatPer100g = 0.0),
                        FoodItem(name = "Zahăr Brun", caloriesPer100g = 380, proteinPer100g = 0.0, carbsPer100g = 98.0, fatPer100g = 0.0),
                        FoodItem(name = "Miere de Albine", caloriesPer100g = 304, proteinPer100g = 0.3, carbsPer100g = 82.0, fatPer100g = 0.0),
                        FoodItem(name = "Ciocolată cu Lapte", caloriesPer100g = 535, proteinPer100g = 7.6, carbsPer100g = 59.0, fatPer100g = 30.0),
                        FoodItem(name = "Ciocolată Neagră (70-85%)", caloriesPer100g = 598, proteinPer100g = 7.8, carbsPer100g = 46.0, fatPer100g = 43.0),
                        FoodItem(name = "Ciocolată Albă", caloriesPer100g = 539, proteinPer100g = 5.9, carbsPer100g = 59.0, fatPer100g = 32.0),
                        FoodItem(name = "Napolitane (cu cacao)", caloriesPer100g = 510, proteinPer100g = 6.0, carbsPer100g = 65.0, fatPer100g = 25.0),
                        FoodItem(name = "Biscuiți Simpli (Petit Beurre)", caloriesPer100g = 440, proteinPer100g = 8.0, carbsPer100g = 73.0, fatPer100g = 12.0),
                        FoodItem(name = "Biscuiți cu Cremă (Sandwich)", caloriesPer100g = 500, proteinPer100g = 5.0, carbsPer100g = 65.0, fatPer100g = 24.0),
                        FoodItem(name = "Croissant cu Ciocolată (Mare)", caloriesPer100g = 445, proteinPer100g = 7.0, carbsPer100g = 52.0, fatPer100g = 24.0),
                        FoodItem(name = "Halva (de floarea soarelui)", caloriesPer100g = 550, proteinPer100g = 12.0, carbsPer100g = 45.0, fatPer100g = 38.0),
                        FoodItem(name = "Rahat", caloriesPer100g = 350, proteinPer100g = 0.5, carbsPer100g = 88.0, fatPer100g = 0.1),
                        FoodItem(name = "Gem / Dulceață", caloriesPer100g = 260, proteinPer100g = 0.5, carbsPer100g = 65.0, fatPer100g = 0.1),
                        FoodItem(name = "Prăjitură Amandină", caloriesPer100g = 350, proteinPer100g = 4.0, carbsPer100g = 45.0, fatPer100g = 18.0),
                        FoodItem(name = "Prăjitură Savarină", caloriesPer100g = 280, proteinPer100g = 3.0, carbsPer100g = 40.0, fatPer100g = 12.0),
                        FoodItem(name = "Ecler cu Ciocolată", caloriesPer100g = 260, proteinPer100g = 5.0, carbsPer100g = 25.0, fatPer100g = 16.0),
                        FoodItem(name = "Cozonac (cu nucă)", caloriesPer100g = 380, proteinPer100g = 8.0, carbsPer100g = 50.0, fatPer100g = 16.0),
                        FoodItem(name = "Chec", caloriesPer100g = 350, proteinPer100g = 6.0, carbsPer100g = 50.0, fatPer100g = 14.0),
                        FoodItem(name = "Salam de Biscuiți", caloriesPer100g = 400, proteinPer100g = 5.0, carbsPer100g = 60.0, fatPer100g = 18.0),
                        FoodItem(name = "Înghețată (Vanilie)", caloriesPer100g = 207, proteinPer100g = 3.5, carbsPer100g = 24.0, fatPer100g = 11.0),
                        FoodItem(name = "Înghețată (Ciocolată)", caloriesPer100g = 216, proteinPer100g = 4.0, carbsPer100g = 28.0, fatPer100g = 11.0),
                        FoodItem(name = "Chipsuri (sare)", caloriesPer100g = 536, proteinPer100g = 6.0, carbsPer100g = 53.0, fatPer100g = 35.0),
                        FoodItem(name = "Pufuleți (simpli)", caloriesPer100g = 450, proteinPer100g = 6.0, carbsPer100g = 65.0, fatPer100g = 20.0),
                        FoodItem(name = "Popcorn (făcut la aer)", caloriesPer100g = 387, proteinPer100g = 13.0, carbsPer100g = 78.0, fatPer100g = 4.5),
                        FoodItem(name = "Popcorn (cinema/unt)", caloriesPer100g = 500, proteinPer100g = 9.0, carbsPer100g = 55.0, fatPer100g = 30.0),
                        FoodItem(name = "Sticks-uri", caloriesPer100g = 380, proteinPer100g = 10.0, carbsPer100g = 75.0, fatPer100g = 4.0),
                        FoodItem(name = "Crackers (sărățele)", caloriesPer100g = 450, proteinPer100g = 9.0, carbsPer100g = 60.0, fatPer100g = 20.0),
                        FoodItem(name = "Nachos / Tortilla Chips", caloriesPer100g = 500, proteinPer100g = 7.0, carbsPer100g = 60.0, fatPer100g = 25.0),
                        FoodItem(name = "Apă (plată/minerală)", caloriesPer100g = 0, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0),
                        FoodItem(name = "Cola / Suc Carbogazos", caloriesPer100g = 42, proteinPer100g = 0.0, carbsPer100g = 10.6, fatPer100g = 0.0),
                        FoodItem(name = "Suc de Mere (natural)", caloriesPer100g = 46, proteinPer100g = 0.1, carbsPer100g = 11.0, fatPer100g = 0.0),
                        FoodItem(name = "Cafea (neagră, fără zahăr)", caloriesPer100g = 1, proteinPer100g = 0.1, carbsPer100g = 0.0, fatPer100g = 0.0),
                        FoodItem(name = "Cappuccino (cu lapte)", caloriesPer100g = 35, proteinPer100g = 2.0, carbsPer100g = 3.5, fatPer100g = 1.5),
                        FoodItem(name = "Bere Blondă", caloriesPer100g = 43, proteinPer100g = 0.5, carbsPer100g = 3.6, fatPer100g = 0.0),
                        FoodItem(name = "Vin (Roșu/Alb Sec)", caloriesPer100g = 85, proteinPer100g = 0.1, carbsPer100g = 2.6, fatPer100g = 0.0),
                        FoodItem(name = "Vin (Dulce)", caloriesPer100g = 100, proteinPer100g = 0.1, carbsPer100g = 6.0, fatPer100g = 0.0),
                        FoodItem(name = "Vodcă / Whisky / Rom (40%)", caloriesPer100g = 231, proteinPer100g = 0.0, carbsPer100g = 0.0, fatPer100g = 0.0),
                        FoodItem(name = "Piept de Pui (Grătar)", caloriesPer100g = 165, proteinPer100g = 31.0, carbsPer100g = 0.0, fatPer100g = 3.6),
                        FoodItem(name = "Piept de Pui (Fiert)", caloriesPer100g = 151, proteinPer100g = 29.0, carbsPer100g = 0.0, fatPer100g = 3.0),
                        FoodItem(name = "Piept de Pui (La cuptor)", caloriesPer100g = 160, proteinPer100g = 30.0, carbsPer100g = 0.0, fatPer100g = 3.5),
                        FoodItem(name = "Piept de Pui (Șnițel / Pane)", caloriesPer100g = 260, proteinPer100g = 22.0, carbsPer100g = 15.0, fatPer100g = 12.0),
                        FoodItem(name = "Pulpe Pui (Grătar, fără piele)", caloriesPer100g = 175, proteinPer100g = 26.0, carbsPer100g = 0.0, fatPer100g = 8.0),
                        FoodItem(name = "Pulpe Pui (La cuptor, cu piele)", caloriesPer100g = 220, proteinPer100g = 24.0, carbsPer100g = 0.0, fatPer100g = 14.0),
                        FoodItem(name = "Pulpe Pui (Prăjite în ulei)", caloriesPer100g = 270, proteinPer100g = 23.0, carbsPer100g = 1.0, fatPer100g = 19.0),
                        FoodItem(name = "Aripioare Pui (Picante / Prăjite)", caloriesPer100g = 320, proteinPer100g = 20.0, carbsPer100g = 5.0, fatPer100g = 24.0),
                        FoodItem(name = "Ficăței de Pui (la tigaie cu ceapă)", caloriesPer100g = 170, proteinPer100g = 22.0, carbsPer100g = 4.0, fatPer100g = 7.0),
                        FoodItem(name = "Ceafă de Porc (Grătar)", caloriesPer100g = 260, proteinPer100g = 24.0, carbsPer100g = 0.0, fatPer100g = 18.0),
                        FoodItem(name = "Cotlet de Porc (Grătar)", caloriesPer100g = 160, proteinPer100g = 28.0, carbsPer100g = 0.0, fatPer100g = 6.0),
                        FoodItem(name = "Șnițel de Porc (Pane)", caloriesPer100g = 350, proteinPer100g = 18.0, carbsPer100g = 22.0, fatPer100g = 20.0),
                        FoodItem(name = "Chiftele (Prăjite în ulei)", caloriesPer100g = 310, proteinPer100g = 16.0, carbsPer100g = 12.0, fatPer100g = 22.0),
                        FoodItem(name = "Chiftele (La cuptor / Marinate)", caloriesPer100g = 190, proteinPer100g = 15.0, carbsPer100g = 8.0, fatPer100g = 10.0),
                        FoodItem(name = "Mușchi de Vită (Grătar)", caloriesPer100g = 180, proteinPer100g = 28.0, carbsPer100g = 0.0, fatPer100g = 7.0),
                        FoodItem(name = "Burger Vită (doar carnea, grătar)", caloriesPer100g = 250, proteinPer100g = 25.0, carbsPer100g = 0.0, fatPer100g = 17.0),
                        FoodItem(name = "Ciorbă de Burtă", caloriesPer100g = 360, proteinPer100g = 14.0, carbsPer100g = 4.0, fatPer100g = 30.0),
                        FoodItem(name = "Ciorbă Rădăuțeană", caloriesPer100g = 350, proteinPer100g = 15.0, carbsPer100g = 5.0, fatPer100g = 28.0),
                        FoodItem(name = "Ciorbă de Văcuță", caloriesPer100g = 60, proteinPer100g = 7.0, carbsPer100g = 4.0, fatPer100g = 2.0),
                        FoodItem(name = "Ciorbă de Perișoare", caloriesPer100g = 75, proteinPer100g = 6.0, carbsPer100g = 5.0, fatPer100g = 4.0),
                        FoodItem(name = "Ciorbă de Pui a la grec", caloriesPer100g = 85, proteinPer100g = 6.0, carbsPer100g = 5.0, fatPer100g = 5.0),
                        FoodItem(name = "Ciorbă de Fasole cu Afumătură", caloriesPer100g = 120, proteinPer100g = 8.0, carbsPer100g = 12.0, fatPer100g = 5.0),
                        FoodItem(name = "Ciorbă de Legume (de post)", caloriesPer100g = 40, proteinPer100g = 1.5, carbsPer100g = 6.0, fatPer100g = 1.0),
                        FoodItem(name = "Supă de Pui cu Tăieței", caloriesPer100g = 50, proteinPer100g = 4.0, carbsPer100g = 6.0, fatPer100g = 1.5),
                        FoodItem(name = "Supă Cremă de Legume (cu crutoane)", caloriesPer100g = 80, proteinPer100g = 2.0, carbsPer100g = 14.0, fatPer100g = 2.0),
                        FoodItem(name = "Borș de Pește", caloriesPer100g = 60, proteinPer100g = 8.0, carbsPer100g = 3.0, fatPer100g = 1.5),
                        FoodItem(name = "Sarmale de Porc (cu orez)", caloriesPer100g = 210, proteinPer100g = 11.0, carbsPer100g = 8.0, fatPer100g = 16.0),
                        FoodItem(name = "Sarmale de Pui / Curcan", caloriesPer100g = 160, proteinPer100g = 14.0, carbsPer100g = 7.0, fatPer100g = 8.0),
                        FoodItem(name = "Sarmale de Post (ciuperci/orez)", caloriesPer100g = 130, proteinPer100g = 4.0, carbsPer100g = 22.0, fatPer100g = 5.0),
                        FoodItem(name = "Mici / Mititei", caloriesPer100g = 300, proteinPer100g = 15.0, carbsPer100g = 2.0, fatPer100g = 25.0),
                        FoodItem(name = "Ardei Umpluți (porc)", caloriesPer100g = 140, proteinPer100g = 8.0, carbsPer100g = 14.0, fatPer100g = 6.0),
                        FoodItem(name = "Musaca de Cartofi (cu carne)", caloriesPer100g = 180, proteinPer100g = 10.0, carbsPer100g = 16.0, fatPer100g = 9.0),
                        FoodItem(name = "Varză Călită cu Carne", caloriesPer100g = 150, proteinPer100g = 9.0, carbsPer100g = 10.0, fatPer100g = 9.0),
                        FoodItem(name = "Iahnie de Fasole (cu ciolan)", caloriesPer100g = 160, proteinPer100g = 7.0, carbsPer100g = 22.0, fatPer100g = 5.0),
                        FoodItem(name = "Fasole Bătută (cu ceapă călită)", caloriesPer100g = 220, proteinPer100g = 8.0, carbsPer100g = 25.0, fatPer100g = 10.0),
                        FoodItem(name = "Tocăniță de Cartofi cu Carne", caloriesPer100g = 130, proteinPer100g = 6.0, carbsPer100g = 18.0, fatPer100g = 4.0),
                        FoodItem(name = "Ostropel de Pui (cu sos roșu)", caloriesPer100g = 140, proteinPer100g = 15.0, carbsPer100g = 8.0, fatPer100g = 5.0),
                        FoodItem(name = "Ciulama de Pui cu Ciuperci", caloriesPer100g = 150, proteinPer100g = 14.0, carbsPer100g = 9.0, fatPer100g = 7.0),
                        FoodItem(name = "Pilaf de Orez cu Pui", caloriesPer100g = 160, proteinPer100g = 10.0, carbsPer100g = 24.0, fatPer100g = 3.0),
                        FoodItem(name = "Pomana Porcului", caloriesPer100g = 290, proteinPer100g = 22.0, carbsPer100g = 1.0, fatPer100g = 21.0),
                        FoodItem(name = "Tochitură Moldovenească", caloriesPer100g = 250, proteinPer100g = 18.0, carbsPer100g = 5.0, fatPer100g = 17.0),
                        FoodItem(name = "Bulz Ciobănesc", caloriesPer100g = 320, proteinPer100g = 14.0, carbsPer100g = 30.0, fatPer100g = 16.0),
                        FoodItem(name = "Mâncare de Mazăre (cu pui)", caloriesPer100g = 110, proteinPer100g = 9.0, carbsPer100g = 12.0, fatPer100g = 4.0),
                        FoodItem(name = "Drob de Miel/Porc", caloriesPer100g = 220, proteinPer100g = 16.0, carbsPer100g = 4.0, fatPer100g = 14.0),
                        FoodItem(name = "Salată de Boeuf", caloriesPer100g = 240, proteinPer100g = 8.0, carbsPer100g = 10.0, fatPer100g = 19.0),
                        FoodItem(name = "Salată de Vinete (cu maioneză)", caloriesPer100g = 190, proteinPer100g = 1.5, carbsPer100g = 5.0, fatPer100g = 18.0),
                        FoodItem(name = "Zacuscă", caloriesPer100g = 120, proteinPer100g = 1.5, carbsPer100g = 10.0, fatPer100g = 8.0),
                        FoodItem(name = "Cartofi Piure (cu unt și lapte)", caloriesPer100g = 110, proteinPer100g = 2.0, carbsPer100g = 17.0, fatPer100g = 4.0),
                        FoodItem(name = "Cartofi Natur (fierți)", caloriesPer100g = 80, proteinPer100g = 2.0, carbsPer100g = 18.0, fatPer100g = 0.1),
                        FoodItem(name = "Cartofi Țărănești (cu ceapă și bacon)", caloriesPer100g = 160, proteinPer100g = 4.0, carbsPer100g = 20.0, fatPer100g = 7.0),
                        FoodItem(name = "Cartofi la Cuptor (cu rozmarin)", caloriesPer100g = 130, proteinPer100g = 2.5, carbsPer100g = 22.0, fatPer100g = 4.0),
                        FoodItem(name = "Orez Sârbesc (cu legume)", caloriesPer100g = 130, proteinPer100g = 2.5, carbsPer100g = 26.0, fatPer100g = 2.0),
                        FoodItem(name = "Sote de Ciuperci", caloriesPer100g = 60, proteinPer100g = 3.0, carbsPer100g = 4.0, fatPer100g = 4.0),
                        FoodItem(name = "Legume la Grătar", caloriesPer100g = 50, proteinPer100g = 2.0, carbsPer100g = 8.0, fatPer100g = 1.0)
                    )
                    db.foodItemDao().insertAll(foodList)
                }

                loadWaterContainers()
            }
        }
    }
}