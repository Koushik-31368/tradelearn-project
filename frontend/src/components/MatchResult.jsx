import { useEffect, useState, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { stompClient } from "../socket";
import rankTier from "../utils/rankTier";

  const navigate = useNavigate();
  const [waiting, setWaiting] = useState(false);
  const [countdown, setCountdown] = useState(null);
  const [ratingDelta, setRatingDelta] = useState(null);
  const [newRating, setNewRating] = useState(null);
  const [playerPnl, setPlayerPnl] = useState(0);
  const [opponentPnl, setOpponentPnl] = useState(0);
  const [tradeCount, setTradeCount] = useState(0);
  const [rank, setRank] = useState("");
  const [showRating, setShowRating] = useState(false);
  const audioRef = useRef(null);

  const requestRematch = async () => {
    try {
      setWaiting(true);
      const res = await fetch(`/api/match/${matchId}/rematch`, {
        method: "POST",
        credentials: "include"
      });
      if (!res.ok) {
        throw new Error("Rematch failed");
      }
    } catch (err) {
      setWaiting(false);
      alert("Something went wrong. Try again.");
    }
  };

  useEffect(() => {
    let subscription;
    let countdownInterval;
    let pollingInterval;

    const playSound = () => {
      if (!audioRef.current) {
        audioRef.current = new Audio("/sounds/countdown.mp3");
      }
      audioRef.current.currentTime = 0;
      audioRef.current.play();
    };

    const startCountdown = (newMatchId) => {
      let seconds = 3;
      setCountdown(seconds);
      playSound();

      countdownInterval = setInterval(() => {
        seconds -= 1;
        setCountdown(seconds);
        if (seconds === 0) {
          clearInterval(countdownInterval);
          navigate(`/match/${newMatchId}`);
        }
      }, 1000);
    };

    const subscribeToRematch = () => {
      subscription = stompClient.subscribe(
        "/user/queue/rematch",
        (message) => {
          const newMatchId = message.body;
          startCountdown(newMatchId);
        }
      );
    };

    if (stompClient?.connected) {
      subscribeToRematch();
    } else {
      pollingInterval = setInterval(() => {
        if (stompClient?.connected) {
          subscribeToRematch();
          clearInterval(pollingInterval);
        }
      }, 200);
    }

    return () => {
      if (subscription) subscription.unsubscribe();
      if (countdownInterval) clearInterval(countdownInterval);
      if (pollingInterval) clearInterval(pollingInterval);
    };
  }, [navigate]);

  return (
    <div className="result-screen arena-bg">
      {/* Winner reveal */}
      <h1 className="winner-banner" style={{ color: winner === rankTier ? '#00ff88' : '#ff3b3b' }}>
        {winner === rankTier ? 'VICTORY' : 'DEFEAT'}
      </h1>

      {/* Rating change animation */}
      {showRating && (
        <div className={`rating-popup ${ratingDelta > 0 ? "gain" : "loss"}`}>
          {ratingDelta > 0 ? `+${ratingDelta}` : ratingDelta}
        </div>
      )}

      {/* Skill reveal after rating animation, only for Ranked mode */}
      {mode === "RANKED" && !showRating && skillType && (
        <div className="skill-reveal">
          Skill Tested: {skillType}
        </div>
      )}

      {/* Rank badge */}
      {rank && (
        <div className="rank-badge">
          {rank}
        </div>
      )}

      {/* Match summary stats panel */}
      <div className="match-summary">
        <h3>Match Summary</h3>
        <p className={playerPnl >= 0 ? 'pnl-positive' : 'pnl-negative'}>
          Your PnL: ₹{playerPnl.toFixed(2)}
        </p>
        <p className={opponentPnl >= 0 ? 'pnl-positive' : 'pnl-negative'}>
          Opponent PnL: ₹{opponentPnl.toFixed(2)}
        </p>
        <p>Total Trades: {tradeCount}</p>
      </div>

      {countdown !== null ? (
        <h1 className="arena-countdown">{countdown}</h1>
      ) : (
        <>
          <button className="arena-btn" onClick={requestRematch} disabled={waiting}>
            Rematch
          </button>
          <button className="arena-btn" onClick={() => navigate("/")} disabled={waiting}>
            Exit
          </button>
          {waiting && <p className="arena-wait">Waiting for opponent...</p>}
        </>
      )}
    </div>
  );
}
