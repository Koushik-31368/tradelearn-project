// frontend/src/utils/missions.js

// Mock historical data for the 3 scenarios.
// Scaled down to short arrays for quick validation (e.g. 15-20 candles per mission).

const spy1987 = [
    { time: "1987-10-12", open: 310, high: 312, low: 308, close: 309, volume: 0 },
    { time: "1987-10-13", open: 309, high: 314, low: 307, close: 314, volume: 0 },
    { time: "1987-10-14", open: 314, high: 315, low: 305, close: 305, volume: 0 },
    { time: "1987-10-15", open: 305, high: 305, low: 295, close: 298, volume: 0 },
    { time: "1987-10-16", open: 298, high: 300, low: 280, close: 282, volume: 0 },
    { time: "1987-10-19", open: 282, high: 282, low: 220, close: 224, volume: 0 }, // Black Monday
    { time: "1987-10-20", open: 224, high: 240, low: 215, close: 236, volume: 0 },
    { time: "1987-10-21", open: 236, high: 258, low: 236, close: 258, volume: 0 },
    { time: "1987-10-22", open: 258, high: 260, low: 245, close: 248, volume: 0 },
    { time: "1987-10-23", open: 248, high: 252, low: 240, close: 248, volume: 0 },
];

const spy2020 = [
    { time: "2020-02-19", open: 338, high: 339, low: 336, close: 338, volume: 0 },
    { time: "2020-02-24", open: 323, high: 333, low: 321, close: 322, volume: 0 },
    { time: "2020-02-27", open: 305, high: 311, low: 297, close: 297, volume: 0 },
    { time: "2020-03-09", open: 275, high: 284, low: 273, close: 274, volume: 0 },
    { time: "2020-03-12", open: 256, high: 266, low: 247, close: 248, volume: 0 },
    { time: "2020-03-16", open: 241, high: 256, low: 237, close: 239, volume: 0 },
    { time: "2020-03-23", open: 228, high: 229, low: 218, close: 222, volume: 0 }, // The Bottom
    { time: "2020-03-24", open: 234, high: 244, low: 233, close: 243, volume: 0 },
    { time: "2020-03-26", open: 249, high: 262, low: 249, close: 261, volume: 0 },
    { time: "2020-04-06", open: 265, high: 267, low: 259, close: 264, volume: 0 },
    { time: "2020-04-14", open: 280, high: 284, low: 276, close: 283, volume: 0 },
    { time: "2020-04-29", open: 291, high: 294, low: 289, close: 293, volume: 0 },
];

const spy2015 = [
    { time: "2015-05-01", open: 210, high: 211, low: 209, close: 210, volume: 0 },
    { time: "2015-05-08", open: 210, high: 211, low: 209, close: 211, volume: 0 },
    { time: "2015-05-15", open: 211, high: 212, low: 210, close: 212, volume: 0 },
    { time: "2015-05-22", open: 213, high: 213, low: 211, close: 212, volume: 0 },
    { time: "2015-05-29", open: 211, high: 212, low: 210, close: 211, volume: 0 },
    { time: "2015-06-05", open: 210, high: 211, low: 209, close: 209, volume: 0 },
    { time: "2015-06-12", open: 209, high: 210, low: 208, close: 209, volume: 0 },
    { time: "2015-06-19", open: 211, high: 212, low: 210, close: 211, volume: 0 },
    { time: "2015-06-26", open: 211, high: 212, low: 209, close: 210, volume: 0 },
    { time: "2015-07-02", open: 208, high: 209, low: 207, close: 207, volume: 0 },
    { time: "2015-07-10", open: 208, high: 210, low: 206, close: 208, volume: 0 },
    { time: "2015-07-17", open: 211, high: 213, low: 211, close: 212, volume: 0 },
];

export const MISSIONS = [
    {
        id: "mission_1",
        title: "Mission 1: Black Monday",
        objective: "Protect your capital. Avoid being wiped out during the 1987 crash.",
        dataset: spy1987,
        constraints: {
            maxTrades: 2,
            maxDrawdownPercent: 15.0
        },
        startingBalance: 100000,
        assess: (history) => {
            if (history.maxDrawdown > 15.0) {
                return {
                    status: "FAIL",
                    wentWell: "You placed trades and participated in the market.",
                    wentWrong: "You exceeded the 15% maximum drawdown limit. You must use stop losses or exit early during a crash.",
                    nextMission: "mission_1"
                };
            }
            return {
                status: "PASS",
                wentWell: "Excellent risk management! You survived one of the worst crashes in history by cutting losses.",
                wentWrong: "None. Capital preservation is the highest priority.",
                nextMission: "mission_2"
            };
        }
    },
    {
        id: "mission_2",
        title: "Mission 2: COVID V-Shape",
        objective: "Don't panic sell at the bottom. Ride the trend upward.",
        dataset: spy2020,
        constraints: {
            maxTrades: 3
        },
        startingBalance: 100000,
        assess: (history) => {
            if (history.finalBalance > 100000) {
                return {
                    status: "PASS",
                    wentWell: "You successfully rode the V-shaped recovery and secured a profit!",
                    wentWrong: "None. Great job identifying the trend reversal.",
                    nextMission: "mission_3"
                };
            }
            return {
                status: "FAIL",
                wentWell: "You managed your risk appropriately.",
                wentWrong: "You got shaken out by the volatility or bet against the trend. You failed to turn a profit.",
                nextMission: "mission_2"
            };
        }
    },
    {
        id: "mission_3",
        title: "Mission 3: Sideways Chop",
        objective: "Identify a ranging market and avoid overtrading.",
        dataset: spy2015,
        constraints: {
            maxTrades: 5
        },
        startingBalance: 100000,
        assess: (history) => {
            if (history.tradeCount > 5) {
                return {
                    status: "FAIL",
                    wentWell: "You were highly active.",
                    wentWrong: "You overtraded in a sideways market. This is a classic trap that bleeds capital.",
                    nextMission: "mission_3"
                };
            }
            if (history.finalBalance >= 100000) {
                return {
                    status: "PASS",
                    wentWell: "You demonstrated extreme patience. Sitting on your hands is a valid strategy.",
                    wentWrong: "None. Protecting capital in a choppy market is a win.",
                    nextMission: "completed"
                };
            }
            return {
                status: "FAIL",
                wentWell: "You avoided the overtrading limit.",
                wentWrong: "You took losses in the chop. Wait for cleaner setups before deploying capital.",
                nextMission: "mission_3"
            };
        }
    }
];
