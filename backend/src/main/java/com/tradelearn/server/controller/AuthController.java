package com.tradelearn.server.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.security.JwtUtil;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository,
                          BCryptPasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        try {
            if (user.getEmail() == null || user.getUsername() == null || user.getPassword() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email, username, and password are required"));
            }

            // Password strength validation
            String passwordError = validatePassword(user.getPassword());
            if (passwordError != null) {
                return ResponseEntity.badRequest().body(Map.of("error", passwordError));
            }

            if (userRepository.existsByEmail(user.getEmail())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
            }

            if (userRepository.existsByUsername(user.getUsername())) {
                return ResponseEntity.badRequest().body(Map.of("error", "Username already taken"));
            }

            user.setPassword(passwordEncoder.encode(user.getPassword()));
            User saved = userRepository.save(user);

            // Auto-login: return token on registration too
            String token = jwtUtil.generateToken(saved.getId(), saved.getEmail(), saved.getUsername());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("token", token);
            response.put("id", saved.getId());
            response.put("username", saved.getUsername());
            response.put("email", saved.getEmail());
            response.put("rating", saved.getRating());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> loginRequest) {
        String email = loginRequest.get("email");
        String password = loginRequest.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email and password are required"));
        }

        return userRepository.findByEmail(email)
                .map(user -> {
                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        return ResponseEntity.badRequest().body((Object) Map.of("error", "Invalid password"));
                    }

                    String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getUsername());

                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("token", token);
                    response.put("id", user.getId());
                    response.put("username", user.getUsername());
                    response.put("email", user.getEmail());
                    response.put("rating", user.getRating());

                    return ResponseEntity.ok((Object) response);
                })
                .orElse(ResponseEntity.badRequest().body((Object) Map.of("error", "Invalid email")));
    }

    /**
     * POST /api/auth/refresh
     * Exchange a still-valid JWT for a fresh one with a renewed expiration.
     * The client sends the current token in the Authorization header.
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing or invalid Authorization header"));
        }

        String oldToken = authHeader.substring(7);

        if (!jwtUtil.isValid(oldToken)) {
            return ResponseEntity.status(401).body(Map.of("error", "Token is expired or invalid"));
        }

        try {
            var claims = jwtUtil.parseToken(oldToken);
            String email = claims.getSubject();
            Long userId = claims.get("uid", Long.class);
            String username = claims.get("name", String.class);

            String newToken = jwtUtil.generateToken(userId, email, username);

            return ResponseEntity.ok(Map.of("token", newToken));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("error", "Token refresh failed"));
        }
    }

    /**
     * Validates password strength.
     * Requirements: min 8 chars, at least one uppercase, one lowercase, one digit.
     */
    private String validatePassword(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters long";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter";
        }
        if (!password.matches(".*\\d.*")) {
            return "Password must contain at least one digit";
        }
        return null;
    }
}