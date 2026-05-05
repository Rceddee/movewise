package com.example.movewise.controller

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.movewise.MainActivity
import com.example.movewise.R
import com.example.movewise.model.ChatMessage
import com.example.movewise.model.ChatRepository
import kotlinx.coroutines.launch

class ChatBotFragment : Fragment(), com.example.movewise.model.DataRepository.DataListener {
    private val repository = ChatRepository()
    private val repo by lazy { com.example.movewise.model.DataRepository.getInstance() }
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chatbot, container, false)
        rvChat = view.findViewById(R.id.rv_chat)
        etMessage = view.findViewById(R.id.et_message)
        val btnSend: FloatingActionButton = view.findViewById(R.id.btn_send)
        val btnPersonalize: ImageButton = view.findViewById(R.id.btn_personalize)

        btnPersonalize.setOnClickListener {
            (activity as? MainActivity)?.openPersonalization()
        }

        adapter = ChatAdapter(repository.getMessages().toMutableList())
        rvChat.layoutManager = LinearLayoutManager(context)
        rvChat.adapter = adapter

        btnSend.setOnClickListener {
            val content = etMessage.text.toString()
            if (content.isNotEmpty()) {
                sendMessage(content)
                etMessage.text.clear()
            }
        }

        repo.addListener(this)
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        repo.removeListener(this)
    }

    override fun onDataChanged() {
        if (isAdded) {
            val messages = repository.getMessages()
            activity?.runOnUiThread {
                adapter.updateMessages(messages)
                rvChat.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    private fun sendMessage(content: String) {
        val userMsg = ChatMessage(content, true)
        repository.addMessage(userMsg)
        adapter.addMessage(userMsg)
        rvChat.scrollToPosition(adapter.itemCount - 1)

        val typingMsg = ChatMessage("...", false)
        adapter.addMessage(typingMsg)
        val typingPosition = adapter.itemCount - 1
        rvChat.scrollToPosition(typingPosition)
        
        lifecycleScope.launch {
            val aiResponse = repository.getAIResponse(content)
            
            // Remove typing indicator and add real message
            adapter.removeMessage(typingPosition)
            
            val aiMsg = ChatMessage(aiResponse, false)
            repository.addMessage(aiMsg)
            adapter.addMessage(aiMsg)
            rvChat.scrollToPosition(adapter.itemCount - 1)
        }
    }
}
