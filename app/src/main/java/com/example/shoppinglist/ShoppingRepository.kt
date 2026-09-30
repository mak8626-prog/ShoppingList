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
        val raw = prefs.getStringSet(KEY_HISTORY, emptySet()) ?: emptySet()
        return raw.toMutableSet()
    }

    fun addToHistory(name: String) {
        val history = loadHistory()
        history.add(name.trim())
        val trimmed = if (history.size > 100) history.toList().takeLast(100).toSet() else history
        prefs.edit().putStringSet(KEY_HISTORY, trimmed).apply()
    }

    // --- Запоминание последней цены ---
    fun saveLastPrice(name: String, price: Double) {
        if (price <= 0 || name.isBlank()) return
        val json = prefs.getString(KEY_PRICES, null)
        val obj = if (json != null) JSONObject(json) else JSONObject()
        obj.put(name.trim().lowercase(), price)
        prefs.edit().putString(KEY_PRICES, obj.toString()).apply()
    }

    fun getLastPrice(name: String): Double? {
        val json = prefs.getString(KEY_PRICES, null) ?: return null
        val obj = JSONObject(json)
        val key = name.trim().lowercase()
        return if (obj.has(key)) obj.getDouble(key) else null
    }

    companion object {
        private const val KEY_ITEMS = "items"
        private const val KEY_HISTORY = "history"
        private const val KEY_PRICES = "prices"
    }
}
