package com.tradelearn.server.controller;

import com.tradelearn.server.model.TradeJournal;
import com.tradelearn.server.repository.TradeJournalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/journals")
public class TradeJournalController {

    @Autowired
    private TradeJournalRepository tradeJournalRepository;

    @PostMapping("/entry")
    public ResponseEntity<?> createJournalEntry(@RequestBody TradeJournal journal) {
        if (journal.getUserId() == null || journal.getSymbol() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user or symbol"));
        }
        journal.setCreatedAt(LocalDateTime.now());
        journal.setReflectionStatus("PENDING");
        TradeJournal saved = tradeJournalRepository.save(journal);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    @PostMapping("/reflect/{id}")
    @SuppressWarnings("null")
    public ResponseEntity<?> submitReflection(@PathVariable Long id, @RequestBody Map<String, String> reflection) {
        Optional<TradeJournal> optJournal = tradeJournalRepository.findById(id);
        if (optJournal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Journal not found"));
        }

        TradeJournal journal = optJournal.get();
        journal.setMistakesMade(reflection.get("mistakesMade"));
        journal.setLessonsLearned(reflection.get("lessonsLearned"));
        journal.setReflectionStatus("COMPLETED");
        journal.setCompletedAt(LocalDateTime.now());

        TradeJournal saved = tradeJournalRepository.save(journal);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/close/{id}")
    @SuppressWarnings("null")
    public ResponseEntity<?> closeJournal(@PathVariable Long id, @RequestBody Map<String, Object> closingData) {
        Optional<TradeJournal> optJournal = tradeJournalRepository.findById(id);
        if (optJournal.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Journal not found"));
        }

        TradeJournal journal = optJournal.get();
        if (closingData.containsKey("pnl")) {
            journal.setPnl(Double.valueOf(closingData.get("pnl").toString()));
        }
        if (closingData.containsKey("outcomeStatus")) {
            journal.setOutcomeStatus((String) closingData.get("outcomeStatus"));
        }

        TradeJournal saved = tradeJournalRepository.save(journal);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<TradeJournal>> getUserJournals(@PathVariable Long userId) {
        List<TradeJournal> journals = tradeJournalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(journals);
    }
    
    @GetMapping("/user/{userId}/pending")
    public ResponseEntity<List<TradeJournal>> getPendingReflections(@PathVariable Long userId) {
        List<TradeJournal> journals = tradeJournalRepository.findByUserIdAndReflectionStatus(userId, "PENDING");
        return ResponseEntity.ok(journals);
    }
}
