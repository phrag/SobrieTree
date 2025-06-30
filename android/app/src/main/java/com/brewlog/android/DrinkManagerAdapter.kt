package com.brewlog.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.brewlog.android.DrinkPreset

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
        holder.details.text = "${drink.type.displayName} • ${drink.volume}ml • ${drink.strength}% ABV"
        holder.favorite.setImageResource(
            if (drink.favorite) android.R.drawable.btn_star_big_on else android.R.drawable.btn_star_big_off
        )
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