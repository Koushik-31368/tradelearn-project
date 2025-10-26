package com.tradelearn.server.model;

import jakarta.persistence.*;

@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This links the portfolio to a specific user.
    // One user can have one portfolio.
    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "virtual_cash", nullable = false)
    private Double virtualCash;

    // Default constructor
    public Portfolio() {}

    // Constructor to create a new portfolio for a user
    public Portfolio(User user, Double startingCash) {
        this.user = user;
        this.virtualCash = startingCash;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }
    public Double getVirtualCash() {
        return virtualCash;
    }
    public void setVirtualCash(Double virtualCash) {
        this.virtualCash = virtualCash;
    }
}