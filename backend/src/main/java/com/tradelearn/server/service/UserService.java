package com.tradelearn.server.service;

import java.sql.Date;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.UserRepository;
import org.springframework.context.annotation.Lazy;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final QuestService questService;

    public UserService(UserRepository userRepository, @Lazy QuestService questService) {
        this.userRepository = userRepository;
        this.questService = questService;
    }

    @Transactional
    public User performDailyCheckin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDate today = LocalDate.now();
        Date sqlToday = Date.valueOf(today);
        
        if (user.getLastLoginDate() != null && user.getLastLoginDate().toString().equals(sqlToday.toString())) {
            // Already checked in today
            return user;
        }

        if (user.getLastLoginDate() != null && user.getLastLoginDate().toLocalDate().equals(today.minusDays(1))) {
            // Streak continues
            user.setLoginStreak(user.getLoginStreak() + 1);
        } else {
            // Streak resets
            user.setLoginStreak(1);
        }

        if (user.getLoginStreak() > user.getLongestLoginStreak()) {
            user.setLongestLoginStreak(user.getLoginStreak());
        }

        user.setLastLoginDate(sqlToday);
        // Grant 10 XP for check-in
        user.setXp(user.getXp() + 10);

        User savedUser = userRepository.save(user);
        
        // Trigger LOGIN quest
        try {
            questService.updateQuestProgress(savedUser.getId(), "LOGIN", 1);
        } catch (Exception e) {
            // Ignore if quest service fails to avoid blocking login
        }
        
        return savedUser;
    }
}
