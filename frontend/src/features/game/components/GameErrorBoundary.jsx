import React from 'react';

/**
 * ErrorBoundary for the Game page.
 *
 * Catches uncaught React render/lifecycle errors inside the game UI
 * (e.g. TypeError in LiveScoreboard.computeEquity, StockChart library,
 *  or any component receiving an unexpected WS payload shape)
 * and renders a visible error card instead of a blank page.
 *
 * Usage:
 *   <GameErrorBoundary gameId={gameId}>
 *     <GamePage />
 *   </GameErrorBoundary>
 */
export class GameErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null, info: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    console.error('[GameErrorBoundary] Caught render error:', error, info);
    this.setState({ info });
  }

  handleReload = () => {
    this.setState({ hasError: false, error: null, info: null });
  };

  render() {
    if (!this.state.hasError) return this.props.children;

    const { error, info } = this.state;
    const { onNavigateBack } = this.props;

    return (
      <div style={{
        minHeight: '100vh',
        background: '#0d1117',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '32px',
      }}>
        <div style={{
          background: '#161b22',
          border: '1px solid #f85149',
          borderRadius: '12px',
          padding: '32px',
          maxWidth: '600px',
          width: '100%',
        }}>
          <div style={{ fontSize: '1.5rem', marginBottom: '8px' }}>⚠️ Game Crashed</div>
          <p style={{ color: '#8b949e', marginBottom: '16px', fontSize: '0.9rem' }}>
            An unexpected error occurred in the game UI. Your match is still running on the server.
          </p>

          <div style={{
            background: '#0d1117',
            border: '1px solid #30363d',
            borderRadius: '8px',
            padding: '12px 16px',
            marginBottom: '20px',
            fontFamily: 'monospace',
            fontSize: '0.8rem',
            color: '#f85149',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
            maxHeight: '200px',
            overflow: 'auto',
          }}>
            {error?.toString()}
            {info?.componentStack && (
              '\n\nComponent stack:' + info.componentStack
            )}
          </div>

          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <button
              onClick={this.handleReload}
              style={{
                background: '#e83e8c',
                border: 'none',
                borderRadius: '8px',
                color: '#fff',
                padding: '10px 20px',
                cursor: 'pointer',
                fontWeight: 700,
                fontSize: '0.85rem',
              }}
            >
              🔄 Try Again
            </button>
            <button
              onClick={() => window.location.reload()}
              style={{
                background: '#21262d',
                border: '1px solid #30363d',
                borderRadius: '8px',
                color: '#e6edf3',
                padding: '10px 20px',
                cursor: 'pointer',
                fontWeight: 600,
                fontSize: '0.85rem',
              }}
            >
              Hard Reload
            </button>
            {onNavigateBack && (
              <button
                onClick={onNavigateBack}
                style={{
                  background: 'transparent',
                  border: '1px solid #30363d',
                  borderRadius: '8px',
                  color: '#8b949e',
                  padding: '10px 20px',
                  cursor: 'pointer',
                  fontSize: '0.85rem',
                }}
              >
                ← Leave Match
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }
}

export default GameErrorBoundary;
