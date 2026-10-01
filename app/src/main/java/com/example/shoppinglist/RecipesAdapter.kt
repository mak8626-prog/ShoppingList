package com.example.shoppinglist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RecipesAdapter(
    private val dishes: List<DishTemplates.Dish>,
    private val onClick: (DishTemplates.Dish) -> Unit
) : RecyclerView.Adapter<RecipesAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val emoji: TextView = view.findViewById(R.id.recipeEmoji)
        val name: TextView = view.findViewById(R.id.recipeName)
        val details: TextView = view.findViewById(R.id.recipeDetails)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = dishes.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val dish = dishes[position]
        holder.emoji.text = dish.emoji
        holder.name.text = dish.name
        holder.details.text = "${dish.ingredients.size} ингредиентов"
        holder.itemView.setOnClickListener { onClick(dish) }
    }
}
