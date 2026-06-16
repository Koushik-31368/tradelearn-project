package com.tradelearn.server.model;

import jakarta.persistence.*;
import java.sql.Timestamp;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "friendships")
public class Friendship {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_id", nullable = false)
    private User friend;

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, ACCEPTED

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Timestamp createdAt;

    public Friendship() {}

    public Friendship(User user, User friend, String status) {
        this.user = user;
        this.friend = friend;
        this.status = status;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public User getFriend() { return friend; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
}
