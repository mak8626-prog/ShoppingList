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
                applyFilter()
            },
            onDelete = { pos -> removeWithUndo(pos) },
            onEdit = { pos -> showEditDialog(pos) }
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
                removeWithUndo(pos)
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
        recipesRecycler.adapter = RecipesAdapter(DishTemplates.dishes) { dish ->
            addDishIngredients(dish)
        }

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

    // ---------- ФОРМА ДОБАВЛЕНИЯ ----------
    private fun showAddSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add, null)
        sheet.setContentView(view)

        val bsChips = view.findViewById<ChipGroup>(R.id.bsChipGroup)
        val bsEditItem = view.findViewById<AutoCompleteTextView>(R.id.bsEditItem)
        val bsEditPrice = view.findViewById<EditText>(R.id.bsEditPrice)
        val bsEditQty = view.findViewById<EditText>(R.id.bsEditQuantity)
        val bsBtnAdd = view.findViewById<Button>(R.id.bsBtnAdd)

        setupChipsInto(bsChips)

        val history = repo.loadHistory().toList()
        val popular = PopularProducts.names
        val all = (history + popular).distinct()
        bsEditItem.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, all)
        )

        bsEditItem.setOnItemClickListener { _, _, _, _ ->
            val name = bsEditItem.text.toString().trim()
            if (bsEditPrice.text.isNullOrEmpty()) {
                repo.getLastPrice(name)?.let { last ->
                    val txt = if (last % 1.0 == 0.0) last.toInt().toString()
                              else String.format("%.2f", last).trimEnd('0').trimEnd('.')
                    bsEditPrice.setText(txt)
                }
            }
            // Автовыбор тега только если товар из истории / популярных
            val userCat = repo.getCategoryForProduct(name)
            val popularCat = PopularProducts.getCategory(name)
            val autoCat = userCat ?: popularCat
            if (autoCat != null) {
                selectChipIn(bsChips, autoCat)
            }
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
                Toast.makeText(
                    this,
                    "«$name» уже в списке: теперь ${existing.quantity} шт",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                allItems.add(ShoppingItem(name, category, price, quantity))
            }

            repo.addToHistory(name)
            repo.saveLastPrice(name, price)
            repo.saveCategoryForProduct(name, category)
            repo.save(allItems)
            applyFilter()
            sheet.dismiss()
        }

        // Автофокус и клавиатура при открытии
        sheet.setOnShowListener {
            bsEditItem.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(bsEditItem, InputMethodManager.SHOW_IMPLICIT)
        }

        sheet.show()
    }

    // ---------- РЕЦЕПТЫ ----------
    private fun addDishIngredients(dish: DishTemplates.Dish) {
        var added = 0
        dish.ingredients.forEach { pair ->
            val name = pair.first
            val category = pair.second
            val exists = allItems.any { it.name.equals(name, ignoreCase = true) }
            if (!exists) {
                val price = repo.getLastPrice(name) ?: 0.0
                allItems.add(ShoppingItem(name, category, price, 1))
                added++
            }
        }
        repo.save(allItems)
        applyFilter()
        if (added > 0) {
            Toast.makeText(this, "${dish.emoji} ${dish.name}: +$added", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Все ингредиенты уже в списке", Toast.LENGTH_SHORT).show()
        }
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

        swDark.setOnCheckedChangeListener { _, isChecked ->
            repo.setBool(ShoppingRepository.SET_DARK_THEME, isChecked)
            recreate()
        }
        swTags.setOnCheckedChangeListener { _, isChecked ->
            repo.setBool(ShoppingRepository.SET_SHOW_TAGS, isChecked)
            applyFilter()
        }
        swCompact.setOnCheckedChangeListener { _, isChecked ->
            repo.setBool(ShoppingRepository.SET_COMPACT, isChecked)
            recreate()
        }
        swShowDone.setOnCheckedChangeListener { _, isChecked ->
            repo.setBool(ShoppingRepository.SET_SHOW_DONE, isChecked)
            applyFilter()
        }
        swShowTotal.setOnCheckedChangeListener { _, isChecked ->
            repo.setBool(ShoppingRepository.SET_SHOW_TOTAL, isChecked)
            applyFilter()
        }
        swClearFields.setOnCheckedChangeListener { _, isChecked ->
            repo.setBool(ShoppingRepository.SET_CLEAR_FIELDS, isChecked)
        }
    }

    // ---------- УДАЛЕНИЕ С ОТМЕНОЙ ----------
    private fun removeWithUndo(pos: Int) {
        if (pos < 0 || pos >= displayedItems.size) return
        val removed = displayedItems.removeAt(pos)
        val globalIndex = allItems.indexOf(removed)
        if (globalIndex >= 0) allItems.removeAt(globalIndex)
        adapter.notifyItemRemoved(pos)
        repo.save(allItems)
        applyFilter()

        val coord = findViewById<View>(R.id.coordinator)
        Snackbar.make(coord, "«${removed.name}» удалён", Snackbar.LENGTH_LONG)
            .setAction("Вернуть") {
                if (globalIndex >= 0 && globalIndex <= allItems.size) {
                    allItems.add(globalIndex, removed)
                } else {
                    allItems.add(removed)
                }
                repo.save(allItems)
                applyFilter()
            }
            .show()
    }

    // ---------- РЕДАКТИРОВАНИЕ ----------
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
        val catIndex = cats.indexOf(item.category)
        spinnerCat.setSelection(if (catIndex >= 0) catIndex else 0)

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
        val cats = allCategoryNames()
        cats.forEach { cat ->
            val chip = Chip(this).apply {
                text = cat
                isCheckable = true
                isClickable = true
                textSize = 11f
                chipMinHeight = 20f * resources.displayMetrics.density
                chipStartPadding = 4f
                chipEndPadding = 4f
            }
            val accent = categoryColor(cat)
            val bg = ColorUtils.setAlphaComponent(accent, 80)
            chip.chipBackgroundColor = ColorStateList.valueOf(bg)
            group.addView(chip)
        }
        val plusChip = Chip(this).apply {
            text = "+ тег"
            isCheckable = false
            textSize = 11f
            chipMinHeight = 20f * resources.displayMetrics.density
            chipStartPadding = 4f
            chipEndPadding = 4f
            setOnClickListener {
                showAddCategoryDialog { newCat ->
                    setupChipsInto(group)
                    selectChipIn(group, newCat)
                }
            }
        }
        group.addView(plusChip)

        // Больше НЕ выбираем чип автоматически — по умолчанию ничего не выбрано
    }

    private fun selectChipIn(group: ChipGroup, cat: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.text.toString() == cat) {
                chip.isChecked = true
                return
            }
        }
    }

    private fun selectedChipIn(group: ChipGroup): String {
        val id = group.checkedChipId
        if (id == View.NO_ID) return "Разное"
        val chip = group.findViewById<Chip>(id)
        return chip?.text?.toString() ?: "Разное"
    }

    private fun categoryColor(cat: String): Int {
        return when (cat) {
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

    private fun showAddCategoryDialog(onAdded: (String) -> Unit) {
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
                    setupTabs()
                    onAdded(name)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
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
        
