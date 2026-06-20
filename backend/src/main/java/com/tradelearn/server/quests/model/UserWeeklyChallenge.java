package com.tradelearn.server.quests.model;
import com.tradelearn.server.user.model.User;

import jakarta.persistence.*;
import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "user_weekly_challenges", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "challenge_id", "week_start_date"})
})
public class UserWeeklyChallenge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "challenge_id", nullable = false)
    private WeeklyChallenge challenge;

    @Column(nullable = false)
    private int progress = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "week_start_date", nullable = false)
    private Date weekStartDate;

    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = new Timestamp(System.currentTimeMillis());
        }
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public WeeklyChallenge getChallenge() { return challenge; }
    public void setChallenge(WeeklyChallenge challenge) { this.challenge = challenge; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Date getWeekStartDate() { return weekStartDate; }
    public void setWeekStartDate(Date weekStartDate) { this.weekStartDate = weekStartDate; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
