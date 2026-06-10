// frontend/src/data/strategies.js

export const strategyCatalog = [

// ============================================================ 
// STRATEGY 1: SMA CROSSOVER
// ============================================================ 
{
  slug: 'sma-cross',
  name: 'SMA Crossover',
  kind: 'SMA_CROSS',
  tagline: 'Catch the wave when momentum shifts',
  hero: 'Picture two lines racing on a chart: when the fast sprinter overtakes the slow walker, momentum is turning bullish—hop in! When the sprinter tires and drops back, exit before the slide.',
  
  concept: {
    simple: 'This strategy buys when a short-term average (fast SMA) crosses above a long-term average (slow SMA), signaling that recent prices are rising faster than the historical trend. It sells when the fast SMA crosses back below.',
    why: 'Trends persist. Once a stock builds momentum, it often continues for a while. The crossover detects that early momentum flip without trying to catch exact tops or bottoms.'
  },

  rules: {
    entry: 'Go long when SMA(fast) crosses above SMA(slow) from below.',
    exit: 'Close position when SMA(fast) crosses below SMA(slow).',
    position: 'All-in on entry (uses 100% of available capital); flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Strong trending stocks',
        examples: 'RELIANCE during a sectoral rally, TCS during tech bull runs, TATASTEEL in commodity upcycles.',
        why: 'Trends last long enough for the strategy to capture the middle chunk of the move; fewer whipsaws.',
        result: 'Expect 2-4 big wins that cover small losses during brief pullbacks.'
      },
      {
        label: 'Post-breakout momentum',
        examples: 'ADANIPORTS after a consolidation breakout, HDFCBANK resuming uptrend post-correction.',
        why: 'The crossover confirms the breakout is real, not a fake-out.',
        result: 'Entry slightly late but safer; catches sustained follow-through.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Choppy, sideways markets',
        examples: 'NIFTY during narrow ranges, low-beta stocks in consolidation (BRITANNIA, HINDUNILVR in range).',
        why: 'The two averages whip back and forth, triggering false entries and exits repeatedly ("whipsaws").',
        result: 'Many small losses eating into capital; win rate drops below 40%.'
      },
      {
        label: 'Sudden news-driven gaps',
        examples: 'Earnings surprises, policy shocks (like sudden rate hikes), geopolitical events.',
        why: 'Moving averages lag; they react after the gap has already happened.',
        result: 'Misses the initial move or enters right before a reversal.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now with these preset scenarios to see the difference:',
    scenarios: [
      {
        label: 'Trending stock (Reliance bull run)',
        symbol: 'RELIANCE',
        params: { fast: 5, slow: 10 },
        expect: 'You will see 1-2 clear entries catching the uptrend; minimal whipsaws.'
      },
      {
        label: 'Choppy stock (sideways Nifty)',
        symbol: 'NIFTY50',
        params: { fast: 5, slow: 10 },
        expect: 'Multiple quick in-and-out trades; returns near zero or negative.'
      },
      {
        label: 'Slower settings (reduce noise)',
        symbol: 'RELIANCE',
        params: { fast: 10, slow: 20 },
        expect: 'Fewer trades, later entries, but smoother equity curve and higher win rate.'
      }
    ]
  },

  defaults: { initialCapital: 100000, smaFast: 5, smaSlow: 10 },
  params: [
    { key: 'smaFast', label: 'Fast SMA', type: 'number', min: 1, hint: 'Smaller = faster reactions, more trades.' },
    { key: 'smaSlow', label: 'Slow SMA', type: 'number', min: 2, hint: 'Larger = smoother, fewer false signals.' }
  ],

  proTips: [
    'Start with 5/10 for active trading; switch to 10/20 for calmer, fewer trades.',
    'Add a 200-SMA filter: only take longs if price is above the 200-day line to avoid bear-market whipsaws.',
    'Combine with volume: enter only if volume surges on the crossover day.'
  ]
},

// ============================================================ 
// STRATEGY 2: RSI MEAN REVERSION
// ============================================================ 
{
  slug: 'rsi-reversion',
  name: 'RSI Mean Reversion',
  kind: 'RSI',
  comingSoon: true,
  tagline: 'Buy the dip, sell the rip—when prices stretch too far',
  hero: 'Imagine a rubber band: stretch it too low (RSI < 30), it snaps back up—buy there. Stretch it too high (RSI > 70), it contracts—sell. This hunts oversold bounces and overbought pullbacks.',

  concept: {
    simple: 'The Relative Strength Index (RSI) measures how overbought or oversold a stock is on a 0-100 scale. Values below 30 suggest "oversold" (bounce likely); above 70 suggest "overbought" (pullback likely).',
    why: 'Prices oscillate around a mean. Extreme moves often correct. RSI detects those extremes numerically.'
  },

  rules: {
    entry: 'Buy when RSI crosses below the lower threshold (e.g., 30) from above.',
    exit: 'Sell when RSI crosses above the upper threshold (e.g., 70).',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Range-bound, oscillating stocks',
        examples: 'HINDUNILVR, ITC, defensive sectors during market calm.',
        why: 'These stocks bounce between support/resistance without sustained trends; RSI captures the rhythm.',
        result: 'Frequent small wins as price ping-pongs within the range.'
      },
      {
        label: 'Post-panic dips in quality stocks',
        examples: 'HDFCBANK after a macro scare, INFY after a sector sell-off.',
        why: 'Strong stocks that dip to RSI < 30 often recover quickly.',
        result: 'Sharp bounce trades with 5-10% gains in days.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Strong trending markets',
        examples: 'RELIANCE in a 3-month uptrend, TATAMOTORS during a bull run.',
        why: 'RSI can stay "overbought" (>70) for weeks in a strong trend; selling early kills profits.',
        result: 'Exits way too soon, misses the bulk of the move.'
      },
      {
        label: 'Falling knives (weak fundamentals)',
        examples: 'Stocks with earnings miss, debt issues, or sector collapse.',
        why: 'RSI < 30 doesn\'t mean the bottom is in—price can keep falling ("oversold can get more oversold").',
        result: 'Buys into continued decline; repeated losses.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now with these scenarios:',
    scenarios: [
      {
        label: 'Range-bound stock (ITC sideways)',
        symbol: 'ITC',
        params: { rsiPeriod: 14, lower: 30, upper: 70 },
        expect: 'Clean bounces at RSI 30; sells near RSI 70; positive returns.'
      },
      {
        label: 'Trending stock (Reliance bull)',
        symbol: 'RELIANCE',
        params: { rsiPeriod: 14, lower: 30, upper: 70 },
        expect: 'RSI stays above 50; few entries, early exits—underperforms buy-and-hold.'
      }
    ]
  },

  defaults: { initialCapital: 100000, rsiPeriod: 14, lower: 30, upper: 70 },
  params: [
    { key: 'rsiPeriod', label: 'RSI Period', type: 'number', min: 2, hint: '14 is standard; lower = more sensitive.' },
    { key: 'lower', label: 'Lower (Buy <)', type: 'number', min: 1, max: 50, hint: '30 is classic oversold.' },
    { key: 'upper', label: 'Upper (Sell >)', type: 'number', min: 50, max: 99, hint: '70 is classic overbought.' }
  ],

  proTips: [
    'Use 20/80 thresholds in strong trends to avoid premature exits.',
    'Combine with support/resistance: only buy RSI < 30 if price is near a known support level.',
    'Check volume: if RSI < 30 with low volume, bounce may be weak.'
  ]
},

// ============================================================ 
// STRATEGY 3: BOLLINGER BAND BOUNCE
// ============================================================ 
{
  slug: 'bollinger-bounce',
  name: 'Bollinger Band Bounce',
  kind: 'BOLLINGER',
  comingSoon: true,
  tagline: 'Ride the rubber band—buy at the bottom band, sell at the top',
  hero: 'Imagine price bouncing inside a channel like a ball in a tunnel. When it hits the lower wall (bottom band), it bounces up—buy. When it hits the upper wall, it bounces down—sell. Perfect for range traders.',

  concept: {
    simple: 'Bollinger Bands draw a moving average with two bands above and below (±2 standard deviations). When price touches the lower band, it\'s statistically "cheap"; when it touches the upper band, it\'s "expensive." We buy low touches and sell high touches.',
    why: 'Prices tend to revert to the mean after extreme deviations. The bands quantify what "extreme" means for each stock dynamically.'
  },

  rules: {
    entry: 'Buy when price touches or crosses below the lower Bollinger Band.',
    exit: 'Sell when price touches or crosses above the upper Bollinger Band.',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Stable, range-bound stocks',
        examples: 'HINDUNILVR, ITC, NESTLEIND—consumer staples that trade in predictable ranges.',
        why: 'These stocks oscillate around a stable mean without breakout trends, making band touches reliable reversal signals.',
        result: '6-8 profitable round-trips per year with 3-5% gains each.'
      },
      {
        label: 'Low-volatility periods',
        examples: 'NIFTY during calm market phases, defensive sectors during stable macro.',
        why: 'Bands tighten during low volatility, making touches more meaningful and bounces sharper.',
        result: 'High win rate (60-70%) as price reliably snaps back to the middle.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Strong breakouts and trends',
        examples: 'RELIANCE breaking out of consolidation, TATASTEEL in a commodity supercycle.',
        why: 'Price can "walk the band" for weeks—touching the upper band repeatedly without reversing. Selling early kills huge gains.',
        result: 'Exits way too soon; misses 20-30% upside moves.'
      },
      {
        label: 'High-volatility whipsaws',
        examples: 'Earnings season, RBI policy days, geopolitical shocks.',
        why: 'Bands widen drastically; touches become frequent and meaningless, triggering false signals.',
        result: 'Whipsawed back and forth; small losses accumulate.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now with these scenarios:',
    scenarios: [
      {
        label: 'Range-bound stock (ITC)',
        symbol: 'ITC',
        params: { period: 20, stdDev: 2 },
        expect: 'Clean bounces at lower band; sells at upper band; steady 4-6% gains.'
      },
      {
        label: 'Trending stock (Reliance)',
        symbol: 'RELIANCE',
        params: { period: 20, stdDev: 2 },
        expect: 'Price walks the upper band; strategy exits early, misses bulk of trend.'
      }
    ]
  },

  defaults: { initialCapital: 100000, period: 20, stdDev: 2 },
  params: [
    { key: 'period', label: 'Period', type: 'number', min: 5, hint: '20 is standard; higher = smoother bands.' },
    { key: 'stdDev', label: 'Std Dev', type: 'number', min: 1, max: 3, hint: '2 = 95% confidence; 3 = wider bands.' }
  ],

  proTips: [
    'Combine with RSI: only buy lower band touch if RSI < 30 for double confirmation.',
    'Use tighter bands (1.5 std dev) in low-volatility stocks for more entries.',
    'Avoid during earnings weeks—bands lose predictive power during high volatility.'
  ]
},

// ============================================================ 
// STRATEGY 4: MACD MOMENTUM
// ============================================================ 
{
  slug: 'macd-momentum',
  name: 'MACD Momentum',
  kind: 'MACD',
  comingSoon: true,
  tagline: 'Catch explosive moves when momentum crosses zero',
  hero: 'Think of MACD as a momentum speedometer. When the fast line crosses above the slow line and both are positive, the engine is revving—go long. When it crosses down, the engine stalls—exit. Ideal for trend-followers.',

  concept: {
    simple: 'MACD (Moving Average Convergence Divergence) measures the difference between a 12-day and 26-day EMA, then smooths it with a 9-day signal line. When MACD crosses above the signal, momentum is turning positive.',
    why: 'Trends accelerate before price does. MACD detects this acceleration early, giving you an edge before the crowd notices.'
  },

  rules: {
    entry: 'Buy when MACD line crosses above the signal line.',
    exit: 'Sell when MACD line crosses below the signal line.',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Strong trending stocks',
        examples: 'TCS during tech rallies, TATAMOTORS in auto upcycles, HDFCBANK post-correction.',
        why: 'MACD captures the acceleration phase of trends, entering earlier than simple moving averages.',
        result: 'Catches 70-80% of major moves with fewer whipsaws than RSI.'
      },
      {
        label: 'Post-consolidation breakouts',
        examples: 'RELIANCE after 3-month range, INFY breaking multi-year highs.',
        why: 'MACD histogram expansion confirms genuine momentum, filtering fake breakouts.',
        result: 'High-confidence entries; follow-through rate above 65%.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Choppy, sideways markets',
        examples: 'NIFTY in 500-point ranges, low-beta stocks during earnings lulls.',
        why: 'MACD crossovers happen frequently but lead nowhere, causing repeated small losses.',
        result: 'Win rate drops below 40%; death by a thousand cuts.'
      },
      {
        label: 'Sudden reversals',
        examples: 'RBI surprise rate hikes, geopolitical shocks, earnings misses.',
        why: 'MACD lags; by the time it crosses down, price has already tanked 5-10%.',
        result: 'Late exits; larger drawdowns than anticipated.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now:',
    scenarios: [
      {
        label: 'Trending stock (TCS bull run)',
        symbol: 'TCS',
        params: { fast: 12, slow: 26, signal: 9 },
        expect: '2-3 clean entries catching major legs; minimal noise.'
      },
      {
        label: 'Sideways stock (Nifty range)',
        symbol: 'NIFTY50',
        params: { fast: 12, slow: 26, signal: 9 },
        expect: '8-10 whipsaws; returns near zero or negative.'
      }
    ]
  },

  defaults: { initialCapital: 100000, fast: 12, slow: 26, signal: 9 },
  params: [
    { key: 'fast', label: 'Fast EMA', type: 'number', min: 5, hint: '12 is standard.' },
    { key: 'slow', label: 'Slow EMA', type: 'number', min: 12, hint: '26 is standard.' },
    { key: 'signal', label: 'Signal Line', type: 'number', min: 5, hint: '9 is standard.' }
  ],

  proTips: [
    'Wait for MACD histogram to flip positive before entering for stronger confirmation.',
    'Use 5/35/5 settings on intraday charts for day-trading momentum.',
    'Combine with 200-SMA: only take longs if price is above 200-day line.'
  ]
},

// ============================================================ 
// STRATEGY 5: 52-WEEK BREAKOUT
// ============================================================ 
{
  slug: 'breakout-52week',
  name: '52-Week Breakout',
  kind: 'BREAKOUT',
  comingSoon: true,
  tagline: 'Ride the rocket when price breaks all-time highs',
  hero: 'When a stock hits a new 52-week high, it means every single person who bought in the past year is now profitable—no one is underwater trying to \"get out even.\" That\'s fuel for explosive continuation.',

  concept: {
    simple: 'Buy stocks when they break above their 52-week high. No overhead supply (trapped sellers) means price can run unimpeded. Momentum follows momentum.',
    why: 'Breakouts to new highs attract attention, media coverage, and institutional money. Psychology shifts from fear to greed, creating self-fulfilling buying pressure.'
  },

  rules: {
    entry: 'Buy when price closes above the 52-week high with above-average volume.',
    exit: 'Sell when price falls below a 10-day EMA or shows first sign of exhaustion (long upper wick, volume spike down).',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Strong bull markets',
        examples: 'RELIANCE during 2020-21 rally, TCS breaking multi-year consolidation, TITAN in jewelry supercycle.',
        why: 'Rising tide lifts all boats. Breakouts in bull markets enjoy sustained follow-through.',
        result: 'Average 15-25% gain per winner; 3-4 home runs per year.'
      },
      {
        label: 'Sector rotation plays',
        examples: 'TATASTEEL when commodity cycle turns, ICICIBANK when financials lead, IT stocks in outsourcing boom.',
        why: 'Sector leaders breaking out first attract flows from entire sector.',
        result: 'First-mover advantage; 20%+ gains in 2-3 months.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Bear markets and corrections',
        examples: 'Any stock during 2022 rate-hike selloff, sectors in structural decline.',
        why: 'Breakouts in weak markets are often traps; price reverses quickly after breakout.',
        result: 'Many false starts; 60%+ of breakouts fail.'
      },
      {
        label: 'Low-volume, illiquid stocks',
        examples: 'Small-cap stocks with thin float, penny stocks.',
        why: 'Lack of institutional interest means no follow-through after breakout.',
        result: 'Quick reversal; stuck in losing position.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now:',
    scenarios: [
      {
        label: 'Bull market breakout (Reliance 2020)',
        symbol: 'RELIANCE',
        params: { lookback: 252, volFilter: 1.5 },
        expect: 'Clean breakout; 20%+ follow-through; smooth ride.'
      },
      {
        label: 'Bear market trap (2022 correction)',
        symbol: 'NIFTY50',
        params: { lookback: 252, volFilter: 1.5 },
        expect: 'Multiple false breakouts; quick reversals; losses.'
      }
    ]
  },

  defaults: { initialCapital: 100000, lookback: 252, volFilter: 1.5 },
  params: [
    { key: 'lookback', label: 'Lookback Days', type: 'number', min: 60, hint: '252 = 52 weeks (1 year).' },
    { key: 'volFilter', label: 'Volume Filter', type: 'number', min: 1, hint: '1.5 = 50% above average volume.' }
  ],

  proTips: [
    'Only take breakouts when market indices (Nifty, Sensex) are also in uptrend.',
    'Avoid breakouts on earnings day—wait for post-earnings consolidation.',
    'Set tight stop at breakout level minus 2-3%; preserve capital if it fails.'
  ]
},

// ============================================================ 
// STRATEGY 6: SUPPORT/RESISTANCE BOUNCE
// ============================================================ 
{
  slug: 'support-resistance',
  name: 'Support/Resistance Bounce',
  kind: 'SUPPORT_RESISTANCE',
  comingSoon: true,
  tagline: 'Buy the floor, sell the ceiling—classic chart levels',
  hero: 'Price has memory. When it hits a level where it bounced before (support), buyers step in again. When it hits a level where it topped before (resistance), sellers show up. Trade these proven zones.',

  concept: {
    simple: 'Support is a price level where buying pressure historically stops declines. Resistance is where selling pressure caps rallies. Buy near support, sell near resistance.',
    why: 'Market participants remember key levels. Traders place orders there, creating self-fulfilling buying/selling pressure.'
  },

  rules: {
    entry: 'Buy when price touches support with bullish candlestick confirmation (hammer, engulfing).',
    exit: 'Sell when price reaches resistance or breaks support with volume.',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Range-bound markets',
        examples: 'NIFTY in 17,000-18,500 range for 3 months, ITC bouncing between 420-450.',
        why: 'Clear support/resistance makes entry/exit obvious; price respects levels consistently.',
        result: '4-6% gains per round-trip; 6-8 trades per year.'
      },
      {
        label: 'Pullbacks in uptrends',
        examples: 'HDFCBANK retracing to prior resistance (now support) in bull market.',
        why: 'Old resistance becomes new support; high-probability bounces.',
        result: 'Win rate above 65%; favorable risk/reward.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Strong breakouts',
        examples: 'Reliance breaking 2,500 after 6-month base, TCS breaking multi-year high.',
        why: 'Support/resistance breaks down during breakouts; old levels become irrelevant.',
        result: 'Sells too early; misses explosive move.'
      },
      {
        label: 'Low-volume touches',
        examples: 'Thin stocks with few participants, overnight gap opens.',
        why: 'Support/resistance needs volume to be valid; low participation means weak bounces.',
        result: 'Price breaks through support easily; losses.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now:',
    scenarios: [
      {
        label: 'Range-bound stock (ITC)',
        symbol: 'ITC',
        params: { support: 420, resistance: 450 },
        expect: 'Clean bounces at 420; sells at 450; 6-7% per trade.'
      },
      {
        label: 'Breakout stock (Reliance)',
        symbol: 'RELIANCE',
        params: { support: 2300, resistance: 2500 },
        expect: 'Breaks resistance; strategy exits, misses 20% run.'
      }
    ]
  },

  defaults: { initialCapital: 100000, support: 0, resistance: 0 },
  params: [
    { key: 'support', label: 'Support Level', type: 'number', min: 0, hint: 'Key floor price.' },
    { key: 'resistance', label: 'Resistance Level', type: 'number', min: 0, hint: 'Key ceiling price.' }
  ],

  proTips: [
    'Draw horizontal lines at obvious prior swing highs/lows.',
    'Wait for candlestick confirmation (hammer, doji) before entering at support.',
    'Tighten stop below support by 1-2%; preserve capital if level breaks.'
  ]
},

// ============================================================ 
// STRATEGY 7: MOVING AVERAGE RIBBON
// ============================================================ 
{
  slug: 'ma-ribbon',
  name: 'Moving Average Ribbon',
  kind: 'MA_RIBBON',
  comingSoon: true,
  tagline: 'Ride the rainbow—when all averages align, trends are strong',
  hero: 'Plot 8 moving averages (5, 10, 15, 20, 30, 40, 50, 100) on one chart. When they stack in order (5 > 10 > 15... > 100), it\'s a powerful uptrend. When they flip, exit. Visual and bulletproof.',

  concept: {
    simple: 'A ribbon of multiple MAs shows trend strength visually. Tight ribbon = strong trend. Expanding ribbon = weakening trend. Flipping ribbon = reversal.',
    why: 'Multiple timeframes confirming the same direction reduces false signals. It\'s like getting unanimous votes from 8 different analysts.'
  },

  rules: {
    entry: 'Buy when all MAs stack bullishly (5 > 10 > 15 > ... > 100) and price is above all.',
    exit: 'Sell when price crosses below the 20-day MA or ribbon starts flipping (e.g., 10 crosses below 20).',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Powerful sustained trends',
        examples: 'RELIANCE 2020-21 rally, TCS during IT boom, TATASTEEL commodity cycle.',
        why: 'All MAs aligned = consensus across all timeframes; low risk of quick reversal.',
        result: 'Catches 80%+ of major trends; smooth equity curve.'
      },
      {
        label: 'Post-consolidation expansions',
        examples: 'Stocks breaking out of 3-month base with all MAs fanning out.',
        why: 'Ribbon expansion confirms acceleration; entry at start of explosive phase.',
        result: '20-30% gains in weeks; high conviction trades.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Choppy, range-bound stocks',
        examples: 'NIFTY in narrow ranges, sideways small-caps.',
        why: 'MAs keep crossing each other; ribbon never stacks cleanly; constant false signals.',
        result: 'Whipsawed repeatedly; negative returns.'
      },
      {
        label: 'Sudden reversals',
        examples: 'Earnings shocks, macro surprises.',
        why: 'Ribbon takes time to flip; exit signal lags by 5-10% after top.',
        result: 'Gives back significant gains before exit triggers.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now:',
    scenarios: [
      {
        label: 'Strong trend (Reliance 2020)',
        symbol: 'RELIANCE',
        params: { mas: [5,10,15,20,30,40,50,100] },
        expect: 'Clean ribbon stack; single entry, smooth ride, late exit.'
      },
      {
        label: 'Sideways market (Nifty range)',
        symbol: 'NIFTY50',
        params: { mas: [5,10,15,20,30,40,50,100] },
        expect: 'Tangled ribbon; multiple crosses; poor returns.'
      }
    ]
  },

  defaults: { initialCapital: 100000, mas: [5,10,15,20,30,40,50,100] },
  params: [
    { key: 'mas', label: 'MA Periods (comma-separated)', type: 'text', hint: 'e.g., 5,10,15,20,30,40,50,100' }
  ],

  proTips: [
    'Use only in clear trends; skip if market is choppy.',
    'Tighten exit to 10-day MA in strong trends for faster stops.',
    'Combine with volume: only enter if volume is rising as ribbon aligns.'
  ]
},

// ============================================================ 
// STRATEGY 8: GAP FILL REVERSAL
// ============================================================ 
{
  slug: 'gap-fill',
  name: 'Gap Fill Reversal',
  kind: 'GAP_FILL',
  comingSoon: true,
  tagline: 'Profit from panic—gaps always want to close',
  hero: 'Stocks gap down on bad news, leaving a void on the chart. Within days, price often drifts back up to "fill the gap." Buy the panic, sell the relief rally. Quick, high-probability trades.',

  concept: {
    simple: 'A gap occurs when today\'s open is far from yesterday\'s close (e.g., gap down on earnings miss). Statistically, 70% of gaps get filled within 5 days as early sellers regret and buyers hunt bargains.',
    why: 'Market overreacts to news. Gaps create technical imbalance. Savvy traders fade the gap, knowing price tends to revert.'
  },

  rules: {
    entry: 'Buy on the open after a 3%+ gap down, if volume is high and no fundamental deterioration.',
    exit: 'Sell when gap is 80% filled or after 5 days, whichever comes first.',
    position: 'All-in on entry; flat on exit.'
  },

  bestFor: {
    title: 'Where this strategy shines',
    scenarios: [
      {
        label: 'Quality stocks with temporary scares',
        examples: 'HDFCBANK gap down on sector worry, TCS gap down on single bad quarter, INFY gap on currency headwind.',
        why: 'Fundamentals intact; gap is emotional overreaction; quick recovery likely.',
        result: '5-8% bounce in 2-5 days; high win rate (70%+).' 
      },
      {
        label: 'Index component stocks',
        examples: 'Nifty 50 / Sensex stocks with high liquidity.',
        why: 'Institutional buying kicks in quickly; gaps fill faster due to demand.',
        result: 'Fast fills; profitable scalps.'
      }
    ]
  },

  avoidFor: {
    title: 'Where this strategy struggles',
    scenarios: [
      {
        label: 'Fundamental deterioration gaps',
        examples: 'Earnings miss with guidance cut, fraud discovery, debt default.',
        why: 'Gap reflects real bad news; price continues lower, gap doesn\'t fill for months.',
        result: 'Catching falling knife; 10-20% losses.'
      },
      {
        label: 'Low-liquidity small-caps',
        examples: 'Penny stocks, illiquid mid-caps.',
        why: 'No institutional interest to drive gap fill; price languishes.',
        result: 'Gaps never fill; capital stuck.'
      }
    ]
  },

  interactiveDemo: {
    setup: 'Try it now:',
    scenarios: [
      {
        label: 'Quality stock gap (HDFC Bank)',
        symbol: 'HDFCBANK',
        params: { gapSize: 3, holdDays: 5 },
        expect: 'Gap fills in 3 days; 6% gain.'
      },
      {
        label: 'Fundamental gap (weak stock)',
        symbol: 'YESBANK',
        params: { gapSize: 5, holdDays: 5 },
        expect: 'Gap widens; strategy loses 10%+.'
      }
    ]
  },

  defaults: { initialCapital: 100000, gapSize: 3, holdDays: 5 },
  params: [
    { key: 'gapSize', label: 'Min Gap %', type: 'number', min: 1, hint: '3% = moderate gap.' },
    { key: 'holdDays', label: 'Max Hold Days', type: 'number', min: 1, hint: '5 days typical for fill.' }
  ],

  proTips: [
    'Only trade gaps in stocks with strong balance sheets and no fraud history.',
    'Check news: if earnings miss with guidance cut, skip the trade.',
    'Set tight stop at 5% below entry; don\'t hold losing gap trades.'
  ]
}

];
