package com.tradelearn.server.learning.service;

import com.tradelearn.server.user.model.User;
import com.tradelearn.server.learning.model.UserLessonProgress;
import com.tradelearn.server.learning.repository.UserLessonProgressRepository;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.quests.service.AchievementService;
import com.tradelearn.server.quests.service.QuestService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LearningService {

    private final UserLessonProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final AchievementService achievementService;
    private final QuestService questService;

    public LearningService(UserLessonProgressRepository progressRepository,
                           UserRepository userRepository,
                           AchievementService achievementService,
                           @org.springframework.context.annotation.Lazy QuestService questService) {
        this.progressRepository = progressRepository;
        this.userRepository = userRepository;
        this.achievementService = achievementService;
        this.questService = questService;
    }

    public List<String> getCompletedLessons(Long userId) {
        return progressRepository.findByUserId(userId).stream()
                .map(UserLessonProgress::getLessonId)
                .collect(Collectors.toList());
    }

    public void completeLesson(User user, String lessonId, boolean isQuiz) {
        if (!progressRepository.existsByUserIdAndLessonId(user.getId(), lessonId)) {
            UserLessonProgress progress = new UserLessonProgress(user, lessonId);
            progressRepository.save(progress);

            int xpGained = isQuiz ? 10 : 20;
            user.setXp(user.getXp() + xpGained);
            userRepository.save(user);

            // Evaluate generic lessons count
            List<UserLessonProgress> allCompleted = progressRepository.findByUserId(user.getId());
            achievementService.evaluateAchievements(user, "LESSON_COUNT", allCompleted.size());
            achievementService.evaluateAchievements(user, "XP_REACHED", user.getXp());
            
            // Check specific path completion
            checkPathCompletion(user, allCompleted);
            
            // Trigger Quests
            try {
                if (isQuiz) {
                    questService.updateQuestProgress(user.getId(), "QUIZ", 1);
                } else {
                    questService.updateQuestProgress(user.getId(), "LESSON", 1);
                }
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private void checkPathCompletion(User user, List<UserLessonProgress> allCompleted) {
        // Path 1: Trading Basics (let's assume it has 6 lessons)
        long basicsCount = allCompleted.stream().filter(p -> p.getLessonId().startsWith("basics_")).count();
        if (basicsCount >= 6) {
            achievementService.evaluateAchievements(user, "PATH_COMPLETE_BASICS", 1);
        }
    }
}
