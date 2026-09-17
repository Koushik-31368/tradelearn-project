import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { backendUrl, authHeaders } from '../../../api/api';
import TierBadge from '../../leaderboard/components/TierBadge';
import { useAuth } from '../../auth/AuthContext';
import './SocialPage.css';

/* ─── Skeleton card ──────────────────────────────────────────── */
const SkeletonCard = () => (
  <div className="sp-skeleton">
    <div className="sp-skeleton__avatar" />
    <div className="sp-skeleton__lines">
      <div className="sp-skeleton__line sp-skeleton__line--wide" />
      <div className="sp-skeleton__line sp-skeleton__line--short" />
    </div>
  </div>
);

/* ─── Friend card (accepted) ─────────────────────────────────── */
const FriendCard = ({ friend, onChallenge }) => (
  <div className="sp-friend-card">
    <div className="sp-friend-card__avatar">
      {friend.username?.charAt(0).toUpperCase()}
    </div>
    <div className="sp-friend-card__info">
      <span className="sp-friend-card__name">{friend.username}</span>
      <TierBadge rating={friend.rating} />
    </div>
    <button
      className="sp-btn sp-btn--challenge"
      onClick={() => onChallenge(friend)}
    >
      ⚔️ Challenge
    </button>
  </div>
);

/* ─── Request card (incoming) ────────────────────────────────── */
const RequestCard = ({ req, onAccept, onDecline, exiting }) => (
  <div className={`sp-req-card${exiting ? ' sp-req-card--exit' : ''}`}>
    <div className="sp-req-card__avatar">
      {req.username?.charAt(0).toUpperCase()}
    </div>
    <div className="sp-req-card__info">
      <span className="sp-req-card__name">{req.username}</span>
      <span className="sp-req-card__sub">Wants to be your friend</span>
    </div>
    <div className="sp-req-card__actions">
      <button className="sp-btn sp-btn--accept" onClick={() => onAccept(req.requestId)}>
        ✓ Accept
      </button>
      <button className="sp-btn sp-btn--decline" onClick={() => onDecline(req.requestId)}>
        ✕
      </button>
    </div>
  </div>
);

/* ─── Sent card (outgoing) ───────────────────────────────────── */
const SentCard = ({ req, onCancel }) => (
  <div className="sp-req-card sp-req-card--sent">
    <div className="sp-req-card__avatar sp-req-card__avatar--sent">
      {req.username?.charAt(0).toUpperCase()}
    </div>
    <div className="sp-req-card__info">
      <span className="sp-req-card__name">{req.username}</span>
      <span className="sp-req-card__sub sp-req-card__sub--pending">
        <span className="sp-pulse-dot" /> Awaiting response
      </span>
    </div>
    <button className="sp-btn sp-btn--cancel" onClick={() => onCancel(req.requestId)}>
      Cancel
    </button>
  </div>
);

/* ══════════════════════════════════════════════════════════════ */
/*  SOCIAL PAGE                                                   */
/* ══════════════════════════════════════════════════════════════ */
const SocialPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  /* ── data ── */
  const [friends, setFriends]       = useState([]);
  const [loading, setLoading]       = useState(true);
  const [activeTab, setActiveTab]   = useState('requests'); // 'requests' | 'friends'

  /* ── search ── */
  const [query, setQuery]           = useState('');
  const [searching, setSearching]   = useState(false);
  const [searchResult, setSearchResult] = useState(null);  // null | {found, ...data}
  const [searchMsg, setSearchMsg]   = useState(null);       // {type, text}
  const [sending, setSending]       = useState(false);

  /* ── exit-animation tracking ── */
  const [exitingIds, setExitingIds] = useState(new Set());
  const inputRef = useRef(null);

  /* ─── fetch ── */
  const fetchFriends = useCallback(async () => {
    try {
      const res = await fetch(backendUrl('/api/social/friends'), { headers: authHeaders() });
      if (res.ok) setFriends(await res.json());
    } catch (err) {
      console.error('[Social] fetch error', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchFriends(); }, [fetchFriends]);

  /* ── derived lists ── */
  const pendingReceived = friends.filter(f => f.status === 'PENDING' && !f.sender);
  const pendingSent     = friends.filter(f => f.status === 'PENDING' &&  f.sender);
  const accepted        = friends.filter(f => f.status === 'ACCEPTED');
  const totalRequests   = pendingReceived.length + pendingSent.length;

  /* auto-switch to Requests tab when there are incoming requests */
  useEffect(() => {
    if (!loading && pendingReceived.length > 0) setActiveTab('requests');
  }, [loading, pendingReceived.length]);

  /* ─── flash message ── */
  const flash = (type, text) => {
    setSearchMsg({ type, text });
    setTimeout(() => setSearchMsg(null), 3500);
  };

  /* ─── animate-then-remove helper ── */
  const animateOut = (id, callback) => {
    setExitingIds(prev => new Set([...prev, id]));
    setTimeout(() => {
      setExitingIds(prev => { const s = new Set(prev); s.delete(id); return s; });
      callback();
    }, 320);
  };

  /* ─── search ── */
  const handleSearch = async (e) => {
    e.preventDefault();
    const trimmed = query.trim();
    if (!trimmed) return;
    setSearchResult(null);
    setSearchMsg(null);
    setSearching(true);
    try {
      const res = await fetch(
        backendUrl(`/api/social/users/search/${encodeURIComponent(trimmed)}`),
        { headers: authHeaders() }
      );
      const ct   = res.headers.get('content-type') || '';
      const data = ct.includes('application/json') ? await res.json() : null;

      if (res.ok && data) {
        const alreadyFriend = friends.some(f => f.username === data.username);
        const sentAlready   = pendingSent.some(f => f.username === data.username);
        setSearchResult({ ...data, alreadyFriend, sentAlready });
      } else if (res.status === 401) {
        flash('error', 'Session expired — please log in again.');
      } else {
        setSearchResult({ notFound: true, username: trimmed });
      }
    } catch {
      flash('error', 'Network error. Please try again.');
    } finally {
      setSearching(false);
    }
  };

  /* ─── add friend ── */
  const handleAdd = async () => {
    if (!searchResult || searchResult.notFound) return;
    setSending(true);
    try {
      const res = await fetch(
        backendUrl(`/api/social/friends/add/${encodeURIComponent(searchResult.username)}`),
        { method: 'POST', headers: authHeaders() }
      );
      if (res.ok) {
        flash('success', `Friend request sent to ${searchResult.username}!`);
        setSearchResult(null);
        setQuery('');
        fetchFriends();
      } else {
        const txt = await res.text();
        flash('error', txt || 'Could not send request. Try again.');
      }
    } catch {
      flash('error', 'Network error.');
    } finally {
      setSending(false);
    }
  };

  /* ─── accept ── */
  const handleAccept = async (id) => {
    animateOut(id, async () => {
      try {
        await fetch(backendUrl(`/api/social/friends/accept/${id}`), {
          method: 'POST', headers: authHeaders()
        });
        fetchFriends();
        flash('success', 'Friend added! 🎉');
      } catch { /* silent */ }
    });
  };

  /* ─── decline / cancel ── */
  const handleDecline = async (id) => {
    animateOut(id, async () => {
      try {
        await fetch(backendUrl(`/api/social/friends/reject/${id}`), {
          method: 'POST', headers: authHeaders()
        });
        fetchFriends();
      } catch { /* silent */ }
    });
  };

  /* ─── challenge ── */
  const handleChallenge = (friend) => {
    navigate('/multiplayer');
  };

  /* ─── clear search ── */
  const clearSearch = () => {
    setSearchResult(null);
    setSearchMsg(null);
    setQuery('');
    inputRef.current?.focus();
  };

  if (!user) {
    return (
      <div className="sp-page">
        <p className="sp-login-prompt">
          Please <span className="sp-link" onClick={() => navigate('/login')}>log in</span> to see your friends.
        </p>
      </div>
    );
  }

  return (
    <div className="sp-page">

      {/* ── Page header ── */}
      <div className="sp-header">
        <h1 className="sp-heading">
          <span className="sp-heading__icon">👥</span> Social
        </h1>
        <p className="sp-subheading">
          Search players, send friend requests, and challenge rivals.
        </p>
      </div>

      {/* ── Search ── */}
      <section className="sp-search-section">
        <form className="sp-search-form" onSubmit={handleSearch}>
          <input
            ref={inputRef}
            type="text"
            className="sp-search-input"
            placeholder="Search by exact username…"
            value={query}
            onChange={e => { setQuery(e.target.value); setSearchResult(null); setSearchMsg(null); }}
            autoComplete="off"
          />
          <button type="submit" className="sp-btn sp-btn--search" disabled={searching || !query.trim()}>
            {searching ? <span className="sp-spinner" /> : '🔍 Search'}
          </button>
        </form>

        {/* Flash message */}
        {searchMsg && (
          <div className={`sp-flash sp-flash--${searchMsg.type}`}>
            {searchMsg.text}
          </div>
        )}

        {/* Search result */}
        {searchResult && (
          <div className={`sp-search-result ${searchResult.notFound ? 'sp-search-result--miss' : 'sp-search-result--hit'}`}>
            {searchResult.notFound ? (
              <>
                <span className="sp-search-result__icon">🔍</span>
                <div className="sp-search-result__body">
                  <span className="sp-search-result__name">No user found</span>
                  <span className="sp-search-result__sub">
                    "{searchResult.username}" doesn't exist. Check spelling (case-sensitive).
                  </span>
                </div>
                <button className="sp-btn-ghost" onClick={clearSearch}>✕</button>
              </>
            ) : (
              <>
                <div className="sp-search-result__avatar">
                  {searchResult.username?.charAt(0).toUpperCase()}
                </div>
                <div className="sp-search-result__body">
                  <span className="sp-search-result__name">{searchResult.username}</span>
                  <TierBadge rating={searchResult.rating} />
                </div>
                <div className="sp-search-result__actions">
                  {searchResult.alreadyFriend ? (
                    <span className="sp-already-friend">✓ Friends</span>
                  ) : searchResult.sentAlready ? (
                    <span className="sp-already-sent">⏳ Request sent</span>
                  ) : (
                    <button
                      className="sp-btn sp-btn--add"
                      onClick={handleAdd}
                      disabled={sending}
                    >
                      {sending ? 'Sending…' : '+ Add Friend'}
                    </button>
                  )}
                </div>
                <button className="sp-btn-ghost" onClick={clearSearch}>✕</button>
              </>
            )}
          </div>
        )}
      </section>

      {/* ── Tabs ── */}
      <div className="sp-tabs">
        <button
          className={`sp-tab${activeTab === 'requests' ? ' sp-tab--active' : ''}`}
          onClick={() => setActiveTab('requests')}
        >
          Requests
          {totalRequests > 0 && (
            <span className="sp-tab__badge">{totalRequests}</span>
          )}
        </button>
        <button
          className={`sp-tab${activeTab === 'friends' ? ' sp-tab--active' : ''}`}
          onClick={() => setActiveTab('friends')}
        >
          Friends
          {accepted.length > 0 && (
            <span className="sp-tab__count">{accepted.length}</span>
          )}
        </button>
      </div>

      {/* ── Tab content ── */}
      <div className="sp-tab-content">

        {/* REQUESTS TAB */}
        {activeTab === 'requests' && (
          <div className="sp-tab-pane">
            {loading ? (
              <>{[1,2].map(i => <SkeletonCard key={i} />)}</>
            ) : totalRequests === 0 ? (
              <div className="sp-empty">
                <span className="sp-empty__icon">📭</span>
                <span className="sp-empty__text">No pending requests</span>
                <span className="sp-empty__sub">
                  Search above to add a friend, or wait for someone to find you.
                </span>
              </div>
            ) : (
              <>
                {/* Incoming */}
                {pendingReceived.length > 0 && (
                  <div className="sp-group">
                    <div className="sp-group__label">
                      Incoming
                      <span className="sp-group__badge sp-group__badge--pink">{pendingReceived.length}</span>
                    </div>
                    {pendingReceived.map(req => (
                      <RequestCard
                        key={req.requestId}
                        req={req}
                        onAccept={handleAccept}
                        onDecline={handleDecline}
                        exiting={exitingIds.has(req.requestId)}
                      />
                    ))}
                  </div>
                )}

                {/* Sent */}
                {pendingSent.length > 0 && (
                  <div className="sp-group">
                    <div className="sp-group__label">
                      Sent
                      <span className="sp-group__badge sp-group__badge--amber">{pendingSent.length}</span>
                    </div>
                    {pendingSent.map(req => (
                      <SentCard
                        key={req.requestId}
                        req={req}
                        onCancel={handleDecline}
                      />
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        )}

        {/* FRIENDS TAB */}
        {activeTab === 'friends' && (
          <div className="sp-tab-pane">
            {loading ? (
              <div className="sp-friends-grid">
                {[1,2,3,4].map(i => <SkeletonCard key={i} />)}
              </div>
            ) : accepted.length === 0 ? (
              <div className="sp-empty">
                <span className="sp-empty__icon">🤝</span>
                <span className="sp-empty__text">No friends yet</span>
                <span className="sp-empty__sub">
                  Search for a player above and send your first friend request!
                </span>
              </div>
            ) : (
              <div className="sp-friends-grid">
                {accepted.map(friend => (
                  <FriendCard
                    key={friend.requestId}
                    friend={friend}
                    onChallenge={handleChallenge}
                  />
                ))}
              </div>
            )}
          </div>
        )}
      </div>

    </div>
  );
};

export default SocialPage;
