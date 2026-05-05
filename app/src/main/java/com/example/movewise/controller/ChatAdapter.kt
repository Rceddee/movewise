package com.example.movewise.controller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.R
import com.example.movewise.model.ChatMessage
import com.example.movewise.util.MarkdownRenderer

class ChatAdapter(private val messages: MutableList<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardUser: View = view.findViewById(R.id.card_user)
        val cardAi: View = view.findViewById(R.id.card_ai)
        val tvUserMessage: TextView = view.findViewById(R.id.tv_user_message)
        val tvAiMessage: TextView = view.findViewById(R.id.tv_ai_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        
        if (message.fromUser) {
            holder.cardUser.visibility = View.VISIBLE
            holder.cardAi.visibility = View.GONE
            holder.tvUserMessage.text = message.content
        } else {
            holder.cardUser.visibility = View.GONE
            holder.cardAi.visibility = View.VISIBLE
            // AI messages: render Markdown to styled Spannable
            holder.tvAiMessage.text = MarkdownRenderer.render(message.content)
        }
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun removeMessage(position: Int) {
        if (position >= 0 && position < messages.size) {
            messages.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }
}
