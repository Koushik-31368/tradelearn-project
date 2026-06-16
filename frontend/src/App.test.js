// src/App.test.js
import React from 'react';
import { render, screen } from '@testing-library/react';
import App from './App';

// Mock the socket hook so tests don't attempt real WebSocket connections
jest.mock('./hooks/useGameSocket', () => ({
  __esModule: true,
  default: () => ({
    socketState: 'DISCONNECTED',
    isConnected: false,
    gamePhase: null,
    syncPhaseFromRest: jest.fn(),
    currentCandle: null,
    candleHistory: [],
    candleIndex: 0,
    remaining: 0,
    seedCandle: jest.fn(),
    tradeLog: [],
    emitTrade: jest.fn(),
    statusMessage: '',
    lastError: null,
    disconnectInfo: null,
    scoreboard: null,
    reconnecting: null,
    rematchRequest: null,
    rematchStarted: null,
    publish: jest.fn(),
    reset: jest.fn(),
  }),
  SocketState: { DISCONNECTED: 'DISCONNECTED', CONNECTING: 'CONNECTING', CONNECTED: 'CONNECTED', ERROR: 'ERROR' },
  GamePhase:   { WAITING: 'WAITING', ACTIVE: 'ACTIVE', FINISHED: 'FINISHED', ABANDONED: 'ABANDONED' },
}));

// Mock market API to avoid network calls
jest.mock('./services/marketApi', () => ({
  fetchMarketHistory: jest.fn().mockResolvedValue([]),
}));

test('renders the app without crashing', () => {
  render(<App />);
  // The app always renders the Navbar — verify at least one known nav link appears
  const navLinks = screen.getAllByRole('link');
  expect(navLinks.length).toBeGreaterThan(0);
});

test('renders the homepage hero content', () => {
  render(<App />);
  // Verify the page has some meaningful content
  expect(document.querySelector('.App')).toBeTruthy();
});
