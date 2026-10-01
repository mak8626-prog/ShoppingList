package com.example.shoppinglist

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

class ShoppingAdapter(
    private val items: MutableList<ShoppingItem>,
    private val compact: Boolean,
    private val onChange: () -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (Int) -> Unit
) : RecyclerView.Adapter<ShoppingAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItem)
        val check: CheckBox = view.findViewById(R.id.checkBox)
        val text: TextView = view.findViewById(R.id.textName)
        val details: TextView = view.findViewById(R.id.textDetails)
        val delete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shopping, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.text.text = item.name

        val priceStr = if (item.price > 0)
            "${formatPrice(item.price)} ₽ × ${item.quantity} = ${formatPrice(item.total)} ₽"
        else
            "×${item.quantity}"

        holder.details.text = "${item.category} • $priceStr"

        val surface = MaterialColors.getColor(
            holder.card,
            com.google.android.material.R.attr.colorSurface
        )
        val accent = categoryColor(item.category)
        val blended = ColorUtils.blendARGB(surface, accent, 0.45f)
        holder.card.setCardBackgroundColor(blended)
        holder.card.strokeColor = accent
        holder.card.strokeWidth = (2 * holder.card.resources.displayMetrics.density).toInt()

        holder.check.isChecked = item.done
        strike(holder.text, item.done)
        strike(holder.details, item.done)

        holder.check.setOnCheckedChangeListener { _, isChecked ->
            item.done = isChecked
            strike(holder.text, isChecked)
            strike(holder.details, isChecked)
            onChange()
        }

        // Удаление — только уведомляем MainActivity. Она сама всё сделает.
        holder.delete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                onDelete(pos)
            }
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onEdit(pos)
        }
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

    private fun strike(tv: TextView, done: Boolean) {
        tv.paintFlags = if (done)
            tv.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else
            tv.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }

    private fun formatPrice(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString()
        else String.format("%.2f", v).trimEnd('0').trimEnd('.')
}
