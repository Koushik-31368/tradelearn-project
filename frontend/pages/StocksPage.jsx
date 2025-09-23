// src/pages/StocksPage.jsx
import React, { useState, useEffect } from 'react';
import { useOutletContext } from 'react-router-dom';
import './StocksPage.css';
import StockList from '../components/StockList';
import Portfolio from '../components/Portfolio';
import StockDetailView from '../components/StockDetailView';
import StockToWatch from '../components/StockToWatch';

const StocksPage = () => {
  const { portfolio, cashBalance, handleBuy, handleSell } = useOutletContext();
  const [allMarketStocks, setAllMarketStocks] = useState([]);
  const [filteredStocks, setFilteredStocks] = useState([]);
  const [selectedStock, setSelectedStock] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [apiError, setApiError] = useState(null);

  // ✅ Your new Twelve Data API key
  const API_KEY = 'd2a19spr01qvhsfvklggd2a19spr01qvhsfvklh0';

  useEffect(() => {
    const fetchInitialStock = async () => {
      setIsLoading(true);
      setApiError(null);
      try {
        const symbol = 'RELIANCE';
        const url = `https://api.twelvedata.com/quote?symbol=${symbol}&exchange=NSE&apikey=${API_KEY}`;
        const response = await fetch(url);
        const data = await response.json();

        if (data.code >= 400 || !data.close) {
          throw new Error(data.message || "Failed to fetch initial stock data.");
        }

        const stock = {
          name: data.name,
          symbol: data.symbol,
          price: parseFloat(data.close),
          change: parseFloat(data.change),
          changePercent: parseFloat(data.percent_change).toFixed(2),
        };

        setAllMarketStocks([stock]);
        setFilteredStocks([stock]);
        setSelectedStock(stock);

      } catch (error) {
        setApiError(error.message);
      }
      setIsLoading(false);
    };
    fetchInitialStock();
  }, []);

  useEffect(() => {
    if (searchQuery.trim() === '') {
      setFilteredStocks(allMarketStocks);
      return;
    }
    const searchStocks = async () => {
      try {
        const url = `https://api.twelvedata.com/symbol_search?symbol=${searchQuery}&exchange=NSE&apikey=${API_KEY}`;
        const response = await fetch(url);
        const data = await response.json();

        if (data.data) {
          const formattedResults = data.data.map(stock => ({
            symbol: stock.symbol,
            name: stock.instrument_name,
            price: 0,
            change: 0,
            changePercent: '0.00'
          }));
          setFilteredStocks(formattedResults);
        }
      } catch (error) {
        console.error("Error searching stocks:", error);
      }
    };
    const delayDebounce = setTimeout(() => { searchStocks(); }, 500);
    return () => clearTimeout(delayDebounce);
  }, [searchQuery, allMarketStocks]);

  const handleStockSelect = async (stock) => {
    if (stock.price !== 0) {
      setSelectedStock(stock);
      return;
    }
    try {
      const url = `https://api.twelvedata.com/quote?symbol=${stock.symbol}&exchange=NSE&apikey=${API_KEY}`;
      const response = await fetch(url);
      const data = await response.json();

      if (data.code >= 400 || !data.close) {
        setApiError(`Could not retrieve price for ${stock.symbol}.`);
        return;
      }

      const detailedStock = {
        ...stock,
        price: parseFloat(data.close),
        change: parseFloat(data.change),
        changePercent: parseFloat(data.percent_change).toFixed(2),
      };
      setSelectedStock(detailedStock);
      setApiError(null);
    } catch (error) {
      console.error("Error fetching quote:", error);
    }
  };

  const handleSearchChange = (event) => { setSearchQuery(event.target.value); };

  return (
    <div className="stocks-page-layout">
      <div className="stock-list-container">
        <StockToWatch stock={selectedStock} />
        <StockDetailView stock={selectedStock} onBuy={handleBuy} onSell={handleSell} />
        {isLoading ? (
          <p className="loading-message">Loading market data...</p>
        ) : apiError ? (
          <div className="error-message">
            <p><strong>Could not load stock data.</strong></p>
            <p>{apiError}</p>
          </div>
        ) : (
          <StockList
            stocks={filteredStocks}
            onStockSelect={handleStockSelect}
            searchQuery={searchQuery}
            onSearchChange={handleSearchChange}
          />
        )}
      </div>
      <div className="portfolio-sidebar">
        <Portfolio portfolio={portfolio} cashBalance={cashBalance} />
      </div>
    </div>
  );
};

export default StocksPage;
