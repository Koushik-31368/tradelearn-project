// src/main/java/com/tradelearn/server/controller/StrategyController.java
package com.tradelearn.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.BacktestRequest;
import com.tradelearn.server.dto.BacktestResult;
import com.tradelearn.server.dto.BatchBacktestRequest;
import com.tradelearn.server.dto.BatchBacktestResult;
import com.tradelearn.server.service.BacktestService;

@RestController
@RequestMapping("/api/strategy")
public class StrategyController {

    private final BacktestService backtestService;

    public StrategyController(BacktestService backtestService) {
        this.backtestService = backtestService;
    }

    @PostMapping("/backtest")
    public ResponseEntity<BacktestResult> backtest(@RequestBody BacktestRequest request) {
        BacktestResult res = backtestService.runSmaCross(request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/backtest/batch")
    public ResponseEntity<BatchBacktestResult> backtestBatch(@RequestBody BatchBacktestRequest request) {
        BatchBacktestResult res = backtestService.runBatch(request);
        return ResponseEntity.ok(res);
    }
}