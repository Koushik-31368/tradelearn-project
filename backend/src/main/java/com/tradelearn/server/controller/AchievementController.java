package com.tradelearn.server.controller;

import com.tradelearn.server.dto.AchievementDTO;
import com.tradelearn.server.model.User;
import com.tradelearn.server.model.UserAchievement;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.service.AchievementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;
    private final UserRepository userRepository;

    public AchievementController(AchievementService achievementService, UserRepository userRepository) {
        this.achievementService = achievementService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    @GetMapping("/user")
    public ResponseEntity<List<AchievementDTO>> getUserAchievements() {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<UserAchievement> userAchievements = achievementService.getUserAchievements(user.getId());
        List<AchievementDTO> dtos = userAchievements.stream().map(ua -> 
            new AchievementDTO(
                ua.getAchievement().getName(),
                ua.getAchievement().getDescription(),
                ua.getAchievement().getIcon(),
                ua.getEarnedAt().toString()
            )
        ).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
