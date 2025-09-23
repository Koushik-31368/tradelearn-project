// src/main/java/com/tradelearn/server/controller/GameController.java
package com.tradelearn.server.controller;

import com.tradelearn.server.dto.CreateGameRequest;
import com.tradelearn.server.dto.JoinGameRequest;
import com.tradelearn.server.model.Game;
import com.tradelearn.server.model.User;
import com.tradelearn.server.repository.GameRepository;
import com.tradelearn.server.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/games")
// This single line fixes the error by giving permission to your React app
public class GameController {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;

    @Autowired
    public GameController(GameRepository gameRepository, UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<?> createGame(@RequestBody CreateGameRequest request) {
        Optional<User> creatorOptional = userRepository.findById(request.getCreatorId());
        if (creatorOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Creator user not found");
        }

        Game newGame = new Game();
        newGame.setCreator(creatorOptional.get());
        newGame.setStockSymbol(request.getStockSymbol());
        newGame.setDurationMinutes(request.getDurationMinutes());
        newGame.setStatus("WAITING");

        Game savedGame = gameRepository.save(newGame);
        return ResponseEntity.ok(savedGame);
    }

    @GetMapping("/open")
    public ResponseEntity<List<Game>> getOpenGames() {
        List<Game> openGames = gameRepository.findByStatus("WAITING");
        return ResponseEntity.ok(openGames);
    }

    @PostMapping("/{gameId}/join")
    public ResponseEntity<?> joinGame(@PathVariable Long gameId, @RequestBody JoinGameRequest request) {
        Optional<Game> gameOptional = gameRepository.findById(gameId);
        if (gameOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Game not found");
        }
        Game game = gameOptional.get();

        Optional<User> opponentOptional = userRepository.findById(request.getOpponentId());
        if (opponentOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Opponent user not found");
        }
        User opponent = opponentOptional.get();

        if (!game.getStatus().equals("WAITING")) {
            return ResponseEntity.badRequest().body("Game is not open to join");
        }
        if (game.getCreator().getId().equals(opponent.getId())) {
            return ResponseEntity.badRequest().body("Cannot join your own game");
        }

        game.setOpponent(opponent);
        game.setStatus("ACTIVE");
        Game updatedGame = gameRepository.save(game);

        return ResponseEntity.ok(updatedGame);
    }
}