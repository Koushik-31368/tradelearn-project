import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { backendUrl } from '../../../api/api';
import { useAuth } from '../../auth/AuthContext';
import './ChallengeListener.css';

// Global STOMP client for challenges
let challengeStompClient = null;

const ChallengeListener = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [incomingChallenge, setIncomingChallenge] = useState(null);

  useEffect(() => {
    if (!user) {
      if (challengeStompClient) {
        challengeStompClient.deactivate();
        challengeStompClient = null;
      }
      return;
    }

    if (!challengeStompClient) {
      const socket = new SockJS(backendUrl('/ws'));
      challengeStompClient = new Client({
        webSocketFactory: () => socket,
        debug: (str) => {
          // console.log('[Challenge STOMP]: ' + str);
        },
        reconnectDelay: 5000,
      });

      challengeStompClient.onConnect = (frame) => {
        const username = frame.headers['user-name'] || user.username;
        challengeStompClient.subscribe(`/user/${username}/queue/challenges`, (message) => {
          const payload = JSON.parse(message.body);
          
          if (payload.type === 'CHALLENGE_RECEIVED') {
            setIncomingChallenge(payload);
          } else if (payload.type === 'CHALLENGE_ACCEPTED') {
            setIncomingChallenge(null);
            navigate(`/game/${payload.gameId}`);
          } else if (payload.type === 'CHALLENGE_DECLINED') {
            // Optional: show a toast
            setIncomingChallenge(null);
          }
        });
      };

      challengeStompClient.activate();
    }

    return () => {
      // In a real app we might not deactivate on unmount if it's placed in a layout
      // But we leave it active for now if it's meant to be global
    };
  }, [user, navigate]);

  const respondChallenge = (accepted) => {
    if (!challengeStompClient || !challengeStompClient.connected) return;
    
    challengeStompClient.publish({
      destination: '/app/challenge.respond',
      body: JSON.stringify({
        challengeId: incomingChallenge.challengeId,
        accepted: accepted
      })
    });

    if (!accepted) {
      setIncomingChallenge(null);
    }
  };

  // Allow sending challenge from anywhere by attaching to window
  useEffect(() => {
    window.sendChallenge = (friendUsername) => {
      if (challengeStompClient && challengeStompClient.connected) {
        challengeStompClient.publish({
          destination: '/app/challenge.send',
          body: JSON.stringify({ challengedUsername: friendUsername })
        });
      } else {
        alert("Not connected to challenge server.");
      }
    };
  }, []);

  if (!incomingChallenge) return null;

  return (
    <div className="challenge-popup-overlay">
      <div className="challenge-popup">
        <div className="challenge-icon">⚔️</div>
        <h3 className="challenge-title">Challenge Received!</h3>
        <p className="challenge-desc"><strong>{incomingChallenge.challengerUsername}</strong> wants to trade!</p>
        <div className="challenge-actions">
          <button className="challenge-btn challenge-btn-accept" onClick={() => respondChallenge(true)}>Accept</button>
          <button className="challenge-btn challenge-btn-decline" onClick={() => respondChallenge(false)}>Decline</button>
        </div>
      </div>
    </div>
  );
};

export default ChallengeListener;
