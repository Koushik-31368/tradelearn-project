package com.tradelearn.server.social.repository;

import com.tradelearn.server.social.model.Friendship;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    /**
     * Eager-loads both the `user` and `friend` associations via a JOIN in a
     * single query. Without this, Hibernate returns lazy proxies that explode
     * with LazyInitializationException once the session closes before the
     * controller mapping lambda accesses .getUsername().
     */
    @EntityGraph(attributePaths = {"user", "friend"})
    List<Friendship> findByUserIdOrFriendId(Long userId, Long friendId);

    /**
     * Used by acceptRequest / rejectRequest — needs both sides eagerly loaded
     * so .getFriend().getId() and .getUser().getId() work outside the session.
     */
    @Query("SELECT f FROM Friendship f JOIN FETCH f.user JOIN FETCH f.friend WHERE f.id = :id")
    Optional<Friendship> findByIdWithUsers(@Param("id") Long id);

    Optional<Friendship> findByUserIdAndFriendId(Long userId, Long friendId);
    boolean existsByUserIdAndFriendId(Long userId, Long friendId);
}
