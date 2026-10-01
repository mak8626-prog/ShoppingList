package com.example.shoppinglist

import android.content.res.ColorStateList
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch

internal fun MainActivity.allCategoryNames(): List<String> {
    val base = resources.getStringArray(R.array.categories).toList()
    val custom = repo.loadCustomCategories()
    return (base + custom).distinct()
}

internal fun MainActivity.setupSettingsScreen() {
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

    swDark.setOnCheckedChangeListener { v, x ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repo.setBool(ShoppingRepository.SET_DARK_THEME, x)
        recreate()
    }
    swTags.setOnCheckedChangeListener { v, x ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repo.setBool(ShoppingRepository.SET_SHOW_TAGS, x)
        applyFilter()
    }
    swCompact.setOnCheckedChangeListener { v, x ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repo.setBool(ShoppingRepository.SET_COMPACT, x)
        recreate()
    }
    swShowDone.setOnCheckedChangeListener { v, x ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repo.setBool(ShoppingRepository.SET_SHOW_DONE, x)
        applyFilter()
    }
    swShowTotal.setOnCheckedChangeListener { v, x ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repo.setBool(ShoppingRepository.SET_SHOW_TOTAL, x)
        applyFilter()
    }
    swClearFields.setOnCheckedChangeListener { v, x ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        repo.setBool(ShoppingRepository.SET_CLEAR_FIELDS, x)
    }
}

internal fun MainActivity.setupChipsInto(group: ChipGroup) {
    group.removeAllViews()
    val cats = allCategoryNames()
    for (cat in cats) {
        val chip = Chip(this)
        chip.text = cat
        chip.isCheckable = true
        chip.isClickable = true
        chip.textSize = 11f
        chip.chipMinHeight = 20f * resources.displayMetrics.density
        chip.chipStartPadding = 4f
        chip.chipEndPadding = 4f
        val bg = ColorUtils.setAlphaComponent(CategoryColors.color(cat), 80)
        chip.chipBackgroundColor = ColorStateList.valueOf(bg)
        chip.setOnCheckedChangeListener { v, _ ->
            v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        group.addView(chip)
    }
    val plusChip = Chip(this)
    plusChip.text = "+ тег"
    plusChip.textSize = 11f
    plusChip.chipMinHeight = 20f * resources.displayMetrics.density
    plusChip.setOnClickListener { v ->
        v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        val input = EditText(this)
        input.hint = "Название категории"
        AlertDialog.Builder(this)
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
    group.addView(plusChip)
}

internal fun MainActivity.selectChipIn(group: ChipGroup, cat: String) {
    for (i in 0 until group.childCount) {
        val chip = group.getChildAt(i) as? Chip ?: continue
        if (chip.text.toString() == cat) {
            chip.isChecked = true
            return
        }
    }
}

internal fun MainActivity.selectedChipIn(group: ChipGroup): String {
    val id = group.checkedChipId
    if (id == View.NO_ID) return "Разное"
    val chip = group.findViewById<Chip>(id)
    return chip?.text?.toString() ?: "Разное"
}
