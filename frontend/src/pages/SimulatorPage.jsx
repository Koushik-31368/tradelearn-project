// src/pages/SimulatorPage.jsx
import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import SimulatorDashboard from '../components/simulator/SimulatorDashboard';
import { useAuth } from '../context/AuthContext';

/**
 * SimulatorPage — entry point for the paper-trading simulator.
 *
 * Responsibilities:
 *  - Set the document title for SEO and browser tab UX
 *  - Guard authenticated routes (redirect to /login if unauthenticated)
 *  - Render the SimulatorDashboard component
 */
const SimulatorPage = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    document.title = 'Simulator — TradeLearn';
  }, []);

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { replace: true, state: { from: '/simulator' } });
    }
  }, [isAuthenticated, navigate]);

  if (!isAuthenticated) return null;

  return <SimulatorDashboard />;
};

export default SimulatorPage;