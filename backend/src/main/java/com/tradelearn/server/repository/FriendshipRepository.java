package com.tradelearn.server.repository;

import com.tradelearn.server.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    List<Friendship> findByUserIdOrFriendId(Long userId, Long friendId);
    Optional<Friendship> findByUserIdAndFriendId(Long userId, Long friendId);
    boolean existsByUserIdAndFriendId(Long userId, Long friendId);
}
