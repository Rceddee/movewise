package com.example.movewise.model

data class ChatBotPersona(
    var name: String = "MoveWise Assistant",
    var tone: String = "Professional", // Professional, Friendly, Motivating
    var focus: String = "General Fitness" // Weight Loss, Muscle Gain, Endurance
)
