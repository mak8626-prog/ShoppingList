package com.example.shoppinglist

object CategoryColors {
    fun color(cat: String): Int = when (cat) {
        "Овощи и фрукты" -> 0xFF66BB6A.toInt()
        "Молочные продукты" -> 0xFF42A5F5.toInt()
        "Мясо и рыба" -> 0xFFEF5350.toInt()
        "Хлеб и выпечка" -> 0xFFFFA726.toInt()
        "Напитки" -> 0xFF29B6F6.toInt()
        "Бакалея" -> 0xFFAB47BC.toInt()
        "Заморозка" -> 0xFF26C6DA.toInt()
        "Сладости" -> 0xFFEC407A.toInt()
        "Бытовая химия" -> 0xFF9CCC65.toInt()
        else -> 0xFF9E9E9E.toInt()
    }
}
