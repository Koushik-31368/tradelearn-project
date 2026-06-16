package com.tradelearn.server.repository;

import com.tradelearn.server.model.UserLessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserLessonProgressRepository extends JpaRepository<UserLessonProgress, Long> {
    List<UserLessonProgress> findByUserId(Long userId);
    Optional<UserLessonProgress> findByUserIdAndLessonId(Long userId, String lessonId);
    boolean existsByUserIdAndLessonId(Long userId, String lessonId);
}
