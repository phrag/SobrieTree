package com.brewlog.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BeerEntryAdapter(
    private val onEditClick: (BeerEntry) -> Unit,
    private val onDeleteClick: (BeerEntry) -> Unit
) : ListAdapter<BeerEntry, BeerEntryAdapter.BeerEntryViewHolder>(BeerEntryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BeerEntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_beer_entry,
            parent,
            false
        )
        return BeerEntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: BeerEntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class BeerEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val beerName: android.widget.TextView = itemView.findViewById(R.id.beer_name)
        private val beerVolume: android.widget.TextView = itemView.findViewById(R.id.beer_volume)
        private val beerAlcohol: android.widget.TextView = itemView.findViewById(R.id.beer_alcohol)
        private val beerDate: android.widget.TextView = itemView.findViewById(R.id.beer_date)
        private val btnEdit: android.widget.ImageButton = itemView.findViewById(R.id.btn_edit)
        private val btnDelete: android.widget.ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(entry: BeerEntry) {
            val beerName: android.widget.TextView = itemView.findViewById(R.id.beer_name)
            beerName.text = entry.name
            beerVolume.text = "${entry.volumeMl.toInt()}ml"
            beerAlcohol.text = "${entry.alcoholPercentage}%"
            
            // Format the date
            val entryDate = LocalDate.parse(entry.date)
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            
            beerDate.text = when {
                entryDate == today -> "Today"
                entryDate == yesterday -> "Yesterday"
                else -> entryDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            }

            btnEdit.setOnClickListener { onEditClick(entry) }
            btnDelete.setOnClickListener { onDeleteClick(entry) }
        }
    }

    private class BeerEntryDiffCallback : DiffUtil.ItemCallback<BeerEntry>() {
        override fun areItemsTheSame(oldItem: BeerEntry, newItem: BeerEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: BeerEntry, newItem: BeerEntry): Boolean {
            return oldItem == newItem
        }
    }
} 