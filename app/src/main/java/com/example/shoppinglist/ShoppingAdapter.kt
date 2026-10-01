package com.example.shoppinglist

import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class ShoppingAdapter(
    private val items: MutableList<ShoppingItem>,
    private val compact: Boolean,
    private val onChange: () -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (Int) -> Unit
) : RecyclerView.Adapter<ShoppingAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardItem)
        val stripe: View = view.findViewById(R.id.colorStripe)
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
        holder.stripe.setBackgroundColor(CategoryColors.color(item.category))

        val anim = AnimationUtils.loadAnimation(holder.itemView.context, android.R.anim.fade_in)
        anim.duration = 220
        holder.itemView.startAnimation(anim)

        holder.check.setOnCheckedChangeListener(null)
        holder.check.isChecked = item.done
        strike(holder.text, item.done)
        strike(holder.details, item.done)

        holder.check.setOnCheckedChangeListener { _, isChecked ->
            holder.itemView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            item.done = isChecked
            strike(holder.text, isChecked)
            strike(holder.details, isChecked)
            holder.itemView.post { onChange() }
        }

        holder.delete.setOnClickListener {
            holder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onDelete(pos)
        }

        holder.itemView.setOnClickListener {
            holder.itemView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onEdit(pos)
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
