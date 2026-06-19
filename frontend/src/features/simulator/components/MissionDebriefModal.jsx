import React from 'react';
import { useNavigate } from 'react-router-dom';

const MissionDebriefModal = ({ assessment, onClose }) => {
  const navigate = useNavigate();

  if (!assessment) return null;

  const isPass = assessment.status === 'PASS';

  const overlayStyle = {
    position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.85)',
    display: 'flex', alignItems: 'center', justifyContent: 'center',
    zIndex: 9999, backdropFilter: 'blur(8px)'
  };

  const modalStyle = {
    backgroundColor: '#0d1117', border: `2px solid ${isPass ? '#238636' : '#da3633'}`,
    borderRadius: '12px', padding: '30px', width: '90%', maxWidth: '500px',
    boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
    textAlign: 'center'
  };

  const handleNext = () => {
    onClose();
    navigate('/missions');
  };

  return (
    <div style={overlayStyle}>
      <div style={modalStyle}>
        <h2 style={{ color: isPass ? '#3fb950' : '#ff7b72', fontSize: '32px', margin: '0 0 10px 0' }}>
          MISSION {assessment.status}
        </h2>
        
        <div style={{ textAlign: 'left', marginTop: '20px', backgroundColor: '#21262d', padding: '15px', borderRadius: '8px' }}>
          <h4 style={{ margin: '0 0 5px 0', color: '#c9d1d9' }}>What Went Well</h4>
          <p style={{ fontSize: '14px', color: '#8b949e', margin: '0 0 15px 0' }}>{assessment.wentWell}</p>

          <h4 style={{ margin: '0 0 5px 0', color: '#c9d1d9' }}>What Went Wrong</h4>
          <p style={{ fontSize: '14px', color: '#8b949e', margin: 0 }}>{assessment.wentWrong}</p>
        </div>

        <button
          onClick={handleNext}
          style={{
            marginTop: '25px', width: '100%', padding: '12px',
            backgroundColor: '#238636', color: '#fff',
            border: 'none', borderRadius: '6px',
            cursor: 'pointer', fontWeight: 'bold', fontSize: '16px'
          }}
        >
          {assessment.nextMission === 'completed' ? 'Return to Dashboard' : 'Continue Training'}
        </button>
      </div>
    </div>
  );
};

export default MissionDebriefModal;
