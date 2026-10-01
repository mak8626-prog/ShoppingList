package com.example.shoppinglist

import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout

class MainActivity : AppCompatActivity() {

    internal lateinit var repo: ShoppingRepository
    internal lateinit var adapter: ShoppingAdapter
    internal lateinit var screenList: View
    internal lateinit var screenRecipes: View
    internal lateinit var screenSettings: View
    internal lateinit var fabAdd: FloatingActionButton
    internal lateinit var btnClearDone: ImageButton
    internal lateinit var tabLayout: TabLayout
    internal lateinit var recycler: RecyclerView
    internal lateinit var emptyState: LinearLayout
    internal lateinit var textTotal: TextView
    internal val allItems = mutableListOf<ShoppingItem>()
    internal val displayedItems = mutableListOf<ShoppingItem>()
    internal var currentCategory: String = "Все"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ShoppingRepository(this)

        if (repo.getBool(ShoppingRepository.SET_DARK_THEME, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

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
                if (hideDone) {
                    recycler.post { applyFilter() }
                } else {
                    updateTotal()
                }
            },
            onDelete = { pos -> removeWithUndo(pos) },
            onEdit = { pos -> showEditDialog(pos) }
        )
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                t: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                val pos = viewHolder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) removeWithUndo(pos)
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(recycler)

        btnClearDone.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val removed = allItems.filter { it.done }
            if (removed.isEmpty()) {
                Toast.makeText(this, "Нет купленных", Toast.LENGTH_SHORT).show()
            } else {
                allItems.removeAll(removed)
                repo.save(allItems)
                applyFilter()
            }
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

        fabAdd.setOnClickListener { v ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            showAddSheet()
        }

        showScreen(0)
        applyFilter()
    }

    internal fun showScreen(index: Int) {
        val screens = listOf(screenList, screenRecipes, screenSettings)
        for (i in screens.indices) {
            val s = screens[i]
            if (i == index) {
                s.visibility = View.VISIBLE
                s.alpha = 0f
                s.animate().alpha(1f).setDuration(220).start()
            } else {
                s.visibility = View.GONE
                s.alpha = 1f
            }
        }
        val showFab = index == 0
        if (showFab) {
            fabAdd.visibility = View.VISIBLE
            fabAdd.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(220).start()
            btnClearDone.visibility = View.VISIBLE
        } else {
            fabAdd.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(180).start()
            btnClearDone.visibility = View.GONE
        }
    }

    internal fun setupTabs() {
        tabLayout.removeAllTabs()
        val cats = listOf("Все") + allCategoryNames()
        for (c in cats) tabLayout.addTab(tabLayout.newTab().setText(c))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.view?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                currentCategory = tab?.text?.toString() ?: "Все"
                applyFilter()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    internal fun applyFilter() {
        val showTabs = repo.getBool(ShoppingRepository.SET_SHOW_TAGS, true)
        tabLayout.visibility = if (showTabs) View.VISIBLE else View.GONE
        if (!showTabs) currentCategory = "Все"

        displayedItems.clear()
        val showDone = repo.getBool(ShoppingRepository.SET_SHOW_DONE, true)
        val byCategory = if (currentCategory == "Все") allItems
                         else allItems.filter { it.category == currentCategory }
        if (showDone) displayedItems.addAll(byCategory)
        else displayedItems.addAll(byCategory.filter { !it.done })

        adapter.notifyDataSetChanged()
        emptyState.visibility = if (displayedItems.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (displayedItems.isEmpty()) View.GONE else View.VISIBLE

        val showTotal = repo.getBool(ShoppingRepository.SET_SHOW_TOTAL, true)
        textTotal.visibility = if (showTotal) View.VISIBLE else View.GONE
        updateTotal()
    }

    internal fun updateTotal() {
    val showTotal = repo.getBool(ShoppingRepository.SET_SHOW_TOTAL, true)
    if (!showTotal || allItems.isEmpty()) {
        textTotal.visibility = View.GONE
        return
    }
    textTotal.visibility = View.VISIBLE

    val total = allItems.filter { !it.done }.sumOf { it.total }
    val formatted = if (total % 1.0 == 0.0) total.toInt().toString()
                    else String.format("%.2f", total)

    val totalCount = allItems.size
    val doneCount = allItems.count { it.done }

    textTotal.text = if (doneCount > 0) {
        "Итого: $formatted ₽ · $doneCount из $totalCount"
    } else {
        "Итого: $formatted ₽"
    }
    }
