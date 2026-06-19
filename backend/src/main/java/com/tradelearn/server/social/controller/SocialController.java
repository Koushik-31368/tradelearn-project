package com.tradelearn.server.social.controller;
import com.tradelearn.server.social.model.GameChallenge;

import com.tradelearn.server.dto.FriendDTO;
import com.tradelearn.server.social.model.Friendship;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.social.service.SocialService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialService socialService;
    private final UserRepository userRepository;

    public SocialController(SocialService socialService, UserRepository userRepository) {
        this.socialService = socialService;
        this.userRepository = userRepository;
    }

    private User getAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username).orElse(null);
    }

    @PostMapping("/friends/add/{username}")
    public ResponseEntity<?> addFriend(@PathVariable String username) {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            socialService.addFriend(user, username);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/friends/accept/{requestId}")
    public ResponseEntity<?> acceptRequest(@PathVariable Long requestId) {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            socialService.acceptRequest(user, requestId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/friends/reject/{requestId}")
    public ResponseEntity<?> rejectRequest(@PathVariable Long requestId) {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            socialService.rejectRequest(user, requestId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/friends")
    public ResponseEntity<List<FriendDTO>> getFriends() {
        User user = getAuthenticatedUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<Friendship> friendships = socialService.getUserFriendships(user.getId());
        List<FriendDTO> dtos = friendships.stream().map(f -> {
            boolean isSender = f.getUser().getId().equals(user.getId());
            User other = isSender ? f.getFriend() : f.getUser();
            return new FriendDTO(
                f.getId(),
                other.getId(),
                other.getUsername(),
                other.getRating(),
                f.getStatus(),
                isSender
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
