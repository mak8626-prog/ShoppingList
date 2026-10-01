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
        if (compact) {
            val d = parent.context.resources.displayMetrics.density
            v.setPadding(0, (3 * d).toInt(), 0, (3 * d).toInt())
        }
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

        // Цвет карточки по категории: смешиваем с фоном темы
        val surface = MaterialColors.getColor(
            holder.card,
            com.google.android.material.R.attr.colorSurface
        )
        val accent = categoryColor(item.category)
        val blended = ColorUtils.blendARGB(surface, accent, 0.22f)
        holder.card.setCardBackgroundColor(blended)

        holder.check.isChecked = item.done
        strike(holder.text, item.done)
        strike(holder.details, item.done)

        holder.check.setOnCheckedChangeListener { _, isChecked ->
            item.done = isChecked
            strike(holder.text, isChecked)
            strike(holder.details, isChecked)
            onChange()
        }

        holder.delete.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                items.removeAt(pos)
                notifyItemRemoved(pos)
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
            "Овощи и фрукты" -> 0xFF66BB6A.toInt()   // зелёный
            "Молочные продукты" -> 0xFF42A5F5.toInt() // синий
            "Мясо и рыба" -> 0xFFEF5350.toInt()       // красный
            "Хлеб и выпечка" -> 0xFFFFA726.toInt()    // оранжевый
            "Напитки" -> 0xFF29B6F6.toInt()           // голубой
            "Бакалея" -> 0xFFAB47BC.toInt()           // фиолетовый
            "Заморозка" -> 0xFF26C6DA.toInt()         // циан
            "Сладости" -> 0xFFEC407A.toInt()          // розовый
            "Бытовая химия" -> 0xFF9CCC65.toInt()     // лайм
            else -> 0xFF9E9E9E.toInt()                // серый
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
