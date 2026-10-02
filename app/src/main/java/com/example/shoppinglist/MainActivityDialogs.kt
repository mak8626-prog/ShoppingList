package com.example.shoppinglist

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.Filter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import java.util.ArrayList

internal fun MainActivity.showAddSheet() {
    val sheet = BottomSheetDialog(this)
    val view = layoutInflater.inflate(R.layout.bottom_sheet_add, null)
    sheet.setContentView(view)

    val bsChips = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.bsChipGroup)
    val bsEditItem = view.findViewById<AutoCompleteTextView>(R.id.bsEditItem)
    val bsEditPrice = view.findViewById<EditText>(R.id.bsEditPrice)
    val bsEditQty = view.findViewById<EditText>(R.id.bsEditQuantity)
    val bsBtnAdd = view.findViewById<Button>(R.id.bsBtnAdd)

    setupChipsInto(bsChips)

    val all = (repo.loadHistory().toList() + PopularProducts.names).distinct()

    bsEditItem.threshold = 1
    bsEditItem.setAdapter(object : ArrayAdapter<String>(
        this,
        android.R.layout.simple_dropdown_item_1line,
        ArrayList<String>()
    ) {
        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    val prefix = constraint?.toString()?.lowercase()?.trim() ?: ""
                    val results = FilterResults()
                    if (prefix.isEmpty()) {
                        results.values = emptyList<String>()
                        results.count = 0
                        return results
                    }
                    val filtered = all.filter {
                        it.lowercase().startsWith(prefix)
                    }.take(6)
                    results.values = filtered
                    results.count = filtered.size
                    return results
                }

                @Suppress("UNCHECKED_CAST")
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    clear()
                    val values = results?.values as? List<String> ?: emptyList()
                    addAll(values)
                    notifyDataSetChanged()
                }
            }
        }
    })

    bsEditItem.setOnItemClickListener { _, _, _, _ ->
        val name = bsEditItem.text.toString().trim()
        if (bsEditPrice.text.isNullOrEmpty()) {
            val last = repo.getLastPrice(name)
            if (last != null) {
                val txt = if (last % 1.0 == 0.0) last.toInt().toString()
                          else String.format("%.2f", last).trimEnd('0').trimEnd('.')
                bsEditPrice.setText(txt)
            }
        }
        val autoCat = repo.getCategoryForProduct(name) ?: PopularProducts.getCategory(name)
        if (autoCat != null) selectChipIn(bsChips, autoCat)
    }

    bsBtnAdd.setOnClickListener { v ->
        if (!bsBtnAdd.isEnabled) return@setOnClickListener
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
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
            Toast.makeText(this, "$name уже в списке: ${existing.quantity} шт", Toast.LENGTH_SHORT).show()
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

internal fun MainActivity.showEditDialog(pos: Int) {
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
        .setTitle("Редактировать")
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

internal fun MainActivity.addDishIngredients(dish: DishTemplates.Dish) {
    var added = 0
    for (pair in dish.ingredients) {
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
    val msg = if (added > 0) "${dish.emoji} ${dish.name}: +$added" else "Уже в списке"
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

internal fun MainActivity.removeWithUndo(pos: Int) {
    if (pos < 0 || pos >= displayedItems.size) return
    val removed = displayedItems.removeAt(pos)
    val globalIndex = allItems.indexOf(removed)
    if (globalIndex >= 0) allItems.removeAt(globalIndex)
    adapter.notifyItemRemoved(pos)
    repo.save(allItems)
    applyFilter()
    val name = removed.name
    Snackbar.make(findViewById(R.id.coordinator), "$name удалён", Snackbar.LENGTH_LONG)
        .setAction("Вернуть") {
            if (globalIndex in 0..allItems.size) allItems.add(globalIndex, removed)
            else allItems.add(removed)
            repo.save(allItems)
            applyFilter()
        }
        .show()
}
