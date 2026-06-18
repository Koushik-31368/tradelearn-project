package com.tradelearn.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReadinessScoreService {

    @Autowired
    private AnalyticsService analyticsService;

    @SuppressWarnings("unchecked")
    public Map<String, Object> evaluateReadiness(Long userId) {
        Map<String, Object> baseAnalytics = analyticsService.getUserAnalytics(userId);
        
        // Ensure data exists before casting
        if (baseAnalytics == null || !baseAnalytics.containsKey("metrics")) {
            return getInsufficientDataResponse(0, 0);
        }

        Map<String, Object> metrics = (Map<String, Object>) baseAnalytics.get("metrics");
        List<Map<String, Object>> thesisAnalytics = (List<Map<String, Object>>) baseAnalytics.get("thesisAnalytics");
        
        // Calculate Total Trades and Reflections
        int totalTrades = 0;
        int reflections = 0;
        
        for (Map<String, Object> ta : thesisAnalytics) {
            totalTrades += (int) ta.getOrDefault("totalTrades", 0);
        }
        
        double reflectionRate = (double) metrics.getOrDefault("reflectionRate", 0.0);
        reflections = (int) (totalTrades * (reflectionRate / 100.0));

        // Minimum Requirements Check (Strict Mode)
        // Production Rules: 20 trades, 10 reflections
        // Note: For actual user testing these might be mocked if needed, but per CTO directive we enforce them.
        if (totalTrades < 20 || reflections < 10) {
            return getInsufficientDataResponse(totalTrades, reflections);
        }

        // 1. Calculate the 4 Pillars
        int disciplineScore = (int) baseAnalytics.getOrDefault("disciplineScore", 0);
        int learningScore = (int) baseAnalytics.getOrDefault("learningScore", 0);
        int tradingScore = (int) baseAnalytics.getOrDefault("tradingScore", 0);
        double winRate = (double) metrics.getOrDefault("winRate", 0.0);
        int resultsScore = (int) Math.min(100, winRate * 1.5);

        // Readiness Formula: 40% Discipline, 30% Learning, 20% Trading Process, 10% Results
        double finalReadiness = (disciplineScore * 0.40) + 
                                (learningScore * 0.30) + 
                                (tradingScore * 0.20) + 
                                (resultsScore * 0.10);
        
        int readinessScore = (int) Math.max(0, Math.min(100, finalReadiness));

        // Graduation Check (Paper Trading Requirements)
        double journalCompliance = (double) metrics.getOrDefault("journalCompliance", 0.0);
        boolean qualifiesForGraduation = 
            disciplineScore >= 85 && 
            learningScore >= 80 && 
            journalCompliance >= 90.0 && 
            totalTrades >= 50 && 
            reflections >= 30;

        // 2. Map to Educational Tiers
        String educationalTier = getTier(readinessScore, qualifiesForGraduation);

        // 3. Rule Engines (Strengths & Weaknesses)
        List<String> strengths = evaluateStrengths(metrics, thesisAnalytics);
        List<String> weaknesses = evaluateWeaknesses(metrics, thesisAnalytics);

        // Fallbacks if lists are empty
        if (strengths.isEmpty()) strengths.add("Continuing to build foundational habits.");
        if (weaknesses.isEmpty()) weaknesses.add("Maintain current discipline.");

        // Recommendation
        String recommendation = generateRecommendation(weaknesses);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "EVALUATED");
        response.put("readinessScore", readinessScore);
        response.put("educationalTier", educationalTier);
        response.put("strengths", strengths);
        response.put("needsImprovement", weaknesses);
        response.put("recommendation", recommendation);
        
        // Include raw scores for UI bars
        response.put("disciplineScore", disciplineScore);
        response.put("learningScore", learningScore);
        response.put("tradingScore", tradingScore);
        response.put("resultsScore", resultsScore);

        return response;
    }

    private List<String> evaluateStrengths(Map<String, Object> metrics, List<Map<String, Object>> thesisAnalytics) {
        List<String> strengths = new ArrayList<>();
        
        if ((double) metrics.get("journalCompliance") > 95.0) {
            strengths.add("Excellent journaling discipline.");
        }
        if ((double) metrics.get("avgRisk") <= 2.0) {
            strengths.add("Exceptional risk management and position sizing.");
        }
        if ((double) metrics.get("stopLossUsage") > 90.0) {
            strengths.add("Consistently protecting capital with stop losses.");
        }
        if ((double) metrics.get("reflectionRate") > 90.0) {
            strengths.add("Strong habit of post-trade review and reflection.");
        }
        
        for (Map<String, Object> ta : thesisAnalytics) {
            String cat = (String) ta.get("category");
            double wr = (double) ta.get("winRate");
            int total = (int) ta.get("totalTrades");
            if (total >= 5 && wr > 60.0) {
                strengths.add(cat + " setups appear to be a major strength.");
            }
        }
        
        return strengths;
    }

    private List<String> evaluateWeaknesses(Map<String, Object> metrics, List<Map<String, Object>> thesisAnalytics) {
        List<String> weaknesses = new ArrayList<>();
        
        if ((double) metrics.get("stopLossUsage") < 80.0) {
            weaknesses.add("Stop losses are missing too often. Capital at risk.");
        }
        if ((double) metrics.get("reflectionRate") < 80.0) {
            weaknesses.add("Reflection compliance needs improvement. Do not skip reviews.");
        }
        if ((double) metrics.get("avgRisk") > 5.0) {
            weaknesses.add("Risking too much capital per trade (> 5%).");
        }
        
        for (Map<String, Object> ta : thesisAnalytics) {
            String cat = (String) ta.get("category");
            double wr = (double) ta.get("winRate");
            int total = (int) ta.get("totalTrades");
            if (total >= 3 && wr < 35.0) {
                weaknesses.add(cat + " setups are underperforming and draining capital.");
            }
        }
        
        return weaknesses;
    }

    private String getTier(int score, boolean qualifiesForGraduation) {
        if (score <= 20) return "Explorer";
        if (score <= 40) return "Student";
        if (score <= 60) return "Practitioner";
        if (score <= 80) return "Disciplined Trader";
        
        // Even if score > 80, cannot graduate to Paper Trading unless strict minimums are met
        if (qualifiesForGraduation) {
            return "Ready For Paper Trading";
        } else {
            return "Simulation Graduate";
        }
    }

    private String generateRecommendation(List<String> weaknesses) {
        for (String w : weaknesses) {
            if (w.contains("Stop losses")) return "Review 'Capital Preservation' Module.";
            if (w.contains("Reflection")) return "Slow down execution; focus on the Journaling Path.";
            if (w.contains("Risking too much")) return "Complete 'Position Sizing 101' Module.";
            if (w.contains("underperforming")) return "Pause trading weak setups; review historical failures.";
        }
        return "Complete 10 more reflected trades to build consistency.";
    }

    private Map<String, Object> getInsufficientDataResponse(int trades, int reflections) {
        return Map.of(
            "status", "INSUFFICIENT_DATA",
            "message", "Assessment In Progress. Coaching Engine requires more data to evaluate your behavior.",
            "requirements", Map.of(
                "minimumTrades", "20 Closed Trades (Current: " + trades + ")",
                "minimumReflections", "10 Reflections (Current: " + reflections + ")",
                "minimumModules", "3 Learning Modules Completed",
                "minimumDays", "7 Active Days"
            )
        );
    }
}
