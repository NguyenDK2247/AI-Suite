package com.aisuite.model;

public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String timezone;
    private String createdAt;

    public User() {
    }

    public User(int id, String username, String passwordHash,
            String timezone, String createdAt) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.timezone = timezone;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String hash) {
        this.passwordHash = hash;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
