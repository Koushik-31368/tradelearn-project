package com.tradelearn.server.game.service;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.GameStatus;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.infrastructure.redis.room.RoomManager;
import com.tradelearn.server.infrastructure.scheduling.MatchSchedulerService;
import com.tradelearn.server.market.service.CandleService;
import com.tradelearn.server.websocket.GameBroadcaster;
import com.tradelearn.server.infrastructure.redis.store.PositionSnapshotStore;
import com.tradelearn.server.infrastructure.scheduling.GameMetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MatchLifecycleService}.
 *
 * <p>All external dependencies (repositories, Redis, WebSocket) are mocked.
 * Tests that exercise methods using {@link TransactionSynchronizationManager}
 * manually initialise and clean up synchronization around each call — this
 * mirrors what Spring's {@code @Transactional} does in production.
 *
 * <p>Tests cover the three core lifecycle paths:
 * <ul>
 *   <li>Create custom match</li>
 *   <li>Join an existing match</li>
 *   <li>Delete (host cancel) a WAITING match</li>
 *   <li>afterCommit retry + fail + notify path (Issue 2)</li>
 *   <li>Sweep job for orphaned ACTIVE games (Issue 2)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("null")
class MatchLifecycleServiceTest {

    @Mock private GameRepository gameRepository;
    @Mock private UserRepository userRepository;
    @Mock private CandleService candleService;
    @Mock private MatchSchedulerService matchSchedulerService;
    @Mock private GameBroadcaster broadcaster;
    @Mock private RoomManager roomManager;
    @Mock private PositionSnapshotStore positionStore;
    @Mock private GameMetricsService metrics;

    @InjectMocks
    private MatchLifecycleService service;

    private User creator;
    private User opponent;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(1L);
        creator.setUsername("creator");
        creator.setRating(1200);

        opponent = new User();
        opponent.setId(2L);
        opponent.setUsername("opponent");
        opponent.setRating(1150);
    }

    // ── createMatch ──────────────────────────────────────────────────────────

    @Test
    void createMatch_setsStatusWaiting_andCreatesRoom() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));

        Game savedGame = new Game();
        savedGame.setId(100L);
        savedGame.setStatus(GameStatus.WAITING);
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        CreateMatchRequest req = new CreateMatchRequest();
        req.setCreatorId(1L);
        req.setStockSymbol("TCS");
        req.setDurationMinutes(5);
        req.setStartingBalance(1_000_000.0);

        // Act
        Game result = service.createMatch(req);

        // Assert
        assertThat(result.getStatus()).isEqualTo(GameStatus.WAITING);
        assertThat(result.getId()).isEqualTo(100L);
        verify(roomManager).createRoom(100L, 1L);
        verify(gameRepository).save(any(Game.class));
    }

    @Test
    void createMatch_throwsWhenCreatorNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        CreateMatchRequest req = new CreateMatchRequest();
        req.setCreatorId(99L);
        req.setStockSymbol("TCS");
        req.setDurationMinutes(5);
        req.setStartingBalance(1_000_000.0);

        assertThatThrownBy(() -> service.createMatch(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Creator not found");
    }

    // ── deleteGame ───────────────────────────────────────────────────────────

    @Test
    void deleteGame_throwsWhenNotCreator() {
        Game game = new Game();
        game.setId(100L);
        game.setCreator(creator);
        game.setStatus(GameStatus.WAITING);

        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        // opponent trying to delete creator's game
        assertThatThrownBy(() -> service.deleteGame(100L, 2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only the host");
    }

    @Test
    void deleteGame_throwsWhenGameNotWaiting() {
        Game game = new Game();
        game.setId(100L);
        game.setCreator(creator);
        game.setStatus(GameStatus.ACTIVE);

        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        assertThatThrownBy(() -> service.deleteGame(100L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot cancel");
    }

    @Test
    void deleteGame_deletesAndCleansUpRoom() {
        Game game = new Game();
        game.setId(100L);
        game.setCreator(creator);
        game.setStatus(GameStatus.WAITING);

        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        // Activate TX synchronization manager so registerSynchronization() works
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.deleteGame(100L, 1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(gameRepository).deleteById(100L);
        verify(roomManager).endGame(eq(100L), anyBoolean());
    }

    // ── createAutoMatch ──────────────────────────────────────────────────────

    @Test
    void createAutoMatch_setsStatusActiveWithBothPlayers() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
        when(userRepository.findById(2L)).thenReturn(Optional.of(opponent));

        Game savedGame = new Game();
        savedGame.setId(200L);
        savedGame.setCreator(creator);
        savedGame.setOpponent(opponent);
        savedGame.setStatus(GameStatus.ACTIVE);
        savedGame.setStartingBalance(1_000_000.0);
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        // Activate TX synchronization manager so registerSynchronization() works
        TransactionSynchronizationManager.initSynchronization();
        Game result;
        try {
            result = service.createAutoMatch(1L, 2L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(result.getStatus()).isEqualTo(GameStatus.ACTIVE);
        assertThat(result.getCreator().getId()).isEqualTo(1L);
        assertThat(result.getOpponent().getId()).isEqualTo(2L);
        verify(metrics).recordMatchCreated();
    }

    // ── afterCommitWithRetry — Issue 2 ───────────────────────────────────────

    /**
     * Verifies the happy path: action succeeds on first attempt — no retries, no failure path.
     */
    @Test
    void afterCommitWithRetry_succeedsOnFirstAttempt_noFailurePath() {
        Runnable action = mock(Runnable.class);
        doNothing().when(action).run();

        service.afterCommitWithRetry("test", 99L, 1L, 2L, action);

        verify(action, times(1)).run();
        verify(gameRepository, never()).findById(anyLong());
        verify(broadcaster, never()).sendToGame(anyLong(), eq("match-failed"), any());
    }

    /**
     * Verifies the retry path: action fails twice then succeeds on the third attempt.
     * The game must NOT be marked FAILED, and players must NOT be notified.
     */
    @Test
    void afterCommitWithRetry_succeeds_onThirdAttempt_noFailurePath() {
        Runnable action = mock(Runnable.class);
        // Fail twice, succeed on third
        doThrow(new RuntimeException("Redis unavailable"))
                .doThrow(new RuntimeException("Redis unavailable"))
                .doNothing()
                .when(action).run();

        service.afterCommitWithRetry("test", 99L, 1L, 2L, action);

        verify(action, times(3)).run();
        verify(broadcaster, never()).sendToGame(anyLong(), eq("match-failed"), any());
    }

    /**
     * Verifies the exhaustion path: action fails on all 3 attempts.
     * <ul>
     *   <li>The game should be fetched and transitioned to FAILED.</li>
     *   <li>{@code match-failed} should be broadcast to both players.</li>
     * </ul>
     */
    @Test
    void afterCommitWithRetry_allAttemptsFail_marksGameFailedAndNotifiesPlayers() {
        // Arrange: action always throws
        Runnable action = mock(Runnable.class);
        doThrow(new RuntimeException("Redis is down")).when(action).run();

        // findById returns an ACTIVE game
        Game activeGame = new Game();
        activeGame.setId(99L);
        activeGame.setStatus(GameStatus.ACTIVE);
        activeGame.setCreator(creator);
        activeGame.setOpponent(opponent);
        when(gameRepository.findById(99L)).thenReturn(Optional.of(activeGame));
        when(gameRepository.save(any(Game.class))).thenReturn(activeGame);

        // Act
        service.afterCommitWithRetry("test", 99L, 1L, 2L, action);

        // Assert: action attempted exactly 3 times
        verify(action, times(3)).run();

        // Assert: game saved with FAILED status
        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());
        assertThat(savedGame.getValue().getStatus()).isEqualTo(GameStatus.FAILED);

        // Assert: match-failed broadcast fired
        verify(broadcaster).sendToGame(eq(99L), eq("match-failed"), any(Map.class));
    }

    /**
     * Verifies markGameFailed is idempotent: if the game is already in a terminal
     * status (not ACTIVE), it should not be overwritten.
     */
    @Test
    void markGameFailed_isIdempotent_doesNotOverwriteTerminalStatus() {
        Game finishedGame = new Game();
        finishedGame.setId(88L);
        finishedGame.setStatus(GameStatus.FINISHED);  // already terminal
        when(gameRepository.findById(88L)).thenReturn(Optional.of(finishedGame));

        service.markGameFailed(88L, 1L, 2L, "test");

        // save should NOT be called because status is not ACTIVE
        verify(gameRepository, never()).save(any());
        // broadcast is still sent regardless (players may still be waiting)
        verify(broadcaster).sendToGame(eq(88L), eq("match-failed"), any());
    }

    // ── sweepOrphanedActiveGames — Issue 2 ───────────────────────────────────

    /**
     * Verifies that the sweep skips games that have a valid Redis room.
     */
    @Test
    void sweep_skipsGamesWithExistingRedisRoom() {
        Game game = buildOldActiveGame(77L);
        when(gameRepository.findByStatus(GameStatus.ACTIVE)).thenReturn(List.of(game));
        when(roomManager.hasRoom(77L)).thenReturn(true);  // room exists — healthy

        service.sweepOrphanedActiveGames();

        verify(broadcaster, never()).sendToGame(anyLong(), eq("match-failed"), any());
        verify(gameRepository, never()).save(any());
    }

    /**
     * Verifies that the sweep skips games younger than the age threshold.
     */
    @Test
    void sweep_skipsGamesThatAreTooYoung() {
        Game youngGame = new Game();
        youngGame.setId(66L);
        youngGame.setStatus(GameStatus.ACTIVE);
        youngGame.setCreator(creator);
        youngGame.setOpponent(opponent);
        // Started just 30 seconds ago — below the 2-minute threshold
        youngGame.setStartTime(LocalDateTime.now().minusSeconds(30));

        when(gameRepository.findByStatus(GameStatus.ACTIVE)).thenReturn(List.of(youngGame));
        when(roomManager.hasRoom(66L)).thenReturn(false);

        service.sweepOrphanedActiveGames();

        verify(broadcaster, never()).sendToGame(anyLong(), eq("match-failed"), any());
    }

    /**
     * Verifies the full sweep path: an old ACTIVE game with no Redis room
     * is fetched, marked FAILED, and players are notified.
     */
    @Test
    void sweep_marksOrphanedOldActiveGames_asFailed() {
        Game orphan = buildOldActiveGame(55L);
        when(gameRepository.findByStatus(GameStatus.ACTIVE)).thenReturn(List.of(orphan));
        when(roomManager.hasRoom(55L)).thenReturn(false);   // no room — orphaned
        when(gameRepository.findById(55L)).thenReturn(Optional.of(orphan));
        when(gameRepository.save(any())).thenReturn(orphan);

        service.sweepOrphanedActiveGames();

        ArgumentCaptor<Game> saved = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(GameStatus.FAILED);
        verify(broadcaster).sendToGame(eq(55L), eq("match-failed"), any(Map.class));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Build an ACTIVE game that is old enough to be swept (started 10 min ago). */
    private Game buildOldActiveGame(long id) {
        Game g = new Game();
        g.setId(id);
        g.setStatus(GameStatus.ACTIVE);
        g.setCreator(creator);
        g.setOpponent(opponent);
        g.setStartTime(LocalDateTime.now().minusMinutes(10));
        g.setCreatedAt(Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)));
        return g;
    }
}
