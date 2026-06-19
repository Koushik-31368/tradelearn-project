// src/components/Footer.jsx
import React from 'react';
import { Link } from 'react-router-dom';
import './Footer.css';

const Footer = () => (
  <footer className="site-footer">
    <div className="site-footer__inner">
      <p className="site-footer__copy">&copy; 2026 TradeLearn. All rights reserved.</p>
      <nav className="site-footer__links">
        <Link to="/terms">Terms of Service</Link>
        <Link to="/privacy">Privacy Policy</Link>
        <Link to="/risk-disclosure">Risk Disclosure</Link>
        <a href="mailto:contact@tradelearn.app">Contact</a>
      </nav>
    </div>
  </footer>
);

export default Footer;
