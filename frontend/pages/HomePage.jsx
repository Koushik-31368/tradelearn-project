// src/pages/HomePage.jsx
import React from 'react';
import Hero from '../components/Hero';
import TopTraders from '../components/TopTraders'; // 1. Import TopTraders

const HomePage = () => {
  return (
    <div>
      <Hero />
      <TopTraders /> {/* 2. Add the component here */}
    </div>
  );
};

export default HomePage;