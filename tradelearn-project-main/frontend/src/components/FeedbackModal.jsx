// src/components/FeedbackModal.jsx
import React from 'react';
import './FeedbackModal.css';

const FeedbackModal = ({ isOpen, onClose, feedbackData }) => {
  if (!isOpen || !feedbackData) {
    return null;
  }

  const { tradeType, stock, news } = feedbackData;
  const tradeColor = tradeType === 'Buy' ? 'positive' : 'negative';

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal-content" onClick={(e) => e.stopPropagation()}>
        <button className="close-button" onClick={onClose}>×</button>
        <h2>Trade Successful!</h2>
        <p className="trade-summary">
          You successfully executed a <span className={tradeColor}>{tradeType}</span> order for {feedbackData.shares} share(s) of <strong>{stock.symbol}</strong>.
        </p>
        
        <div className="feedback-section">
          <h3>Relevant Market News</h3>
          <div className="news-list-modal">
            {news.length > 0 ? news.map((article, index) => (
              <a key={index} href={article.url} target="_blank" rel="noopener noreferrer" className="news-article-modal">
                <div className="article-content-modal">
                  <span className="article-source-modal">{article.source.name}</span>
                  <h4>{article.title}</h4>
                </div>
              </a>
            )) : <p>No recent news found for this stock.</p>}
          </div>
        </div>
      </div>
    </div>
  );
};

export default FeedbackModal;