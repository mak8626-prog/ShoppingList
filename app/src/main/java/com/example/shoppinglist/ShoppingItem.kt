package com.example.shoppinglist

data class ShoppingItem(
    var name: String,
    var category: String = "Разное",
    var price: Double = 0.0,
    var quantity: Int = 1,
    var done: Boolean = false
) {
    val total: Double get() = price * quantity
}
