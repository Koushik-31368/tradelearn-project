import React, { useState } from 'react';
import './LearnPage.css';

const LearnPage = () => {
  const [searchQuery, setSearchQuery] = useState('');
  const [stockData, setStockData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [searchError, setSearchError] = useState('');

  const searchStock = async () => {
    if (!searchQuery.trim()) return;

    setLoading(true);
    setSearchError('');
    setStockData(null);
    try {
      const apiKey = process.env.REACT_APP_ALPHA_VANTAGE_KEY;
      if (!apiKey) {
        setSearchError('Stock search is not configured. Set REACT_APP_ALPHA_VANTAGE_KEY in .env');
        return;
      }
      const res = await fetch(
        `https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=${searchQuery}.BSE&apikey=${apiKey}`
      );
      if (!res.ok) throw new Error('API request failed');
      const json = await res.json();
      const quote = json['Global Quote'];
      if (!quote || !quote['01. symbol']) {
        setSearchError('Stock not found. Try a symbol like TCS, INFY, RELIANCE');
        return;
      }
      setStockData(quote);
    } catch (error) {
      console.error('Error fetching stock:', error);
      setSearchError('Stock lookup failed. Please try again later.');
    } finally {
      setLoading(false);
    }
  };

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

        {/* Stock Search Section */}
        <section className="search-section">
          <h2>Search Stock</h2>
          <div className="search-bar">
            <input
              type="text"
              placeholder="Enter stock symbol (e.g., TCS, INFY, RELIANCE)"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value.toUpperCase())}
              onKeyPress={(e) => e.key === 'Enter' && searchStock()}
            />
            <button onClick={searchStock} disabled={loading}>
              {loading ? 'Searching...' : 'Search'}
            </button>
          </div>

          {stockData && stockData['01. symbol'] && (
            <div className="stock-card">
              <h3>{stockData['01. symbol']}</h3>
              <div className="stock-details">
                <div className="detail-item">
                  <span className="label">Price:</span>
                  <span className="value">₹{parseFloat(stockData['05. price']).toFixed(2)}</span>
                </div>
                <div className="detail-item">
                  <span className="label">Change:</span>
                  <span className={`value ${parseFloat(stockData['09. change']) >= 0 ? 'positive' : 'negative'}`}>
                    {stockData['09. change']} ({stockData['10. change percent']})
                  </span>
                </div>
                <div className="detail-item">
                  <span className="label">High:</span>
                  <span className="value">₹{parseFloat(stockData['03. high']).toFixed(2)}</span>
                </div>
                <div className="detail-item">
                  <span className="label">Low:</span>
                  <span className="value">₹{parseFloat(stockData['04. low']).toFixed(2)}</span>
                </div>
                <div className="detail-item">
                  <span className="label">Volume:</span>
                  <span className="value">{parseInt(stockData['06. volume']).toLocaleString()}</span>
                </div>
              </div>
            </div>
          )}
          {searchError && <p className="search-error">{searchError}</p>}
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
