package com.tradelearn.server.social.controller;

import com.tradelearn.server.game.model.Game;
import com.tradelearn.server.game.model.GameStatus;
import com.tradelearn.server.social.model.GameChallenge;
import com.tradelearn.server.user.model.User;
import com.tradelearn.server.social.repository.GameChallengeRepository;
import com.tradelearn.server.game.repository.GameRepository;
import com.tradelearn.server.user.repository.UserRepository;
import com.tradelearn.server.quests.service.QuestService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Controller
public class ChallengeWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final GameChallengeRepository challengeRepository;
    private final GameRepository gameRepository;
    private final QuestService questService;

    public ChallengeWebSocketController(SimpMessagingTemplate messagingTemplate,
                                        UserRepository userRepository,
                                        GameChallengeRepository challengeRepository,
                                        GameRepository gameRepository,
                                        @org.springframework.context.annotation.Lazy QuestService questService) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.gameRepository = gameRepository;
        this.questService = questService;
    }

    @MessageMapping("/challenge.send")
    @Transactional
    @SuppressWarnings("null")
    public void sendChallenge(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() == null) return;
        String challengerUsername = headerAccessor.getUser().getName();
        String challengedUsername = (String) payload.get("challengedUsername");

        User challenger = userRepository.findByUsername(challengerUsername).orElse(null);
        User challenged = userRepository.findByUsername(challengedUsername).orElse(null);

        if (challenger == null || challenged == null) return;

        GameChallenge challenge = new GameChallenge(challenger, challenged);
        challengeRepository.save(challenge);

        // Send to challenged user's queue
        messagingTemplate.convertAndSendToUser(
            challengedUsername,
            "/queue/challenges",
            Map.of(
                "type", "CHALLENGE_RECEIVED",
                "challengeId", challenge.getId(),
                "challengerId", challenger.getId(),
                "challengerUsername", challenger.getUsername()
            )
        );

        // Trigger CHALLENGE_FRIEND quest
        try {
            questService.updateQuestProgress(challenger.getId(), "CHALLENGE_FRIEND", 1);
        } catch (Exception e) {
            // Ignore
        }
    }

    @MessageMapping("/challenge.respond")
    @Transactional
    @SuppressWarnings("null")
    public void respondChallenge(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() == null) return;
        String responderUsername = headerAccessor.getUser().getName();
        Long challengeId = Long.valueOf(payload.get("challengeId").toString());
        boolean accepted = (Boolean) payload.get("accepted");

        GameChallenge challenge = challengeRepository.findById(challengeId).orElse(null);
        if (challenge == null || !challenge.getChallenged().getUsername().equals(responderUsername)) return;

        if (!accepted) {
            challenge.setStatus("DECLINED");
            challengeRepository.save(challenge);
            messagingTemplate.convertAndSendToUser(
                challenge.getChallenger().getUsername(),
                "/queue/challenges",
                Map.of("type", "CHALLENGE_DECLINED", "challengeId", challengeId)
            );
            return;
        }

        challenge.setStatus("ACCEPTED");
        challengeRepository.save(challenge);

        // Create a Game Room immediately
        Game game = new Game();
        game.setCreator(challenge.getChallenger());
        game.setOpponent(challenge.getChallenged());
        game.setStatus(GameStatus.ACTIVE);
        // Simplified config for direct challenges
        game.setStockSymbol("AAPL");
        game.setStartingBalance(100000.0);
        game.setCreatorFinalBalance(100000.0);
        game.setOpponentFinalBalance(100000.0);
        gameRepository.save(game);

        // Notify BOTH players to join the room
        Map<String, Object> joinPayload = Map.of(
            "type", "CHALLENGE_ACCEPTED",
            "gameId", game.getId()
        );

        messagingTemplate.convertAndSendToUser(challenge.getChallenger().getUsername(), "/queue/challenges", joinPayload);
        messagingTemplate.convertAndSendToUser(challenge.getChallenged().getUsername(), "/queue/challenges", joinPayload);
    }
}
