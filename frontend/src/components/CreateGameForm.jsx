// src/components/CreateGameForm.jsx
import React, { useState } from 'react';
import './CreateGameForm.css';
import { useAuth } from '../context/AuthContext'; // 1. Import useAuth

const CreateGameForm = ({ onCreate, onCancel }) => {
  const [stockSymbol, setStockSymbol] = useState('');
  const [duration, setDuration] = useState(2);
  const [error, setError] = useState('');
  const { user } = useAuth(); // 2. Get the logged-in user's data

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    // 3. Check if user is logged in
    if (!user) {
      setError("You must be logged in to create a game.");
      return;
    }

    try {
      const response = await fetch('http://localhost:8080/api/games', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          stockSymbol: stockSymbol,
          durationMinutes: duration,
          creatorId: user.id // 4. Use the real user ID from context
        })
      });

      if (!response.ok) {
        throw new Error('Failed to create game. Please try again.');
      }
      onCreate();
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