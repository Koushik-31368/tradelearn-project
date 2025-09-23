// src/components/TopTraders.jsx
import React from 'react';
import './TopTraders.css';

const TopTraders = () => {
  return (
    <section className="top-traders-section">
      <h2>Top Traders This Week</h2>
      <div className="traders-container">
        <div className="trader-card">
          <h3>CryptoKing</h3>
          <p className="profit">+$12,450</p>
        </div>
        <div className="trader-card">
          <h3>WallStWolf</h3>
          <p className="profit">+$9,870</p>
        </div>
        <div className="trader-card">
          <h3>DayTradeDiva</h3>
          <p className="profit">+$7,650</p>
        </div>
        <div className="trader-card">
          <h3>BullMarket</h3>
          <p className="profit">+$6,320</p>
        </div>
        <div className="trader-card">
          <h3>OptionQueen</h3>
          <p className="profit">+$5,980</p>
        </div>
      </div>
    </section>
  );
};

export default TopTraders;