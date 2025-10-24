// src/pages/LobbyPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './LobbyPage.css';
import Modal from '../components/Modal';
import CreateGameForm from '../components/CreateGameForm';
import { useAuth } from '../context/AuthContext'; // 1. Import useAuth

const LobbyPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openGames, setOpenGames] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();
  const { user } = useAuth(); // 2. Get the logged-in user's data

  const fetchOpenGames = async () => {
    // ... (this function remains the same) ...
    setIsLoading(true);
    try {
      const response = await fetch('http://localhost:8080/api/games/open');
      if (!response.ok) {
        throw new Error('Could not fetch open games.');
      }
      const data = await response.json();
      setOpenGames(data);
    } catch (error) {
      console.error(error);
    }
    setIsLoading(false);
  };

  useEffect(() => {
    fetchOpenGames();
  }, []);

  const handleJoinGame = async (gameId) => {
    // 3. Check if user is logged in
    if (!user) {
      alert("Please log in to join a game.");
      return;
    }

    try {
      const response = await fetch(`http://localhost:8080/api/games/${gameId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ opponentId: user.id }) // 4. Use the real user ID
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || 'Failed to join game.');
      }
      navigate(`/game/${gameId}`);
    } catch (error) {
      alert(error.message);
      console.error(error);
    }
  };

  return (
    <div className="lobby-container">
      <div className="lobby-header">
        <h1>Game Lobby</h1>
        <button className="create-game-btn" onClick={() => setIsModalOpen(true)}>
          + Create New Game
        </button>
      </div>
      <div className="lobby-section">
        <h2>Open Games (Waiting for Opponent)</h2>
        <div className="game-list">
          {/* ... (rest of the render logic remains the same) ... */}
          {isLoading ? (
            <p>Loading games...</p>
          ) : openGames.length > 0 ? (
            openGames.map(game => (
              <div key={game.id} className="game-card">
                <div className="game-details">
                  <p><strong>Stock:</strong> {game.stockSymbol}</p>
                  <p><strong>Created by:</strong> {game.creator.username}</p>
                  <p><strong>Duration:</strong> {game.durationMinutes} minutes</p>
                </div>
                {/* 5. Prevent user from joining their own game */}
                {user && user.id === game.creator.id ? (
                  <button className="join-btn" disabled>Your Game</button>
                ) : (
                  <button className="join-btn" onClick={() => handleJoinGame(game.id)}>Join Game</button>
                )}
              </div>
            ))
          ) : (
            <p className="no-games-message">No open games available. Why not create one?</p>
          )}
        </div>
      </div>
      <Modal isOpen={isModalOpen} onClose={() => setIsModalOpen(false)}>
        <CreateGameForm 
          onCancel={() => setIsModalOpen(false)}
          onCreate={() => {
            setIsModalOpen(false);
            fetchOpenGames();
          }}
        />
      </Modal>
    </div>
  );
};

export default LobbyPage;