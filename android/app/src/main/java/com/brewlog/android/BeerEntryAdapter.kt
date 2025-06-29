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

    inner class BeerEntryViewHolder(
        itemView: View
    ) : RecyclerView.ViewHolder(itemView) {

        fun bind(entry: BeerEntry) {
            itemView.findViewById<android.widget.TextView>(R.id.beer_name).text = entry.name
            itemView.findViewById<android.widget.TextView>(R.id.beer_details).text = "${entry.alcoholPercentage}% • ${entry.volumeMl.toInt()}ml"
            itemView.findViewById<android.widget.TextView>(R.id.beer_date).text = formatDate(entry.date)
            
            val notesView = itemView.findViewById<android.widget.TextView>(R.id.beer_notes)
            if (entry.notes.isNotEmpty()) {
                notesView.text = entry.notes
                notesView.visibility = View.VISIBLE
            } else {
                notesView.visibility = View.GONE
            }

            itemView.findViewById<View>(R.id.btn_edit).setOnClickListener { onEditClick(entry) }
            itemView.findViewById<View>(R.id.btn_delete).setOnClickListener { onDeleteClick(entry) }
        }

        private fun formatDate(dateString: String): String {
            return try {
                val date = LocalDate.parse(dateString, DateTimeFormatter.ISO_LOCAL_DATE)
                val today = LocalDate.now()
                
                when {
                    date == today -> "Today"
                    date == today.minusDays(1) -> "Yesterday"
                    date.isAfter(today.minusDays(7)) -> date.format(DateTimeFormatter.ofPattern("EEEE"))
                    else -> date.format(DateTimeFormatter.ofPattern("MMM dd"))
                }
            } catch (e: Exception) {
                dateString
            }
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