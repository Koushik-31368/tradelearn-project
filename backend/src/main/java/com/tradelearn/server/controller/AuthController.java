package com.tradelearn.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository,
                          BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {

        if (userRepository.existsByEmail(user.getEmail())) {
            return ResponseEntity.badRequest().body("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginUser) {

        return userRepository.findByEmail(loginUser.getEmail())
                .map(user -> {
                    if (!passwordEncoder.matches(
                            loginUser.getPassword(),
                            user.getPassword())) {
                        return ResponseEntity.badRequest().body("Invalid password");
                    }
                    return ResponseEntity.ok(user);
                })
                .orElse(ResponseEntity.badRequest().body("Invalid email"));
    }

    // Temporary endpoint to delete old accounts with plain-text passwords
    @DeleteMapping("/delete-account/{email}")
    public ResponseEntity<?> deleteAccount(@PathVariable String email) {
        return userRepository.findByEmail(email)
                .map(user -> {
                    userRepository.delete(user);
                    return ResponseEntity.ok("Account deleted: " + email);
                })
                .orElse(ResponseEntity.notFound().build());
    }
}