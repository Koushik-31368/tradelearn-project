// Mock Indian stock data with realistic prices
export const indianStocks = [
  { symbol: 'TCS', name: 'Tata Consultancy Services', price: 3650.50, change: 2.3 },
  { symbol: 'INFY', name: 'Infosys Limited', price: 1452.30, change: -0.8 },
  { symbol: 'RELIANCE', name: 'Reliance Industries', price: 2456.75, change: 1.5 },
  { symbol: 'HDFCBANK', name: 'HDFC Bank', price: 1589.90, change: 0.5 },
  { symbol: 'ICICIBANK', name: 'ICICI Bank', price: 945.20, change: -1.2 },
  { symbol: 'WIPRO', name: 'Wipro Limited', price: 432.65, change: 0.9 },
  { symbol: 'SBIN', name: 'State Bank of India', price: 578.40, change: 1.8 },
  { symbol: 'BHARTIARTL', name: 'Bharti Airtel', price: 1234.50, change: -0.3 },
  { symbol: 'ITC', name: 'ITC Limited', price: 456.30, change: 0.7 },
  { symbol: 'KOTAKBANK', name: 'Kotak Mahindra Bank', price: 1789.25, change: 1.1 },
  { symbol: 'LT', name: 'Larsen & Toubro', price: 3456.80, change: -0.6 },
  { symbol: 'AXISBANK', name: 'Axis Bank', price: 1023.45, change: 0.4 },
  { symbol: 'HINDUNILVR', name: 'Hindustan Unilever', price: 2567.90, change: 1.3 },
  { symbol: 'MARUTI', name: 'Maruti Suzuki', price: 10245.60, change: -1.5 },
  { symbol: 'TATASTEEL', name: 'Tata Steel', price: 134.75, change: 2.1 }
];

// Function to get stock by symbol
export const getStockBySymbol = (symbol) => {
  return indianStocks.find(stock => stock.symbol === symbol);
};

// Function to get random price fluctuation (for simulation)
export const updateStockPrice = (stock) => {
  const fluctuation = (Math.random() - 0.5) * 20; // ±10 price change
  const newPrice = Math.max(stock.price + fluctuation, 10); // Min price ₹10
  const changePercent = ((newPrice - stock.price) / stock.price) * 100;
  
  return {
    ...stock,
    price: parseFloat(newPrice.toFixed(2)),
    change: parseFloat(changePercent.toFixed(2))
  };
};
