import React, { useState, useEffect } from 'react';
import { backendUrl, authHeaders } from '../../../api/api';
import TierBadge from '../../leaderboard/components/TierBadge';
import './FriendsPanel.css';

const FriendsPanel = ({ onChallenge }) => {
  const [friends, setFriends] = useState([]);
  const [newFriendName, setNewFriendName] = useState('');
  const [message, setMessage] = useState(null);

  const fetchFriends = async () => {
    try {
      const res = await fetch(backendUrl('/api/social/friends'), { headers: authHeaders() });
      if (res.ok) setFriends(await res.json());
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    fetchFriends();
  }, []);

  const handleAddFriend = async (e) => {
    e.preventDefault();
    if (!newFriendName.trim()) return;
    try {
      const res = await fetch(backendUrl(`/api/social/friends/add/${newFriendName}`), {
        method: 'POST',
        headers: authHeaders()
      });
      if (res.ok) {
        setMessage({ type: 'success', text: 'Friend request sent!' });
        setNewFriendName('');
        fetchFriends();
      } else {
        const errText = await res.text();
        setMessage({ type: 'error', text: errText || 'Failed to add friend' });
      }
    } catch (err) {
      setMessage({ type: 'error', text: 'Network error' });
    }
    setTimeout(() => setMessage(null), 3000);
  };

  const handleAccept = async (requestId) => {
    try {
      await fetch(backendUrl(`/api/social/friends/accept/${requestId}`), {
        method: 'POST',
        headers: authHeaders()
      });
      fetchFriends();
    } catch (err) {
      console.error(err);
    }
  };

  const pendingRequests = friends.filter(f => f.status === 'PENDING' && !f.isSender);
  const acceptedFriends = friends.filter(f => f.status === 'ACCEPTED');

  return (
    <div className="friends-panel">
      <h3 className="fp-title">Friends List</h3>
      
      <form className="fp-add-form" onSubmit={handleAddFriend}>
        <input 
          type="text" 
          placeholder="Add friend by username..." 
          value={newFriendName}
          onChange={(e) => setNewFriendName(e.target.value)}
          className="fp-input"
        />
        <button type="submit" className="fp-btn fp-btn-add">+</button>
      </form>
      {message && <div className={`fp-msg fp-msg-${message.type}`}>{message.text}</div>}

      {pendingRequests.length > 0 && (
        <div className="fp-section">
          <h4 className="fp-section-title">Pending Requests</h4>
          <div className="fp-list">
            {pendingRequests.map(req => (
              <div key={req.requestId} className="fp-item">
                <span className="fp-name">{req.username}</span>
                <button className="fp-btn fp-btn-accept" onClick={() => handleAccept(req.requestId)}>Accept</button>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="fp-section">
        <h4 className="fp-section-title">Your Friends</h4>
        {acceptedFriends.length === 0 ? (
          <p className="fp-empty">No friends yet.</p>
        ) : (
          <div className="fp-list">
            {acceptedFriends.map(friend => (
              <div key={friend.requestId} className="fp-item">
                <div className="fp-info">
                  <span className="fp-name">{friend.username}</span>
                  <TierBadge rating={friend.rating} className="fp-badge" />
                </div>
                {onChallenge && (
                  <button className="fp-btn fp-btn-challenge" onClick={() => onChallenge(friend.username)}>
                    ⚔️ Challenge
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default FriendsPanel;
