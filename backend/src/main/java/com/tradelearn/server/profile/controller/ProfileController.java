package com.tradelearn.server.profile.controller;

import com.tradelearn.server.profile.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes user profile data at {@code /api/profile/{userId}}.
 *
 * <p>This controller was extracted from {@code LeaderboardController} where
 * the profile endpoint lived at {@code /api/users/{userId}/profile}. The new
 * canonical path is {@code /api/profile/{userId}}.
 *
 * <p>The old path is preserved in {@link LegacyProfileController} via a
 * redirect so existing frontend and API consumers continue to work without
 * changes.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * Returns the full public profile for a user.
     *
     * <p>Response includes: username, rating, rank tier, global rank,
     * win/loss/draw record, average drawdown, trade accuracy, score,
     * and the last 10 recent matches.
     *
     * @param userId the target user's database ID
     * @return 200 + {@link ProfileService.ProfileResponse}, or 404 if not found
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getProfile(@PathVariable Long userId) {
        return profileService.getProfile(userId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
