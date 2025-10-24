// src/pages/GamePage.jsx
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import './GamePage.css';
import { useAuth } from '../context/AuthContext';

const GamePage = () => {
    const stompClientRef = useRef(null); // Ref is correctly inside the component
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth();
    const [game, setGame] = useState(null);
    const [historicalData, setHistoricalData] = useState([]);
    
    // --- THIS IS THE MISSING LINE ---
    const [newsFeed, setNewsFeed] = useState([]); 
    // --- END FIX ---

    const [playerState, setPlayerState] = useState({ cash: 1000000, shares: 0 });
    const [opponentState, setOpponentState] = useState({ cash: 1000000, shares: 0 });
    const [currentDayIndex, setCurrentDayIndex] = useState(0);
    const [timeLeft, setTimeLeft] = useState(null);
    const [tradeAmount, setTradeAmount] = useState(1);
    const [isRoundOver, setIsRoundOver] = useState(false);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [gameOver, setGameOver] = useState(false);
    const [wsMessage, setWsMessage] = useState('');
    const [isReadyForNext, setIsReadyForNext] = useState(false);

    // --- Fetch Game Data ---
    const fetchGameData = useCallback(async () => {
        setIsLoading(true); setError(null);
        try {
          const response = await fetch(`http://localhost:8080/api/games/${gameId}`);
          if (!response.ok) { throw new Error(await response.text() || `HTTP error!`); }
          const data = await response.json();
          if (!data.game || !data.historicalData || data.historicalData.length === 0) { throw new Error("Invalid/empty game data."); }

          setGame(data.game);
          setHistoricalData(data.historicalData);
          
          if (data.newsData) {
            const parsedNews = JSON.parse(data.newsData);
            setNewsFeed(parsedNews.feed || []); // This line will now work
          }

          const durationSeconds = data.game.durationMinutes > 0 ? data.game.durationMinutes * 60 : 120;
          setTimeLeft(durationSeconds);
          setCurrentDayIndex(0);
          setIsRoundOver(false);
          setGameOver(false);
          setIsReadyForNext(false);
          setPlayerState({ cash: 1000000, shares: 0 });
          setOpponentState({ cash: 1000000, shares: 0 });
        } catch (error) { 
            console.error("Fetch game error:", error); 
            setError(`Load failed: ${error.message}`);
            setTimeout(() => navigate('/multiplayer'), 3000); 
        } finally { setIsLoading(false); }
    }, [gameId, navigate]);

    useEffect(() => { fetchGameData(); }, [fetchGameData]);

    // --- WebSocket Connection & Subscriptions ---
    useEffect(() => {
        if (!game || !user) return; 
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws-game'),
            reconnectDelay: 5000,
            debug: (str) => { console.log('STOMP: ' + str); },
            onConnect: (frame) => {
                console.log('Connected: ' + frame);
                setWsMessage('Connected!');
                client.subscribe(`/topic/game/${gameId}/nextRound`, (message) => {
                    if (message.body === 'NEXT_ROUND') {
                        setWsMessage('Next round starting...');
                        setCurrentDayIndex(prevIndex => {
                            if (prevIndex < historicalData.length - 1) { return prevIndex + 1; }
                            else { setGameOver(true); setIsRoundOver(true); alert("Game Over!"); return prevIndex; }
                        });
                        const durationSeconds = game?.durationMinutes > 0 ? game.durationMinutes * 60 : 120;
                        setTimeLeft(durationSeconds);
                        setIsRoundOver(false);
                        setIsReadyForNext(false);
                    }
                });
                client.subscribe(`/topic/game/${gameId}/state`, (message) => {
                    const gameState = JSON.parse(message.body);
                    console.log('Received game state update: ', gameState);
                    setWsMessage('Game state updated!');
                    if (user.id === game.creator.id) {
                        setPlayerState(gameState.player1);
                        setOpponentState(gameState.player2);
                    } else {
                        setPlayerState(gameState.player2);
                        setOpponentState(gameState.player1);
                    }
                });
            },
            onStompError: (frame) => { setWsMessage('WS Error!'); console.error('Stomp Error:', frame);},
            onWebSocketError: (error) => { setWsMessage('WS Failed!'); console.error('WS Error:', error);},
            onDisconnect: () => { setWsMessage('Disconnected.'); console.log('Disconnected!');}
        });
        client.activate();
        stompClientRef.current = client;
        return () => { if (stompClientRef.current) stompClientRef.current.deactivate(); };
    }, [gameId, game, user, historicalData.length]);

    // --- Game Timer Logic ---
    useEffect(() => { 
        if (isLoading || isRoundOver || gameOver || timeLeft === null || timeLeft <= 0) { if (timeLeft !== null && timeLeft <= 0 && !isRoundOver && !gameOver) setIsRoundOver(true); return; }
        const timerInterval = setInterval(() => { setTimeLeft(prevTime => { if (prevTime <= 1) { clearInterval(timerInterval); setIsRoundOver(true); return 0; } return prevTime - 1; }); }, 1000);
        return () => clearInterval(timerInterval);
    }, [timeLeft, isLoading, isRoundOver, gameOver]);

    // --- Trading Logic ---
    const handleTrade = (type) => {
        if (isRoundOver || gameOver || !tradeAmount || tradeAmount <= 0 || !stompClientRef.current?.connected || !user) return;
        if (currentDayIndex >= historicalData.length) return;
        const currentPrice = historicalData[currentDayIndex].close;
        const cost = tradeAmount * currentPrice;
        if (type === 'buy' && playerState.cash < cost) { alert("Not enough cash!"); return; }
        if (type === 'sell' && playerState.shares < tradeAmount) { alert("Not enough shares!"); return; }
        const tradeAction = { type: type.toUpperCase(), amount: tradeAmount, price: currentPrice, playerId: user.id };
        stompClientRef.current.publish({ destination: `/app/game/${gameId}/trade`, body: JSON.stringify(tradeAction) });
        setWsMessage(`Sent ${type} order...`);
    };

    // --- Handle "Ready for Next Day" ---
    const handleReadyClick = () => {
        if (!isRoundOver || gameOver || !stompClientRef.current?.connected) return;
        stompClientRef.current.publish({ destination: `/app/game/${gameId}/ready`, body: '' });
        setIsReadyForNext(true); setWsMessage('Waiting for opponent...');
    };

    // --- Portfolio Calculation ---
    const getPortfolioValue = (pState, dayIndex) => {
       if (historicalData.length === 0) return pState.cash;
        const validDayIndex = Math.min(Math.max(0, dayIndex), historicalData.length - 1);
        const currentPrice = historicalData[validDayIndex]?.close || 0;
        return pState.cash + (pState.shares * currentPrice);
    };

    // --- Render Logic ---
    if (isLoading) return <div>Loading Game Data...</div>;
    if (error) return <div style={{ color: 'red', padding: '20px' }}>{error}</div>;
    if (!user) return <div>Please log in to play.</div>
    if (!game || historicalData.length === 0) return <div>Waiting for game data...</div>;
    if (currentDayIndex >= historicalData.length) return <div>Error: Game state invalid (day index).</div>;
    
    const currentDayData = historicalData[currentDayIndex];
    if (!currentDayData) return <div>Error rendering current day data.</div>;

    const playerPortfolioValue = getPortfolioValue(playerState, currentDayIndex);
    const opponentPortfolioValue = getPortfolioValue(opponentState, currentDayIndex);
    const creatorName = game.creator?.username || 'Player 1';
    const opponentName = game.opponent?.username || 'Player 2';
    const isUserCreator = user.id === game.creator?.id;

    // Filter news for the current day
    const relevantNews = newsFeed.filter(item => {
        const newsDate = item.time_published.substring(0, 8); // "YYYYMMDD"
        const gameDate = currentDayData.time.replace(/-/g, ""); // "YYYYMMDD"
        return newsDate === gameDate;
    });

    return (
        <div className="game-page-grid">
             <header className="game-header">
                <h2>Round {currentDayIndex + 1}/{historicalData.length} ({currentDayData.time})</h2>
                <div className="ws-status">{wsMessage}</div>
                <div className={`game-timer ${timeLeft <= 10 ? 'low-time' : ''}`}>
                    Time Left: {Math.floor(timeLeft / 60)}:{(timeLeft % 60).toString().padStart(2, '0')}
                </div>
             </header>

            <aside className="player-dashboard player1">
                <h3>You ({isUserCreator ? creatorName : opponentName})</h3>
                <p>Portfolio Value: <strong>₹{playerPortfolioValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></p>
                <p>Cash: ₹{playerState.cash.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                <p>Shares: {playerState.shares}</p>
            </aside>

             <main className="chart-container">
                <div className="price-display">
                    <h2>Current Price for {game.stockSymbol}</h2>
                    <p className={currentDayData.close >= currentDayData.open ? 'positive' : 'negative'}>
                        ₹{currentDayData.close.toFixed(2)}
                    </p>
                    <div className="ohlc-data">
                        <span>Open: {currentDayData.open.toFixed(2)}</span>
                        <span>High: {currentDayData.high.toFixed(2)}</span>
                        <span>Low: {currentDayData.low.toFixed(2)}</span>
                        <span>Close: {currentDayData.close.toFixed(2)}</span>
                    </div>
                </div>
             </main>

            <aside className="player-dashboard player2">
                 <h3>Opponent ({isUserCreator ? opponentName : creatorName})</h3>
                <p>Portfolio Value: <strong>₹{opponentPortfolioValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></p>
                <p>Cash: ₹{opponentState.cash.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                <p>Shares: {opponentState.shares}</p>
            </aside>

            {/* --- News Feed Section --- */}
            <aside className="news-feed">
                <h3>Market News</h3>
                <div className="news-list">
                    {relevantNews.length > 0 ? (
                        relevantNews.map((item, index) => (
                            <div key={index} className="news-item">
                                <span className={`sentiment-label ${item.overall_sentiment_label.toLowerCase().replace(' ', '-')}`}>
                                    {item.overall_sentiment_label}
                                </span>
                                <a href={item.url} target="_blank" rel="noopener noreferrer">
                                    {item.title}
                                </a>
                                <p>{item.summary.substring(0, 100)}...</p>
                            </div>
                        ))
                    ) : (
                        <p>No news for this day.</p>
                    )}
                </div>
            </aside>

             <footer className="trade-panel">
                <div className="trade-input-group">
                  <label>Shares</label>
                  <input
                    type="number"
                    value={tradeAmount}
                    onChange={(e) => setTradeAmount(Math.max(1, parseInt(e.target.value, 10) || 1))}
                    min="1"
                    disabled={isRoundOver || gameOver}
                  />
                </div>
                <button className="trade-btn buy" onClick={() => handleTrade('buy')} disabled={isRoundOver || gameOver}>Buy</button>
                <button className="trade-btn sell" onClick={() => handleTrade('sell')} disabled={isRoundOver || gameOver}>Sell</button>
                <button
                  className={`trade-btn ready ${isReadyForNext ? 'waiting' : ''}`}
                  onClick={handleReadyClick}
                  disabled={!isRoundOver || gameOver || isReadyForNext}
                >
                  {currentDayIndex < historicalData.length - 1 ? 'Ready for Next Day' : 'Finish Game'}
                </button>
             </footer>
        </div>
    );
};

export default GamePage;