package com.example.movewise.model

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    @SerializedName("content") val content: String,
    @SerializedName("fromUser") val fromUser: Boolean,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
