package com.tradelearn.server.learning.controller;

import com.tradelearn.server.user.model.User;
import com.tradelearn.server.learning.service.LearningService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    /**
     * GET /api/learning/progress
     * Returns the list of lesson IDs completed by the authenticated user.
     */
    @GetMapping("/progress")
    public ResponseEntity<List<String>> getProgress(@AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(learningService.getCompletedLessons(user.getId()));
    }

    /**
     * POST /api/learning/complete/{lessonId}
     * Marks a lesson (or quiz) as complete for the authenticated user.
     */
    @PostMapping("/complete/{lessonId}")
    public ResponseEntity<?> completeLesson(
            @PathVariable String lessonId,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal User user) {
        if (user == null) return ResponseEntity.status(401).build();

        boolean isQuiz = body.getOrDefault("isQuiz", false);
        learningService.completeLesson(user, lessonId, isQuiz);

        return ResponseEntity.ok().build();
    }
}

