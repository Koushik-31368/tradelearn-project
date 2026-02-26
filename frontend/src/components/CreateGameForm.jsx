// src/components/CreateGameForm.jsx
import React, { useState } from 'react';
import './CreateGameForm.css';
import { useAuth } from '../context/AuthContext';
import { backendUrl, jsonAuthHeaders } from '../utils/api';

const CreateGameForm = ({ onCreate, onCancel }) => {
  const [stockSymbol, setStockSymbol] = useState('');
  const [duration, setDuration] = useState(2);
  const [error, setError] = useState('');
  const { user } = useAuth();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!user) {
      setError("You must be logged in to create a game.");
      return;
    }

    try {
      const response = await fetch(backendUrl('/api/match/create'), {
        method: 'POST',
        headers: jsonAuthHeaders(),
        body: JSON.stringify({
          stockSymbol: stockSymbol,
          durationMinutes: duration,
          startingBalance: 1000000
          // creatorId is no longer sent — extracted from JWT server-side
        })
      });

      if (!response.ok) {
        throw new Error('Failed to create game. Please try again.');
      }
      const game = await response.json();
      onCreate(game);
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <form className="create-game-form" onSubmit={handleSubmit}>
      <h2>Create New Game</h2>
      {/* ... (rest of the form remains the same) ... */}
      <div className="form-group">
        <label htmlFor="stock-symbol">Stock Symbol</label>
        <input
          type="text"
          id="stock-symbol"
          value={stockSymbol}
          onChange={(e) => setStockSymbol(e.target.value.toUpperCase())}
          placeholder="e.g., RELIANCE"
          required
        />
      </div>
      <div className="form-group">
        <label htmlFor="duration">Game Duration (in minutes)</label>
        <select
          id="duration"
          value={duration}
          onChange={(e) => setDuration(parseInt(e.target.value, 10))}
        >
          <option value="2">2 Minutes</option>
          <option value="5">5 Minutes</option>
          <option value="10">10 Minutes</option>
          <option value="30">30 Minutes</option>
        </select>
      </div>
      {error && <p className="form-error" style={{color: 'red', textAlign: 'center'}}>{error}</p>}
      <div className="form-actions">
        <button type="button" className="btn-cancel" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn-create">Create Game</button>
      </div>
    </form>
  );
};

export default CreateGameForm;