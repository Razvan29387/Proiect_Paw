package com.example.demo.DTO;

public class UserDTO {
    private Long id;
    private String userName;
    private String password;
    private String role;
    private String email;
    private String name;
    private String surname;

    // Constructors
    public UserDTO() {
    }

    public UserDTO(Long id, String userName, String password, String role, String email, String name, String surname) {
        this.id = id;
        this.userName = userName;
        this.password = password;
        this.role = role;
        this.email = email;
        this.name = name;
        this.surname = surname;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSurname() {
        return surname;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

}
