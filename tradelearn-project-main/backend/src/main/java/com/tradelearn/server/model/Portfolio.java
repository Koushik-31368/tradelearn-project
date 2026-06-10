package com.tradelearn.server.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "portfolios")
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    private User user;

    @Column(name = "virtual_cash", nullable = false)
    private Double virtualCash;

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<Holding> holdings = new ArrayList<>();

    public Portfolio() {}

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
    public List<Holding> getHoldings() {
        return holdings;
    }
    public void setHoldings(List<Holding> holdings) {
        this.holdings = holdings;
    }
}