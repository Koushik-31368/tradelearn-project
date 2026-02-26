package com.tradelearn.server.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tradelearn.server.model.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findTop10ByOrderByRatingDesc();

    List<User> findAllByOrderByRatingDesc();
}