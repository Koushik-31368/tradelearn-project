package com.tradelearn.server.quests.service;
import com.tradelearn.server.quests.repository.UserDailyQuestRepository;
import com.tradelearn.server.quests.repository.UserWeeklyChallengeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class QuestCleanupService {

    // Removed the actual logic as Spring Data JPA doesn't have deleteBy... out of the box unless we define it
    // We can rely on the date-based approach instead of cron jobs.
    
    @Scheduled(cron = "0 0 0 * * ?") // Midnight every day
    public void dailyReset() {
        // Daily quests automatically "reset" because getTodayQuests() uses LocalDate.now()
        // No explicit row deletion is needed; historical data is kept.
    }
}
