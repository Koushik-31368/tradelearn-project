// src/pages/LobbyPage.jsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import './LobbyPage.css';
import Modal from '../components/Modal';
import CreateGameForm from '../components/CreateGameForm';
import { useAuth } from '../context/AuthContext';
import { backendUrl, wsBase, getToken, authHeaders } from '../utils/api';

const LobbyPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [openGames, setOpenGames] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isSearching, setIsSearching] = useState(false);
  const [searchTime, setSearchTime] = useState(0);
  const navigate = useNavigate();
  const { user } = useAuth();
  const stompClientRef = useRef(null);
  const searchTimerRef = useRef(null);

  // ── Fetch open games (for Custom Game section) ──
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

  // ── WebSocket subscriptions for matchmaking events ──
  const connectMatchmaking = useCallback(() => {
    if (!user) return;

    const base = wsBase();
    const socketUrl = base ? `${base}/ws` : '/ws';

    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: { Authorization: `Bearer ${getToken()}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        // Subscribe to personal match-found channel (for queued matches)
        client.subscribe(`/topic/user/${user.id}/match-found`, (message) => {
          const data = JSON.parse(message.body);
          console.log('[Matchmaking] Match found via WS!', data);
          
          setIsSearching(false);
          if (searchTimerRef.current) {
            clearInterval(searchTimerRef.current);
            searchTimerRef.current = null;
          }

          if (data.gameId) {
            navigate(`/game/${data.gameId}`);
          }
        });

        // Subscribe to match-expired channel (2-minute timeout)
        client.subscribe(`/topic/user/${user.id}/match-expired`, (message) => {
          const data = JSON.parse(message.body);
          console.log('[Matchmaking] Search expired', data);

          setIsSearching(false);
          if (searchTimerRef.current) {
            clearInterval(searchTimerRef.current);
            searchTimerRef.current = null;
          }
          setSearchTime(0);
          setError(data.message || 'Search timed out. Please try again.');
        });
      },
      onStompError: (frame) => {
        console.error('[Matchmaking] STOMP error:', frame.headers?.message);
      },
    });

    client.activate();
    stompClientRef.current = client;
  }, [user, navigate]);

  // Connect WS when user is available
  useEffect(() => {
    if (user) {
      connectMatchmaking();
    }
    return () => {
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, [user, connectMatchmaking]);

  // ── Find Ranked Match ──
  const handleFindMatch = async () => {
    if (!user) {
      setError('Please log in to find a ranked match.');
      return;
    }

    try {
      const response = await fetch(backendUrl('/api/matchmaking/queue'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => null);
        throw new Error(errorData?.error || 'Failed to join matchmaking queue.');
      }

      const data = await response.json();

      // ── Instant match: navigate directly (no searching animation) ──
      if (data.status === 'MATCHED' && data.gameId) {
        console.log('[Matchmaking] Instant match!', data);
        navigate(`/game/${data.gameId}`);
        return;
      }

      // ── Queued: enter searching state, wait for WS notification ──
      setIsSearching(true);
      setSearchTime(0);
      setError(null);

      searchTimerRef.current = setInterval(() => {
        setSearchTime((prev) => prev + 1);
      }, 1000);

    } catch (err) {
      setError(err.message);
      console.error(err);
    }
  };

  // ── Cancel Search ──
  const handleCancelSearch = async () => {
    try {
      await fetch(backendUrl('/api/matchmaking/queue'), {
        method: 'DELETE',
        headers: { ...authHeaders() },
      });
    } catch (err) {
      console.error('Failed to cancel search:', err);
    }

    setIsSearching(false);
    if (searchTimerRef.current) {
      clearInterval(searchTimerRef.current);
      searchTimerRef.current = null;
    }
    setSearchTime(0);
  };

  // Cleanup timer on unmount
  useEffect(() => {
    return () => {
      if (searchTimerRef.current) {
        clearInterval(searchTimerRef.current);
      }
    };
  }, []);

  // ── Join existing game ──
  const handleJoinGame = async (gameId) => {
    if (!user) {
      setError("Please log in to join a game.");
      return;
    }

    try {
      const response = await fetch(backendUrl(`/api/match/${gameId}/join`), {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          ...authHeaders()
        }
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

  // ── Format search time ──
  const formatSearchTime = (seconds) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return m > 0 ? `${m}:${s.toString().padStart(2, '0')}` : `0:${s.toString().padStart(2, '0')}`;
  };

  // ── Search phase label ──
  const getSearchPhase = (seconds) => {
    if (seconds < 20) return 'Searching nearby ratings (±100)...';
    if (seconds < 40) return 'Expanding search (±200)...';
    return 'Searching all players...';
  };

  return (
    <div className="lobby-container">
      {/* ── Ranked Matchmaking Section ── */}
      <div className="ranked-section">
        <div className="ranked-header">
          <h1>Ranked Match</h1>
          {user && (
            <div className="player-rating">
              <span className="rating-label">Your Rating</span>
              <span className="rating-value">{user.rating || 1000}</span>
            </div>
          )}
        </div>

        {isSearching ? (
          <div className="searching-container">
            <div className="searching-animation">
              <div className="pulse-ring"></div>
              <div className="pulse-ring delay-1"></div>
              <div className="pulse-ring delay-2"></div>
              <div className="searching-icon">⚔️</div>
            </div>
            <p className="searching-text">{getSearchPhase(searchTime)}</p>
            <p className="search-timer">{formatSearchTime(searchTime)}</p>
            <button className="cancel-search-btn" onClick={handleCancelSearch}>
              Cancel Search
            </button>
          </div>
        ) : (
          <div className="ranked-action">
            <p className="ranked-description">
              Get matched with an opponent of similar skill. Matches are 5 minutes with ₹10,00,000 starting balance and a random stock.
            </p>
            <button
              className="find-match-btn"
              onClick={handleFindMatch}
              disabled={!user}
            >
              Find Ranked Match
            </button>
            {!user && (
              <p className="login-prompt">Log in to play ranked matches</p>
            )}
          </div>
        )}
      </div>

      {/* ── Custom Games Section ── */}
      <div className="custom-section">
        <div className="lobby-header">
          <h2>Custom Games</h2>
          <button className="create-game-btn" onClick={() => setIsModalOpen(true)}>
            + Create Game
          </button>
        </div>
        <div className="lobby-section">
          <h3 className="section-subtitle">Open Games (Waiting for Opponent)</h3>
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
                  {user && user.id === game.creator.id ? (
                    <button className="join-btn" onClick={() => navigate(`/game/${game.id}`)}>Enter Game</button>
                  ) : (
                    <button className="join-btn" onClick={() => handleJoinGame(game.id)}>Join Game</button>
                  )}
                </div>
              ))
            ) : (
              <p className="no-games-message">No open games available. Create one or find a ranked match!</p>
            )}
          </div>
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