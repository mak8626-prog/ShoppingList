package com.example.shoppinglist

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ShoppingRepository
    private lateinit var adapter: ShoppingAdapter
    private lateinit var textTotal: TextView
    private val items = mutableListOf<ShoppingItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ShoppingRepository(this)
        items.addAll(repo.load())

        val editItem = findViewById<AutoCompleteTextView>(R.id.editItem)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val editPrice = findViewById<EditText>(R.id.editPrice)
        val editQuantity = findViewById<EditText>(R.id.editQuantity)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnClear = findViewById<Button>(R.id.btnClearDone)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        textTotal = findViewById(R.id.textTotal)

        setupAutoComplete(editItem)

        adapter = ShoppingAdapter(items) {
            repo.save(items)
            updateTotal()
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnAdd.setOnClickListener {
            val name = editItem.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = spinnerCategory.selectedItem.toString()
            val price = editPrice.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val quantity = editQuantity.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

            items.add(ShoppingItem(name, category, price, quantity))
            adapter.notifyItemInserted(items.size - 1)

            repo.addToHistory(name)
            setupAutoComplete(editItem)

            editItem.text.clear()
            editPrice.text.clear()
            editQuantity.setText("1")
            editItem.requestFocus()

            repo.save(items)
            updateTotal()
        }

        btnClear.setOnClickListener {
            val removed = items.filter { it.done }
            if (removed.isEmpty()) {
                Toast.makeText(this, "Нет купленных", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            items.removeAll(removed)
            adapter.notifyDataSetChanged()
            repo.save(items)
            updateTotal()
        }

        updateTotal()
    }

    private fun setupAutoComplete(edit: AutoCompleteTextView) {
        val history = repo.loadHistory().toList().sorted()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            history
        )
        edit.setAdapter(adapter)
    }

    private fun updateTotal() {
        val total = items.filter { !it.done }.sumOf { it.total }
        val formatted = if (total % 1.0 == 0.0) total.toInt().toString()
                        else String.format("%.2f", total)
        textTotal.text = "Итого: $formatted ₽"
    }
}
