package com.tradelearn.server.quests.service;
import com.tradelearn.server.game.model.Game;

import com.tradelearn.server.quests.model.Achievement;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.quests.model.UserAchievement;
import com.tradelearn.server.quests.repository.AchievementRepository;
import com.tradelearn.server.quests.repository.UserAchievementRepository;
import com.tradelearn.server.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserRepository userRepository;

    public AchievementService(AchievementRepository achievementRepository,
                              UserAchievementRepository userAchievementRepository,
                              UserRepository userRepository) {
        this.achievementRepository = achievementRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.userRepository = userRepository;
    }

    public List<UserAchievement> getUserAchievements(Long userId) {
        return userAchievementRepository.findByUserId(userId);
    }

    public void evaluateAchievements(User user, String conditionType, int currentValue) {
        List<Achievement> achievements = achievementRepository.findAll();
        for (Achievement a : achievements) {
            if (a.getConditionType().equals(conditionType)) {
                if (currentValue >= a.getConditionValue()) {
                    unlockAchievement(user, a);
                }
            }
        }
    }

    private void unlockAchievement(User user, Achievement achievement) {
        if (!userAchievementRepository.existsByUserIdAndAchievementId(user.getId(), achievement.getId())) {
            UserAchievement ua = new UserAchievement(user, achievement);
            userAchievementRepository.save(ua);
            
            // Grant XP for achievement
            user.setXp(user.getXp() + achievement.getXpReward());
            userRepository.save(user);

            // Re-evaluate XP achievements
            evaluateAchievements(user, "XP_REACHED", user.getXp());
        }
    }
}
