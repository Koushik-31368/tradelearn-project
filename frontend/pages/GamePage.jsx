// src/pages/GamePage.jsx
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';
import './GamePage.css';
import { useAuth } from '../context/AuthContext'; // Import useAuth to potentially get user ID

const stompClientRef = useRef(null);

const GamePage = () => {
    const { gameId } = useParams();
    const navigate = useNavigate();
    const { user } = useAuth(); // Get user info from context (assuming it stores ID later)
    const [game, setGame] = useState(null);
    const [historicalData, setHistoricalData] = useState([]);
    // Player state now managed by incoming WebSocket messages, but keep local for rendering
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
        // ... (fetchGameData remains the same) ...
        setIsLoading(true); setError(null);
        try { /* ... fetch logic ... */
          const response = await fetch(`http://localhost:8080/api/games/${gameId}`);
          if (!response.ok) { throw new Error(await response.text() || `HTTP error! status: ${response.status}`); }
          const data = await response.json();
          if (!data.game || !data.historicalData || data.historicalData.length === 0) { throw new Error("Invalid/empty game data."); }
          setGame(data.game); setHistoricalData(data.historicalData);
          const durationSeconds = data.game.durationMinutes > 0 ? data.game.durationMinutes * 60 : 120;
          setTimeLeft(durationSeconds); setCurrentDayIndex(0); setIsRoundOver(false); setGameOver(false); setIsReadyForNext(false);
          // Initialize states based on fetched game (might receive initial state via WS later)
          setPlayerState({ cash: 1000000, shares: 0 });
          setOpponentState({ cash: 1000000, shares: 0 });
        } catch (error) { console.error("Fetch game error:", error); setError(`Load failed: ${error.message}`);
        } finally { setIsLoading(false); }
    }, [gameId]);

    useEffect(() => { fetchGameData(); }, [fetchGameData]);

    // --- WebSocket Connection & Subscriptions ---
    useEffect(() => {
        const client = new Client({
            webSocketFactory: () => new SockJS('http://localhost:8080/ws-game'),
            reconnectDelay: 5000,
            debug: (str) => { console.log('STOMP: ' + str); },
            onConnect: (frame) => {
                console.log('Connected: ' + frame);
                setWsMessage('Connected!');

                // Subscribe to NEXT ROUND signal
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

                // *** NEW: Subscribe to GAME STATE updates ***
                client.subscribe(`/topic/game/${gameId}/state`, (message) => {
                    const gameState = JSON.parse(message.body);
                    console.log('Received game state update: ', gameState);
                    setWsMessage('Game state updated!');
                    // Update local state based on broadcast (NEED TO MAP player1/player2 correctly)
                    // **This assumes player 1 is always the creator for now**
                     if (game?.creator?.id === 1) { // Assuming current user is creator with ID 1
                         setPlayerState(gameState.player1);
                         setOpponentState(gameState.player2);
                     } else { // Assuming current user is opponent
                         setPlayerState(gameState.player2);
                         setOpponentState(gameState.player1);
                     }
                });

            },
            onStompError: (frame) => { /* ... error handling ... */ setWsMessage('WS Error!'); console.error('Stomp Error:', frame);},
            onWebSocketError: (error) => { /* ... error handling ... */ setWsMessage('WS Failed!'); console.error('WS Error:', error);},
            onDisconnect: () => { /* ... disconnect handling ... */ setWsMessage('Disconnected.'); console.log('Disconnected!');}
        });

        client.activate();
        stompClientRef.current = client;
        return () => { if (stompClientRef.current) stompClientRef.current.deactivate(); };
    }, [gameId, game?.durationMinutes, historicalData.length, game?.creator?.id]); // Add game.creator.id dependency


    // --- Game Timer Logic ---
    useEffect(() => { /* ... timer logic remains the same ... */
        if (isLoading || isRoundOver || gameOver || timeLeft === null || timeLeft <= 0) { if (timeLeft !== null && timeLeft <= 0 && !isRoundOver && !gameOver) setIsRoundOver(true); return; }
        const timerInterval = setInterval(() => { setTimeLeft(prevTime => { if (prevTime <= 1) { clearInterval(timerInterval); setIsRoundOver(true); return 0; } return prevTime - 1; }); }, 1000);
        return () => clearInterval(timerInterval);
    }, [timeLeft, isLoading, isRoundOver, gameOver]);


    // --- UPDATED Trading Logic: Send via WebSocket ---
    const handleTrade = (type) => {
        if (isRoundOver || gameOver || historicalData.length === 0 || !tradeAmount || tradeAmount <= 0 || !stompClientRef.current?.connected) return;
        if (currentDayIndex >= historicalData.length) return;

        const currentPrice = historicalData[currentDayIndex].close;
        const cost = tradeAmount * currentPrice;

        // **Basic client-side validation before sending**
        if (type === 'buy' && playerState.cash < cost) {
             alert("Not enough cash!");
             return;
         }
         if (type === 'sell' && playerState.shares < tradeAmount) {
             alert("Not enough shares!");
             return;
         }

        // Send trade action to the backend via WebSocket
        const tradeAction = {
            type: type.toUpperCase(),
            amount: tradeAmount,
            price: currentPrice,
            // **TEMPORARY: Assume player ID 1 - REPLACE with real ID from auth context**
            playerId: 1
        };
        stompClientRef.current.publish({
            destination: `/app/game/${gameId}/trade`,
            body: JSON.stringify(tradeAction)
        });
        setWsMessage(`Sent ${type} order...`);

        // NOTE: We NO LONGER update local state directly here.
        // The update will come back via the /topic/game/{gameId}/state subscription.
        // setPlayer(prev => ({ ... })); // <-- REMOVE or comment out direct updates
    };


    // --- Handle "Ready for Next Day" CLICK ---
    const handleReadyClick = () => {
        // ... (handleReadyClick remains the same - sends WS message) ...
        if (!isRoundOver || gameOver || !stompClientRef.current?.connected) return;
        stompClientRef.current.publish({ destination: `/app/game/${gameId}/ready`, body: '' });
        setIsReadyForNext(true); setWsMessage('Waiting for opponent...');
    };

    // --- Portfolio Calculation ---
    const getPortfolioValue = (pState, dayIndex) => {
       // ... (getPortfolioValue remains the same but uses pState arg) ...
       if (historicalData.length === 0) return pState.cash;
        const validDayIndex = Math.min(Math.max(0, dayIndex), historicalData.length - 1);
        const currentPrice = historicalData[validDayIndex]?.close || 0;
        return pState.cash + (pState.shares * currentPrice);
    };

    // --- Render Logic ---
    if (isLoading) return <div>Loading Game Data...</div>;
    if (error) return <div style={{ color: 'red', padding: '20px' }}>{error}</div>;
    if (!game || historicalData.length === 0) return <div>Waiting for game data...</div>;
    if (currentDayIndex >= historicalData.length) return <div>Error: Game state invalid (day index).</div>;
    const currentDayData = historicalData[currentDayIndex];
    if (!currentDayData) return <div>Error rendering current day data.</div>;

    // Use playerState and opponentState from WebSocket updates for display
    const playerPortfolioValue = getPortfolioValue(playerState, currentDayIndex);
    const opponentPortfolioValue = getPortfolioValue(opponentState, currentDayIndex);

    return (
        <div className="game-page-grid">
             <header className="game-header"> /* ... */ </header>

             {/* Use playerState for "You" */}
            <aside className="player-dashboard player1">
                <h3>You ({game.creator?.username || 'Player 1'})</h3>
                <p>Portfolio Value: <strong>₹{playerPortfolioValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></p>
                <p>Cash: ₹{playerState.cash.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                <p>Shares: {playerState.shares}</p>
            </aside>

             <main className="chart-container"> /* ... Price Display ... */ </main>

             {/* Use opponentState for "Opponent" */}
            <aside className="player-dashboard player2">
                 <h3>Opponent ({game.opponent?.username || 'Player 2'})</h3>
                <p>Portfolio Value: <strong>₹{opponentPortfolioValue.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</strong></p>
                <p>Cash: ₹{opponentState.cash.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                <p>Shares: {opponentState.shares}</p>
            </aside>

             <footer className="trade-panel"> /* ... Buttons call updated handleTrade/handleReadyClick ... */ </footer>
        </div>
    );
};

export default GamePage;