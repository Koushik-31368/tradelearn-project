package com.tradelearn.server.learning.controller;

import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.learning.service.LearningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;
    private final UserRepository userRepository;

    public LearningController(LearningService learningService, UserRepository userRepository) {
        this.learningService = learningService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    @GetMapping("/progress")
    public ResponseEntity<List<String>> getProgress() {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(learningService.getCompletedLessons(user.getId()));
    }

    @PostMapping("/complete/{lessonId}")
    public ResponseEntity<?> completeLesson(@PathVariable String lessonId, @RequestBody Map<String, Boolean> body) {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        boolean isQuiz = body.getOrDefault("isQuiz", false);
        learningService.completeLesson(user, lessonId, isQuiz);

        return ResponseEntity.ok().build();
    }
}
