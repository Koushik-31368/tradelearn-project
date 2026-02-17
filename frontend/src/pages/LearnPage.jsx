import React from 'react';
import './LearnPage.css';

const LearnPage = () => {
  return (
    <div className="learn-page">
      <div className="container">
        
        {/* Educational Section */}
        <section className="education-section">
          <h1>Learn Stock Market Basics</h1>
          <div className="education-cards">
            <div className="edu-card">
              <h3>📈 What is a Stock?</h3>
              <p>A stock represents ownership in a company. When you buy a stock, you own a small piece of that company.</p>
            </div>
            <div className="edu-card">
              <h3>💹 NSE vs BSE</h3>
              <p>NSE (National Stock Exchange) and BSE (Bombay Stock Exchange) are India's two main stock exchanges where stocks are traded.</p>
            </div>
            <div className="edu-card">
              <h3>Market Indices</h3>
              <p>Nifty 50 and Sensex are indices that track the performance of top Indian companies.</p>
            </div>
            <div className="edu-card">
              <h3>🎯 Risk & Return</h3>
              <p>Higher potential returns come with higher risk. Diversify your portfolio to manage risk.</p>
            </div>
          </div>
        </section>

        {/* Tips Section */}
        <section className="news-section">
          <h2>Trading Tips</h2>
          <div className="news-grid">
            <div className="news-card">
              <div className="news-content">
                <h3>💡 Start with Paper Trading</h3>
                <p>Practice with our simulator before risking real money. Get comfortable with buy, sell, short, and cover orders.</p>
              </div>
            </div>
            <div className="news-card">
              <div className="news-content">
                <h3>📊 Read the Candlesticks</h3>
                <p>Green candles mean the price went up, red means it went down. Learn to spot patterns like doji, hammer, and engulfing.</p>
              </div>
            </div>
            <div className="news-card">
              <div className="news-content">
                <h3>🛡️ Manage Your Risk</h3>
                <p>Never risk more than 2-5% of your portfolio on a single trade. Use stop-losses and position sizing.</p>
              </div>
            </div>
            <div className="news-card">
              <div className="news-content">
                <h3>🏆 Compete & Learn</h3>
                <p>Challenge other traders in 1v1 matches. Analyze your accuracy, drawdown, and hybrid score to improve.</p>
              </div>
            </div>
          </div>
        </section>

      </div>
    </div>
  );
};

export default LearnPage;
