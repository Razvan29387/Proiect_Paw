package com.example.demo.Controllers;

import com.example.demo.DTO.LoginRequest;
import com.example.demo.DTO.UserDTO;
import com.example.demo.Mapping.UserMapper;
import com.example.demo.Service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @PostMapping("/signup")
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        com.example.demo.Entity.User user = UserMapper.toEntity(userDTO);
        // CORECȚIE CRITICĂ: Criptarea parolei la înregistrare
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setRole("USER");
        com.example.demo.Entity.User savedUser = userService.saveUser(user);
        return new ResponseEntity<>(UserMapper.toDTO(savedUser), HttpStatus.CREATED);
    }

    @PostMapping("/signin")
    public ResponseEntity<String> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUserName(), loginRequest.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return new ResponseEntity<>("User signed-in successfully!", HttpStatus.OK);
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers().stream()
                .map(UserMapper::toDTO)
                .collect(Collectors.toList());
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(UserMapper::toDTO)
                .map(userDTO -> new ResponseEntity<>(userDTO, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return userService.getUserById(id)
                .map(existingUser -> {
                    existingUser.setName(userDTO.getName());
                    existingUser.setSurname(userDTO.getSurname());
                    existingUser.setEmail(userDTO.getEmail());
                    existingUser.setUserName(userDTO.getUserName());
                    existingUser.setRole(userDTO.getRole());

                    if (userDTO.getPassword() != null && !userDTO.getPassword().isEmpty()) {
                        existingUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));
                    }

                    com.example.demo.Entity.User updatedUser = userService.saveUser(existingUser);
                    return new ResponseEntity<>(UserMapper.toDTO(updatedUser), HttpStatus.OK);
                })
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id){
        userService.deleteUser(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
