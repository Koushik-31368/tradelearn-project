// src/pages/LobbyPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom'; // 1. Import useNavigate
import './LobbyPage.css';
import Modal from '../components/Modal';
import CreateGameForm from '../components/CreateGameForm';

const LobbyPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openGames, setOpenGames] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate(); // 2. Initialize the navigate function

  const fetchOpenGames = async () => {
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

  // 3. This function is now updated to call the backend
  const handleJoinGame = async (gameId) => {
    try {
      // For now, we'll assume the user joining has an ID of 2.
      // Later, this will come from our AuthContext.
      const opponentId = 2; 

      const response = await fetch(`http://localhost:8080/api/games/${gameId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ opponentId: opponentId })
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || 'Failed to join game.');
      }

      // If successful, navigate to the game screen
      navigate(`/game/${gameId}`);

    } catch (error) {
      alert(error.message); // Show an alert if joining fails
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
                <button className="join-btn" onClick={() => handleJoinGame(game.id)}>Join Game</button>
              </div>
            ))
          ) : (
            <p className="no-games-message">No open games available. Why not create one?</p>
          )}
        </div>
      </div>
      
      {/* ... (Your Active Games section) ... */}

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