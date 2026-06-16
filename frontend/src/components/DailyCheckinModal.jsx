import React, { useState, useEffect } from 'react';
import { dailyCheckin } from '../utils/api';
import { useAuth } from '../context/AuthContext';
import './DailyCheckinModal.css';

const DailyCheckinModal = () => {
  const { isAuthenticated, user, updateUser } = useAuth();
  const [showModal, setShowModal] = useState(false);
  const [checkinData, setCheckinData] = useState(null);

  useEffect(() => {
    // Only attempt check-in if authenticated and haven't checked in recently
    // We can just call the endpoint. If already checked in, the backend should ideally 
    // handle it safely, but since it returns the user object unchanged, we can check 
    // the "message" or just always try on fresh load.
    let mounted = true;
    
    if (isAuthenticated && user) {
      const lastCheckinKey = `lastCheckin_${user.id}`;
      const lastCheckinStr = localStorage.getItem(lastCheckinKey);
      const todayStr = new Date().toDateString();
      
      if (lastCheckinStr !== todayStr) {
        dailyCheckin()
          .then((res) => {
            if (mounted && res.data && res.data.message && res.data.message.includes('+10 XP')) {
              setCheckinData(res.data);
              setShowModal(true);
              updateUser({
                xp: res.data.xp,
                loginStreak: res.data.loginStreak
              });
              localStorage.setItem(lastCheckinKey, todayStr);
            }
          })
          .catch((err) => {
            console.error("Daily checkin failed", err);
          });
      }
    }
    
    return () => { mounted = false; };
  }, [isAuthenticated, user, updateUser]);

  if (!showModal || !checkinData) return null;

  return (
    <div className="checkin-modal-overlay">
      <div className="checkin-modal-content">
        <h2>🔥 Daily Check-in!</h2>
        <p className="checkin-message">{checkinData.message}</p>
        <div className="checkin-stats">
          <div className="checkin-stat">
            <span className="stat-value">+{checkinData.xp - (user?.xp || 0)}</span>
            <span className="stat-label">XP Gained</span>
          </div>
          <div className="checkin-stat">
            <span className="stat-value">🔥 {checkinData.loginStreak}</span>
            <span className="stat-label">Day Streak</span>
          </div>
        </div>
        <button className="checkin-close-btn" onClick={() => setShowModal(false)}>Awesome!</button>
      </div>
    </div>
  );
};

export default DailyCheckinModal;
