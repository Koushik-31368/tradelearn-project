package com.tradelearn.server.quests.service;
import com.tradelearn.server.quests.model.DailyQuest;
import com.tradelearn.server.quests.model.UserDailyQuest;
import com.tradelearn.server.quests.model.WeeklyChallenge;
import com.tradelearn.server.quests.model.UserWeeklyChallenge;
import com.tradelearn.server.quests.repository.DailyQuestRepository;
import com.tradelearn.server.quests.repository.UserDailyQuestRepository;
import com.tradelearn.server.quests.repository.WeeklyChallengeRepository;
import com.tradelearn.server.quests.repository.UserWeeklyChallengeRepository;
import com.tradelearn.server.user.model.User;

import com.tradelearn.server.dto.ChallengeDTO;
import com.tradelearn.server.dto.QuestDTO;
import com.tradelearn.server.user.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class QuestService {

    private final DailyQuestRepository dailyQuestRepository;
    private final UserDailyQuestRepository userDailyQuestRepository;
    private final WeeklyChallengeRepository weeklyChallengeRepository;
    private final UserWeeklyChallengeRepository userWeeklyChallengeRepository;
    private final UserRepository userRepository;
    private final AchievementService achievementService;

    public QuestService(DailyQuestRepository dailyQuestRepository,
                        UserDailyQuestRepository userDailyQuestRepository,
                        WeeklyChallengeRepository weeklyChallengeRepository,
                        UserWeeklyChallengeRepository userWeeklyChallengeRepository,
                        UserRepository userRepository,
                        @Lazy AchievementService achievementService) {
        this.dailyQuestRepository = dailyQuestRepository;
        this.userDailyQuestRepository = userDailyQuestRepository;
        this.weeklyChallengeRepository = weeklyChallengeRepository;
        this.userWeeklyChallengeRepository = userWeeklyChallengeRepository;
        this.userRepository = userRepository;
        this.achievementService = achievementService;
    }

    @Transactional
    @SuppressWarnings("null")
    public List<QuestDTO> getTodayQuests(Long userId) {
        Date today = Date.valueOf(LocalDate.now());
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        List<DailyQuest> activeQuests = dailyQuestRepository.findByIsActiveTrue();
        List<QuestDTO> dtoList = new ArrayList<>();

        for (DailyQuest quest : activeQuests) {
            Optional<UserDailyQuest> userQuestOpt = userDailyQuestRepository.findByUserIdAndQuestIdAndQuestDate(userId, quest.getId(), today);
            UserDailyQuest userQuest;

            if (userQuestOpt.isEmpty()) {
                userQuest = new UserDailyQuest();
                userQuest.setUser(user);
                userQuest.setQuest(quest);
                userQuest.setQuestDate(today);
                userQuest.setProgress(0);
                userQuest.setCompleted(false);
                userQuest = userDailyQuestRepository.save(userQuest);
            } else {
                userQuest = userQuestOpt.get();
            }

            QuestDTO dto = new QuestDTO();
            dto.setId(quest.getId());
            dto.setName(quest.getName());
            dto.setDescription(quest.getDescription());
            dto.setQuestType(quest.getQuestType());
            dto.setTargetValue(quest.getTargetValue());
            dto.setXpReward(quest.getXpReward());
            dto.setProgress(userQuest.getProgress());
            dto.setCompleted(userQuest.isCompleted());
            dtoList.add(dto);
        }

        return dtoList;
    }

    @Transactional
    @SuppressWarnings("null")
    public List<ChallengeDTO> getThisWeekChallenges(Long userId) {
        LocalDate startOfWeekLocal = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Date startOfWeek = Date.valueOf(startOfWeekLocal);
        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found"));

        List<WeeklyChallenge> activeChallenges = weeklyChallengeRepository.findByIsActiveTrue();
        List<ChallengeDTO> dtoList = new ArrayList<>();

        for (WeeklyChallenge challenge : activeChallenges) {
            Optional<UserWeeklyChallenge> userChallengeOpt = userWeeklyChallengeRepository.findByUserIdAndChallengeIdAndWeekStartDate(userId, challenge.getId(), startOfWeek);
            UserWeeklyChallenge userChallenge;

            if (userChallengeOpt.isEmpty()) {
                userChallenge = new UserWeeklyChallenge();
                userChallenge.setUser(user);
                userChallenge.setChallenge(challenge);
                userChallenge.setWeekStartDate(startOfWeek);
                userChallenge.setProgress(0);
                userChallenge.setCompleted(false);
                userChallenge = userWeeklyChallengeRepository.save(userChallenge);
            } else {
                userChallenge = userChallengeOpt.get();
            }

            ChallengeDTO dto = new ChallengeDTO();
            dto.setId(challenge.getId());
            dto.setName(challenge.getName());
            dto.setDescription(challenge.getDescription());
            dto.setChallengeType(challenge.getChallengeType());
            dto.setTargetValue(challenge.getTargetValue());
            dto.setXpReward(challenge.getXpReward());
            dto.setBonusPoints(challenge.getBonusPoints());
            dto.setBadgeReward(challenge.getBadgeReward());
            dto.setProgress(userChallenge.getProgress());
            dto.setCompleted(userChallenge.isCompleted());
            dtoList.add(dto);
        }

        return dtoList;
    }

    @Transactional
    @SuppressWarnings("null")
    public void updateQuestProgress(Long userId, String questType, int amount) {
        Date today = Date.valueOf(LocalDate.now());
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        List<DailyQuest> quests = dailyQuestRepository.findByIsActiveTrue();
        for (DailyQuest quest : quests) {
            if (quest.getQuestType().equals(questType)) {
                Optional<UserDailyQuest> userQuestOpt = userDailyQuestRepository.findByUserIdAndQuestIdAndQuestDate(userId, quest.getId(), today);
                if (userQuestOpt.isPresent()) {
                    UserDailyQuest userQuest = userQuestOpt.get();
                    if (!userQuest.isCompleted()) {
                        userQuest.setProgress(userQuest.getProgress() + amount);
                        if (userQuest.getProgress() >= quest.getTargetValue()) {
                            userQuest.setProgress(quest.getTargetValue());
                            userQuest.setCompleted(true);
                            user.setXp(user.getXp() + quest.getXpReward());
                            userRepository.save(user);
                            
                            // Also update 'EARN_XP' weekly challenge
                            updateChallengeProgress(userId, "EARN_XP", quest.getXpReward());
                        }
                        userDailyQuestRepository.save(userQuest);
                    }
                }
            }
        }
    }

    @Transactional
    @SuppressWarnings("null")
    public void updateChallengeProgress(Long userId, String challengeType, int amount) {
        LocalDate startOfWeekLocal = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Date startOfWeek = Date.valueOf(startOfWeekLocal);
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        List<WeeklyChallenge> challenges = weeklyChallengeRepository.findByIsActiveTrue();
        for (WeeklyChallenge challenge : challenges) {
            if (challenge.getChallengeType().equals(challengeType)) {
                Optional<UserWeeklyChallenge> userChallengeOpt = userWeeklyChallengeRepository.findByUserIdAndChallengeIdAndWeekStartDate(userId, challenge.getId(), startOfWeek);
                if (userChallengeOpt.isPresent()) {
                    UserWeeklyChallenge userChallenge = userChallengeOpt.get();
                    if (!userChallenge.isCompleted()) {
                        userChallenge.setProgress(userChallenge.getProgress() + amount);
                        if (userChallenge.getProgress() >= challenge.getTargetValue()) {
                            userChallenge.setProgress(challenge.getTargetValue());
                            userChallenge.setCompleted(true);
                            user.setXp(user.getXp() + challenge.getXpReward());
                            userRepository.save(user);
                            // Evaluate XP-based achievements after XP grant
                            achievementService.evaluateAchievements(user, "XP_REACHED", user.getXp());
                            // Evaluate badge achievements if this challenge has a badge reward
                            if (challenge.getBadgeReward() != null && !challenge.getBadgeReward().isBlank()) {
                                achievementService.evaluateAchievements(user, "CHALLENGE_COMPLETED", 1);
                            }
                        }
                        userWeeklyChallengeRepository.save(userChallenge);
                    }
                }
            }
        }
    }
}
