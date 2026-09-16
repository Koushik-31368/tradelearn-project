package com.tradelearn.server.social.controller;

import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.GameStatus;
import com.tradelearn.server.game.service.MatchLifecycleService;
import com.tradelearn.server.social.model.GameChallenge;
import com.tradelearn.server.social.repository.GameChallengeRepository;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.quests.service.QuestService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ChallengeWebSocketController}.
 *
 * Verifies that an accepted friend-challenge delegates to
 * MatchLifecycleService.createAutoMatch and produces a game with:
 *   - starting balance Rs.10,00,000 (not 1,00,000)
 *   - stock symbol from RANKED_SYMBOLS (not hard-coded AAPL)
 *   - null creatorFinalBalance / opponentFinalBalance at creation time
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("null")
class ChallengeWebSocketControllerTest {

    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private UserRepository userRepository;
    @Mock private GameChallengeRepository challengeRepository;
    @Mock private MatchLifecycleService matchLifecycleService;
    @Mock private QuestService questService;

    @InjectMocks
    private ChallengeWebSocketController controller;

    private User challenger;
    private User challenged;

    @BeforeEach
    void setUp() {
        challenger = new User();
        challenger.setId(1L);
        challenger.setUsername("challenger");

        challenged = new User();
        challenged.setId(2L);
        challenged.setUsername("challenged");
    }

    /** Accepted challenge: delegates to createAutoMatch, checks balance, symbol, null finals. */
    @Test
    void respondChallenge_accepted_delegatesToCreateAutoMatch() {
        GameChallenge pendingChallenge = new GameChallenge(challenger, challenged);
        setId(pendingChallenge, 10L);
        when(challengeRepository.findById(10L)).thenReturn(Optional.of(pendingChallenge));

        String nseSymbol = MatchLifecycleService.RANKED_SYMBOLS[0]; // e.g. "RELIANCE"
        Game createdGame = new Game();
        setGameId(createdGame, 99L);
        createdGame.setCreator(challenger);
        createdGame.setOpponent(challenged);
        createdGame.setStatus(GameStatus.ACTIVE);
        createdGame.setStockSymbol(nseSymbol);
        createdGame.setStartingBalance(1_000_000.0);
        // creatorFinalBalance / opponentFinalBalance intentionally left null

        when(matchLifecycleService.createAutoMatch(1L, 2L)).thenReturn(createdGame);

        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        Principal principal = () -> "challenged";
        when(headerAccessor.getUser()).thenReturn(principal);

        Map<String, Object> payload = Map.of("challengeId", 10, "accepted", true);

        TransactionSynchronizationManager.initSynchronization();
        try {
            controller.respondChallenge(payload, headerAccessor);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(matchLifecycleService).createAutoMatch(1L, 2L);

        assertThat(createdGame.getStartingBalance())
                .as("starting balance must be Rs.10,00,000")
                .isEqualTo(1_000_000.0);

        Set<String> validSymbols = Set.of(MatchLifecycleService.RANKED_SYMBOLS);
        assertThat(createdGame.getStockSymbol())
                .as("symbol must be from NSE RANKED_SYMBOLS, not AAPL")
                .isIn(validSymbols)
                .isNotEqualTo("AAPL");

        assertThat(createdGame.getCreatorFinalBalance())
                .as("creatorFinalBalance must be null at creation")
                .isNull();
        assertThat(createdGame.getOpponentFinalBalance())
                .as("opponentFinalBalance must be null at creation")
                .isNull();

        // Both players notified
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate, times(2))
                .convertAndSendToUser(anyString(), eq("/queue/challenges"), captor.capture());
        captor.getAllValues().forEach(m -> {
            assertThat(m).containsEntry("type", "CHALLENGE_ACCEPTED");
            assertThat(m).containsEntry("gameId", 99L);
        });
    }

    /** Declined challenge does NOT create a game. */
    @Test
    void respondChallenge_declined_doesNotCreateGame() {
        GameChallenge pendingChallenge = new GameChallenge(challenger, challenged);
        setId(pendingChallenge, 11L);
        when(challengeRepository.findById(11L)).thenReturn(Optional.of(pendingChallenge));

        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        when(headerAccessor.getUser()).thenReturn(() -> "challenged");

        controller.respondChallenge(Map.of("challengeId", 11, "accepted", false), headerAccessor);

        verify(matchLifecycleService, never()).createAutoMatch(anyLong(), anyLong());
        verify(messagingTemplate).convertAndSendToUser(
                eq("challenger"), eq("/queue/challenges"),
                argThat(m -> "CHALLENGE_DECLINED".equals(((Map<?, ?>) m).get("type"))));
    }

    /** A user who is not the challenged party cannot accept the challenge. */
    @Test
    void respondChallenge_wrongResponder_isIgnored() {
        GameChallenge pendingChallenge = new GameChallenge(challenger, challenged);
        setId(pendingChallenge, 12L);
        when(challengeRepository.findById(12L)).thenReturn(Optional.of(pendingChallenge));

        SimpMessageHeaderAccessor headerAccessor = mock(SimpMessageHeaderAccessor.class);
        when(headerAccessor.getUser()).thenReturn(() -> "intruder");

        controller.respondChallenge(Map.of("challengeId", 12, "accepted", true), headerAccessor);

        verify(matchLifecycleService, never()).createAutoMatch(anyLong(), anyLong());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    private static void setId(GameChallenge challenge, long id) {
        try {
            var field = GameChallenge.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(challenge, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set GameChallenge.id", e);
        }
    }

    private static void setGameId(Game game, long id) {
        try {
            var field = Game.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(game, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set Game.id", e);
        }
    }
}
