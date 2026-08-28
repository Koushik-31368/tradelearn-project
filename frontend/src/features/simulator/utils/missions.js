// frontend/src/features/simulator/utils/missions.js
// Rich mission data with 30+ candles, volumes, NIFTY-based Indian market scenarios

/* ── Helper: generate realistic candle from seed ── */
function mc(date, o, h, l, c, v) {
  return { date, time: date, open: o, high: h, low: l, close: c, volume: v };
}

/* ═══════════════════════════════════════════════════════
   Mission 1: 2008 Indian Market Crash
   NIFTY fell from ~6300 to ~2500 in months
   Lesson: Risk management — cut losses, use stop losses
   ═══════════════════════════════════════════════════════ */
const nifty2008 = [
  mc('2008-01-07', 6140, 6250, 6090, 6200, 18200000),
  mc('2008-01-14', 6200, 6280, 6100, 6150, 19500000),
  mc('2008-01-18', 6150, 6180, 5700, 5750, 28000000),
  mc('2008-01-21', 5750, 5800, 5200, 5280, 35000000), // gap down
  mc('2008-01-28', 5280, 5500, 5200, 5450, 22000000),
  mc('2008-02-04', 5450, 5600, 5350, 5380, 18000000),
  mc('2008-02-11', 5380, 5420, 5100, 5150, 21000000),
  mc('2008-02-18', 5150, 5300, 5050, 5250, 17000000),
  mc('2008-02-25', 5250, 5400, 5200, 5350, 16000000),
  mc('2008-03-03', 5350, 5380, 5050, 5100, 24000000),
  mc('2008-03-10', 5100, 5150, 4800, 4850, 29000000), // breakdown
  mc('2008-03-17', 4850, 5000, 4750, 4900, 20000000),
  mc('2008-03-24', 4900, 5100, 4850, 5050, 19000000),
  mc('2008-03-31', 5050, 5200, 4950, 5150, 17000000), // dead cat bounce
  mc('2008-04-07', 5150, 5250, 5050, 5100, 16000000),
  mc('2008-04-14', 5100, 5180, 4900, 4950, 22000000),
  mc('2008-04-21', 4950, 5000, 4700, 4750, 26000000),
  mc('2008-04-28', 4750, 4800, 4500, 4550, 28000000),
  mc('2008-05-05', 4550, 4650, 4400, 4420, 30000000),
  mc('2008-05-12', 4420, 4500, 4350, 4480, 21000000),
  mc('2008-05-19', 4480, 4600, 4400, 4550, 18000000),
  mc('2008-05-26', 4550, 4580, 4200, 4250, 32000000), // another leg down
  mc('2008-06-02', 4250, 4300, 4050, 4100, 34000000),
  mc('2008-06-09', 4100, 4200, 3950, 3980, 36000000),
  mc('2008-06-16', 3980, 4100, 3900, 4050, 25000000),
  mc('2008-06-23', 4050, 4150, 3850, 3900, 28000000),
  mc('2008-06-30', 3900, 3950, 3700, 3750, 38000000),
  mc('2008-07-07', 3750, 3850, 3600, 3650, 35000000), // capitulation
  mc('2008-07-14', 3650, 3800, 3550, 3780, 30000000),
  mc('2008-07-21', 3780, 3900, 3700, 3850, 22000000),
];

/* ═══════════════════════════════════════════════════════
   Mission 2: COVID Crash & V-Recovery (NIFTY)
   Fell from ~12000 to ~7500, then recovered to ~10000+
   Lesson: Don't panic — hold through volatility
   ═══════════════════════════════════════════════════════ */
const nifty2020 = [
  mc('2020-02-17', 12100, 12200, 12000, 12080, 14000000),
  mc('2020-02-19', 12080, 12150, 11950, 12000, 15000000),
  mc('2020-02-24', 12000, 12050, 11500, 11600, 22000000), // sell-off starts
  mc('2020-02-27', 11600, 11700, 11200, 11250, 26000000),
  mc('2020-03-02', 11250, 11500, 11100, 11400, 19000000),
  mc('2020-03-05', 11400, 11450, 11100, 11200, 20000000),
  mc('2020-03-09', 11200, 11250, 10450, 10500, 32000000), // gap down
  mc('2020-03-12', 10500, 10600, 9600, 9700, 40000000), // lockdown fear
  mc('2020-03-16', 9700, 9800, 8600, 8750, 48000000), // circuit breaker
  mc('2020-03-19', 8750, 8900, 8100, 8250, 52000000),
  mc('2020-03-23', 8250, 8300, 7500, 7610, 55000000), // THE BOTTOM
  mc('2020-03-25', 7610, 8300, 7500, 8200, 45000000), // bounce
  mc('2020-03-27', 8200, 8500, 8100, 8400, 38000000),
  mc('2020-03-30', 8400, 8600, 8200, 8300, 30000000),
  mc('2020-04-01', 8300, 8500, 8100, 8450, 28000000),
  mc('2020-04-06', 8450, 8800, 8400, 8750, 32000000),
  mc('2020-04-09', 8750, 9100, 8700, 9050, 29000000),
  mc('2020-04-13', 9050, 9200, 8900, 9100, 25000000),
  mc('2020-04-16', 9100, 9300, 9000, 9250, 22000000),
  mc('2020-04-20', 9250, 9350, 9100, 9150, 20000000),
  mc('2020-04-23', 9150, 9400, 9050, 9350, 24000000),
  mc('2020-04-27', 9350, 9550, 9300, 9500, 22000000),
  mc('2020-04-30', 9500, 9700, 9400, 9650, 20000000),
  mc('2020-05-04', 9650, 9800, 9500, 9600, 18000000),
  mc('2020-05-07', 9600, 9900, 9550, 9850, 21000000),
  mc('2020-05-11', 9850, 10050, 9750, 10000, 23000000),
  mc('2020-05-14', 10000, 10200, 9900, 10150, 19000000),
  mc('2020-05-18', 10150, 10300, 10050, 10250, 17000000),
  mc('2020-05-21', 10250, 10350, 10100, 10200, 16000000),
  mc('2020-05-25', 10200, 10400, 10150, 10350, 18000000),
];

/* ═══════════════════════════════════════════════════════
   Mission 3: Sideways Chop (NIFTY 2015)
   Range-bound between ~8000-8600 for months
   Lesson: Patience — don't overtrade in choppy markets
   ═══════════════════════════════════════════════════════ */
const nifty2015 = [
  mc('2015-05-04', 8350, 8420, 8300, 8400, 12000000),
  mc('2015-05-08', 8400, 8450, 8350, 8380, 11000000),
  mc('2015-05-11', 8380, 8500, 8350, 8480, 13000000),
  mc('2015-05-15', 8480, 8520, 8380, 8410, 12000000),
  mc('2015-05-18', 8410, 8450, 8300, 8320, 14000000),
  mc('2015-05-22', 8320, 8400, 8250, 8370, 11000000),
  mc('2015-05-25', 8370, 8430, 8280, 8290, 12000000),
  mc('2015-05-29', 8290, 8350, 8200, 8220, 15000000), // dip
  mc('2015-06-01', 8220, 8350, 8180, 8310, 13000000),
  mc('2015-06-05', 8310, 8400, 8280, 8380, 11000000),
  mc('2015-06-08', 8380, 8450, 8350, 8420, 10000000),
  mc('2015-06-12', 8420, 8500, 8390, 8470, 12000000),
  mc('2015-06-15', 8470, 8550, 8430, 8530, 13000000), // push up
  mc('2015-06-19', 8530, 8580, 8400, 8430, 14000000), // rejected
  mc('2015-06-22', 8430, 8480, 8350, 8370, 12000000),
  mc('2015-06-26', 8370, 8400, 8250, 8280, 16000000), // dip again
  mc('2015-06-29', 8280, 8320, 8200, 8240, 15000000),
  mc('2015-07-02', 8240, 8350, 8210, 8330, 13000000),
  mc('2015-07-06', 8330, 8420, 8300, 8400, 11000000),
  mc('2015-07-10', 8400, 8500, 8380, 8480, 12000000),
  mc('2015-07-13', 8480, 8530, 8440, 8500, 11000000),
  mc('2015-07-17', 8500, 8570, 8460, 8540, 13000000),
  mc('2015-07-20', 8540, 8580, 8450, 8470, 14000000), // rejected again
  mc('2015-07-24', 8470, 8500, 8380, 8400, 12000000),
  mc('2015-07-27', 8400, 8440, 8320, 8350, 13000000),
  mc('2015-07-31', 8350, 8420, 8290, 8380, 14000000),
  mc('2015-08-03', 8380, 8450, 8350, 8430, 11000000),
  mc('2015-08-07', 8430, 8490, 8400, 8460, 10000000),
  mc('2015-08-10', 8460, 8510, 8390, 8410, 12000000),
  mc('2015-08-14', 8410, 8450, 8350, 8380, 11000000),
];

/* ═══════════════════════════════════════════════════════ */
export const MISSIONS = [
  {
    id: 'mission_1',
    title: 'Survive the Crash',
    subtitle: '2008 Financial Crisis',
    icon: '🔴',
    difficulty: 'Hard',
    lesson: 'Risk Management',
    description: 'The global financial crisis hits India. NIFTY is in freefall — down 40% and counting. Your job: protect your capital. Use stop losses, exit early, or stay in cash. Survive with less than 20% drawdown.',
    objective: 'End the mission with less than 20% portfolio drawdown.',
    dataset: nifty2008,
    ticker: 'NIFTY',
    constraints: {
      maxTrades: 4,
      maxDrawdownPercent: 20.0,
      timePerCandle: 2000,
    },
    startingBalance: 500000,
    assess: (history) => {
      if (history.forcedFail || history.maxDrawdown > 20.0) {
        return {
          status: 'FAIL',
          grade: 'F',
          title: 'Wiped Out',
          wentWell: 'You entered the market and participated.',
          wentWrong: `Your drawdown hit ${history.maxDrawdown.toFixed(1)}% — exceeding the 20% limit. In a crash, capital preservation beats returns. Use stop losses or stay flat.`,
          lesson: 'Never risk more than you can afford to lose. A 50% loss needs a 100% gain to recover.',
          nextMission: 'mission_1',
        };
      }
      const pnlPct = ((history.finalBalance - 500000) / 500000 * 100).toFixed(1);
      return {
        status: 'PASS',
        grade: history.finalBalance >= 500000 ? 'A' : 'B',
        title: history.finalBalance >= 500000 ? 'Master Risk Manager' : 'Survived',
        wentWell: `You survived one of India's worst market crashes with only ${history.maxDrawdown.toFixed(1)}% drawdown. P&L: ${pnlPct}%`,
        wentWrong: history.finalBalance < 500000 ? 'You took some losses, but stayed within limits.' : 'Nothing — excellent discipline.',
        lesson: 'In a crash, staying in cash IS a strategy. The best trade is often no trade.',
        nextMission: 'mission_2',
      };
    },
  },
  {
    id: 'mission_2',
    title: 'Catch the Recovery',
    subtitle: 'COVID-19 V-Shape',
    icon: '🟡',
    difficulty: 'Medium',
    lesson: 'Emotional Control',
    description: 'March 2020. COVID lockdowns crash NIFTY from 12,000 to 7,500. Fear is everywhere. But a V-shaped recovery is coming. Can you hold your nerve, buy the dip, and ride the wave back up?',
    objective: 'End the mission with a profit. Don\'t panic sell at the bottom.',
    dataset: nifty2020,
    ticker: 'NIFTY',
    constraints: {
      maxTrades: 5,
      timePerCandle: 2000,
    },
    startingBalance: 500000,
    assess: (history) => {
      const pnlPct = ((history.finalBalance - 500000) / 500000 * 100).toFixed(1);
      if (history.finalBalance > 550000) {
        return {
          status: 'PASS',
          grade: 'A',
          title: 'Diamond Hands',
          wentWell: `You profited ${pnlPct}% during one of the most volatile periods in history. You bought when others were panicking.`,
          wentWrong: 'Nothing — exceptional emotional control.',
          lesson: 'The market rewards those who can think clearly when everyone else is panicking.',
          nextMission: 'mission_3',
        };
      }
      if (history.finalBalance > 500000) {
        return {
          status: 'PASS',
          grade: 'B',
          title: 'Cautious Winner',
          wentWell: `You ended with a ${pnlPct}% profit. You didn't panic.`,
          wentWrong: 'You could have been more aggressive near the bottom.',
          lesson: 'In a V-recovery, the biggest gains come from buying when fear is highest.',
          nextMission: 'mission_3',
        };
      }
      return {
        status: 'FAIL',
        grade: 'D',
        title: 'Panic Sold',
        wentWell: 'You participated in the market.',
        wentWrong: `You ended at ${pnlPct}%. You likely sold at the bottom or failed to buy the recovery. Fear is the mind-killer.`,
        lesson: 'Panic selling locks in losses. The market always recovers — the question is whether you\'re still in it.',
        nextMission: 'mission_2',
      };
    },
  },
  {
    id: 'mission_3',
    title: 'Resist the Chop',
    subtitle: 'Sideways Market 2015',
    icon: '🟢',
    difficulty: 'Easy',
    lesson: 'Patience & Discipline',
    description: 'NIFTY is range-bound between 8200-8550 for months. No trend, no direction — just noise. The trap: overtrading. Can you resist the urge to trade every wiggle and preserve your capital?',
    objective: 'End with your capital intact. Use 5 or fewer trades.',
    dataset: nifty2015,
    ticker: 'NIFTY',
    constraints: {
      maxTrades: 5,
      timePerCandle: 1800,
    },
    startingBalance: 500000,
    assess: (history) => {
      const pnlPct = ((history.finalBalance - 500000) / 500000 * 100).toFixed(1);
      if (history.tradeCount <= 2 && history.finalBalance >= 498000) {
        return {
          status: 'PASS',
          grade: 'A',
          title: 'Zen Master',
          wentWell: `Only ${history.tradeCount} trades. You understood that doing nothing IS a strategy. P&L: ${pnlPct}%`,
          wentWrong: 'Nothing — supreme discipline.',
          lesson: 'The best traders know when NOT to trade. Sideways markets destroy overtraders.',
          nextMission: 'completed',
        };
      }
      if (history.finalBalance >= 495000) {
        return {
          status: 'PASS',
          grade: 'B',
          title: 'Capital Preserver',
          wentWell: `You kept your capital mostly intact (${pnlPct}%).`,
          wentWrong: history.tradeCount > 3 ? 'You traded a bit too much for a choppy market.' : 'Minor losses from noise.',
          lesson: 'In a range-bound market, every trade is a coin flip minus fees. Less is more.',
          nextMission: 'mission_4',
        };
      }
      return {
        status: 'FAIL',
        grade: 'D',
        title: 'Chopped Up',
        wentWell: 'You engaged with the market.',
        wentWrong: `You lost ${Math.abs(pnlPct)}% trying to trade in a sideways market. ${history.tradeCount} trades — each one bleeding capital.`,
        lesson: 'Sideways markets are a trap. The market doesn\'t always have to be traded.',
        nextMission: 'mission_3',
      };
    },
  },
];

/* ═══════════════════════════════════════════════════════
   Mission 4 dataset: 2016 Demonetisation Shock
   NIFTY fell ~14% in 3 weeks after Nov 8 announcement
   Lesson: Trading around government/policy events — manage gap risk
   ═══════════════════════════════════════════════════════ */
const nifty2016demonetisation = [
  mc('2016-10-03', 8700, 8780, 8670, 8750, 12000000),
  mc('2016-10-10', 8750, 8810, 8710, 8770, 11000000),
  mc('2016-10-17', 8770, 8820, 8730, 8800, 10500000),
  mc('2016-10-24', 8800, 8860, 8760, 8840, 11000000),
  mc('2016-10-31', 8840, 8900, 8780, 8820, 12500000),
  mc('2016-11-07', 8820, 8860, 8780, 8850, 11000000), // eve of demonetisation
  mc('2016-11-09', 8850, 8880, 8100, 8150, 38000000), // SHOCK DROP — gap down
  mc('2016-11-14', 8150, 8200, 7900, 7940, 32000000), // continued sell-off
  mc('2016-11-21', 7940, 8050, 7890, 8000, 28000000), // stabilising
  mc('2016-11-28', 8000, 8100, 7950, 8070, 22000000),
  mc('2016-12-05', 8070, 8150, 8010, 8080, 18000000),
  mc('2016-12-12', 8080, 8200, 8050, 8160, 16000000), // dead cat
  mc('2016-12-19', 8160, 8220, 8100, 8130, 14000000),
  mc('2016-12-26', 8130, 8200, 8100, 8180, 13000000),
  mc('2017-01-02', 8180, 8280, 8150, 8250, 15000000),
  mc('2017-01-09', 8250, 8350, 8220, 8320, 14000000),
  mc('2017-01-16', 8320, 8400, 8280, 8380, 13000000),
  mc('2017-01-23', 8380, 8480, 8350, 8450, 14000000),
  mc('2017-01-30', 8450, 8530, 8410, 8500, 13500000),
  mc('2017-02-06', 8500, 8600, 8460, 8570, 13000000),
  mc('2017-02-13', 8570, 8650, 8530, 8620, 12500000),
  mc('2017-02-20', 8620, 8720, 8590, 8700, 12000000), // recovery approaching pre-demo
  mc('2017-02-27', 8700, 8800, 8680, 8780, 12000000),
  mc('2017-03-06', 8780, 8850, 8740, 8820, 11500000),
  mc('2017-03-13', 8820, 8900, 8800, 8880, 11000000),
  mc('2017-03-20', 8880, 8950, 8840, 8930, 10500000),
  mc('2017-03-27', 8930, 9050, 8900, 9020, 13000000),
  mc('2017-04-03', 9020, 9100, 8960, 9070, 12000000),
  mc('2017-04-10', 9070, 9150, 9030, 9120, 11500000),
  mc('2017-04-17', 9120, 9200, 9080, 9170, 11000000),
];

/* ═══════════════════════════════════════════════════════
   Mission 5 dataset: 2021 NIFTY Bull Run
   NIFTY ran from ~14000 to ~18600 — a 33% bull run
   Lesson: Riding a trend — when to take profit
   ═══════════════════════════════════════════════════════ */
const nifty2021bull = [
  mc('2021-01-11', 14150, 14350, 14100, 14300, 17000000),
  mc('2021-01-18', 14300, 14500, 14250, 14430, 15000000),
  mc('2021-01-25', 14430, 14600, 14380, 14550, 13000000),
  mc('2021-02-01', 14550, 14800, 14500, 14750, 18000000),
  mc('2021-02-08', 14750, 15000, 14700, 14940, 20000000),
  mc('2021-02-15', 14940, 15200, 14900, 15150, 22000000),
  mc('2021-02-22', 15150, 15400, 15100, 14750, 25000000), // small dip
  mc('2021-03-01', 14750, 15100, 14650, 15000, 21000000),
  mc('2021-03-08', 15000, 15200, 14950, 15100, 18000000),
  mc('2021-03-15', 15100, 15350, 15050, 15250, 17000000),
  mc('2021-03-22', 15250, 15500, 15200, 15450, 19000000),
  mc('2021-03-29', 15450, 15700, 15400, 15650, 20000000),
  mc('2021-04-05', 15650, 15850, 15600, 14900, 23000000), // 2nd wave COVID dip
  mc('2021-04-12', 14900, 15100, 14800, 15050, 22000000),
  mc('2021-04-19', 15050, 15200, 14950, 14850, 24000000),
  mc('2021-04-26', 14850, 15000, 14700, 14950, 22000000),
  mc('2021-05-03', 14950, 15200, 14900, 15150, 20000000),
  mc('2021-05-10', 15150, 15400, 15100, 15350, 18000000),
  mc('2021-05-17', 15350, 15600, 15300, 15550, 17000000),
  mc('2021-05-24', 15550, 15800, 15500, 15750, 19000000),
  mc('2021-05-31', 15750, 16000, 15700, 15900, 21000000),
  mc('2021-06-07', 15900, 16200, 15850, 16100, 20000000),
  mc('2021-06-14', 16100, 16400, 16050, 16350, 18000000),
  mc('2021-06-21', 16350, 16600, 16300, 16550, 17000000),
  mc('2021-06-28', 16550, 16800, 16500, 16750, 19000000),
  mc('2021-07-05', 16750, 17000, 16700, 16950, 20000000),
  mc('2021-07-12', 16950, 17200, 16900, 17100, 18000000),
  mc('2021-07-19', 17100, 17400, 17050, 17350, 21000000),
  mc('2021-08-02', 17350, 17600, 17300, 17500, 19000000),
  mc('2021-08-09', 17500, 17750, 17450, 17700, 18000000), // all-time high push
];

/* ─── append new missions to the exported array ─── */
MISSIONS.push(
  {
    id: 'mission_4',
    title: 'Short the Shock',
    subtitle: '2016 Demonetisation',
    icon: '⚡',
    difficulty: 'Hard',
    lesson: 'Policy Event Trading',
    description: 'November 8, 2016. PM Modi announces demonetisation — ₹500 and ₹1000 notes are invalid overnight. NIFTY gaps down 700 points the next morning. Can you profit from the chaos while managing the extreme gap risk?',
    objective: 'Profit from the demonetisation shock. End with more than ₹5,10,000.',
    dataset: nifty2016demonetisation,
    ticker: 'NIFTY',
    constraints: {
      maxTrades: 6,
      maxDrawdownPercent: 15.0,
      timePerCandle: 2000,
    },
    startingBalance: 500000,
    assess: (history) => {
      const pnlPct = ((history.finalBalance - 500000) / 500000 * 100).toFixed(1);
      if (history.forcedFail || history.maxDrawdown > 15.0) {
        return {
          status: 'FAIL',
          grade: 'F',
          title: 'Gap Risk Victim',
          wentWell: 'You participated in one of India\'s most historic market events.',
          wentWrong: `Your drawdown hit ${history.maxDrawdown.toFixed(1)}%. Policy shocks create brutal gaps — you must size positions small and use tight stops.`,
          lesson: 'Before major policy events, reduce exposure. Gap downs can breach any stop loss.',
          nextMission: 'mission_4',
        };
      }
      if (history.finalBalance >= 515000) {
        return {
          status: 'PASS',
          grade: 'A',
          title: 'Policy Trader',
          wentWell: `Exceptional! You profited ${pnlPct}% from the demonetisation shock. You read the macro signal and executed correctly.`,
          wentWrong: 'Nothing — this was masterful event-driven trading.',
          lesson: 'Government policy creates predictable directional moves. Learn to read the macro calendar.',
          nextMission: 'mission_5',
        };
      }
      if (history.finalBalance >= 502000) {
        return {
          status: 'PASS',
          grade: 'B',
          title: 'Careful Navigator',
          wentWell: `You navigated the shock and came out ahead (${pnlPct}%). Good risk control under extreme uncertainty.`,
          wentWrong: 'You were perhaps too cautious — there was more profit to capture on the short side.',
          lesson: 'In a black swan event, the initial direction is almost always correct. Act decisively but with small size.',
          nextMission: 'mission_5',
        };
      }
      return {
        status: 'FAIL',
        grade: 'D',
        title: 'Caught Off Guard',
        wentWell: 'You managed to survive without catastrophic losses.',
        wentWrong: `You ended at ${pnlPct}%. You either missed the short opportunity or bought the dip too early during continued selling.`,
        lesson: 'After a policy shock, don\'t bottom-fish immediately. Let price stabilise before buying.',
        nextMission: 'mission_4',
      };
    },
  },
  {
    id: 'mission_5',
    title: 'Ride the Bull',
    subtitle: '2021 NIFTY Bull Run',
    icon: '🚀',
    difficulty: 'Medium',
    lesson: 'Trend Following & Profit-Taking',
    description: 'Post-COVID euphoria. Retail investors flood the market, FIIs pour in billions, and NIFTY begins a relentless march from 14,000 to 18,600. The challenge: ride the trend without getting shaken out by dips — and know when to take profit.',
    objective: 'End the mission with more than ₹5,60,000. Don\'t sell too early.',
    dataset: nifty2021bull,
    ticker: 'NIFTY',
    constraints: {
      maxTrades: 8,
      timePerCandle: 1800,
    },
    startingBalance: 500000,
    assess: (history) => {
      const pnlPct = ((history.finalBalance - 500000) / 500000 * 100).toFixed(1);
      if (history.finalBalance >= 580000) {
        return {
          status: 'PASS',
          grade: 'A',
          title: 'Bull Rider 🐂',
          wentWell: `Magnificent! You captured ${pnlPct}% of the bull run. You held through the COVID 2nd wave dip and kept buying strength.`,
          wentWrong: 'Nothing — this was textbook trend following.',
          lesson: 'In a bull market, the trend is your friend. Add to winners, cut losers fast, and let profits run.',
          nextMission: 'completed',
        };
      }
      if (history.finalBalance >= 560000) {
        return {
          status: 'PASS',
          grade: 'B',
          title: 'Trend Follower',
          wentWell: `You captured ${pnlPct}% — a solid return riding one of India's greatest bull markets.`,
          wentWrong: 'You may have taken profit too early or missed some of the later legs.',
          lesson: 'Use trailing stop losses to stay in trending trades longer. Let the market tell you when to exit.',
          nextMission: 'completed',
        };
      }
      if (history.finalBalance >= 510000) {
        return {
          status: 'PASS',
          grade: 'C',
          title: 'Partial Ride',
          wentWell: `You made money (${pnlPct}%) in a bull market — at least you were on the right side.`,
          wentWrong: 'You undersized your position or sold too early. In a strong bull run, hesitation is expensive.',
          lesson: 'When fundamentals AND technicals align upward, size up. Don\'t be afraid of heights in a bull market.',
          nextMission: 'completed',
        };
      }
      return {
        status: 'FAIL',
        grade: 'D',
        title: 'Bull Market Underperformer',
        wentWell: 'You survived without major losses.',
        wentWrong: `Only ${pnlPct}% in a year where NIFTY rose 33%. You either panicked during dips or didn't participate enough.`,
        lesson: 'The biggest mistake in a bull market is under-exposure. When the trend is clear, trust it.',
        nextMission: 'mission_5',
      };
    },
  }
);
