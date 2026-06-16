package com.tradelearn.server.service;

import com.tradelearn.server.model.Friendship;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.FriendshipRepository;
import com.tradelearn.server.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SocialService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public SocialService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    public void addFriend(User user, String friendUsername) throws Exception {
        if (user.getUsername().equals(friendUsername)) throw new Exception("Cannot add yourself");
        
        User friend = userRepository.findByUsername(friendUsername)
            .orElseThrow(() -> new Exception("User not found"));

        if (friendshipRepository.existsByUserIdAndFriendId(user.getId(), friend.getId()) ||
            friendshipRepository.existsByUserIdAndFriendId(friend.getId(), user.getId())) {
            throw new Exception("Friendship or request already exists");
        }

        Friendship friendship = new Friendship(user, friend, "PENDING");
        friendshipRepository.save(friendship);
    }

    public void acceptRequest(User user, Long requestId) throws Exception {
        Friendship friendship = friendshipRepository.findById(requestId)
            .orElseThrow(() -> new Exception("Request not found"));

        if (!friendship.getFriend().getId().equals(user.getId())) {
            throw new Exception("Not authorized to accept this request");
        }

        friendship.setStatus("ACCEPTED");
        friendshipRepository.save(friendship);
    }

    public void rejectRequest(User user, Long requestId) throws Exception {
        Friendship friendship = friendshipRepository.findById(requestId)
            .orElseThrow(() -> new Exception("Request not found"));

        if (!friendship.getFriend().getId().equals(user.getId()) && !friendship.getUser().getId().equals(user.getId())) {
            throw new Exception("Not authorized to reject this request");
        }

        friendshipRepository.delete(friendship);
    }

    public List<Friendship> getUserFriendships(Long userId) {
        return friendshipRepository.findByUserIdOrFriendId(userId, userId);
    }
}
