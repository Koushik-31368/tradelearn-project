package com.tradelearn.server.controller;

import com.tradelearn.server.dto.ChallengeDTO;
import com.tradelearn.server.dto.QuestDTO;
import com.tradelearn.server.service.QuestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.tradelearn.server.security.CustomUserDetailsService;

import java.util.List;

@RestController
@RequestMapping("/api/quests")
public class QuestController {

    private final QuestService questService;

    public QuestController(QuestService questService) {
        this.questService = questService;
    }

    @GetMapping("/daily")
    public ResponseEntity<List<QuestDTO>> getDailyQuests(@AuthenticationPrincipal CustomUserDetailsService.UserPrincipal userPrincipal) {
        if (userPrincipal == null) return ResponseEntity.status(401).build();
        List<QuestDTO> quests = questService.getTodayQuests(userPrincipal.getId());
        return ResponseEntity.ok(quests);
    }

    @GetMapping("/weekly")
    public ResponseEntity<List<ChallengeDTO>> getWeeklyChallenges(@AuthenticationPrincipal CustomUserDetailsService.UserPrincipal userPrincipal) {
        if (userPrincipal == null) return ResponseEntity.status(401).build();
        List<ChallengeDTO> challenges = questService.getThisWeekChallenges(userPrincipal.getId());
        return ResponseEntity.ok(challenges);
    }
}
