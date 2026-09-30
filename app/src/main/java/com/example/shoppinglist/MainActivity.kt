package com.example.shoppinglist

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ShoppingRepository
    private lateinit var adapter: ShoppingAdapter
    private lateinit var textTotal: TextView
    private val allItems = mutableListOf<ShoppingItem>()
    private val displayedItems = mutableListOf<ShoppingItem>()
    private var currentCategory: String = "Все"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ShoppingRepository(this)
        allItems.addAll(repo.load())

        val editItem = findViewById<AutoCompleteTextView>(R.id.editItem)
        val spinnerCategory = findViewById<Spinner>(R.id.spinnerCategory)
        val editPrice = findViewById<EditText>(R.id.editPrice)
        val editQuantity = findViewById<EditText>(R.id.editQuantity)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnClear = findViewById<Button>(R.id.btnClearDone)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        textTotal = findViewById(R.id.textTotal)

        // --- Спиннер: "Все" первым ---
        val spinnerItems = mutableListOf("Все")
        spinnerItems.addAll(resources.getStringArray(R.array.categories))
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            spinnerItems
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        // --- Табы ---
        val cats = listOf("Все") + resources.getStringArray(R.array.categories).toList()
        cats.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentCategory = tab?.text?.toString() ?: "Все"
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        setupAutoComplete(editItem)
        setupPriceAutofill(editItem, editPrice)

        adapter = ShoppingAdapter(
            items = displayedItems,
            onChange = {
                repo.save(allItems)
                updateTotal()
            },
            onDelete = { item ->
                allItems.remove(item)
                repo.save(allItems)
                updateTotal()
            }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnAdd.setOnClickListener {
            val name = editItem.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var category = spinnerCategory.selectedItem.toString()
            if (category == "Все") category = "Разное"

            val price = editPrice.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val quantity = editQuantity.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

            allItems.add(ShoppingItem(name, category, price, quantity))

            repo.addToHistory(name)
            repo.saveLastPrice(name, price)
            setupAutoComplete(editItem)

            editItem.text.clear()
            editPrice.text.clear()
            editQuantity.setText("1")
            editItem.requestFocus()

            repo.save(allItems)
            applyFilter()
        }

        btnClear.setOnClickListener {
            val removed = allItems.filter { it.done }
            if (removed.isEmpty()) {
                Toast.makeText(this, "Нет купленных", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            allItems.removeAll(removed)
            repo.save(allItems)
            applyFilter()
        }

        applyFilter()
    }

    // При выборе из выпадашки — подставить последнюю цену
    private fun setupPriceAutofill(edit: AutoCompleteTextView, priceField: EditText) {
        val handler = Handler(Looper.getMainLooper())
        var pending: Runnable? = null

        edit.setOnItemClickListener { _, _, _, _ ->
            val name = edit.text.toString().trim()
            fillPrice(name, priceField)
        }

        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let { handler.removeCallbacks(it) }
                val r = Runnable {
                    val name = s?.toString()?.trim() ?: return@Runnable
                    if (name.isEmpty()) return@Runnable
                    if (!priceField.text.isNullOrEmpty()) return@Runnable
                    fillPrice(name, priceField)
                }
                pending = r
                handler.postDelayed(r, 600)
            }
        })
    }

    private fun fillPrice(name: String, priceField: EditText) {
        val last = repo.getLastPrice(name) ?: return
        if (!priceField.text.isNullOrEmpty()) return
        val txt = if (last % 1.0 == 0.0) last.toInt().toString()
                  else String.format("%.2f", last).trimEnd('0').trimEnd('.')
        priceField.setText(txt)
    }

    private fun applyFilter() {
        displayedItems.clear()
        if (currentCategory == "Все") {
            displayedItems.addAll(allItems)
        } else {
            displayedItems.addAll(allItems.filter { it.category == currentCategory })
        }
        adapter.notifyDataSetChanged()
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
        val total = allItems.filter { !it.done }.sumOf { it.total }
        val formatted = if (total % 1.0 == 0.0) total.toInt().toString()
                        else String.format("%.2f", total)
        textTotal.text = "Итого: $formatted ₽"
    }
}
