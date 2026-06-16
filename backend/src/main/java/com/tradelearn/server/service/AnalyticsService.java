package com.tradelearn.server.service;

import com.tradelearn.server.model.TradeJournal;
import com.tradelearn.server.repository.TradeJournalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    @Autowired
    private TradeJournalRepository tradeJournalRepository;

    public Map<String, Object> getUserAnalytics(Long userId) {
        List<TradeJournal> journals = tradeJournalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        
        Map<String, Object> analytics = new HashMap<>();

        if (journals.isEmpty()) {
            return getEmptyAnalytics();
        }

        // --- Core Calculations ---
        int totalTrades = journals.size();
        int wins = 0;
        int losses = 0;
        double grossProfit = 0;
        double grossLoss = 0;
        
        int stopLossUsed = 0;
        int reflectionsCompleted = 0;
        double totalRiskPercent = 0;
        
        // Thesis tracking
        Map<String, Integer> thesisWins = new HashMap<>();
        Map<String, Integer> thesisTotal = new HashMap<>();

        for (TradeJournal j : journals) {
            // Discipline Metrics
            if (j.getStopLoss() != null && j.getStopLoss() > 0) stopLossUsed++;
            if ("COMPLETED".equals(j.getReflectionStatus())) reflectionsCompleted++;
            
            // Assuming Account size is 100,000 for local dev right now to calculate historical risk % if not stored.
            // But we stored riskAmount. We approximate risk % if base portfolio cash is 100000.
            double riskPct = (j.getRiskAmount() != null ? j.getRiskAmount() : 0) / 100000.0 * 100;
            totalRiskPercent += riskPct;

            // Trading Metrics
            String category = j.getThesisCategory() != null ? j.getThesisCategory() : "Other";
            thesisTotal.put(category, thesisTotal.getOrDefault(category, 0) + 1);

            if (j.getPnl() != null) {
                if (j.getPnl() > 0) {
                    wins++;
                    grossProfit += j.getPnl();
                    thesisWins.put(category, thesisWins.getOrDefault(category, 0) + 1);
                } else if (j.getPnl() < 0) {
                    losses++;
                    grossLoss += Math.abs(j.getPnl());
                }
            }
        }

        // --- Trading Score Calculations ---
        double winRate = totalTrades > 0 ? (double) wins / totalTrades * 100 : 0;
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 99.99 : 0);
        // Normalize Trading Score (0-100 heuristic based on Win Rate and Profit Factor)
        int tradingScore = (int) Math.min(100, (winRate * 0.5) + (Math.min(profitFactor, 3.0) / 3.0 * 50));

        // --- Discipline Score Calculations ---
        double slUsageRate = totalTrades > 0 ? (double) stopLossUsed / totalTrades * 100 : 0;
        double reflectionRate = totalTrades > 0 ? (double) reflectionsCompleted / totalTrades * 100 : 0;
        // Average risk should be <= 5% for good score
        double avgRisk = totalTrades > 0 ? totalRiskPercent / totalTrades : 0;
        double riskPenalty = avgRisk > 5.0 ? (avgRisk - 5.0) * 10 : 0; 

        // Weighting: 40% SL, 40% Reflection, 20% Journal (assuming all trades here are journaled if they exist)
        double disciplineScoreRaw = (slUsageRate * 0.4) + (reflectionRate * 0.4) + 20 - riskPenalty;
        int disciplineScore = (int) Math.max(0, Math.min(100, disciplineScoreRaw));

        // --- Learning Score ---
        // Mocked for now until Learning Progress entities are tied in fully
        int learningScore = 85; 

        // --- Thesis Analytics ---
        List<Map<String, Object>> thesisAnalytics = thesisTotal.entrySet().stream().map(entry -> {
            String category = entry.getKey();
            int total = entry.getValue();
            int w = thesisWins.getOrDefault(category, 0);
            double wRate = total > 0 ? (double) w / total * 100 : 0;
            return Map.<String, Object>of(
                "category", category,
                "totalTrades", total,
                "winRate", wRate
            );
        }).collect(Collectors.toList());

        // Find Strongest and Weakest
        String strongestStrategy = "None";
        String weakestStrategy = "None";
        double maxWR = -1;
        double minWR = 101;
        
        for (Map<String, Object> stat : thesisAnalytics) {
            double r = (double) stat.get("winRate");
            int t = (int) stat.get("totalTrades");
            if (t >= 1) { // need at least 1 trade
                if (r > maxWR) { maxWR = r; strongestStrategy = (String) stat.get("category"); }
                if (r < minWR) { minWR = r; weakestStrategy = (String) stat.get("category"); }
            }
        }

        analytics.put("tradingScore", tradingScore);
        analytics.put("disciplineScore", disciplineScore);
        analytics.put("learningScore", learningScore);
        
        analytics.put("metrics", Map.of(
            "winRate", winRate,
            "profitFactor", profitFactor,
            "stopLossUsage", slUsageRate,
            "reflectionRate", reflectionRate,
            "avgRisk", avgRisk,
            "journalCompliance", 100.0 // All trades here are journaled
        ));

        analytics.put("thesisAnalytics", thesisAnalytics);
        analytics.put("strongestStrategy", strongestStrategy);
        analytics.put("weakestStrategy", weakestStrategy);

        return analytics;
    }

    private Map<String, Object> getEmptyAnalytics() {
        return Map.of(
            "tradingScore", 0,
            "disciplineScore", 0,
            "learningScore", 0,
            "metrics", Map.of(
                "winRate", 0,
                "profitFactor", 0,
                "stopLossUsage", 0,
                "reflectionRate", 0,
                "avgRisk", 0,
                "journalCompliance", 0
            ),
            "thesisAnalytics", List.of(),
            "strongestStrategy", "None",
            "weakestStrategy", "None"
        );
    }
}
