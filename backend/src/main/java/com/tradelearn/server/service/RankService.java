package com.tradelearn.server.service;

import org.springframework.stereotype.Service;

@Service
public class RankService {
    public String getRankTier(int rating) {
        if (rating < 500) return "Bronze";
        if (rating < 800) return "Silver";
        if (rating < 1100) return "Gold";
        if (rating < 1500) return "Platinum";
        if (rating < 2000) return "Diamond";
        return "Master";
    }
}
