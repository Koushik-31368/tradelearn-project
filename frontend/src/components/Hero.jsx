// src/components/Hero.jsx
import React from 'react';
import { useNavigate } from 'react-router-dom';
import './Hero.css';

// IMPORT YOUR IMAGE HERE
// Make sure to replace 'background.jpg' with your exact image file name
import BackgroundImage from '../assets/background.jpg';

const Hero = () => {
  const navigate = useNavigate();

  // This style object will be applied directly to the div
  const heroStyle = {
    backgroundImage: `url(${BackgroundImage})`
  };

  return (
    // Apply the style to the container
    <div className="hero-container" style={heroStyle}>
      <div className="hero-content">
        <h1>Learn. Trade. Grow with <span className="highlight">Confidence.</span></h1>
        <p>Practice trading with real strategies and virtual money – the smart way to start.</p>
        <button className="hero-button" onClick={() => navigate('/register')}>Get Started for Free</button>
      </div>
    </div>
  );
};

export default Hero;