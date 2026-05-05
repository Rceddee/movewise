package com.example.movewise.controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.Badge

class BadgeAdapter(private val badges: List<Badge>) :
    RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    class BadgeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.iv_badge_icon)
        val tvTitle: TextView = view.findViewById(R.id.tv_badge_title)
        val tvDesc: TextView = view.findViewById(R.id.tv_badge_desc)
        val ivLock: ImageView = view.findViewById(R.id.iv_lock_status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = badges[position]
        holder.tvTitle.text = badge.title
        holder.tvDesc.text = badge.description
        
        if (badge.isUnlocked) {
            holder.ivIcon.alpha = 1.0f
            holder.ivLock.visibility = View.GONE
        } else {
            holder.ivIcon.alpha = 0.3f
            holder.ivLock.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = badges.size
}
