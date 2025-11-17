package com.example.demo.Mapping;

public class UserMapper {
    // Mapează un User la un UserDTO
    public static com.example.demo.DTO.UserDTO toDTO(com.example.demo.Entity.User user) {
        if (user == null) {
            return null;
        }
        return new com.example.demo.DTO.UserDTO(
                user.getId(),
                user.getUserName(),
                null, // Atenție: de obicei nu se returnează parola într-un DTO
                user.getRole(),
                user.getEmail(),
                user.getName(),
                user.getSurname()
        );
    }

    // Mapează un UserDTO la un User
    public static com.example.demo.Entity.User toEntity(com.example.demo.DTO.UserDTO userDTO) {
        if (userDTO == null) {
            return null;
        }
        com.example.demo.Entity.User user = new com.example.demo.Entity.User();
        user.setId(userDTO.getId());
        user.setUserName(userDTO.getUserName());
        user.setPassword(userDTO.getPassword());
        user.setRole(userDTO.getRole());
        user.setEmail(userDTO.getEmail());
        user.setName(userDTO.getName());
        user.setSurname(userDTO.getSurname());
        return user;
    }

}
