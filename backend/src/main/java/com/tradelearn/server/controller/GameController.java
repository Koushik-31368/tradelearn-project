package com.tradelearn.server.controller;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tradelearn.server.dto.CreateGameRequest;
import com.tradelearn.server.dto.JoinGameRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.UserRepository;
import com.tradelearn.server.service.MatchService;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private static final Logger log = LoggerFactory.getLogger(GameController.class);

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final MatchService matchService;

    public GameController(GameRepository gameRepository,
                          UserRepository userRepository,
                          MatchService matchService) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
        this.matchService = matchService;
    }

    @PostMapping
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest request) {

        Optional<User> creator =
                userRepository.findById(Objects.requireNonNull(request.getCreatorId()));

        if (creator.isEmpty()) {
            return ResponseEntity.badRequest().body("Creator not found");
        }

        Game game = new Game();
        game.setCreator(creator.get());
        game.setStockSymbol(request.getStockSymbol());
        game.setDurationMinutes(request.getDurationMinutes());
        game.setStatus("WAITING");

        return ResponseEntity.ok(gameRepository.save(game));
    }

    @GetMapping("/open")
    public ResponseEntity<List<Game>> getOpenGames() {
        return ResponseEntity.ok(
                gameRepository.findByStatus("WAITING")
        );
    }

    /**
     * POST /api/games/{gameId}/join
     *
     * LEGACY endpoint — delegates to MatchService.joinMatch() for safe atomic
     * CAS join with Redis room sync and auto-start. The old read-modify-write
     * pattern had a TOCTOU race: two players could read status=WAITING at the
     * same time and both set their opponent, with the last save() silently
     * overwriting the first.
     */
    @PostMapping("/{gameId}/join")
    public ResponseEntity<?> joinGame(
            @PathVariable long gameId,
            @RequestBody JoinGameRequest request
    ) {
        try {
            Long opponentId = Objects.requireNonNull(request.getOpponentId(), "opponentId is required");
            Game game = matchService.joinMatch(gameId, opponentId);
            return ResponseEntity.ok(game);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("[Legacy Join] Game {} join failed: {}", gameId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}