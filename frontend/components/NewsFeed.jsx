// src/components/NewsFeed.jsx
import React, { useState, useEffect } from 'react';
import './NewsFeed.css';

const NewsFeed = () => {
  const [news, setNews] = useState([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState(null);

  // Add your new key from News API here
  const NEWS_API_KEY = 'ed42aa102bf84e61aaf6c1684c4970c6';

  useEffect(() => {
    const fetchIndianNews = async () => {
      setIsLoading(true);
      setError(null);
      try {
        // This query searches for articles about top Indian companies AND the stock market
        const query = encodeURIComponent('(Reliance OR TCS OR HDFC OR Infosys) AND (stock OR market OR shares)');
        const url = `https://newsapi.org/v2/everything?q=${query}&language=en&sortBy=publishedAt&pageSize=15&apiKey=${NEWS_API_KEY}`;
        
        const response = await fetch(url);
        const data = await response.json();

        if (data.status === 'error') {
          setError(data.message);
        } else if (data.articles.length === 0) {
          setError("No relevant news found. Your API key might be invalid or have hit its limit.");
        } else {
          setNews(data.articles);
        }
      } catch (err) {
        setError("Failed to fetch news. Please check your network connection.");
        console.error("Error fetching news:", err);
      }
      setIsLoading(false);
    };

    fetchIndianNews();
  }, []);

  if (isLoading) {
    return <div className="news-container"><h3>Loading Indian Market News...</h3></div>;
  }

  return (
    <div className="news-container">
      <h3>Latest Indian Market News</h3>
      {error && <p className="news-error">{error}</p>}
      <div className="news-list">
        {news.length > 0 && news.map((article, index) => (
          <a key={index} href={article.url} target="_blank" rel="noopener noreferrer" className="news-article">
            {article.urlToImage && <img src={article.urlToImage} alt={article.title} />}
            <div className="article-content">
              <span className="article-source">{article.source.name}</span>
              <h4>{article.title}</h4>
            </div>
          </a>
        ))}
      </div>
    </div>
  );
};

export default NewsFeed;