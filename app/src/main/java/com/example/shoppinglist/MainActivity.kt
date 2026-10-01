package com.example.shoppinglist

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    private lateinit var repo: ShoppingRepository
    private lateinit var adapter: ShoppingAdapter
    private lateinit var screenList: View
    private lateinit var screenRecipes: View
    private lateinit var screenSettings: View
    private lateinit var fabAdd: FloatingActionButton
    private lateinit var btnClearDone: ImageButton
    private lateinit var tabLayout: TabLayout
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var textTotal: TextView
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

        screenList = findViewById(R.id.screenList)
        screenRecipes = findViewById(R.id.screenRecipes)
        screenSettings = findViewById(R.id.screenSettings)
        fabAdd = findViewById(R.id.fabAdd)
        btnClearDone = findViewById(R.id.btnClearDone)
        tabLayout = findViewById(R.id.tabLayout)
        recycler = findViewById(R.id.recycler)
        emptyState = findViewById(R.id.emptyState)
        textTotal = findViewById(R.id.textTotal)

        setupTabs()

        val compact = repo.getBool(ShoppingRepository.SET_COMPACT, false)
        adapter = ShoppingAdapter(
            items = displayedItems,
            compact = compact,
            onChange = {
                repo.save(allItems)
                val hideDone = !repo.getBool(ShoppingRepository.SET_SHOW_DONE, true)
                if (hideDone) recycler.post { applyFilter() } else updateTotal()
            },
            onDelete = { pos -> removeWithUndo(pos) },
            onEdit = { pos -> showEditDialog(pos) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) removeWithUndo(pos)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recycler)

        btnClearDone.setOnClickListener {
            val removed = allItems.filter { it.done }
            if (removed.isEmpty()) {
                Toast.makeText(this, "Нет купленных", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            allItems.removeAll(removed)
            repo.save(allItems)
            applyFilter()
        }

        val recipesRecycler = findViewById<RecyclerView>(R.id.recipesRecycler)
        recipesRecycler.layoutManager = LinearLayoutManager(this)
        recipesRecycler.adapter = RecipesAdapter(DishTemplates.dishes) { dish -> addDishIngredients(dish) }

        setupSettingsScreen()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_list -> { showScreen(0); true }
                R.id.nav_recipes -> { showScreen(1); true }
                R.id.nav_settings -> { showScreen(2); true }
                else -> false
            }
        }

        fabAdd.setOnClickListener { showAddSheet() }
        showScreen(0)
        applyFilter()
    }

    private fun showScreen(index: Int) {
        screenList.visibility = if (index == 0) View.VISIBLE else View.GONE
        screenRecipes.visibility = if (index == 1) View.VISIBLE else View.GONE
        screenSettings.visibility = if (index == 2) View.VISIBLE else View.GONE
        fabAdd.visibility = if (index == 0) View.VISIBLE else View.GONE
        btnClearDone.visibility = if (index == 0) View.VISIBLE else View.GONE
    }

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

    private fun showAddSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add, null)
        sheet.setContentView(view)

        val bsChips = view.findViewById<ChipGroup>(R.id.bsChipGroup)
        val bsEditItem = view.findViewById<AutoCompleteTextView>(R.id.bsEditItem)
        val bsEditPrice = view.findViewById<EditText>(R.id.bsEditPrice)
        val bsEditQty = view.findViewById<EditText>(R.id.bsEditQuantity)
        val bsBtnTogglePrice = view.findViewById<Button>(R.id.bsBtnTogglePrice)
        val bsBtnAdd = view.findViewById<Button>(R.id.bsBtnAdd)

        setupChipsInto(bsChips)

        val all = (repo.loadHistory().toList() + PopularProducts.names).distinct()
        bsEditItem.threshold = 1
        bsEditItem.dropDownHeight = (200 * resources.displayMetrics.density).toInt()
        bsEditItem.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, all))

        bsBtnTogglePrice.setOnClickListener {
            if (bsEditPrice.visibility == View.GONE) {
                bsEditPrice.visibility = View.VISIBLE
                bsEditPrice.requestFocus()
                bsBtnTogglePrice.text = "- Цена"
            } else {
                bsEditPrice.visibility = View.GONE
                bsEditPrice.text.clear()
                bsBtnTogglePrice.text = "+ Цена"
            }
        }

        bsEditItem.setOnItemClickListener { _, _, _, _ ->
            val name = bsEditItem.text.toString().trim()
            if (bsEditPrice.text.isNullOrEmpty()) {
                val last = repo.getLastPrice(name)
                if (last != null) {
                    val txt = if (last % 1.0 == 0.0) last.toInt().toString()
                              else String.format("%.2f", last).trimEnd('0').trimEnd('.')
                    bsEditPrice.setText(txt)
                    if (bsEditPrice.visibility == View.GONE) {
                        bsEditPrice.visibility = View.VISIBLE
                        bsBtnTogglePrice.text = "- Цена"
                    }
                }
            }
            val autoCat = repo.getCategoryForProduct(name) ?: PopularProducts.getCategory(name)
            if (autoCat != null) selectChipIn(bsChips, autoCat)
        }

        bsBtnAdd.setOnClickListener {
            if (!bsBtnAdd.isEnabled) return@setOnClickListener
            bsBtnAdd.isEnabled = false
            val name = bsEditItem.text.toString().trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Введите название", Toast.LENGTH_SHORT).show()
                bsBtnAdd.isEnabled = true
                return@setOnClickListener
            }
            val category = selectedChipIn(bsChips)
            val price = bsEditPrice.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val quantity = bsEditQty.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
            val existing = allItems.find { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                existing.quantity += quantity
                if (existing.price <= 0 && price > 0) existing.price = price
                Toast.makeText(this, "$name уже в списке: теперь ${existing.quantity} шт", Toast.LENGTH_SHORT).show()
            } else {
                allItems.add(ShoppingItem(name, category, price, quantity))
            }
            repo.addToHistory(name)
            if (price > 0) repo.saveLastPrice(name, price)
            repo.saveCategoryForProduct(name, category)
            repo.save(allItems)
            applyFilter()
            sheet.dismiss()
        }

        sheet.setOnShowListener {
            bsEditItem.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(bsEditItem, InputMethodManager.SHOW_IMPLICIT)
        }
        sheet.show()
    }

    private fun addDishIngredients(dish: DishTemplates.Dish) {
        var added = 0
        dish.ingredients.forEach { pair ->
            val name = pair.first
            val category = pair.second
            if (allItems.none { it.name.equals(name, ignoreCase = true) }) {
                val price = repo.getLastPrice(name) ?: 0.0
                allItems.add(ShoppingItem(name, category, price, 1))
                added++
            }
        }
        repo.save(allItems)
        applyFilter()
        val msg = if (added > 0) "${dish.emoji} ${dish.name}: +$added" else "Все ингредиенты уже в списке"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun setupSettingsScreen() {
        val swDark = findViewById<MaterialSwitch>(R.id.swDarkTheme)
        val swTags = findViewById<MaterialSwitch>(R.id.swShowTags)
        val swCompact = findViewById<MaterialSwitch>(R.id.swCompact)
        val swShowDone = findViewById<MaterialSwitch>(R.id.swShowDone)
        val swShowTotal = findViewById<MaterialSwitch>(R.id.swShowTotal)
        val swClearFields = findViewById<MaterialSwitch>(R.id.swClearFields)

        swDark.isChecked = repo.getBool(ShoppingRepository.SET_DARK_THEME, false)
        swTags.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)
        swCompact.isChecked = repo.getBool(ShoppingRepository.SET_COMPACT, false)
        swShowDone.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_DONE, true)
        swShowTotal.isChecked = repo.getBool(ShoppingRepository.SET_SHOW_TOTAL, true)
        swClearFields.isChecked = repo.getBool(ShoppingRepository.SET_CLEAR_FIELDS, true)

        swDark.setOnCheckedChangeListener { _, v -> repo.setBool(ShoppingRepository.SET_DARK_THEME, v); recreate() }
        swTags.setOnCheckedChangeListener { _, v -> repo.setBool(ShoppingRepository.SET_SHOW_TAGS, v); applyFilter() }
        swCompact.setOnCheckedChangeListener { _, v -> repo.setBool(ShoppingRepository.SET_COMPACT, v); recreate() }
        swShowDone.setOnCheckedChangeListener { _, v -> repo.setBool(ShoppingRepository.SET_SHOW_DONE, v); applyFilter() }
        swShowTotal.setOnCheckedChangeListener { _, v -> repo.setBool(ShoppingRepository.SET_SHOW_TOTAL, v); applyFilter() }
        swClearFields.setOnCheckedChangeListener { _, v -> repo.setBool(ShoppingRepository.SET_CLEAR_FIELDS, v) }
    }

    private fun removeWithUndo(pos: Int) {
        if (pos < 0 || pos >= displayedItems.size) return
        val removed = displayedItems.removeAt(pos)
        val globalIndex = allItems.indexOf(removed)
        if (globalIndex >= 0) allItems.removeAt(globalIndex)
        adapter.notifyItemRemoved(pos)
        repo.save(allItems)
        applyFilter()
        Snackbar.make(findViewById(R.id.coordinator), "$removed.name удалён", Snackbar.LENGTH_LONG)
            .setAction("Вернуть") {
                if (globalIndex in 0..allItems.size) allItems.add(globalIndex, removed)
                else allItems.add(removed)
                repo.save(allItems)
                applyFilter()
            }.show()
    }

    private fun showEditDialog(pos: Int) {
        if (pos < 0 || pos >= displayedItems.size) return
        val item = displayedItems[pos]
        val view = layoutInflater.inflate(R.layout.dialog_edit, null)
        val eName = view.findViewById<EditText>(R.id.editName)
        val ePrice = view.findViewById<EditText>(R.id.editPrice)
        val eQty = view.findViewById<EditText>(R.id.editQty)
        val spinnerCat = view.findViewById<Spinner>(R.id.spinnerCat)

        eName.setText(item.name)
        if (item.price > 0) {
            val txt = if (item.price % 1.0 == 0.0) item.price.toInt().toString()
                      else String.format("%.2f", item.price).trimEnd('0').trimEnd('.')
            ePrice.setText(txt)
        }
        eQty.setText(item.quantity.toString())

        val cats = allCategoryNames()
        val spAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, cats)
        spAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCat.adapter = spAdapter
        val idx = cats.indexOf(item.category)
        spinnerCat.setSelection(if (idx >= 0) idx else 0)

        AlertDialog.Builder(this)
            .setTitle("Редактировать товар")
            .setView(view)
            .setPositiveButton("Сохранить") { _, _ ->
                val newName = eName.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                item.name = newName
                item.price = ePrice.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
                item.quantity = eQty.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 1
                item.category = spinnerCat.selectedItem.toString()
                repo.save(allItems)
                repo.saveLastPrice(newName, item.price)
                repo.saveCategoryForProduct(newName, item.category)
                applyFilter()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun allCategoryNames(): List<String> {
        val base = resources.getStringArray(R.array.categories).toList()
        return (base + repo.loadCustomCategories()).distinct()
    }

    private fun setupChipsInto(group: ChipGroup) {
        group.removeAllViews()
        allCategoryNames().forEach { cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isClickable = true
                textSize = 11f
                chipMinHeight = 20f * resources.displayMetrics.density
                chipStartPadding = 4f
                chipEndPadding = 4f
            }
            val bg = ColorUtils.setAlphaComponent(categoryColor(cat), 80)
            chip.chipBackgroundColor = ColorStateList.valueOf(bg)
            group.addView(chip)
        }
        val plusChip = Chip(this).apply {
            text = "+ тег"
            textSize = 11f
            chipMinHeight = 20f * resources.displayMetrics.density
            setOnClickListener {
                val input = EditText(this@MainActivity).apply { hint = "Название категории" }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Новая категория")
                    .setView(input)
                    .setPositiveButton("Добавить") { _, _ ->
                        val n = input.text.toString().trim()
                        if (n.isNotEmpty()) {
                            repo.addCustomCategory(n)
                            setupTabs()
                            setupChipsInto(group)
                            selectChipIn(group, n)
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
        }
        group.addView(plusChip)
    }

    private fun selectChipIn(group: ChipGroup, cat: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == cat) { chip.isChecked = true; return }
        }
    }

    private fun selectedChipIn(group: ChipGroup): String {
        val id = group.checkedChipId
        if (id == View.NO_ID) return "Разное"
        return group.findViewById<Chip>(id)?.text?.toString() ?: "Разное"
    }

    private fun categoryColor(cat: String): Int = when (cat) {
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

    private fun applyFilter() {
        val showTabs = repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)
        tabLayout.visibility = if (showTabs) View.VISIBLE else View.GONE
        if (!showTabs) currentCategory = "Все"
        displayedItems.clear()
        val showDone = repo.getBool(ShoppingRepository.SET_SHOW_DONE, true)
        val byCategory = if (currentCategory == "Все") allItems
                         else allItems.filter { it.category == currentCategory }
        displayedItems.addAll(if (showDone) byCategory else byCategory.filter { !it.done })
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (displayedItems.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (displayedItems.isEmpty()) View.GONE else View.VISIBLE
        val showTotal = repo.getBool(ShoppingRepository.SET_SHOW_TOTAL, true)
        textTotal.visibility = if (showTotal) View.VISIBLE else View.GONE
        updateTotal()
    }

    private fun updateTotal() {
        val total = allItems.filter { !it.done }.sumOf { it.total }
        val formatted = if (total % 1.0 == 0.0) total.toInt().toString()
                        else String.format("%.2f", total)
        textTotal.text = "Итого: $formatted ₽"
    }
}
