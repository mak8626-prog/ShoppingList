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
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ShoppingRepository
    private lateinit var adapter: ShoppingAdapter
    private lateinit var textTotal: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var tabLayout: TabLayout
    private lateinit var emptyState: LinearLayout
    private val allItems = mutableListOf<ShoppingItem>()
    private val displayedItems = mutableListOf<ShoppingItem>()
    private var currentCategory: String = "Все"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repo = ShoppingRepository(this)

        AppCompatDelegate.setDefaultNightMode(
            if (repo.getBool(ShoppingRepository.SET_DARK_THEME, false))
                AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        setContentView(R.layout.activity_main)

        allItems.addAll(repo.load())

        val editItem = findViewById<AutoCompleteTextView>(R.id.editItem)
        val editPrice = findViewById<EditText>(R.id.editPrice)
        val editQuantity = findViewById<EditText>(R.id.editQuantity)
        val btnAdd = findViewById<Button>(R.id.btnAdd)
        val btnClear = findViewById<Button>(R.id.btnClearDone)
        val btnSettings = findViewById<ImageButton>(R.id.btnSettings)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        tabLayout = findViewById(R.id.tabLayout)
        chipGroup = findViewById(R.id.chipGroup)
        textTotal = findViewById(R.id.textTotal)
        emptyState = findViewById(R.id.emptyState)

        setupChips()
        setupTabs()

        setupAutoComplete(editItem)
        setupAutofill(editItem, editPrice)

        editItem.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(editItem)
                true
            } else false
        }

        val compact = repo.getBool(ShoppingRepository.SET_COMPACT, false)
        adapter = ShoppingAdapter(
            items = displayedItems,
            compact = compact,
            onChange = {
                repo.save(allItems)
                applyFilter()
            },
            onDelete = { pos -> syncAfterDelete(pos) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

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
                applyFilter()
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

            val clearFields = repo.getBool(ShoppingRepository.SET_CLEAR_FIELDS, true)
            if (clearFields) {
                editItem.text.clear()
                editPrice.text.clear()
                editQuantity.setText("1")
                editItem.requestFocus()
            }

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

        btnSettings.setOnClickListener { showSettings() }

        applyFilter()
    }

    // ---------- ТАБЫ ----------
    private fun setupTabs() {
        tabLayout.removeAllTabs()
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
    }

    private fun showSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)
        val swDark = view.findViewById<MaterialSwitch>(R.id.swDarkTheme)
        val swTabs = view.findViewById<MaterialSwitch>(R.id.swShowTabs)
        val swTags = view.findViewById<MaterialSwitch>(R.id.swShowTags)
        val swCompact = view.findViewById<MaterialSwitch>(R.id.swCompact)
        val swShowDone = view.findViewById<MaterialSwitch>(R.id.swShowDone)
        val swShowTotal = view.findViewById<MaterialSwitch>(R.id.swShowTotal)
        val swClearFields = view.findViewById<MaterialSwitch>(R.id.swClearFields)

        swDark.isChecked = repo.getBool(ShoppingRepository.SET_DARK_THEME, false)
        swTabs.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_TABS, true)
        swTags.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)
        swCompact.isChecked = repo.getBool(ShoppingRepository.SET_COMPACT, false)
        swShowDone.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_DONE, true)
        swShowTotal.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_TOTAL, true)
        swClearFields.isChecked = repo.getBool(ShoppingRepository.SET_CLEAR_FIELDS, true)

        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setView(view)
            .setPositiveButton("Ок") { _, _ ->
                repo.setBool(ShoppingRepository.SET_DARK_THEME, swDark.isChecked)
                repo.setBool(ShoppingRepository.SET_SHOW_TABS, swTabs.isChecked)
                repo.setBool(ShoppingRepository.SET_SHOW_TAGS, swTags.isChecked)
                repo.setBool(ShoppingRepository.SET_COMPACT, swCompact.isChecked)
                repo.setBool(ShoppingRepository.SET_SHOW_DONE, swShowDone.isChecked)
                repo.setBool(ShoppingRepository.SET_SHOW_TOTAL, swShowTotal.isChecked)
                repo.setBool(ShoppingRepository.SET_CLEAR_FIELDS, swClearFields.isChecked)
                recreate()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun syncAfterDelete(pos: Int) {
        val toRemove = allItems.filter { item -> !displayedItems.contains(item) }
        allItems.removeAll(toRemove)
        repo.save(allItems)
        applyFilter()
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
            textSize = 12f
            chipMinHeight = 28f * resources.displayMetrics.density
            chipStartPadding = 6f
            chipEndPadding = 6f
            setPadding(0, 0, 0, 0)
        }
        chipGroup.addView(chip)
    }

    val plusChip = Chip(this).apply {
        text = "+ тег"
        isCheckable = false
        textSize = 12f
        chipMinHeight = 28f * resources.displayMetrics.density
        chipStartPadding = 6f
        chipEndPadding = 6f
        setPadding(0, 0, 0, 0)
        setOnClickListener { showAddCategoryDialog() }
    }
    chipGroup.addView(plusChip)

    if (chipGroup.childCount > 0) {
        (chipGroup.getChildAt(0) as? Chip)?.isChecked = true
    }
    }

    private fun selectedCategory(): String {
        if (!repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)) return "Разное"

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
                    setupTabs()
                    selectCategory(name)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // ---------- АВТОЗАПОЛНЕНИЕ ----------
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
        // Цена: сначала последняя сохранённая пользователем
        if (priceField.text.isNullOrEmpty()) {
            repo.getLastPrice(name)?.let { last ->
                val txt = if (last % 1.0 == 0.0) last.toInt().toString()
                          else String.format("%.2f", last).trimEnd('0').trimEnd('.')
                priceField.setText(txt)
            }
        }

        // Категория: сначала пользовательская, потом из популярных
        if (repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)) {
            val userCat = repo.getCategoryForProduct(name)
            val popularCat = PopularProducts.getCategory(name)
            (userCat ?: popularCat)?.let { selectCategory(it) }
        }
    }

    // ---------- ФИЛЬТР + ВИДИМОСТЬ ----------
    private fun applyFilter() {
        val showTags = repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)
        val showTabs = repo.getBool(ShoppingRepository.SET_SHOW_TABS, true)

        chipGroup.visibility = if (showTags) View.VISIBLE else View.GONE
        tabLayout.visibility = if (showTabs) View.VISIBLE else View.GONE

        if (!showTabs) currentCategory = "Все"

        displayedItems.clear()
        val showDone = repo.getBool(ShoppingRepository.SET_SHOW_DONE, true)

        val byCategory = if (currentCategory == "Все") allItems
                         else allItems.filter { it.category == currentCategory }

        displayedItems.addAll(if (showDone) byCategory else byCategory.filter { !it.done })

        adapter.notifyDataSetChanged()

        emptyState.visibility = if (displayedItems.isEmpty()) View.VISIBLE else View.GONE

        val showTotal = repo.getBool(ShoppingRepository.SET_SHOW_TOTAL, true)
        textTotal.visibility = if (showTotal) View.VISIBLE else View.GONE

        updateTotal()
    }

    // ---------- АВТОДОПОЛНЕНИЕ: популярные + история ----------
    private fun setupAutoComplete(edit: AutoCompleteTextView) {
        val history = repo.loadHistory().toList()
        val popular = PopularProducts.names
        // Пользовательская история — в приоритете, потом популярные
        val all = (history + popular).distinct()
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            all
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
