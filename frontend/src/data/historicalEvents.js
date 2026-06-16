// src/data/historicalEvents.js
// Historical market scenarios for the Replay Engine.

export const historicalEvents = [
  {
    id:          "covidCrash",
    title:       "COVID-19 Market Crash",
    subtitle:    "Feb – Apr 2020",
    symbol:      "RELIANCE",
    symbolName:  "Reliance Industries",
    start:       "2020-02-01",
    end:         "2020-04-30",
    type:        "crash",
    description: "Global markets collapsed as lockdowns began. Nifty 50 fell ~38% in under 6 weeks.",
    difficulty:  "Hard",
    capital:     100000,
  },
  {
    id:          "dotComCrash",
    title:       "Dot-Com Crash",
    subtitle:    "Jan 2000 – Dec 2000",
    symbol:      "INFY",
    symbolName:  "Infosys Ltd",
    start:       "2000-01-01",
    end:         "2000-12-31",
    type:        "crash",
    description: "The burst of the technology bubble. Tech stocks experienced extreme volatility and massive drawdowns.",
    difficulty:  "Hard",
    capital:     50000,
  },
  {
    id:          "financialCrisis2008",
    title:       "2008 Global Financial Crisis",
    subtitle:    "Sep 2008 – Feb 2009",
    symbol:      "SBIN",
    symbolName:  "State Bank of India",
    start:       "2008-09-01",
    end:         "2009-02-28",
    type:        "crash",
    description: "Lehman Brothers collapsed. Global credit markets froze. Peak-to-trough drawdowns were severe.",
    difficulty:  "Extreme",
    capital:     100000,
  },
  {
    id:          "niftyBullRun2021",
    title:       "NIFTY Bull Run 2021",
    subtitle:    "Jan – Oct 2021",
    symbol:      "HDFCBANK",
    symbolName:  "HDFC Bank",
    start:       "2021-01-01",
    end:         "2021-10-31",
    type:        "rally",
    description: "Massive liquidity and post-COVID recovery drove indices to all-time highs.",
    difficulty:  "Easy",
    capital:     200000,
  },
  {
    id:          "aiBoom2023",
    title:       "AI Boom 2023",
    subtitle:    "Jan – Jul 2023",
    symbol:      "TCS",
    symbolName:  "Tata Consultancy Services",
    start:       "2023-01-01",
    end:         "2023-07-31",
    type:        "rally",
    description: "ChatGPT launched late 2022, sparking a global AI infrastructure rally.",
    difficulty:  "Medium",
    capital:     150000,
  },
  {
    id:          "adaniCrisis2023",
    title:       "Adani Group Crisis",
    subtitle:    "Jan – Mar 2023",
    symbol:      "SBIN",
    symbolName:  "State Bank of India",
    start:       "2023-01-01",
    end:         "2023-03-31",
    type:        "crash",
    description: "Hindenburg Research short report triggered massive selloffs in Adani and exposed banking stocks.",
    difficulty:  "Hard",
    capital:     100000,
  }
];

/** Quick lookup by id */
export function findEvent(id) {
  return historicalEvents.find((e) => e.id === id) || historicalEvents[0];
}

