package com.treetotal.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.treetotal.android.DrinkPreset

class DrinkManagerAdapter(
    private var drinks: List<DrinkPreset>,
    private val onSelect: (DrinkPreset) -> Unit,
    private val onEdit: (DrinkPreset) -> Unit,
    private val onDelete: (DrinkPreset) -> Unit,
    private val onFavorite: (DrinkPreset) -> Unit
) : RecyclerView.Adapter<DrinkManagerAdapter.DrinkViewHolder>() {

    inner class DrinkViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_drink_name)
        val details: TextView = view.findViewById(R.id.tv_drink_details)
        val favorite: ImageView = view.findViewById(R.id.iv_favorite)
        val edit: ImageView = view.findViewById(R.id.iv_edit)
        val delete: ImageView = view.findViewById(R.id.iv_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrinkViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_drink, parent, false)
        return DrinkViewHolder(view)
    }

    override fun onBindViewHolder(holder: DrinkViewHolder, position: Int) {
        val drink = drinks[position]
        holder.name.text = drink.name
        val costPart = if (drink.cost > 0) " • ${Money.format(drink.cost.toDouble(), 2)}" else ""
        holder.details.text = "${drink.volume}ml • ${drink.strength}% ABV$costPart"
        holder.favorite.setImageResource(R.drawable.ic_star)
        holder.favorite.imageAlpha = if (drink.favorite) 255 else 70
        holder.favorite.setOnClickListener { onFavorite(drink) }
        holder.edit.setOnClickListener { onEdit(drink) }
        holder.delete.setOnClickListener { onDelete(drink) }
        holder.itemView.setOnClickListener { onSelect(drink) }
    }

    override fun getItemCount(): Int = drinks.size

    fun updateDrinks(newDrinks: List<DrinkPreset>) {
        drinks = newDrinks
        notifyDataSetChanged()
    }
} 