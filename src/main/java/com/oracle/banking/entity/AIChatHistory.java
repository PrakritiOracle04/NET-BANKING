package com.oracle.banking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(name = "AI_CHAT_HISTORY")
public class AIChatHistory {

    @Id
    @Column(name = "CHAT_ID")
    private String chatId;

    @ManyToOne
    @JoinColumn(name = "USER_ID")
    private AppUser user;

    @Column(name = "QUESTION", length = 1000)
    private String question;

    @Column(name = "ANSWER", length = 4000)
    private String answer;

    @Column(name = "ASKED_AT")
    private LocalDateTime askedAt;

    public AIChatHistory() {
    }

    public AIChatHistory(String chatId,
                         AppUser user,
                         String question,
                         String answer,
                         LocalDateTime askedAt) {
        this.chatId = chatId;
        this.user = user;
        this.question = question;
        this.answer = answer;
        this.askedAt = askedAt;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public LocalDateTime getAskedAt() {
        return askedAt;
    }

    public void setAskedAt(LocalDateTime askedAt) {
        this.askedAt = askedAt;
    }

    @Override
    public String toString() {
        return "AIChatHistory{" +
                "chatId='" + chatId + '\'' +
                ", question='" + question + '\'' +
                ", askedAt=" + askedAt +
                '}';
    }
}