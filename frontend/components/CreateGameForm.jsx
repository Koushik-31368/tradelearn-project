// src/components/CreateGameForm.jsx
import React, { useState } from 'react';
import './CreateGameForm.css';

const CreateGameForm = ({ onCreate, onCancel }) => {
  const [stockSymbol, setStockSymbol] = useState('');
  const [duration, setDuration] = useState(2);
  const [error, setError] = useState('');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    try {
      // For now, we'll assume the user creating the game has an ID of 1.
      // Later, we'll get this from our authentication context.
      const creatorId = 1; 

      const response = await fetch('http://localhost:8080/api/games', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stockSymbol: stockSymbol,
          durationMinutes: duration,
          creatorId: creatorId
        })
      });

      if (!response.ok) {
        throw new Error('Failed to create game. Please try again.');
      }

      // If successful, call the onCreate function passed from the lobby
      onCreate();
      
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <form className="create-game-form" onSubmit={handleSubmit}>
      <h2>Create New Game</h2>
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
      {error && <p className="form-error">{error}</p>}
      <div className="form-actions">
        <button type="button" className="btn-cancel" onClick={onCancel}>Cancel</button>
        <button type="submit" className="btn-create">Create Game</button>
      </div>
    </form>
  );
};

export default CreateGameForm;