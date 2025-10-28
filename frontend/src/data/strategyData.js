// Strategy Library Data
export const strategies = [
  {
    id: 1,
    slug: 'scalping',
    category: 'Day Trading',
    name: 'Scalping',
    icon: '⚡',
    risk: 'HIGH',
    timeHorizon: 'Minutes to Hours',
    description: 'Quick trades capturing small price movements throughout the day. Requires intense focus and fast decision-making.',
    pros: ['Fast profits', 'Multiple opportunities daily', 'Low overnight risk'],
    cons: ['High stress', 'Requires constant monitoring', 'Transaction costs add up'],
    bestFor: 'Full-time traders with quick reflexes',
    capital: '₹50,000+'
  },
  {
    id: 2,
    slug: 'momentum-trading',
    category: 'Day Trading',
    name: 'Momentum Trading',
    icon: '🚀',
    risk: 'HIGH',
    timeHorizon: 'Hours to Days',
    description: 'Riding strong price movements by identifying stocks with high volume and volatility.',
    pros: ['Catch big moves', 'Clear entry/exit signals', 'Works in trending markets'],
    cons: ['Can reverse quickly', 'Needs market timing', 'False signals common'],
    bestFor: 'Experienced traders who can read market sentiment',
    capital: '₹1,00,000+'
  },
  {
    id: 3,
    slug: 'support-resistance',
    category: 'Swing Trading',
    name: 'Support & Resistance',
    icon: '📊',
    risk: 'MEDIUM',
    timeHorizon: '2-7 Days',
    description: 'Buy at support levels, sell at resistance. Hold positions for days to capture swing moves.',
    pros: ['Less time intensive', 'Clear risk/reward', 'Works in range-bound markets'],
    cons: ['Overnight risk', 'Requires patience', 'False breakouts happen'],
    bestFor: 'Part-time traders with analytical skills',
    capital: '₹50,000+'
  },
  {
    id: 4,
    slug: 'sma-cross',
    category: 'Swing Trading',
    name: 'Moving Average Crossover',
    icon: '〰️',
    risk: 'MEDIUM',
    timeHorizon: '3-10 Days',
    description: 'Buy when short MA crosses above long MA, sell on opposite. Classic technical strategy.',
    pros: ['Simple to understand', 'Objective signals', 'Good for trending markets'],
    cons: ['Lags in choppy markets', 'Multiple false signals', 'Needs confirmation'],
    bestFor: 'Beginners learning technical analysis',
    capital: '₹30,000+'
  },
  {
    id: 5,
    slug: 'buy-hold',
    category: 'Long-term Investing',
    name: 'Buy & Hold',
    icon: '💎',
    risk: 'LOW',
    timeHorizon: 'Months to Years',
    description: 'Invest in quality companies for long-term wealth creation. Ignore short-term volatility.',
    pros: ['Least stressful', 'Tax efficient', 'Compound growth'],
    cons: ['Capital locked', 'Slow returns', 'Requires conviction'],
    bestFor: 'Long-term wealth builders',
    capital: '₹10,000+'
  },
  {
    id: 6,
    slug: 'dividend-investing',
    category: 'Long-term Investing',
    name: 'Dividend Investing',
    icon: '💰',
    risk: 'LOW',
    timeHorizon: 'Years',
    description: 'Focus on stocks with consistent dividend payouts for passive income.',
    pros: ['Regular income', 'Lower volatility', 'Stable companies'],
    cons: ['Lower capital appreciation', 'Tax on dividends', 'Limited growth'],
    bestFor: 'Income-focused investors',
    capital: '₹50,000+'
  },
  {
also
    id: 7,
    slug: 'rsi-reversion',
    category: 'Technical Analysis',
    name: 'RSI Strategy',
    icon: '📈',
    risk: 'MEDIUM',
    timeHorizon: '1-5 Days',
    description: 'Use Relative Strength Index to identify overbought (>70) and oversold (<30) conditions.',
    pros: ['Clear signals', 'Works in all timeframes', 'Easy to backtest'],
    cons: ['Can stay overbought/oversold', 'Needs other indicators', 'Lagging indicator'],
    bestFor: 'Technical traders',
    capital: '₹30,000+'
  },
  {
    id: 8,
    slug: 'macd-strategy',
    category: 'Technical Analysis',
    name: 'MACD Strategy',
    icon: '',
    risk: 'MEDIUM',
    timeHorizon: '2-7 Days',
    description: 'Moving Average Convergence Divergence - buy on bullish crossover, sell on bearish.',
    pros: ['Trend following', 'Momentum indicator', 'Visual signals'],
    cons: ['Lagging', 'False signals in sideways markets', 'Needs confirmation'],
    bestFor: 'Intermediate technical traders',
    capital: '₹40,000+'
  }
];

// Quiz Questions
export const quizQuestions = [
  {
    id: 1,
    question: "What's your risk tolerance?",
    options: [
      { text: '🟢 Conservative - I prefer steady, safe returns', value: 'low', score: { low: 3, medium: 1, high: 0 } },
      { text: '🟡 Moderate - Balanced risk and reward', value: 'medium', score: { low: 1, medium: 3, high: 1 } },
      { text: '🔴 Aggressive - Maximum returns, I can handle volatility', value: 'high', score: { low: 0, medium: 1, high: 3 } }
    ]
  },
  {
    id: 2,
    question: "How much time can you dedicate to trading?",
    options: [
      { text: '⏱️ <1 hour/day - I have a full-time job', value: 'minimal', score: { dayTrading: 0, swing: 2, longTerm: 3 } },
      { text: '⏰ 2-4 hours/day - Part-time focus', value: 'moderate', score: { dayTrading: 1, swing: 3, longTerm: 1 } },
      { text: '🕐 Full-time - Trading is my main activity', value: 'fulltime', score: { dayTrading: 3, swing: 2, longTerm: 0 } }
    ]
  },
  {
    id: 3,
    question: "What's your primary investment goal?",
    options: [
      { text: 'Steady income - Regular returns', value: 'income', score: { dividend: 3, buyHold: 2, dayTrading: 0 } },
      { text: 'Capital growth - Build wealth over time', value: 'growth', score: { dividend: 1, buyHold: 3, dayTrading: 1 } },
      { text: 'Maximum returns - Aggressive wealth building', value: 'maxReturns', score: { dividend: 0, buyHold: 1, dayTrading: 3 } }
    ]
  },
  {
    id: 4,
    question: "How much capital do you have to invest?",
    options: [
      { text: '₹10K-50K - Starting small', value: 'small', score: { small: 3, medium: 0, large: 0 } },
      { text: '₹50K-5L - Moderate capital', value: 'medium', score: { small: 1, medium: 3, large: 1 } },
      { text: '₹5L+ - Significant capital', value: 'large', score: { small: 0, medium: 1, large: 3 } }
    ]
  },
  {
    id: 5,
    question: "What's your trading experience level?",
    options: [
      { text: 'Beginner - Just starting out', value: 'beginner', score: { beginner: 3, intermediate: 0, advanced: 0 } },
      { text: 'Intermediate - Some market knowledge', value: 'intermediate', score: { beginner: 1, intermediate: 3, advanced: 1 } },
      { text: 'Advanced - Experienced trader', value: 'advanced', score: { beginner: 0, intermediate: 1, advanced: 3 } }
    ]
  }
];

