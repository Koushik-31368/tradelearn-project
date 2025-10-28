import React, { useState } from 'react';
import { strategies, quizQuestions } from '../data/strategyData';
import './StrategiesPage.css';
import TrySimulatorPage from './TrySimulatorPage';

const StrategiesPage = () => {
  const [activeTab, setActiveTab] = useState('library');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [selectedStrategy, setSelectedStrategy] = useState(null);
  const [quizStarted, setQuizStarted] = useState(false);
  const [currentQuestion, setCurrentQuestion] = useState(0);
  const [quizAnswers, setQuizAnswers] = useState([]);
  const [quizResults, setQuizResults] = useState(null);

  // Filter strategies by category
  const filteredStrategies = selectedCategory === 'all'
    ? strategies
    : strategies.filter(s => s.category === selectedCategory);

  const categories = ['all', ...new Set(strategies.map(s => s.category))];

  // Handle quiz answer
  const handleQuizAnswer = (answer) => {
    const newAnswers = [...quizAnswers, answer];
    setQuizAnswers(newAnswers);

    if (currentQuestion < quizQuestions.length - 1) {
      setCurrentQuestion(currentQuestion + 1);
    } else {
      calculateQuizResults(newAnswers);
    }
  };

  // Calculate quiz results
  const calculateQuizResults = (answers) => {
    const scores = {};
    
    answers.forEach(answer => {
      Object.entries(answer.score).forEach(([key, value]) => {
        scores[key] = (scores[key] || 0) + value;
      });
    });

    // Map scores to strategy recommendations
    const recommendations = strategies.map(strategy => {
      let matchScore = 0;
      
      // Risk matching
      if (scores.low >= 5 && strategy.risk === 'LOW') matchScore += 30;
      if (scores.medium >= 5 && strategy.risk === 'MEDIUM') matchScore += 30;
      if (scores.high >= 5 && strategy.risk === 'HIGH') matchScore += 30;
      
      // Time matching
      if (scores.dayTrading >= 4 && strategy.category === 'Day Trading') matchScore += 25;
      if (scores.swing >= 4 && strategy.category === 'Swing Trading') matchScore += 25;
      if (scores.longTerm >= 4 && strategy.category === 'Long-term Investing') matchScore += 25;
      
      // Experience matching
      if (scores.beginner >= 3 && strategy.bestFor.includes('Beginner')) matchScore += 20;
      if (scores.intermediate >= 3) matchScore += 15;
      if (scores.advanced >= 3 && strategy.bestFor.includes('Experienced')) matchScore += 20;
      
      // Capital matching
      const capitalNum = parseInt(strategy.capital.replace(/[^0-9]/g, ''));
      if (scores.small >= 3 && capitalNum <= 50000) matchScore += 15;
      if (scores.medium >= 3 && capitalNum <= 500000) matchScore += 15;
      if (scores.large >= 3) matchScore += 15;
      
      return { ...strategy, matchScore: Math.min(matchScore, 100) };
    });

    const topThree = recommendations
      .sort((a, b) => b.matchScore - a.matchScore)
      .slice(0, 3);

    setQuizResults(topThree);
  };

  // Reset quiz
  const resetQuiz = () => {
    setQuizStarted(false);
    setCurrentQuestion(0);
    setQuizAnswers([]);
    setQuizResults(null);
  };

  return (
    <div className="strategies-page">
      <div className="container">
        
        {/* Hero Section */}
        <section className="hero-section">
          <h1>🎯 Trading Strategies</h1>
          <p>Master the art of trading with proven strategies</p>
        </section>

        {/* Tab Navigation */}
        <div className="tab-navigation">
          <button 
            className={`tab-btn ${activeTab === 'library' ? 'active' : ''}`}
            onClick={() => setActiveTab('library')}
          >
            📚 Strategy Library
          </button>
          <button 
            className={`tab-btn ${activeTab === 'simulator' ? 'active' : ''}`}
            onClick={() => setActiveTab('simulator')}
          >
            Try Simulator
          </button>
          <button 
            className={`tab-btn ${activeTab === 'quiz' ? 'active' : ''}`}
            onClick={() => setActiveTab('quiz')}
          >
            🧠 Find Your Strategy
          </button>
        </div>

        {/* Strategy Library Tab */}
        {activeTab === 'library' && (
          <section className="library-section">
            <div className="category-filters">
              {categories.map(cat => (
                <button
                  key={cat}
                  className={`filter-btn ${selectedCategory === cat ? 'active' : ''}`}
                  onClick={() => setSelectedCategory(cat)}
                >
                  {cat === 'all' ? 'All Strategies' : cat}
                </button>
              ))}
            </div>

            <div className="strategies-grid">
              {filteredStrategies.map(strategy => (
                <div key={strategy.id} className="strategy-card">
                  <div className="strategy-header">
                    <span className="strategy-icon">{strategy.icon}</span>
                    <h3>{strategy.name}</h3>
                    <span className={`risk-badge ${strategy.risk.toLowerCase()}`}>
                      {strategy.risk}
                    </span>
                  </div>
                  
                  <div className="strategy-body">
                    <p className="time-horizon">⏱️ {strategy.timeHorizon}</p>
                    <p className="description">{strategy.description}</p>
                    
                    <div className="pros-cons">
                      <div className="pros">
                        <h4>✅ Pros:</h4>
                        <ul>
                          {strategy.pros.map((pro, i) => <li key={i}>{pro}</li>)}
                        </ul>
                      </div>
                      <div className="cons">
                        <h4>❌ Cons:</h4>
                        <ul>
                          {strategy.cons.map((con, i) => <li key={i}>{con}</li>)}
                        </ul>
                      </div>
                    </div>

                    <div className="strategy-meta">
                      <p><strong>Best for:</strong> {strategy.bestFor}</p>
                      <p><strong>Capital needed:</strong> {strategy.capital}</p>
                    </div>
                  </div>

                  <button 
                    className="try-btn"
                    onClick={() => {
                      setSelectedStrategy(strategy);
                      setActiveTab('simulator');
                    }}
                  >
                    Try This Strategy →
                  </button>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* Simulator Tab */}
        {activeTab === 'simulator' && (
          <TrySimulatorPage />
        )}

        {/* Quiz Tab */}
        {activeTab === 'quiz' && (
          <section className="quiz-section">
            {!quizStarted && !quizResults && (
              <div className="quiz-intro">
                <h2>🧠 Find Your Perfect Strategy</h2>
                <p>Answer 5 quick questions to get personalized strategy recommendations</p>
                <button className="start-quiz-btn" onClick={() => setQuizStarted(true)}>
                  Start Quiz
                </button>
              </div>
            )}

            {quizStarted && !quizResults && (
              <div className="quiz-container">
                <div className="quiz-progress">
                  <div 
                    className="progress-bar" 
                    style={{ width: `${((currentQuestion + 1) / quizQuestions.length) * 100}%` }}
                  ></div>
                  <p className="progress-text">Question {currentQuestion + 1} of {quizQuestions.length}</p>
                </div>

                <div className="quiz-question">
                  <h3>{quizQuestions[currentQuestion].question}</h3>
                  <div className="quiz-options">
                    {quizQuestions[currentQuestion].options.map((option, index) => (
                      <button
                        key={index}
                        className="quiz-option"
                        onClick={() => handleQuizAnswer(option)}
                      >
                        {option.text}
                      </button>
                    ))}
                  </div>
                </div>
              </div>
            )}

            {quizResults && (
              <div className="quiz-results">
                <h2>🎉 Your Strategy Matches!</h2>
                <p className="results-intro">Based on your answers, here are your top 3 recommended strategies:</p>
                
                <div className="results-grid">
                  {quizResults.map((result, index) => (
                    <div key={result.id} className={`result-card rank-${index + 1}`}>
                      <div className="rank-badge">#{index + 1}</div>
                      <div className="match-score">
                        <div className="score-circle" style={{background: `conic-gradient(var(--primary-green) ${result.matchScore}%, transparent 0)`}}>
                          <span>{result.matchScore}%</span>
                        </div>
                      </div>
                      <h3>{result.icon} {result.name}</h3>
                      <p className="match-reason">
                        {index === 0 && "Perfect match for your profile!"}
                        {index === 1 && "Great secondary option"}
                        {index === 2 && "Worth considering"}
                      </p>
                      <div className="result-details">
                        <p><strong>Risk:</strong> {result.risk}</p>
                        <p><strong>Time:</strong> {result.timeHorizon}</p>
                        <p><strong>Capital:</strong> {result.capital}</p>
                      </div>
                      <button 
                        className="learn-more-btn"
                        onClick={() => {
                          setSelectedStrategy(result);
                          setActiveTab('library');
                        }}
                      >
                        Learn More
                      </button>
                    </div>
                  ))}
                </div>

                <button className="retake-btn" onClick={resetQuiz}>
                  Retake Quiz
                </button>
              </div>
            )}
          </section>
        )}

      </div>
    </div>
  );
};

export default StrategiesPage;;