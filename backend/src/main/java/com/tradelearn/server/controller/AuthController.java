package com.tradelearn.server.controller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.Portfolio;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.PortfolioRepository;
import com.tradelearn.server.repository.UserRepository;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Autowired
    public AuthController(UserRepository userRepository,
                          PortfolioRepository portfolioRepository,
                          BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {

        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            return ResponseEntity
                    .status(409)
                    .body(Map.of("message", "Email already exists"));
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);

        Portfolio portfolio = new Portfolio(savedUser, 100000.0);
        portfolioRepository.save(portfolio);

        return ResponseEntity.ok(
                Map.of("message", "User registered", "id", savedUser.getId())
        );
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody User loginDetails) {

        Optional<User> optionalUser =
                userRepository.findByEmail(loginDetails.getEmail());

        if (optionalUser.isEmpty()) {
            return ResponseEntity
                    .status(401)
                    .body(Map.of("message", "User not found"));
        }

        User user = optionalUser.get();

        if (!passwordEncoder.matches(
                loginDetails.getPassword(),
                user.getPassword())) {

            return ResponseEntity
                    .status(401)
                    .body(Map.of("message", "Invalid credentials"));
        }

        portfolioRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            Portfolio p = new Portfolio(user, 100000.0);
            return portfolioRepository.save(p);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());

        return ResponseEntity.ok(response);
    }
}
