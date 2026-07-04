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
    if (rating >= 2000) return "Diamond League";
    if (rating >= 1500) return "Gold League";
    if (rating >= 1200) return "Silver League";
    return "Bronze League";
  };

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
            <span className="dp-stat-value">{calculateLeague(user?.rating || 1000)}</span>
            <span className="dp-stat-label">{user?.rating || 1000} Rating</span>
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
