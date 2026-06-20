import React from 'react';
import { useNavigate } from 'react-router-dom';
import { MISSIONS } from '../utils/missions';
import '../../learn/pages/LearnPage.css'; // Reusing some base styling

const MissionSelectionPage = () => {
  const navigate = useNavigate();

  const launchMission = (missionId) => {
    navigate(`/mission-dashboard/${missionId}`);
  };

  return (
    <div className="learn-map-page" style={{ padding: '40px' }}>
      <div className="learn-header">
        <h1 className="learn-title">Flight School: Missions</h1>
        <p style={{ color: '#8b949e', fontSize: '16px', maxWidth: '600px', margin: '0 auto' }}>
          Complete these historical scenarios to validate your risk management and psychological discipline before trading live capital.
        </p>
      </div>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '20px', maxWidth: '800px', margin: '40px auto' }}>
        {MISSIONS.map((mission, index) => (
          <div key={mission.id} style={{
            backgroundColor: '#0d1117',
            border: '1px solid #30363d',
            borderRadius: '8px',
            padding: '20px',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <div>
              <h3 style={{ color: '#c9d1d9', margin: '0 0 10px 0' }}>{mission.title}</h3>
              <p style={{ color: '#8b949e', fontSize: '14px', margin: '0 0 10px 0' }}>{mission.objective}</p>
              <div style={{ display: 'flex', gap: '15px', fontSize: '12px', color: '#58a6ff' }}>
                <span>Max Trades: {mission.constraints.maxTrades}</span>
                {mission.constraints.maxDrawdownPercent && (
                  <span>Max Drawdown: {mission.constraints.maxDrawdownPercent}%</span>
                )}
              </div>
            </div>
            
            <button
              onClick={() => launchMission(mission.id)}
              style={{
                backgroundColor: '#238636',
                color: '#fff',
                border: 'none',
                borderRadius: '6px',
                padding: '10px 20px',
                fontWeight: 'bold',
                cursor: 'pointer'
              }}
            >
              Launch Mission
            </button>
          </div>
        ))}
      </div>
    </div>
  );
};

export default MissionSelectionPage;
