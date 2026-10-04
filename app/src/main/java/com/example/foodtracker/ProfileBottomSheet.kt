package com.example.foodtracker

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.example.foodtracker.db.AppDatabase
import com.example.foodtracker.entity.UserProfile
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ProfileActivity : AppCompatActivity() {

    private lateinit var etAge: EditText
    private lateinit var etWeight: EditText
    private lateinit var etHeight: EditText
    private lateinit var etGoalWeight: EditText
    private lateinit var etName: EditText

    private lateinit var switchAuto: MaterialSwitch

    private lateinit var etCal: EditText
    private lateinit var etProt: EditText
    private lateinit var etCarb: EditText
    private lateinit var etFat: EditText
    private lateinit var etSteps: EditText
    private lateinit var etWater: EditText

    private lateinit var tvAvatarInitial: TextView
    private lateinit var tvGreeting: TextView

    private lateinit var cardMale: CardView
    private lateinit var cardFemale: CardView
    private lateinit var tvMaleLabel: TextView
    private lateinit var tvFemaleLabel: TextView
    private var isMale = true

    private lateinit var cardLose: CardView
    private lateinit var cardMaintain: CardView
    private lateinit var cardBulk: CardView
    private lateinit var tvLoseLabel: TextView
    private lateinit var tvMaintainLabel: TextView
    private lateinit var tvBulkLabel: TextView
    private lateinit var tvLoseArrow: TextView
    private lateinit var tvMaintainArrow: TextView
    private lateinit var tvBulkArrow: TextView
    private var selectedGoal = "Maintain"

    private lateinit var cardSedentary: CardView
    private lateinit var cardLightActive: CardView
    private lateinit var cardActive: CardView
    private lateinit var cardVeryActive: CardView
    private lateinit var tvSedentaryLabel: TextView
    private lateinit var tvLightActiveLabel: TextView
    private lateinit var tvActiveLabel: TextView
    private lateinit var tvVeryActiveLabel: TextView
    private var selectedActivity = "LightActive"

    private var isUserInteracting = false

    private val COLOR_SELECTED_BG = Color.parseColor("#4CAF50")
    private val COLOR_UNSELECTED_BG = Color.parseColor("#3A3A3C")
    private val COLOR_SELECTED_TEXT = Color.WHITE
    private val COLOR_UNSELECTED_TEXT = Color.parseColor("#AAAAAA")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_profile)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)

        tvAvatarInitial = findViewById(R.id.tvAvatarInitial)
        tvGreeting = findViewById(R.id.tvGreeting)

        etName = findViewById(R.id.etName)
        etAge = findViewById(R.id.etAge)
        etWeight = findViewById(R.id.etWeight)
        etHeight = findViewById(R.id.etHeight)
        etGoalWeight = findViewById(R.id.etGoalWeight)

        cardMale = findViewById(R.id.cardMale)
        cardFemale = findViewById(R.id.cardFemale)
        tvMaleLabel = findViewById(R.id.tvMaleLabel)
        tvFemaleLabel = findViewById(R.id.tvFemaleLabel)

        cardLose = findViewById(R.id.cardLose)
        cardMaintain = findViewById(R.id.cardMaintain)
        cardBulk = findViewById(R.id.cardBulk)
        tvLoseLabel = findViewById(R.id.tvLoseLabel)
        tvMaintainLabel = findViewById(R.id.tvMaintainLabel)
        tvBulkLabel = findViewById(R.id.tvBulkLabel)
        tvLoseArrow = findViewById(R.id.tvLoseArrow)
        tvMaintainArrow = findViewById(R.id.tvMaintainArrow)
        tvBulkArrow = findViewById(R.id.tvBulkArrow)

        cardSedentary = findViewById(R.id.cardSedentary)
        cardLightActive = findViewById(R.id.cardLightActive)
        cardActive = findViewById(R.id.cardActive)
        cardVeryActive = findViewById(R.id.cardVeryActive)
        tvSedentaryLabel = findViewById(R.id.tvSedentaryLabel)
        tvLightActiveLabel = findViewById(R.id.tvLightActiveLabel)
        tvActiveLabel = findViewById(R.id.tvActiveLabel)
        tvVeryActiveLabel = findViewById(R.id.tvVeryActiveLabel)

        switchAuto = findViewById(R.id.switchAutoMode)
        etCal = findViewById(R.id.etCalorieGoal)
        etProt = findViewById(R.id.etProteinGoal)
        etCarb = findViewById(R.id.etCarbGoal)
        etFat = findViewById(R.id.etFatGoal)
        etSteps = findViewById(R.id.etStepsGoal)
        etWater = findViewById(R.id.etWaterGoal)

        val savedName = prefs.getString("USER_NAME", "") ?: ""
        etName.setText(savedName)
        updateAvatar(savedName)

        etAge.setText(prefs.getInt("USER_AGE", 0).takeIf { it > 0 }?.toString() ?: "")
        etWeight.setText(prefs.getFloat("USER_WEIGHT", 0f).takeIf { it > 0 }?.toString() ?: "")
        etHeight.setText(prefs.getInt("USER_HEIGHT", 0).takeIf { it > 0 }?.toString() ?: "")
        etGoalWeight.setText(prefs.getFloat("GOAL_WEIGHT", 0f).takeIf { it > 0 }?.toString() ?: "")

        isMale = prefs.getBoolean("IS_MALE", true)
        updateGenderCards()

        selectedGoal = prefs.getString("GOAL_TYPE", "Maintain") ?: "Maintain"
        updateGoalCards()

        selectedActivity = prefs.getString("ACTIVITY_LEVEL", "LightActive") ?: "LightActive"
        updateActivityCards()

        val isAuto = prefs.getBoolean("IS_AUTO_CALC", true)
        switchAuto.isChecked = isAuto
        updateInputFieldsState(isAuto)

        etCal.setText(prefs.getInt("CALORIE_GOAL", 2000).toString())
        etProt.setText(prefs.getInt("PROTEIN_GOAL", 150).toString())
        etCarb.setText(prefs.getInt("CARB_GOAL", 250).toString())
        etFat.setText(prefs.getInt("FAT_GOAL", 70).toString())
        etSteps.setText(prefs.getInt("STEPS_GOAL", 10000).toString())
        etWater.setText(prefs.getInt("WATER_GOAL", 2000).toString())

        etName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateAvatar(s.toString()) }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        cardMale.setOnClickListener { isMale = true; updateGenderCards(); recalcIfAuto() }
        cardFemale.setOnClickListener { isMale = false; updateGenderCards(); recalcIfAuto() }

        cardLose.setOnClickListener { selectedGoal = "Lose"; updateGoalCards(); recalcIfAuto() }
        cardMaintain.setOnClickListener { selectedGoal = "Maintain"; updateGoalCards(); recalcIfAuto() }
        cardBulk.setOnClickListener { selectedGoal = "Gain"; updateGoalCards(); recalcIfAuto() }

        cardSedentary.setOnClickListener { selectedActivity = "Sedentary"; updateActivityCards(); recalcIfAuto() }
        cardLightActive.setOnClickListener { selectedActivity = "LightActive"; updateActivityCards(); recalcIfAuto() }
        cardActive.setOnClickListener { selectedActivity = "Active"; updateActivityCards(); recalcIfAuto() }
        cardVeryActive.setOnClickListener { selectedActivity = "VeryActive"; updateActivityCards(); recalcIfAuto() }

        isUserInteracting = true
        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { if (isUserInteracting) recalcIfAuto() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }

        etAge.addTextChangedListener(textWatcher)
        etWeight.addTextChangedListener(textWatcher)
        etHeight.addTextChangedListener(textWatcher)

        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            updateInputFieldsState(isChecked)
            if (isChecked) calculateAutoTargets()
        }

        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            val editor = prefs.edit()

            editor.putString("USER_NAME", etName.text.toString())
            etAge.text.toString().toIntOrNull()?.let { editor.putInt("USER_AGE", it) }
            etWeight.text.toString().toFloatOrNull()?.let { editor.putFloat("USER_WEIGHT", it) }
            etHeight.text.toString().toIntOrNull()?.let { editor.putInt("USER_HEIGHT", it) }
            etGoalWeight.text.toString().toFloatOrNull()?.let { editor.putFloat("GOAL_WEIGHT", it) }

            editor.putBoolean("IS_MALE", isMale)
            editor.putString("GOAL_TYPE", selectedGoal)
            editor.putString("ACTIVITY_LEVEL", selectedActivity)
            editor.putBoolean("IS_AUTO_CALC", switchAuto.isChecked)

            etCal.text.toString().toIntOrNull()?.let { editor.putInt("CALORIE_GOAL", it) }
            etProt.text.toString().toIntOrNull()?.let { editor.putInt("PROTEIN_GOAL", it) }
            etCarb.text.toString().toIntOrNull()?.let { editor.putInt("CARB_GOAL", it) }
            etFat.text.toString().toIntOrNull()?.let { editor.putInt("FAT_GOAL", it) }
            etSteps.text.toString().toIntOrNull()?.let { editor.putInt("STEPS_GOAL", it) }
            etWater.text.toString().toIntOrNull()?.let { editor.putInt("WATER_GOAL", it) }

            editor.apply()

            // Salvăm și în baza de date Room
            val profile = UserProfile(
                name = etName.text.toString(),
                age = etAge.text.toString().toIntOrNull() ?: 0,
                weight = etWeight.text.toString().toFloatOrNull() ?: 0f,
                height = etHeight.text.toString().toIntOrNull() ?: 0,
                goalWeight = etGoalWeight.text.toString().toFloatOrNull() ?: 0f,
                isMale = isMale,
                goalType = selectedGoal,
                activityLevel = selectedActivity,
                isAutoCalc = switchAuto.isChecked,
                calorieGoal = etCal.text.toString().toIntOrNull() ?: 2000,
                proteinGoal = etProt.text.toString().toIntOrNull() ?: 150,
                carbGoal = etCarb.text.toString().toIntOrNull() ?: 250,
                fatGoal = etFat.text.toString().toIntOrNull() ?: 70,
                stepsGoal = etSteps.text.toString().toIntOrNull() ?: 10000,
                waterGoal = etWater.text.toString().toIntOrNull() ?: 2000
            )
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(applicationContext).userProfileDao().upsert(profile)
                }
            }

            Toast.makeText(this, "Profil salvat!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun updateAvatar(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty()) {
            tvAvatarInitial.text = trimmed.first().uppercase()
            tvGreeting.text = "Salut, $trimmed!"
        } else {
            tvAvatarInitial.text = "?"
            tvGreeting.text = "Salut!"
        }
    }

    private fun updateGenderCards() {
        setCardState(cardMale, tvMaleLabel, isMale)
        setCardState(cardFemale, tvFemaleLabel, !isMale)
    }

    private fun updateGoalCards() {
        setCardState(cardLose, tvLoseArrow, tvLoseLabel, selectedGoal == "Lose")
        setCardState(cardMaintain, tvMaintainArrow, tvMaintainLabel, selectedGoal == "Maintain")
        setCardState(cardBulk, tvBulkArrow, tvBulkLabel, selectedGoal == "Gain")
    }

    private fun updateActivityCards() {
        setCardState(cardSedentary, tvSedentaryLabel, selectedActivity == "Sedentary")
        setCardState(cardLightActive, tvLightActiveLabel, selectedActivity == "LightActive")
        setCardState(cardActive, tvActiveLabel, selectedActivity == "Active")
        setCardState(cardVeryActive, tvVeryActiveLabel, selectedActivity == "VeryActive")
    }

    private fun setCardState(card: CardView, label: TextView, isSelected: Boolean) {
        card.setCardBackgroundColor(if (isSelected) COLOR_SELECTED_BG else COLOR_UNSELECTED_BG)
        label.setTextColor(if (isSelected) COLOR_SELECTED_TEXT else COLOR_UNSELECTED_TEXT)
    }

    private fun setCardState(card: CardView, arrow: TextView, label: TextView, isSelected: Boolean) {
        card.setCardBackgroundColor(if (isSelected) COLOR_SELECTED_BG else COLOR_UNSELECTED_BG)
        val color = if (isSelected) COLOR_SELECTED_TEXT else COLOR_UNSELECTED_TEXT
        arrow.setTextColor(color)
        label.setTextColor(color)
    }

    private fun updateInputFieldsState(isAuto: Boolean) {
        val isEditable = !isAuto
        etCal.isEnabled = isEditable
        etProt.isEnabled = isEditable
        etCarb.isEnabled = isEditable
        etFat.isEnabled = isEditable
        etWater.isEnabled = true
    }

    private fun recalcIfAuto() {
        if (switchAuto.isChecked && isUserInteracting) calculateAutoTargets()
    }

    private fun getActivityMultiplier(): Double {
        return when (selectedActivity) {
            "Sedentary" -> 1.2
            "LightActive" -> 1.375
            "Active" -> 1.55
            "VeryActive" -> 1.725
            else -> 1.375
        }
    }

    private fun calculateAutoTargets() {
        if (!switchAuto.isChecked) return

        val weight = etWeight.text.toString().toDoubleOrNull() ?: return
        val height = etHeight.text.toString().toIntOrNull() ?: return
        val age = etAge.text.toString().toIntOrNull() ?: return

        var bmr = (10 * weight) + (6.25 * height) - (5 * age)
        bmr += if (isMale) 5 else -161

        val tdee = bmr * getActivityMultiplier()

        val (calOffset, protMultiplier, fatMultiplier) = when (selectedGoal) {
            "Lose" -> Triple(-500.0, 1.6, 0.7)
            "Gain" -> Triple(300.0, 2.0, 1.0)
            else -> Triple(0.0, 1.3, 0.9)
        }

        var targetCalories = tdee + calOffset
        if (targetCalories < 1200) targetCalories = 1200.0

        val proteinGrams = weight * protMultiplier
        val fatGrams = weight * fatMultiplier

        val calsUsed = (proteinGrams * 4) + (fatGrams * 9)
        var remainingCals = targetCalories - calsUsed

        if (remainingCals < 50) {
            remainingCals = 50.0
            targetCalories = calsUsed + 50.0
        }
        val carbGrams = remainingCals / 4

        if (etWater.text.isEmpty()) {
            val waterMl = weight * 35
            etWater.setText(waterMl.roundToInt().toString())
        }

        isUserInteracting = false
        etCal.setText(targetCalories.roundToInt().toString())
        etProt.setText(proteinGrams.roundToInt().toString())
        etFat.setText(fatGrams.roundToInt().toString())
        etCarb.setText(carbGrams.roundToInt().toString())
        isUserInteracting = true
    }
}
