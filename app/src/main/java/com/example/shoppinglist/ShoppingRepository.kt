package com.example.shoppinglist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ShoppingRepository(context: Context) {

    private val prefs = context.getSharedPreferences("shopping_prefs", Context.MODE_PRIVATE)

    fun load(): MutableList<ShoppingItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return mutableListOf()
        val arr = JSONArray(raw)
        val list = mutableListOf<ShoppingItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                ShoppingItem(
                    name = o.getString("name"),
                    category = o.optString("category", "Разное"),
                    price = o.optDouble("price", 0.0),
                    quantity = o.optInt("quantity", 1),
                    done = o.optBoolean("done", false)
                )
            )
        }
        return list
    }

    fun save(items: List<ShoppingItem>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("category", it.category)
                put("price", it.price)
                put("quantity", it.quantity)
                put("done", it.done)
            })
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun loadHistory(): MutableSet<String> {
        return prefs.getStringSet(KEY_HISTORY, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun addToHistory(name: String) {
        val history = loadHistory()
        history.add(name.trim())
        val trimmed = if (history.size > 100) history.toList().takeLast(100).toSet() else history
        prefs.edit().putStringSet(KEY_HISTORY, trimmed).apply()
    }

    fun saveLastPrice(name: String, price: Double) {
        if (price <= 0 || name.isBlank()) return
        val obj = getPricesObj()
        obj.put(name.trim().lowercase(), price)
        prefs.edit().putString(KEY_PRICES, obj.toString()).apply()
    }

    fun getLastPrice(name: String): Double? {
        val obj = getPricesObj()
        val key = name.trim().lowercase()
        return if (obj.has(key)) obj.getDouble(key) else null
    }

    private fun getPricesObj(): JSONObject {
        val json = prefs.getString(KEY_PRICES, null)
        return if (json != null) JSONObject(json) else JSONObject()
    }

    fun saveCategoryForProduct(name: String, category: String) {
        if (name.isBlank() || category.isBlank()) return
        val obj = getCategoriesObj()
        obj.put(name.trim().lowercase(), category)
        prefs.edit().putString(KEY_PRODUCT_CATS, obj.toString()).apply()
    }

    fun getCategoryForProduct(name: String): String? {
        val obj = getCategoriesObj()
        val key = name.trim().lowercase()
        return if (obj.has(key)) obj.getString(key) else null
    }

    private fun getCategoriesObj(): JSONObject {
        val json = prefs.getString(KEY_PRODUCT_CATS, null)
        return if (json != null) JSONObject(json) else JSONObject()
    }

    fun loadCustomCategories(): MutableSet<String> {
        return prefs.getStringSet(KEY_CUSTOM_CATS, emptySet())?.toMutableSet() ?: mutableSetOf()
    }

    fun addCustomCategory(name: String) {
        val cats = loadCustomCategories()
        cats.add(name.trim())
        prefs.edit().putStringSet(KEY_CUSTOM_CATS, cats).apply()
    }

    // --- Настройки ---
    fun getBool(key: String, def: Boolean): Boolean = prefs.getBoolean(key, def)
    fun setBool(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    companion object {
    const val KEY_ITEMS = "items"
    const val KEY_HISTORY = "history"
    const val KEY_PRICES = "prices"
    const val KEY_PRODUCT_CATS = "product_cats"
    const val KEY_CUSTOM_CATS = "custom_cats"

    // Настройки
    const val SET_DARK_THEME = "dark_theme"
    const val SET_COMPACT = "compact_mode"
    const val SET_SHOW_DONE = "show_done"
    const val SET_SHOW_TOTAL = "show_total"
    const val SET_CLEAR_FIELDS = "clear_fields"
    const val SET_SHOW_TAGS = "show_tags"
    const val SET_SHOW_TABS = "show_tabs"
    }
}
