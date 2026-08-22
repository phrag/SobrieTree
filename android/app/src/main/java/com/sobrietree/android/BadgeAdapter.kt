package com.sobrietree.android

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class BadgeAdapter(
    private var items: List<GamificationManager.BadgeState>
) : RecyclerView.Adapter<BadgeAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_badge)
        val name: TextView = view.findViewById(R.id.tv_badge_name)
        val hint: TextView = view.findViewById(R.id.tv_badge_hint)
    }

    fun update(newItems: List<GamificationManager.BadgeState>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_badge, parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.name.text = item.badge.title
        val earned = item.earnedDate != null
        if (earned) {
            holder.icon.imageTintList = ContextCompat.getColorStateList(context, R.color.badge_earned)
            holder.itemView.alpha = 1f
            holder.hint.text = try {
                LocalDate.parse(item.earnedDate).format(DateTimeFormatter.ofPattern("MMM d"))
            } catch (_: Exception) {
                context.getString(R.string.badge_earned_description)
            }
        } else {
            // Locked badges show a visible goal, not a gray void
            holder.icon.imageTintList = ContextCompat.getColorStateList(context, R.color.badge_locked)
            holder.itemView.alpha = 0.7f
            holder.hint.text = item.progressHint
        }
    }
}
