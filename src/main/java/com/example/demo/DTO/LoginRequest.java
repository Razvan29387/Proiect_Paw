package com.example.demo.DTO;

// Un simplu DTO pentru a mapa cererea de login
public class LoginRequest {
    private String userName;
    private String password;

    // Getters și Setters
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
