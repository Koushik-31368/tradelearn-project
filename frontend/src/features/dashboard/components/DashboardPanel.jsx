import React, { useEffect, useState } from 'react';
import { fetchDailyQuests, fetchWeeklyChallenges } from '../../../api/api';
import './DashboardPanel.css';

const DashboardPanel = ({ user }) => {
  const [dailyQuests, setDailyQuests] = useState([]);
  const [weeklyChallenges, setWeeklyChallenges] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadQuests = async () => {
      try {
        const [dailyRes, weeklyRes] = await Promise.all([
          fetchDailyQuests(),
          fetchWeeklyChallenges()
        ]);
        // fetchDailyQuests / fetchWeeklyChallenges already return res.data (the array),
        // NOT a full axios response — accessing .data again yields undefined.
        setDailyQuests(Array.isArray(dailyRes) ? dailyRes : []);
        setWeeklyChallenges(Array.isArray(weeklyRes) ? weeklyRes : []);
      } catch (err) {
        console.error("Failed to load quests", err);
      } finally {
        setLoading(false);
      }
    };
    if (user) {
      loadQuests();
    }
  }, [user]);

  const calculateLeague = (rating) => {
    if (rating >= 2000) return { name: 'Diamond League', next: null, min: 2000, max: 3000 };
    if (rating >= 1500) return { name: 'Gold League',    next: 'Diamond', min: 1500, max: 2000 };
    if (rating >= 1200) return { name: 'Silver League',  next: 'Gold',    min: 1200, max: 1500 };
    return                      { name: 'Bronze League',  next: 'Silver',  min: 1000, max: 1200 };
  };

  const league = calculateLeague(user?.rating || 1000);
  const leaguePct = league.max > league.min
    ? Math.min(100, Math.round(((user?.rating || 1000) - league.min) / (league.max - league.min) * 100))
    : 100;

  return (
    <div className="dashboard-panel">
      {/* Header Stats */}
      <div className="dp-header">
        <div className="dp-stat-card">
          <span className="dp-stat-icon">🔥</span>
          <div className="dp-stat-info">
            <span className="dp-stat-value">{user?.loginStreak || 0} Day Streak</span>
            <span className="dp-stat-label">Keep it up!</span>
          </div>
        </div>
        <div className="dp-stat-card">
          <span className="dp-stat-icon">⭐</span>
          <div className="dp-stat-info">
            <span className="dp-stat-value">{user?.xp || 0} XP</span>
            <span className="dp-stat-label">Total Experience</span>
          </div>
        </div>
        <div className="dp-stat-card">
          <span className="dp-stat-icon">🏆</span>
          <div className="dp-stat-info">
            <span className="dp-stat-value">{league.name}</span>
            <span className="dp-stat-label">{user?.rating || 1000} Rating</span>
            <div className="dp-xp-bar-track" title={`${leaguePct}% to ${league.next ? league.next + ' League' : 'max'}`}>
              <div
                className="dp-xp-bar-fill"
                style={{ width: `${leaguePct}%` }}
                role="progressbar"
                aria-valuenow={leaguePct}
                aria-valuemin={0}
                aria-valuemax={100}
                aria-label="League progress"
              />
            </div>
            {league.next && (
              <span className="dp-xp-next">{leaguePct}% → {league.next} League</span>
            )}
          </div>
        </div>
      </div>

      <div className="dp-main">
        {/* Daily Quests */}
        <div className="dp-section">
          <h2 className="dp-section-title">📅 Today's Quests</h2>
          {loading ? (
            <p className="dp-loading">Loading quests...</p>
          ) : (
            <div className="dp-quest-list">
              {dailyQuests.map(quest => (
                <div key={quest.id} className={`dp-quest-item ${quest.completed ? 'completed' : ''}`}>
                  <div className="dp-quest-header">
                    <div className="dp-quest-title">
                      {quest.completed ? '✓ ' : '○ '} {quest.name}
                    </div>
                    <div className="dp-quest-reward">+{quest.xpReward} XP</div>
                  </div>
                  {!quest.completed && (
                    <div className="dp-progress-bar-container">
                      <div 
                        className="dp-progress-bar" 
                        style={{ width: `${(quest.progress / quest.targetValue) * 100}%` }}
                      ></div>
                    </div>
                  )}
                  <div className="dp-quest-desc">
                    {quest.description} 
                    {!quest.completed && ` (${quest.progress}/${quest.targetValue})`}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Weekly Challenges */}
        <div className="dp-section">
          <h2 className="dp-section-title">🎯 Weekly Challenges</h2>
          {loading ? (
            <p className="dp-loading">Loading challenges...</p>
          ) : (
            <div className="dp-quest-list">
              {weeklyChallenges.map(challenge => (
                <div key={challenge.id} className={`dp-challenge-item ${challenge.completed ? 'completed' : ''}`}>
                  <div className="dp-challenge-header">
                    <div className="dp-challenge-title">
                      {challenge.name}
                    </div>
                  </div>
                  <div className="dp-challenge-desc">
                    {challenge.description}
                    {!challenge.completed && ` (${challenge.progress}/${challenge.targetValue})`}
                  </div>
                  {!challenge.completed && (
                    <div className="dp-progress-bar-container">
                      <div 
                        className="dp-progress-bar" 
                        style={{ width: `${(challenge.progress / challenge.targetValue) * 100}%` }}
                      ></div>
                    </div>
                  )}
                  <div className="dp-challenge-rewards">
                    <span className="dp-reward-pill">+{challenge.xpReward} XP</span>
                    {challenge.badgeReward && <span className="dp-reward-pill badge">🎖️ {challenge.badgeReward}</span>}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default DashboardPanel;
