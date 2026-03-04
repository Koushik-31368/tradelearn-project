// src/data/historicalEvents.js
// Historical market events for Practice Mode.
// start/end are Unix epoch seconds — passed directly to Yahoo Finance's
// period1/period2 params. The backend auto-selects 5m / 1h / 1d resolution.

export const historicalEvents = [
  {
    id:          "covidCrash",
    title:       "COVID-19 Market Crash",
    subtitle:    "Feb – Apr 2020",
    symbol:      "RELIANCE",
    symbolName:  "Reliance Industries",
    start:       1580515200,   // 2020-02-01
    end:         1585699200,   // 2020-04-01
    type:        "crash",
    description: "Global markets collapsed as lockdowns began. Nifty 50 fell ~38% in under 6 weeks.",
  },
  {
    id:          "postCovidRally",
    title:       "Post-COVID Bull Run",
    subtitle:    "Jul – Nov 2020",
    symbol:      "TCS",
    symbolName:  "Tata Consultancy Services",
    start:       1593561600,   // 2020-07-01
    end:         1604188800,   // 2020-11-01
    type:        "rally",
    description: "Massive V-shaped recovery driven by stimulus packages and vaccine optimism.",
  },
  {
    id:          "financialCrisis2008",
    title:       "2008 Global Financial Crisis",
    subtitle:    "Sep 2008 – Feb 2009",
    symbol:      "INFY",
    symbolName:  "Infosys Ltd",
    start:       1220227200,   // 2008-09-01
    end:         1235865600,   // 2009-03-01
    type:        "crash",
    description: "Lehman Brothers collapsed. Global credit markets froze. Nifty fell ~60% peak-to-trough.",
  },
  {
    id:          "budgetRally2021",
    title:       "Union Budget Rally 2021",
    subtitle:    "Feb – Apr 2021",
    symbol:      "HDFCBANK",
    symbolName:  "HDFC Bank",
    start:       1612137600,   // 2021-02-01
    end:         1617235200,   // 2021-04-01
    type:        "rally",
    description: "Surprise pro-growth Union Budget 2021. Nifty hit new all-time highs above 15,000.",
  },
  {
    id:          "russiaUkraine2022",
    title:       "Russia-Ukraine War Sell-off",
    subtitle:    "Feb – Apr 2022",
    symbol:      "SBIN",
    symbolName:  "State Bank of India",
    start:       1643673600,   // 2022-02-01
    end:         1648771200,   // 2022-04-01
    type:        "crash",
    description: "War broke out on Feb 24, 2022. Commodity shock, FII outflows, and global risk-off.",
  },
  {
    id:          "adaniCrisis2023",
    title:       "Adani Group Crisis",
    subtitle:    "Jan – Mar 2023",
    symbol:      "ITC",
    symbolName:  "ITC Ltd",
    start:       1672531200,   // 2023-01-01
    end:         1680307200,   // 2023-04-01
    type:        "crash",
    description: "Hindenburg Research short report triggered a ₹11 lakh crore Adani Group wipeout in days.",
  },
  {
    id:          "demonetisation2016",
    title:       "Demonetisation Shock",
    subtitle:    "Nov 2016 – Jan 2017",
    symbol:      "MARUTI",
    symbolName:  "Maruti Suzuki",
    start:       1478044800,   // 2016-11-02
    end:         1483228800,   // 2017-01-01
    type:        "crash",
    description: "PM Modi announced overnight demonetisation of ₹500/₹1000 notes. Realty and FMCG collapsed.",
  },
  {
    id:          "techBull2023",
    title:       "IT Sector Bull Run 2023",
    subtitle:    "Mar – Jul 2023",
    symbol:      "WIPRO",
    symbolName:  "Wipro Ltd",
    start:       1677628800,   // 2023-03-01
    end:         1690848000,   // 2023-08-01
    type:        "rally",
    description: "AI euphoria from ChatGPT drove global tech stocks and Indian IT names sharply higher.",
  },
];

/** Quick lookup by id */
export function findEvent(id) {
  return historicalEvents.find((e) => e.id === id) || historicalEvents[0];
}
