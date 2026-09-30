package com.example.shoppinglist

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ShoppingRepository
    private lateinit var adapter: ShoppingAdapter
    private lateinit var textTotal: TextView
    private lateinit var chipGroup: ChipGroup
    private val allItems = mutableListOf<ShoppingItem>()
    private val displayedItems = mutableListOf<ShoppingItem>()
    private var currentCategory: String = "Все"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repo = ShoppingRepository(this)
        allItems.addAll(repo.load())

        val editItem = findViewById<AutoCompleteTextView>(R.id.editItem)
        val editPrice = findViewById<EditText>(R.id.editPrice)
        val editQuantity = findViewById<EditText>(R.id.editQuantity)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnClear = findViewById<Button>(R.id.btnClearDone)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        chipGroup = findViewById(R.id.chipGroup)
        textTotal = findViewById(R.id.textTotal)

        setupChips()

        // --- Табы ---
        val cats = listOf("Все") + allCategoryNames()
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
        setupAutofill(editItem, editPrice)

        // --- Скрывать клавиатуру при нажатии "Готово" ---
        editItem.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editItem)
                true
            } else false
        }

        adapter = ShoppingAdapter(
            items = displayedItems,
            onChange = {
                repo.save(allItems)
                updateTotal()
            },
            onDelete = { pos -> syncAfterDelete(pos) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // --- Свайп влево для удаления ---
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0, ItemTouchHelper.LEFT
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val removed = displayedItems.removeAt(pos)
                allItems.remove(removed)
                adapter.notifyItemRemoved(pos)
                repo.save(allItems)
                updateTotal()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recycler)

        btnAdd.setOnClickListener {
            val name = editItem.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = selectedCategory()
            val price = editPrice.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val quantity = editQuantity.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1

            allItems.add(ShoppingItem(name, category, price, quantity))

            repo.addToHistory(name)
            repo.saveLastPrice(name, price)
            repo.saveCategoryForProduct(name, category)
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

    private fun syncAfterDelete(pos: Int) {
        val toRemove = allItems.filter { item -> !displayedItems.contains(item) }
        allItems.removeAll(toRemove)
        repo.save(allItems)
        updateTotal()
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    // ---------- ЧИПЫ ----------
    private fun allCategoryNames(): List<String> {
        val base = resources.getStringArray(R.array.categories).toList()
        return (base + repo.loadCustomCategories()).distinct()
    }

    private fun setupChips() {
        chipGroup.removeAllViews()
        val cats = allCategoryNames()
        cats.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isClickable = true
            }
            chipGroup.addView(chip)
        }

        val plusChip = Chip(this).apply {
            text = "+ тег"
            isCheckable = false
            setOnClickListener { showAddCategoryDialog() }
        }
        chipGroup.addView(plusChip)

        if (chipGroup.childCount > 0) {
            (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
        }
    }

    private fun selectedCategory(): String {
        val id = chipGroup.checkedChipId
        if (id == View.NO_ID) return "Разное"
        val chip = chipGroup.findViewById<Chip>(id)
        return chip?.text?.toString() ?: "Разное"
    }

    private fun selectCategory(cat: String) {
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == cat) {
                chip.isChecked = true
                return
            }
        }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this).apply {
            hint = "Название категории"
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(this)
            .setTitle("Новая категория")
            .setView(input)
            .setPositiveButton("Добавить") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    repo.addCustomCategory(name)
                    setupChips()
                    selectCategory(name)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- АВТОЗАПОЛНЕНИЕ ЦЕНЫ + ТЕГА ----------
    private fun setupAutofill(edit: AutoCompleteTextView, priceField: EditText) {
        val handler = Handler(Looper.getMainLooper())
        var pending: Runnable? = null

        edit.setOnItemClickListener { _, _, _, _ ->
            autofill(edit.text.toString().trim(), priceField)
        }

        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                pending?.let { handler.removeCallbacks(it) }
                val r = Runnable {
                    val name = s?.toString()?.trim() ?: return@Runnable
                    if (name.isEmpty()) return@Runnable
                    autofill(name, priceField)
                }
                pending = r
                handler.postDelayed(r, 600)
            }
        })
    }

    private fun autofill(name: String, priceField: EditText) {
        if (priceField.text.isNullOrEmpty()) {
            repo.getLastPrice(name)?.let { last ->
                val txt = if (last % 1.0 == 0.0) last.toInt().toString()
                          else String.format("%.2f", last).trimEnd('0').trimEnd('.')
                priceField.setText(txt)
            }
        }
        repo.getCategoryForProduct(name)?.let { selectCategory(it) }
    }

    // ---------- ФИЛЬТР ----------
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
