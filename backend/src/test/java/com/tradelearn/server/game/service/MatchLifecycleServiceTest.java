package com.tradelearn.server.game.service;

import com.tradelearn.server.dto.CreateMatchRequest;
import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.infrastructure.redis.room.RoomManager;
import com.tradelearn.server.infrastructure.scheduling.MatchSchedulerService;
import com.tradelearn.server.market.service.CandleService;
import com.tradelearn.server.websocket.GameBroadcaster;
import com.tradelearn.server.infrastructure.redis.store.PositionSnapshotStore;
import com.tradelearn.server.infrastructure.pipeline.GameMetricsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration-style unit tests for {@link MatchLifecycleService}.
 *
 * <p>All external dependencies (repositories, Redis, WebSocket) are mocked.
 * Tests cover the three core lifecycle paths:
 * <ul>
 *   <li>Create custom match</li>
 *   <li>Join an existing match</li>
 *   <li>Delete (host cancel) a WAITING match</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
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
        savedGame.setStatus("WAITING");
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        CreateMatchRequest req = new CreateMatchRequest();
        req.setCreatorId(1L);
        req.setStockSymbol("TCS");
        req.setDurationMinutes(5);
        req.setStartingBalance(1_000_000.0);

        // Act
        Game result = service.createMatch(req);

        // Assert
        assertThat(result.getStatus()).isEqualTo("WAITING");
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
        game.setStatus("WAITING");

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
        game.setStatus("ACTIVE");

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
        game.setStatus("WAITING");

        when(gameRepository.findById(100L)).thenReturn(Optional.of(game));

        service.deleteGame(100L, 1L);

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
        savedGame.setStatus("ACTIVE");
        savedGame.setStartingBalance(1_000_000.0);
        when(gameRepository.save(any(Game.class))).thenReturn(savedGame);

        Game result = service.createAutoMatch(1L, 2L);

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCreator().getId()).isEqualTo(1L);
        assertThat(result.getOpponent().getId()).isEqualTo(2L);
        verify(metrics).recordMatchCreated();
    }
}
