import React, { useState, useEffect, useCallback } from 'react';
import { backendUrl, authHeaders } from '../../../api/api';
import TierBadge from '../../leaderboard/components/TierBadge';
import './FriendsPanel.css';

const FriendsPanel = ({ onChallenge }) => {
  const [friends, setFriends] = useState([]);
  const [newFriendName, setNewFriendName] = useState('');
  const [message, setMessage] = useState(null);

  // Two-step lookup state
  const [searchResult, setSearchResult] = useState(null);
  const [searchError, setSearchError] = useState(null);
  const [isSearching, setIsSearching] = useState(false);
  const [isAdding, setIsAdding] = useState(false);

  const fetchFriends = useCallback(async () => {
    try {
      const res = await fetch(backendUrl('/api/social/friends'), { headers: authHeaders() });
      if (res.ok) setFriends(await res.json());
    } catch (err) {
      console.error(err);
    }
  }, []);

  useEffect(() => {
    fetchFriends();
  }, [fetchFriends]);

  const showMessage = (type, text) => {
    setMessage({ type, text });
    setTimeout(() => setMessage(null), 3500);
  };

  // ── Step 1: Search username ──────────────────────────────────────────────────
  const handleSearch = async (e) => {
    e.preventDefault();
    const trimmed = newFriendName.trim();
    if (!trimmed) return;

    setSearchResult(null);
    setSearchError(null);
    setIsSearching(true);

    try {
      const res = await fetch(
        backendUrl(`/api/social/users/search/${encodeURIComponent(trimmed)}`),
        { headers: authHeaders() }
      );

      let data = null;
      const contentType = res.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        data = await res.json();
      }

      if (res.ok && data) {
        const alreadyFriend = friends.some(f => f.username === data.username);
        setSearchResult({ ...data, alreadyFriend });
      } else if (res.status === 401) {
        setSearchError('Session expired — please log in again.');
      } else {
        setSearchError(data?.error || 'User not found');
      }
    } catch (err) {
      console.error('Search error:', err);
      setSearchError('Network error. Please try again.');
    } finally {
      setIsSearching(false);
    }
  };

  // ── Step 2: Send friend request ──────────────────────────────────────────────
  const handleAddFriend = async () => {
    if (!searchResult) return;
    setIsAdding(true);
    try {
      const res = await fetch(
        backendUrl(`/api/social/friends/add/${encodeURIComponent(searchResult.username)}`),
        { method: 'POST', headers: authHeaders() }
      );
      if (res.ok) {
        showMessage('success', `Friend request sent to ${searchResult.username}!`);
        setSearchResult(null);
        setNewFriendName('');
        fetchFriends();
      } else {
        const errText = await res.text();
        showMessage('error', errText || 'Failed to send request');
      }
    } catch {
      showMessage('error', 'Network error');
    } finally {
      setIsAdding(false);
    }
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

  const handleCancelRequest = async (requestId) => {
    try {
      await fetch(backendUrl(`/api/social/friends/reject/${requestId}`), {
        method: 'POST',
        headers: authHeaders()
      });
      fetchFriends();
    } catch (err) {
      console.error(err);
    }
  };

  const clearSearch = () => {
    setSearchResult(null);
    setSearchError(null);
    setNewFriendName('');
  };

  const pendingReceived = friends.filter(f => f.status === 'PENDING' && !f.sender);
  const pendingSent     = friends.filter(f => f.status === 'PENDING' &&  f.sender);
  const acceptedFriends = friends.filter(f => f.status === 'ACCEPTED');

  return (
    <div className="friends-panel">
      <h3 className="fp-title">Friends List</h3>

      {/* ── Search form ── */}
      <form className="fp-add-form" onSubmit={handleSearch}>
        <input
          type="text"
          placeholder="Search by username..."
          value={newFriendName}
          onChange={(e) => {
            setNewFriendName(e.target.value);
            setSearchResult(null);
            setSearchError(null);
          }}
          className="fp-input"
        />
        <button type="submit" className="fp-btn fp-btn-add" disabled={isSearching}>
          {isSearching ? '…' : '🔍'}
        </button>
      </form>

      {/* ── Search result: user NOT found ── */}
      {searchError && (
        <div className="fp-search-result fp-search-notfound">
          <span className="fp-notfound-icon">⚠️</span>
          <span className="fp-notfound-text">{searchError}</span>
          <button className="fp-btn-clear" onClick={clearSearch}>✕</button>
        </div>
      )}

      {/* ── Search result: user FOUND ── */}
      {searchResult && (
        <div className="fp-search-result fp-search-found">
          <div className="fp-found-info">
            <span className="fp-found-avatar">👤</span>
            <div>
              <span className="fp-found-name">{searchResult.username}</span>
              <TierBadge rating={searchResult.rating} className="fp-badge" />
            </div>
          </div>
          <div className="fp-found-actions">
            {searchResult.alreadyFriend ? (
              <span className="fp-already-friend">✓ Already friends</span>
            ) : (
              <button
                className="fp-btn fp-btn-send-request"
                onClick={handleAddFriend}
                disabled={isAdding}
              >
                {isAdding ? 'Sending…' : '+ Add Friend'}
              </button>
            )}
            <button className="fp-btn-clear" onClick={clearSearch}>✕</button>
          </div>
        </div>
      )}

      {/* ── Status message (success / error) ── */}
      {message && (
        <div className={`fp-msg fp-msg-${message.type}`}>{message.text}</div>
      )}

      {/* ── Sent requests (outgoing — waiting for response) ── */}
      {pendingSent.length > 0 && (
        <div className="fp-section">
          <h4 className="fp-section-title">Sent Requests</h4>
          <div className="fp-list">
            {pendingSent.map(req => (
              <div key={req.requestId} className="fp-item">
                <div className="fp-info">
                  <span className="fp-sent-dot" />
                  <span className="fp-name">{req.username}</span>
                </div>
                <div className="fp-item-actions">
                  <span className="fp-pending-label">Pending</span>
                  <button
                    className="fp-btn-cancel"
                    onClick={() => handleCancelRequest(req.requestId)}
                    title="Cancel request"
                  >
                    ✕
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Incoming friend requests ── */}
      {pendingReceived.length > 0 && (
        <div className="fp-section">
          <h4 className="fp-section-title">Friend Requests</h4>
          <div className="fp-list">
            {pendingReceived.map(req => (
              <div key={req.requestId} className="fp-item">
                <span className="fp-name">{req.username}</span>
                <button className="fp-btn fp-btn-accept" onClick={() => handleAccept(req.requestId)}>
                  Accept
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Accepted friends ── */}
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
