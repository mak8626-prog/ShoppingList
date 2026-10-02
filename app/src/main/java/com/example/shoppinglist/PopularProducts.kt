package com.example.shoppinglist

object PopularProducts {

    // Объединяем base + extra, убираем дубликаты
    val map: Map<String, String> = (PopularProductsData.base + PopularProductsExtra.extra)

    val names: List<String> get() = map.keys.sorted()

    fun getCategory(name: String): String? {
        val key = name.trim()
        return map.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }
}
