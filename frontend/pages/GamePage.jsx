// src/pages/GamePage.jsx
import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import StockChart from '../components/StockChart';
import './GamePage.css';

// Sample historical data for the chart (OHLC format)
const sampleChartData = [
    { time: '2023-01-02', open: 100, high: 105, low: 98, close: 102 },
    { time: '2023-01-03', open: 102, high: 110, low: 101, close: 108 },
    { time: '2023-01-04', open: 108, high: 109, low: 103, close: 105 },
    { time: '2023-01-05', open: 105, high: 107, low: 100, close: 101 },
    { time: '2023-01-06', open: 101, high: 112, low: 100, close: 110 },
];

const GamePage = () => {
  const { gameId } = useParams();

  // We'll use fake "demo" state for the game for now
  const [gameState, setGameState] = useState({
    player1: { name: 'Koushik', cash: 95000, shares: 10, portfolioValue: 106000 },
    player2: { name: 'Opponent', cash: 100000, shares: 0, portfolioValue: 100000 },
    currentRound: 5,
    totalRounds: 10,
    currentDate: 'Jan 06, 2023',
    timeLeft: 120, // 2 minutes in seconds
  });
  
  return (
    <div className="game-page-grid">
      <header className="game-header">
        <h2>Round {gameState.currentRound}/{gameState.totalRounds} ({gameState.currentDate})</h2>
        <div className="game-timer">Time Left: {Math.floor(gameState.timeLeft / 60)}:{(gameState.timeLeft % 60).toString().padStart(2, '0')}</div>
      </header>

      <aside className="player-dashboard player1">
        <h3>You ({gameState.player1.name})</h3>
        <p>Portfolio Value: <strong>₹{gameState.player1.portfolioValue.toLocaleString()}</strong></p>
        <p>Cash: ₹{gameState.player1.cash.toLocaleString()}</p>
        <p>Shares: {gameState.player1.shares}</p>
      </aside>

      <main className="chart-container">
        <StockChart data={sampleChartData} />
      </main>

      <aside className="player-dashboard player2">
        <h3>Opponent ({gameState.player2.name})</h3>
        <p>Portfolio Value: <strong>₹{gameState.player2.portfolioValue.toLocaleString()}</strong></p>
        <p>Cash: ₹{gameState.player2.cash.toLocaleString()}</p>
        <p>Shares: {gameState.player2.shares}</p>
      </aside>

      <footer className="trade-panel">
        <div className="trade-input-group">
            <label>Shares</label>
            <input type="number" defaultValue="1" min="1" />
        </div>
        <button className="trade-btn buy">Buy</button>
        <button className="trade-btn sell">Sell</button>
        <button className="trade-btn ready">Ready for Next Day</button>
      </footer>
    </div>
  );
};

export default GamePage;