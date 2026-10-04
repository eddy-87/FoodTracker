package com.example.foodtracker

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.foodtracker.db.AppDatabase
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class StatsActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

    private lateinit var ringProgress: ProgressBar
    private lateinit var tvRingCalories: TextView
    private lateinit var tvCalPercent: TextView

    private lateinit var pbProtein: ProgressBar
    private lateinit var pbCarbs: ProgressBar
    private lateinit var pbFat: ProgressBar
    private lateinit var tvStatProt: TextView
    private lateinit var tvStatCarb: TextView
    private lateinit var tvStatFat: TextView

    private lateinit var tvStreakCount: TextView
    private lateinit var tvAvgCalories: TextView
    private lateinit var tvTotalMeals: TextView

    private lateinit var tvMealBreakfast: TextView
    private lateinit var tvMealLunch: TextView
    private lateinit var tvMealDinner: TextView
    private lateinit var tvMealSnack: TextView

    private lateinit var tvPrognosisTitle: TextView
    private lateinit var tvPrognosisTdee: TextView
    private lateinit var tvPrognosisAvg: TextView
    private lateinit var tvDailyBalance: TextView
    private lateinit var tvPrognosis30: TextView
    private lateinit var tvPrognosis60: TextView
    private lateinit var tvPrognosis90: TextView
    private lateinit var layoutPrognosisWithSteps: LinearLayout
    private lateinit var tvLabelWithSteps: TextView
    private lateinit var tvPrognosis30Steps: TextView
    private lateinit var tvPrognosis60Steps: TextView
    private lateinit var tvPrognosis90Steps: TextView
    private lateinit var layoutDaysToGoal: LinearLayout
    private lateinit var tvDaysToGoal: TextView
    private lateinit var tvPrognosisInsufficient: TextView

    private lateinit var barChart: BarChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_stats_bottom_sheet)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        db = AppDatabase.getDatabase(this)

        ringProgress = findViewById(R.id.ringProgress)
        tvRingCalories = findViewById(R.id.tvRingCalories)
        tvCalPercent = findViewById(R.id.tvCalPercent)

        pbProtein = findViewById(R.id.pbProtein)
        pbCarbs = findViewById(R.id.pbCarbs)
        pbFat = findViewById(R.id.pbFat)
        tvStatProt = findViewById(R.id.tvStatProt)
        tvStatCarb = findViewById(R.id.tvStatCarb)
        tvStatFat = findViewById(R.id.tvStatFat)

        tvStreakCount = findViewById(R.id.tvStreakCount)
        tvAvgCalories = findViewById(R.id.tvAvgCalories)
        tvTotalMeals = findViewById(R.id.tvTotalMeals)

        tvMealBreakfast = findViewById(R.id.tvMealBreakfast)
        tvMealLunch = findViewById(R.id.tvMealLunch)
        tvMealDinner = findViewById(R.id.tvMealDinner)
        tvMealSnack = findViewById(R.id.tvMealSnack)

        tvPrognosisTitle = findViewById(R.id.tvPrognosisTitle)
        tvPrognosisTdee = findViewById(R.id.tvPrognosisTdee)
        tvPrognosisAvg = findViewById(R.id.tvPrognosisAvg)
        tvDailyBalance = findViewById(R.id.tvDailyBalance)
        tvPrognosis30 = findViewById(R.id.tvPrognosis30)
        tvPrognosis60 = findViewById(R.id.tvPrognosis60)
        tvPrognosis90 = findViewById(R.id.tvPrognosis90)
        layoutPrognosisWithSteps = findViewById(R.id.layoutPrognosisWithSteps)
        tvLabelWithSteps = findViewById(R.id.tvLabelWithSteps)
        tvPrognosis30Steps = findViewById(R.id.tvPrognosis30Steps)
        tvPrognosis60Steps = findViewById(R.id.tvPrognosis60Steps)
        tvPrognosis90Steps = findViewById(R.id.tvPrognosis90Steps)
        layoutDaysToGoal = findViewById(R.id.layoutDaysToGoal)
        tvDaysToGoal = findViewById(R.id.tvDaysToGoal)
        tvPrognosisInsufficient = findViewById(R.id.tvPrognosisInsufficient)

        barChart = findViewById(R.id.barChartCalories)
        setupBarChartStyle()

        loadData()
    }

    private fun setupBarChartStyle() {
        barChart.description.isEnabled = false
        barChart.setDrawGridBackground(false)
        barChart.axisRight.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setTouchEnabled(false)
        barChart.setScaleEnabled(false)

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textColor = Color.parseColor("#AAAAAA")
        xAxis.textSize = 11f
        xAxis.axisLineColor = Color.TRANSPARENT

        val yAxis = barChart.axisLeft
        yAxis.textColor = Color.parseColor("#666666")
        yAxis.textSize = 10f
        yAxis.gridColor = Color.parseColor("#2C2C2E")
        yAxis.axisLineColor = Color.TRANSPARENT
        yAxis.setDrawAxisLine(false)

        barChart.setExtraOffsets(0f, 0f, 0f, 8f)
    }

    private fun loadData() {
        val prefs = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val calorieGoal = prefs.getInt("CALORIE_GOAL", 2000)
        val proteinGoal = prefs.getInt("PROTEIN_GOAL", 150)
        val carbGoal = prefs.getInt("CARB_GOAL", 250)
        val fatGoal = prefs.getInt("FAT_GOAL", 70)

        CoroutineScope(Dispatchers.IO).launch {
            val todayStart = getStartOfDay(0)
            val todayEnd = todayStart + (24 * 60 * 60 * 1000) - 1
            val todayFoods = db.foodDao().getFoodsByDate(todayStart, todayEnd)

            val totalCal = todayFoods.sumOf { it.calories }
            val totalProt = todayFoods.sumOf { it.protein }
            val totalCarbs = todayFoods.sumOf { it.carbs }
            val totalFat = todayFoods.sumOf { it.fat }
            val totalMeals = todayFoods.size

            val calPercent = if (calorieGoal > 0) min((totalCal * 100.0 / calorieGoal).roundToInt(), 999) else 0
            val protPercent = if (proteinGoal > 0) min((totalProt * 100.0 / proteinGoal).roundToInt(), 100) else 0
            val carbPercent = if (carbGoal > 0) min((totalCarbs * 100.0 / carbGoal).roundToInt(), 100) else 0
            val fatPercent = if (fatGoal > 0) min((totalFat * 100.0 / fatGoal).roundToInt(), 100) else 0

            val breakfastCal = todayFoods.filter { it.mealType.lowercase() in listOf("breakfast", "mic dejun") }.sumOf { it.calories }
            val lunchCal = todayFoods.filter { it.mealType.lowercase() in listOf("lunch", "prânz") }.sumOf { it.calories }
            val dinnerCal = todayFoods.filter { it.mealType.lowercase() in listOf("dinner", "cină") }.sumOf { it.calories }
            val snackCal = todayFoods.filter { it.mealType.lowercase() in listOf("snack", "gustare") }.sumOf { it.calories }

            val entries = ArrayList<BarEntry>()
            val labels = ArrayList<String>()
            val barColors = ArrayList<Int>()
            val dateFormat = SimpleDateFormat("EEE", Locale.getDefault())
            val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val stepPrefs = getSharedPreferences("StepPrefs", Context.MODE_PRIVATE)
            var totalWeekCals = 0
            var daysWithFood = 0
            var totalWeekStepsCals = 0
            var daysWithSteps = 0

            for (i in 6 downTo 0) {
                val start = getStartOfDay(i)
                val end = start + (24 * 60 * 60 * 1000) - 1
                val foods = db.foodDao().getFoodsByDate(start, end)
                val dailyCals = foods.sumOf { it.calories }.toFloat()

                entries.add(BarEntry((6 - i).toFloat(), dailyCals))

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -i)
                labels.add(if (i == 0) "Azi" else dateFormat.format(calendar.time))

                barColors.add(if (i == 0) Color.parseColor("#4CAF50") else Color.parseColor("#555555"))

                if (dailyCals > 0) {
                    totalWeekCals += dailyCals.toInt()
                    daysWithFood++
                }

                val dateStr = dayFormat.format(calendar.time)
                val stepCals = stepPrefs.getInt("steps_cals_$dateStr", 0)
                if (stepCals > 0) {
                    totalWeekStepsCals += stepCals
                    daysWithSteps++
                }
            }

            val avgCals = if (daysWithFood > 0) totalWeekCals / daysWithFood else 0
            val avgStepsCals = if (daysWithSteps > 0) totalWeekStepsCals / daysWithSteps else 0

            var streak = 0
            var dayOffset = 0
            while (true) {
                val start = getStartOfDay(dayOffset)
                val end = start + (24 * 60 * 60 * 1000) - 1
                val foods = db.foodDao().getFoodsByDate(start, end)
                if (foods.isNotEmpty()) {
                    streak++
                    dayOffset++
                } else {
                    break
                }
            }

            withContext(Dispatchers.Main) {
                tvRingCalories.text = totalCal.toString()
                tvCalPercent.text = "$calPercent% din obiectiv"

                if (calPercent > 100) {
                    tvCalPercent.setTextColor(Color.parseColor("#E57373"))
                }

                animateProgress(ringProgress, min(calPercent, 100))

                tvStatProt.text = "${totalProt.roundToInt()}g / ${proteinGoal}g"
                tvStatCarb.text = "${totalCarbs.roundToInt()}g / ${carbGoal}g"
                tvStatFat.text = "${totalFat.roundToInt()}g / ${fatGoal}g"

                animateProgress(pbProtein, protPercent)
                animateProgress(pbCarbs, carbPercent)
                animateProgress(pbFat, fatPercent)

                tvStreakCount.text = streak.toString()
                tvAvgCalories.text = avgCals.toString()
                tvTotalMeals.text = totalMeals.toString()

                tvMealBreakfast.text = "$breakfastCal kcal"
                tvMealLunch.text = "$lunchCal kcal"
                tvMealDinner.text = "$dinnerCal kcal"
                tvMealSnack.text = "$snackCal kcal"

                updateBarChart(entries, labels, barColors)

                updatePrognosis(avgCals, avgStepsCals, prefs)
            }
        }
    }

    private fun updatePrognosis(avgCals: Int, avgStepsCals: Int, prefs: android.content.SharedPreferences) {
        val weight = prefs.getFloat("USER_WEIGHT", 0f).toDouble()
        val height = prefs.getInt("USER_HEIGHT", 0)
        val age = prefs.getInt("USER_AGE", 0)
        val isMale = prefs.getBoolean("IS_MALE", true)
        val activityLevel = prefs.getString("ACTIVITY_LEVEL", "LightActive") ?: "LightActive"
        val goalType = prefs.getString("GOAL_TYPE", "Maintain") ?: "Maintain"
        val goalWeight = prefs.getFloat("GOAL_WEIGHT", 0f).toDouble()

        tvPrognosisTitle.text = when (goalType) {
            "Lose" -> "Prognoză Slabire"
            "Gain" -> "Prognoză Masă"
            else   -> "Prognoză Greutate"
        }

        if (weight <= 0 || height <= 0 || age <= 0 || avgCals == 0) {
            tvPrognosisInsufficient.visibility = android.view.View.VISIBLE
            return
        }
        tvPrognosisInsufficient.visibility = android.view.View.GONE

        var bmr = 10 * weight + 6.25 * height - 5 * age
        bmr += if (isMale) 5.0 else -161.0 // Mifflin-St Jeor

        val actMultiplier = when (activityLevel) {
            "Sedentary"   -> 1.2
            "LightActive" -> 1.375
            "Active"      -> 1.55
            "VeryActive"  -> 1.725
            else          -> 1.375
        }
        val tdee = (bmr * actMultiplier).roundToInt()

        val dailyBalanceFood = avgCals - tdee
        val dailyWeightChangeFood = dailyBalanceFood / 7700.0

        val w30 = weight + dailyWeightChangeFood * 30
        val w60 = weight + dailyWeightChangeFood * 60
        val w90 = weight + dailyWeightChangeFood * 90

        val dailyBalanceWithSteps = avgCals - tdee - avgStepsCals
        val dailyWeightChangeWithSteps = dailyBalanceWithSteps / 7700.0

        val totalBurned = tdee + avgStepsCals
        if (avgStepsCals > 0) {
            tvPrognosisTdee.text = "$totalBurned kcal\n(incl. pași)"
        } else {
            tvPrognosisTdee.text = "$tdee kcal"
        }
        tvPrognosisAvg.text = "$avgCals kcal"

        val displayBalance = if (avgStepsCals > 0) dailyBalanceWithSteps else dailyBalanceFood
        val balanceSign = if (displayBalance >= 0) "+" else ""
        val balanceColor = colorForBalance(displayBalance, goalType)
        tvDailyBalance.text = "$balanceSign$displayBalance kcal"
        tvDailyBalance.setTextColor(balanceColor)

        tvPrognosis30.text = String.format("%.1f kg", w30)
        tvPrognosis60.text = String.format("%.1f kg", w60)
        tvPrognosis90.text = String.format("%.1f kg", w90)
        val progColorFood = colorForChange(dailyWeightChangeFood, goalType)
        tvPrognosis30.setTextColor(progColorFood)
        tvPrognosis60.setTextColor(progColorFood)
        tvPrognosis90.setTextColor(progColorFood)

        if (avgStepsCals > 0) {
            val w30s = weight + dailyWeightChangeWithSteps * 30
            val w60s = weight + dailyWeightChangeWithSteps * 60
            val w90s = weight + dailyWeightChangeWithSteps * 90
            val progColorSteps = colorForChange(dailyWeightChangeWithSteps, goalType)

            tvLabelWithSteps.text = "Cu pași (~$avgStepsCals kcal/zi arse)"
            tvPrognosis30Steps.text = String.format("%.1f kg", w30s)
            tvPrognosis60Steps.text = String.format("%.1f kg", w60s)
            tvPrognosis90Steps.text = String.format("%.1f kg", w90s)
            tvPrognosis30Steps.setTextColor(progColorSteps)
            tvPrognosis60Steps.setTextColor(progColorSteps)
            tvPrognosis90Steps.setTextColor(progColorSteps)
            layoutPrognosisWithSteps.visibility = android.view.View.VISIBLE
        } else {
            layoutPrognosisWithSteps.visibility = android.view.View.GONE
        }

        val bestDailyChange = if (avgStepsCals > 0) dailyWeightChangeWithSteps else dailyWeightChangeFood
        if (goalWeight > 0 && bestDailyChange != 0.0) {
            val diff = goalWeight - weight
            val daysToGoal = (diff / bestDailyChange).toInt()
            val verb = if (goalType == "Gain") "Atingi masa" else "Atingi greutatea"
            if (daysToGoal > 0 && daysToGoal < 3650) {
                layoutDaysToGoal.visibility = android.view.View.VISIBLE
                tvDaysToGoal.text = "$verb țintă de ${String.format("%.1f", goalWeight)} kg în $daysToGoal zile"
            } else if (kotlin.math.abs(weight - goalWeight) < 0.5) {
                layoutDaysToGoal.visibility = android.view.View.VISIBLE
                tvDaysToGoal.text = if (goalType == "Gain") "Ai atins masa țintă!" else "Ai atins greutatea țintă!"
            } else {
                layoutDaysToGoal.visibility = android.view.View.GONE
            }
        } else {
            layoutDaysToGoal.visibility = android.view.View.GONE
        }
    }

    private fun colorForBalance(balance: Int, goalType: String): Int = when (goalType) {
        "Gain" -> when {
            balance > 100  -> Color.parseColor("#4CAF50")
            balance < -100 -> Color.parseColor("#E57373")
            else           -> Color.parseColor("#FFB74D")
        }
        else -> when {
            balance > 100  -> Color.parseColor("#E57373")
            balance < -100 -> Color.parseColor("#4CAF50")
            else           -> Color.parseColor("#FFB74D")
        }
    }

    private fun colorForChange(change: Double, goalType: String): Int = when (goalType) {
        "Gain" -> when {
            change > 0 -> Color.parseColor("#4CAF50")
            change < 0 -> Color.parseColor("#E57373")
            else       -> Color.parseColor("#FFB74D")
        }
        else -> when {
            change < 0 -> Color.parseColor("#4CAF50")
            change > 0 -> Color.parseColor("#E57373")
            else       -> Color.parseColor("#FFB74D")
        }
    }

    private fun animateProgress(progressBar: ProgressBar, targetProgress: Int) {
        val animator = ObjectAnimator.ofInt(progressBar, "progress", 0, targetProgress)
        animator.duration = 800
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    private fun updateBarChart(entries: List<BarEntry>, labels: List<String>, colors: List<Int>) {
        val dataSet = BarDataSet(entries, "")
        dataSet.colors = colors
        dataSet.setDrawValues(false)
        dataSet.barBorderWidth = 0f

        val data = BarData(dataSet)
        data.barWidth = 0.5f
        barChart.data = data
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        barChart.animateY(800)
        barChart.invalidate()
    }

    private fun getStartOfDay(daysAgo: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
