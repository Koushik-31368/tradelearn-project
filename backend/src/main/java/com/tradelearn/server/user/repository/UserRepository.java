package com.tradelearn.server.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradelearn.server.user.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findTop10ByOrderByRatingDesc();

    List<User> findAllByOrderByRatingDesc();

    long countByRatingGreaterThan(int rating);
}