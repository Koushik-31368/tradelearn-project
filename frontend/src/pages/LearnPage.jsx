import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { backendUrl, authHeaders } from '../utils/api';
import { useAuth } from '../context/AuthContext';
import './LearnPage.css';

const PATHS = [
  {
    id: 'basics',
    title: 'Trading Basics',
    description: 'Learn the foundational concepts of the stock market.',
    color: '#10b981', // green
    lessons: [
      { id: 'basics_1', title: 'What is a Stock?', type: 'lesson', icon: '📊' },
      { id: 'basics_2', title: 'The Stock Market', type: 'lesson', icon: '🏛️' },
      { id: 'basics_3', title: 'How Prices Move', type: 'lesson', icon: '📈' },
      { id: 'basics_4', title: 'Candlesticks', type: 'lesson', icon: '🕯️' },
      { id: 'basics_5', title: 'Volume', type: 'lesson', icon: '📉' },
      { id: 'basics_quiz', title: 'Basics Quiz', type: 'quiz', icon: '📝' }
    ]
  },
  {
    id: 'realworld',
    title: 'Real World Market',
    description: 'Understand how macro factors affect prices.',
    color: '#3b82f6', // blue
    lessons: [
      { id: 'realworld_1', title: 'Interest Rates', type: 'lesson', icon: '🏦' },
      { id: 'realworld_2', title: 'Inflation', type: 'lesson', icon: '💸' },
      { id: 'realworld_3', title: 'Bull vs Bear', type: 'lesson', icon: '🐂' },
      { id: 'realworld_4', title: 'Institutions', type: 'lesson', icon: '🏢' },
      { id: 'realworld_quiz', title: 'Market Quiz', type: 'quiz', icon: '📝' }
    ]
  },
  {
    id: 'advanced',
    title: 'Advanced Concepts',
    description: 'Master risk management and market structure.',
    color: '#8b5cf6', // purple
    lessons: [
      { id: 'advanced_1', title: 'Risk Management', type: 'lesson', icon: '🛡️' },
      { id: 'advanced_2', title: 'Stop Loss', type: 'lesson', icon: '🚫' },
      { id: 'advanced_3', title: 'Risk/Reward', type: 'lesson', icon: '⚖️' },
      { id: 'advanced_4', title: 'Drawdown', type: 'lesson', icon: '📉' },
      { id: 'advanced_quiz', title: 'Advanced Quiz', type: 'quiz', icon: '📝' }
    ]
  },
  {
    id: 'psychology',
    title: 'Trading Psychology',
    description: 'Control your emotions and trade with discipline.',
    color: '#f59e0b', // orange
    lessons: [
      { id: 'psychology_1', title: 'Fear', type: 'lesson', icon: '😨' },
      { id: 'psychology_2', title: 'Greed', type: 'lesson', icon: '🤑' },
      { id: 'psychology_3', title: 'Overtrading', type: 'lesson', icon: '🔄' },
      { id: 'psychology_4', title: 'FOMO', type: 'lesson', icon: '📱' },
      { id: 'psychology_quiz', title: 'Psychology Quiz', type: 'quiz', icon: '📝' }
    ]
  }
];

const LearnPage = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [completedLessons, setCompletedLessons] = useState([]);
  const [loading, setLoading] = useState(true);
  const [xpPopup, setXpPopup] = useState(null);

  useEffect(() => {
    if (user) {
      fetchProgress();
    } else {
      setLoading(false);
    }
  }, [user]);

  const fetchProgress = async () => {
    try {
      const res = await fetch(backendUrl('/api/learning/progress'), { headers: authHeaders() });
      if (res.ok) {
        const data = await res.json();
        setCompletedLessons(data);
      }
    } catch (err) {
      console.error('Failed to fetch progress', err);
    } finally {
      setLoading(false);
    }
  };

  const completeLesson = async (lessonId, isQuiz) => {
    if (!user) return navigate('/login');
    if (completedLessons.includes(lessonId)) return; // Already completed

    try {
      await fetch(backendUrl(`/api/learning/complete/${lessonId}`), {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ isQuiz })
      });
      setCompletedLessons([...completedLessons, lessonId]);
      showXpPopup(isQuiz ? 10 : 20);
    } catch (err) {
      console.error(err);
    }
  };

  const showXpPopup = (amount) => {
    setXpPopup(`+${amount} XP`);
    setTimeout(() => setXpPopup(null), 2500);
  };

  if (!user) {
    return (
      <div className="learn-map-page">
        <p style={{ textAlign: 'center', marginTop: '100px' }}>
          Please <span style={{ color: '#00ff88', cursor: 'pointer' }} onClick={() => navigate('/login')}>log in</span> to start your learning journey.
        </p>
      </div>
    );
  }

  if (loading) return <div className="learn-map-page"><div className="learn-spinner"></div></div>;

  let globalLessonIndex = 0;
  let firstLockedEncountered = false;

  return (
    <div className="learn-map-page">
      {xpPopup && (
        <div className="xp-popup-overlay">
          <div className="xp-popup-content">{xpPopup}</div>
        </div>
      )}

      <div className="learn-header">
        <h1 className="learn-title">Your Journey</h1>
        <div className="learn-progress">
          <div className="learn-progress-bar">
            <div 
              className="learn-progress-fill" 
              style={{ width: `${(completedLessons.length / 20) * 100}%` }}
            ></div>
          </div>
          <span className="learn-progress-text">{completedLessons.length} / 20</span>
        </div>
      </div>

      <div className="learn-map-container">
        {PATHS.map((path, pathIndex) => {
          return (
            <div key={path.id} className="learn-path-section">
              <div className="path-header" style={{ borderBottomColor: path.color }}>
                <h2 style={{ color: path.color }}>Unit {pathIndex + 1}: {path.title}</h2>
                <p>{path.description}</p>
              </div>

              <div className="path-nodes">
                {path.lessons.map((lesson, lessonIndex) => {
                  const isCompleted = completedLessons.includes(lesson.id);
                  let isLocked = false;
                  
                  if (!isCompleted) {
                    if (!firstLockedEncountered) {
                      firstLockedEncountered = true; // This is the current active lesson
                    } else {
                      isLocked = true;
                    }
                  }

                  const isActive = !isCompleted && !isLocked;
                  globalLessonIndex++;

                  // Calculate alternating positions
                  const offsetX = Math.sin(lessonIndex * 1.5) * 60;

                  return (
                    <div 
                      key={lesson.id} 
                      className={`path-node-wrapper ${isLocked ? 'locked' : ''} ${isActive ? 'active' : ''}`}
                      style={{ transform: `translateX(${offsetX}px)` }}
                    >
                      <button 
                        className="path-node-btn"
                        style={{
                          backgroundColor: isCompleted ? path.color : (isLocked ? '#1e2733' : '#2d3748'),
                          borderColor: isCompleted ? path.color : (isLocked ? '#30363d' : path.color),
                          boxShadow: isActive ? `0 0 0 6px rgba(255,255,255,0.1), 0 0 20px ${path.color}` : 'none'
                        }}
                        onClick={() => {
                          if (!isLocked) {
                            // In a real app, this would open a modal with content.
                            // For this MVP, clicking it just "completes" it.
                            completeLesson(lesson.id, lesson.type === 'quiz');
                          }
                        }}
                      >
                        <span className="node-icon">{isLocked ? '🔒' : lesson.icon}</span>
                      </button>
                      <div className="node-tooltip">{lesson.title}</div>
                      {/* Connection line */}
                      {lessonIndex < path.lessons.length - 1 && (
                        <div className="path-line" style={{ backgroundColor: isCompleted ? path.color : '#30363d' }}></div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default LearnPage;
