package com.tradelearn.server.quests.model;
import com.tradelearn.server.user.model.User;

import jakarta.persistence.*;
import java.sql.Date;
import java.sql.Timestamp;

@Entity
@Table(name = "user_daily_quests", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "quest_id", "quest_date"})
})
public class UserDailyQuest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "quest_id", nullable = false)
    private DailyQuest quest;

    @Column(nullable = false)
    private int progress = 0;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "quest_date", nullable = false)
    private Date questDate;

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
    public DailyQuest getQuest() { return quest; }
    public void setQuest(DailyQuest quest) { this.quest = quest; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public Date getQuestDate() { return questDate; }
    public void setQuestDate(Date questDate) { this.questDate = questDate; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
