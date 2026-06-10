// src/pages/OverviewPage.jsx
import React from 'react';
import StockScreener from '../components/StockScreener';
import NewsFeed from '../components/NewsFeed';
import './OverviewPage.css';

const OverviewPage = () => {
  return (
    <div className="overview-page-layout">
      <div className="screener-container">
        <StockScreener />
      </div>
      <div className="news-container-main">
        <NewsFeed />
      </div>
    </div>
  );
};

export default OverviewPage;