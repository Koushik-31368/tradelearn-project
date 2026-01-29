package com.tradelearn.server.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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

@RestController
@RequestMapping("/api/games")
@CrossOrigin(
    origins = {
        "http://localhost:3000",
        "https://tradelearn-project.vercel.app",
        "https://tradelearn-project-kethans-projects-3fb29448.vercel.app"
    },
    allowCredentials = "true"
)
public class GameController {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    public GameController(GameRepository gameRepository,
                          UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest request) {

        Optional<User> creator =
                userRepository.findById(request.getCreatorId());

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

    @PostMapping("/{gameId}/join")
    public ResponseEntity<?> joinGame(
            @PathVariable long gameId,
            @RequestBody JoinGameRequest request
    ) {
        Optional<Game> gameOpt = gameRepository.findById(gameId);
        if (gameOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("Game not found");
        }

        Optional<User> opponent =
                userRepository.findById(request.getOpponentId());

        if (opponent.isEmpty()) {
            return ResponseEntity.badRequest().body("Opponent not found");
        }

        Game game = gameOpt.get();

        if (!"WAITING".equals(game.getStatus())) {
            return ResponseEntity.badRequest().body("Game not joinable");
        }

        if (game.getCreator().getId().equals(opponent.get().getId())) {
            return ResponseEntity.badRequest().body("Cannot join own game");
        }

        game.setOpponent(opponent.get());
        game.setStatus("ACTIVE");

        return ResponseEntity.ok(gameRepository.save(game));
    }
}