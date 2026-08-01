package com.aisuite.model;

import com.fasterxml.jackson.databind.JsonNode;

public class HistoryEntry {
    private int id;
    private int userId;
    private String page;
    private String question;
    private String answer;
    private JsonNode extra; // raw weather/rate data for card replay
    private String time; // maps to created_at

    public HistoryEntry() {
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getPage() {
        return page;
    }

    public String getQuestion() {
        return question;
    }

    public String getAnswer() {
        return answer;
    }

    public JsonNode getExtra() {
        return extra;
    }

    public String getTime() {
        return time;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public void setQuestion(String q) {
        this.question = q;
    }

    public void setAnswer(String a) {
        this.answer = a;
    }

    public void setExtra(JsonNode extra) {
        this.extra = extra;
    }

    public void setTime(String time) {
        this.time = time;
    }
}
