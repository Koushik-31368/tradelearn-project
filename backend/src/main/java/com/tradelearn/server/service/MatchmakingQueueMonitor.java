package com.tradelearn.server.service;

import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

@Component
public class MatchmakingQueueMonitor {
    private final QueueSizeProvider queueSizeProvider;
    private final MeterRegistry registry;

    public MatchmakingQueueMonitor(QueueSizeProvider queueSizeProvider, MeterRegistry registry) {
        this.queueSizeProvider = queueSizeProvider;
        this.registry = registry;
    }

    @PostConstruct
    public void registerGauge() {
        registry.gauge("matchmaking.queue.size", queueSizeProvider, QueueSizeProvider::queueSize);
    }
}
