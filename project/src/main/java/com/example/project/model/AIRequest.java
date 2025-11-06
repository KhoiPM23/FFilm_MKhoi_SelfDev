package com.example.project.model;

public class AIRequest {
    private String message;
    private String conversationId;

    // Constructors
    public AIRequest() {}

    public AIRequest(String message, String conversationId) {
        this.message = message;
        this.conversationId = conversationId;
    }

    // Getters and Setters
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @Override
    public String toString() {
        return "AIRequest{message='" + message + "', conversationId='" + conversationId + "'}";
    }
}