package com.treetotal.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.format.DateTimeFormatter

class TimelineAdapter(
    private var items: List<GamificationManager.TimelineEntry>
) : RecyclerView.Adapter<TimelineAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.v_dot)
        val title: TextView = view.findViewById(R.id.tv_milestone_title)
        val desc: TextView = view.findViewById(R.id.tv_milestone_desc)
        val status: TextView = view.findViewById(R.id.tv_milestone_status)
    }

    fun update(newItems: List<GamificationManager.TimelineEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_timeline_milestone, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.title.text = item.milestone.title
        holder.desc.text = item.milestone.description

        val dateFormat = DateTimeFormatter.ofPattern("MMM d")
        when {
            item.reachedDate != null -> {
                holder.dot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.state_positive)
                holder.status.setTextColor(ContextCompat.getColor(context, R.color.state_positive))
                holder.status.text = context.getString(R.string.milestone_reached_on, item.reachedDate.format(dateFormat))
                holder.itemView.alpha = 1f
            }
            item.isNext -> {
                holder.dot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.brand_primary)
                holder.status.setTextColor(ContextCompat.getColor(context, R.color.brand_primary))
                holder.status.text = context.getString(R.string.milestone_next_up)
                holder.itemView.alpha = 1f
            }
            else -> {
                holder.dot.backgroundTintList = ContextCompat.getColorStateList(context, R.color.badge_locked)
                holder.status.setTextColor(ContextCompat.getColor(context, R.color.text_hint))
                holder.status.text = context.getString(R.string.milestone_af_days, item.milestone.afDays)
                holder.itemView.alpha = 0.55f
            }
        }
    }
}
