import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './LearnPage.css';

const LearnPage = () => {
  const [news, setNews] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [stockData, setStockData] = useState(null);
  const [loading, setLoading] = useState(false);

  // Fetch Indian stock market news on page load
  useEffect(() => {
    fetchNews();
  }, []);

  const fetchNews = async () => {
    try {
      const response = await axios.get('https://newsapi.org/v2/top-headlines', {
        params: {
          country: 'in',
          category: 'business',
          apiKey: 'ed42aa102bf84e61aaf6c1684c4970c6' // Replace with your free NewsAPI key from newsapi.org
        }
      });
      setNews(response.data.articles.slice(0, 10));
    } catch (error) {
      console.error('Error fetching news:', error);
    }
  };

  const searchStock = async () => {
    if (!searchQuery.trim()) return;
    
    setLoading(true);
    try {
      // Using Alpha Vantage API for stock data
      const response = await axios.get('https://www.alphavantage.co/query', {
        params: {
          function: 'GLOBAL_QUOTE',
          symbol: `${searchQuery}.BSE`, // Indian stock format
          apikey: 'F9HE0WX2CTKBJOGU' // Your existing Alpha Vantage key
        }
      });
      
      setStockData(response.data['Global Quote']);
    } catch (error) {
      console.error('Error fetching stock:', error);
      alert('Stock not found. Try symbol like TCS, INFY, RELIANCE');
    }
    setLoading(false);
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
        </section>

        {/* News Section */}
        <section className="news-section">
          <h2>Latest Indian Stock Market News</h2>
          <div className="news-grid">
            {news.length > 0 ? (
              news.map((article, index) => (
                <div key={index} className="news-card">
                  {article.urlToImage && (
                    <img src={article.urlToImage} alt={article.title} />
                  )}
                  <div className="news-content">
                    <h3>{article.title}</h3>
                    <p>{article.description}</p>
                    <div className="news-meta">
                      <span className="source">{article.source.name}</span>
                      <span className="date">{new Date(article.publishedAt).toLocaleDateString()}</span>
                    </div>
                    <a href={article.url} target="_blank" rel="noopener noreferrer">
                      Read More →
                    </a>
                  </div>
                </div>
              ))
            ) : (
              <p className="no-news">Loading news...</p>
            )}
          </div>
        </section>

      </div>
    </div>
  );
};

export default LearnPage;
