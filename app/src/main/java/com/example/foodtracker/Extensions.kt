package com.example.foodtracker.entity // Sau pachetul tău

import java.text.Normalizer
import java.util.regex.Pattern

// Funcție care elimină diacriticele și face litere mici
fun String.cleanForSearch(): String {
    val temp = Normalizer.normalize(this, Normalizer.Form.NFD)
    val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    return pattern.matcher(temp).replaceAll("").lowercase().trim()
}