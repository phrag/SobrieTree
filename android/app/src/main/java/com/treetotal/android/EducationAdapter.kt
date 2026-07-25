package com.treetotal.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.treetotal.android.engine.EducationCard

class EducationAdapter(
    private var items: List<EducationCard>,
    private val onClick: (EducationCard) -> Unit
) : RecyclerView.Adapter<EducationAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_edu_title)
        val summary: TextView = view.findViewById(R.id.tv_edu_summary)
    }

    fun update(newItems: List<EducationCard>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_education_card, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = items[position]
        holder.title.text = card.title
        holder.summary.text = card.summary
        holder.itemView.setOnClickListener { onClick(card) }
    }
}
