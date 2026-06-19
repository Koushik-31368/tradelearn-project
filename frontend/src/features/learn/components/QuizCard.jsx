import React, { useState } from 'react';
import './QuizCard.css';

const QuizCard = ({ question, options, correctIndex, explanation }) => {
  const [selected, setSelected] = useState(null);
  const [showResult, setShowResult] = useState(false);

  const handleSelect = (index) => {
    if (showResult) return;
    setSelected(index);
  };

  const handleCheck = () => {
    if (selected === null) return;
    setShowResult(true);
  };

  const handleReset = () => {
    setSelected(null);
    setShowResult(false);
  };

  const isCorrect = selected === correctIndex;

  return (
    <div className="quiz-card">
      <div className="quiz-card__header">
        <span className="quiz-card__badge">Quiz</span>
        <span className="quiz-card__label">Test Your Knowledge</span>
      </div>

      <p className="quiz-card__question">{question}</p>

      <div className="quiz-card__options">
        {options.map((option, i) => {
          let stateClass = '';
          if (showResult && i === correctIndex) stateClass = ' quiz-option--correct';
          else if (showResult && i === selected && !isCorrect) stateClass = ' quiz-option--wrong';
          else if (i === selected && !showResult) stateClass = ' quiz-option--selected';

          return (
            <button
              key={i}
              className={`quiz-option${stateClass}`}
              onClick={() => handleSelect(i)}
              disabled={showResult}
            >
              <span className="quiz-option__marker">
                {String.fromCharCode(65 + i)}
              </span>
              <span className="quiz-option__text">{option}</span>
              {showResult && i === correctIndex && (
                <span className="quiz-option__icon">✓</span>
              )}
              {showResult && i === selected && !isCorrect && (
                <span className="quiz-option__icon quiz-option__icon--wrong">✗</span>
              )}
            </button>
          );
        })}
      </div>

      {showResult && (
        <div className={`quiz-card__result ${isCorrect ? 'quiz-card__result--correct' : 'quiz-card__result--wrong'}`}>
          <span className="quiz-card__result-icon">
            {isCorrect ? '🎯' : '💡'}
          </span>
          <div>
            <strong>{isCorrect ? 'Correct!' : 'Not quite.'}</strong>
            {explanation && <p className="quiz-card__explanation">{explanation}</p>}
          </div>
        </div>
      )}

      <div className="quiz-card__actions">
        {!showResult ? (
          <button
            className="quiz-card__btn quiz-card__btn--check"
            onClick={handleCheck}
            disabled={selected === null}
          >
            Check Answer
          </button>
        ) : (
          <button className="quiz-card__btn quiz-card__btn--retry" onClick={handleReset}>
            Try Again
          </button>
        )}
      </div>
    </div>
  );
};

export default QuizCard;
