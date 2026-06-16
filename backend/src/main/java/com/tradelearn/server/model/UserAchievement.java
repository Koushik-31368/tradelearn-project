package com.tradelearn.server.model;

import jakarta.persistence.*;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "user_achievements")
public class UserAchievement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    @CreationTimestamp
    @Column(name = "earned_at", updatable = false)
    private Timestamp earnedAt;

    public UserAchievement() {}

    public UserAchievement(User user, Achievement achievement) {
        this.user = user;
        this.achievement = achievement;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Achievement getAchievement() { return achievement; }
    public Timestamp getEarnedAt() { return earnedAt; }
}
