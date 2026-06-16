package com.tradelearn.server.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.model.User;
import com.tradelearn.server.security.UserPrincipal;
import com.tradelearn.server.service.UserService;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/daily-checkin")
    public ResponseEntity<?> dailyCheckin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        try {
            User updatedUser = userService.performDailyCheckin(principal.getId());
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
