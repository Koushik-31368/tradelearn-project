package com.tradelearn.server.service;

import com.tradelearn.server.model.Game;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.socket.GameBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Background service that automatically removes WAITING lobby games
 * that have been open for longer than STALE_MINUTES minutes.
 */
@Service
public class GameCleanupService {

    private static final Logger log = LoggerFactory.getLogger(GameCleanupService.class);

    private static final long STALE_MINUTES = 10;
    private static final long CLEANUP_INTERVAL_MS = 5 * 60_000L;

    private final GameRepository  gameRepository;
    private final RoomManager     roomManager;
    private final GameBroadcaster broadcaster;

    public GameCleanupService(GameRepository gameRepository,
                              RoomManager roomManager,
                              GameBroadcaster broadcaster) {
        this.gameRepository = gameRepository;
        this.roomManager    = roomManager;
        this.broadcaster    = broadcaster;
    }

    @Transactional
    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupStaleGames() {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(STALE_MINUTES, ChronoUnit.MINUTES));
        List<Game> stale = gameRepository.findStaleWaitingGames(cutoff);

        if (stale.isEmpty()) return;

        log.info("[GameCleanup] Found {} stale WAITING game(s) older than {} min – cleaning up",
                stale.size(), STALE_MINUTES);

        for (Game game : stale) {
            long gameId = game.getId();
            try {
                gameRepository.delete(game);
                log.debug("[GameCleanup] Deleted game {}", gameId);
            } catch (Exception e) {
                log.error("[GameCleanup] Failed to delete game {}: {}", gameId, e.getMessage());
                continue;
            }
            try {
                roomManager.endGame(gameId, false);
            } catch (Exception e) {
                log.warn("[GameCleanup] Redis cleanup skipped for game {}: {}", gameId, e.getMessage());
            }
        }

        try {
            broadcaster.broadcastLobbyUpdate();
        } catch (Exception e) {
            log.warn("[GameCleanup] broadcastLobbyUpdate failed after cleanup: {}", e.getMessage());
        }
    }
}
