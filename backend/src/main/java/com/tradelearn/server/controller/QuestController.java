package com.tradelearn.server.controller;

import com.tradelearn.server.dto.ChallengeDTO;
import com.tradelearn.server.dto.QuestDTO;
import com.tradelearn.server.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.tradelearn.server.security.CustomUserDetailsService;

import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.List;

@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestService questService;
    private final UserRepository userRepository;

    public QuestController(QuestService questService, UserRepository userRepository) {
        this.questService = questService;
        this.userRepository = userRepository;
    }

    @GetMapping("/daily")
    public ResponseEntity<List<QuestDTO>> getDailyQuests(@AuthenticationPrincipal UserDetails userPrincipal) {
        if (userPrincipal == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(userPrincipal.getUsername()).orElseThrow();
        List<QuestDTO> quests = questService.getTodayQuests(user.getId());
        return ResponseEntity.ok(quests);
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<ChallengeDTO>> getWeeklyChallenges(@AuthenticationPrincipal UserDetails userPrincipal) {
        if (userPrincipal == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByEmail(userPrincipal.getUsername()).orElseThrow();
        List<ChallengeDTO> challenges = questService.getThisWeekChallenges(user.getId());
        return ResponseEntity.ok(challenges);
    }
}
