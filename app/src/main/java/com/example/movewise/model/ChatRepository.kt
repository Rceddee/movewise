package com.example.movewise.model

class ChatRepository {
    private val repo by lazy { DataRepository.getInstance() }

    fun updatePersona(newName: String, newTone: String, newFocus: String) {
        val persona = ChatBotPersona(newName, newTone, newFocus)
        repo.savePersona(persona)
    }

    fun getPersona(): ChatBotPersona = repo.getPersona()

    fun addMessage(message: ChatMessage) {
        val messages = repo.getMessages().toMutableList()
        messages.add(message)
        repo.saveMessages(messages)
    }

    fun getMessages(): List<ChatMessage> = repo.getMessages()

    suspend fun getAIResponse(userMessage: String): String {
        val persona = repo.getPersona()
        val history = repo.getMessages()
        val geminiClient = GeminiApiClient()
        return geminiClient.getChatResponse(userMessage, persona, history)
    }
}
