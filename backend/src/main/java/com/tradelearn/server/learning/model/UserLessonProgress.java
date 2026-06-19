package com.tradelearn.server.learning.model;
import com.tradelearn.server.user.model.User;

import jakarta.persistence.*;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_lesson_progress")
public class UserLessonProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "lesson_id", nullable = false)
    private String lessonId;

    @Column(name = "completed", nullable = false)
    private boolean completed = true;

    @CreationTimestamp
    @Column(name = "completed_at", updatable = false)
    private Timestamp completedAt;

    public UserLessonProgress() {}

    public UserLessonProgress(User user, String lessonId) {
        this.user = user;
        this.lessonId = lessonId;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getLessonId() { return lessonId; }
    public void setLessonId(String lessonId) { this.lessonId = lessonId; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) { this.completedAt = completedAt; }
}
