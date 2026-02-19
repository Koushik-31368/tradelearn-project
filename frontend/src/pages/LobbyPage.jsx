// src/pages/LobbyPage.jsx
import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './LobbyPage.css';
import Modal from '../components/Modal';
import CreateGameForm from '../components/CreateGameForm';
import { useAuth } from '../context/AuthContext'; // 1. Import useAuth
import { backendUrl } from '../utils/api';

const LobbyPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openGames, setOpenGames] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();
  const { user } = useAuth(); // 2. Get the logged-in user's data

  const fetchOpenGames = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await fetch(backendUrl('/api/match/open'));
      if (!response.ok) {
        throw new Error('Could not fetch open games.');
      }
      const data = await response.json();
      setOpenGames(data);
    } catch (err) {
      console.error(err);
      setError(err.message || 'Failed to load games. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchOpenGames();
  }, []);

  const handleJoinGame = async (gameId) => {
    // 3. Check if user is logged in
    if (!user) {
      setError("Please log in to join a game.");
      return;
    }

    try {
      const response = await fetch(backendUrl(`/api/match/${gameId}/join?userId=${user.id}`), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' }
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.error || 'Failed to join game.');
      }
      navigate(`/game/${gameId}`);
    } catch (err) {
      setError(err.message);
      console.error(err);
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
          ) : error ? (
            <p className="no-games-message" style={{ color: '#e74c3c' }}>
              {error}{' '}
              <button onClick={fetchOpenGames} style={{ cursor: 'pointer', textDecoration: 'underline', border: 'none', background: 'none', color: '#3498db' }}>Retry</button>
            </p>
          ) : openGames.length > 0 ? (
            openGames.map(game => (
              <div key={game.id} className="game-card">
                <div className="game-details">
                  <p><strong>Stock:</strong> {game.stockSymbol}</p>
                  <p><strong>Created by:</strong> {game.creator?.username}</p>
                  <p><strong>Duration:</strong> {game.durationMinutes} minutes</p>
                  <p><strong>Balance:</strong> ₹{(game.startingBalance || 1000000).toLocaleString()}</p>
                  <span className={`status-badge status-${game.status?.toLowerCase()}`}>{game.status}</span>
                </div>
                {/* Creator sees 'Enter Game', others see 'Join Game' */}
                {user && user.id === game.creator.id ? (
                  <button className="join-btn" onClick={() => navigate(`/game/${game.id}`)}>Enter Game</button>
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
          onCreate={(game) => {
            setIsModalOpen(false);
            if (game?.id) {
              navigate(`/game/${game.id}`);
            } else {
              fetchOpenGames();
            }
          }}
        />
      </Modal>
    </div>
  );
};

export default LobbyPage;