package com.tradelearn.server.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.User;
import com.tradelearn.server.service.UserService;
import org.springframework.security.core.userdetails.UserDetails;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/daily-checkin")
    public ResponseEntity<?> dailyCheckin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        UserDetails principal = (UserDetails) authentication.getPrincipal();
        try {
            User user = userService.findByUsername(principal.getUsername());
            User updatedUser = userService.performDailyCheckin(user.getId());
            return ResponseEntity.ok(Map.of(
                "xp", updatedUser.getXp(),
                "loginStreak", updatedUser.getLoginStreak(),
                "longestLoginStreak", updatedUser.getLongestLoginStreak(),
                "message", "Daily check-in successful! +10 XP"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
