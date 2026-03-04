package com.tradelearn.server.service;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.socket.GameBroadcaster;
import com.tradelearn.server.util.RoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Background service that automatically removes WAITING lobby games
 * that have been open for longer than {@value #STALE_MINUTES} minutes.
 *
 * <p>This prevents the lobby from filling up with orphaned rooms whose
 * host has left without explicitly cancelling.</p>
 *
 * <p>{@code @EnableScheduling} is already declared in
 * {@code SchedulerConfig} – no configuration changes needed.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameCleanupService {

    /** Games in WAITING state older than this many minutes are considered stale. */
    private static final long STALE_MINUTES = 10;

    /** Cleanup runs every 5 minutes. */
    private static final long CLEANUP_INTERVAL_MS = 5 * 60_000L;

    private final GameRepository    gameRepository;
    private final RoomManager       roomManager;
    private final GameBroadcaster   broadcaster;

    /**
     * Finds all WAITING games created more than {@value #STALE_MINUTES} minutes ago
     * and deletes them, cleaning up any associated Redis room state.
     *
     * <p>A single {@code /topic/lobby/refresh} broadcast is sent after the
     * transaction commits if at least one game was cleaned up.</p>
     */
    @Transactional
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupStaleGames() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES));
        List<Game> stale = gameRepository.findStaleWaitingGames(cutoff);

        if (stale.isEmpty()) {
            return;
        }

        log.info("[GameCleanup] Found {} stale WAITING game(s) older than {} min – cleaning up",
                stale.size(), STALE_MINUTES);

        for (Game game : stale) {
            long gameId = game.getId();
            try {
                gameRepository.delete(game);
                log.debug("[GameCleanup] Deleted game {}", gameId);
            } catch (Exception e) {
                log.error("[GameCleanup] Failed to delete game {}: {}", gameId, e.getMessage());
                continue; // skip Redis cleanup if DB delete failed
            }
            try {
                roomManager.endGame(gameId, false);
            } catch (Exception e) {
                // Redis key may not exist for very old games – safe to ignore
                log.warn("[GameCleanup] Redis cleanup skipped for game {}: {}", gameId, e.getMessage());
            }
        }

        // Broadcast once (not once-per-game) to avoid flooding connected clients
        try {
            broadcaster.broadcastLobbyUpdate();
        } catch (Exception e) {
            log.warn("[GameCleanup] broadcastLobbyUpdate failed after cleanup: {}", e.getMessage());
        }
    }
}
