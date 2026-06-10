// src/main/java/com/tradelearn/server/controller/StrategyController.java
package com.tradelearn.server.controller;

import com.tradelearn.server.dto.BacktestRequest;
import com.tradelearn.server.dto.BacktestResult;
import com.tradelearn.server.dto.BatchBacktestRequest;
import com.tradelearn.server.dto.BatchBacktestResult;
import com.tradelearn.server.service.BacktestService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/strategy")
@CrossOrigin(origins = {"http://localhost:3000", "https://tradelearn-project.vercel.app"}, allowCredentials = "true")
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