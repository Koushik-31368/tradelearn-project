package com.tradelearn.server.controller;

import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Optional;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = {"http://localhost:3000", "https://tradelearn-project.vercel.app"}, allowCredentials = "true")
public class AuthController {
    private final UserRepository userRepository;

    @Autowired
    public AuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        userRepository.save(user);
        return ResponseEntity.ok("User registered successfully!");
    }
@PostMapping("/login")
public ResponseEntity<?> loginUser(@RequestBody User loginDetails) {
    Optional<User> optionalUser = userRepository.findByEmail(loginDetails.getEmail());
    if (optionalUser.isEmpty()) {
        return ResponseEntity.status(401).body(Map.of("message", "Error: User not found"));
    }

    User user = optionalUser.get();
    if (!user.getPassword().equals(loginDetails.getPassword())) {
        return ResponseEntity.status(401).body(Map.of("message", "Error: Invalid credentials"));
    }

    // ✅ Return full user info as JSON (id, username, email)
    Map<String, Object> response = new HashMap<>();
    response.put("id", user.getId());
    response.put("username", user.getUsername());
    response.put("email", user.getEmail());

    return ResponseEntity.ok(response);
}


}
