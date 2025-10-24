// src/components/StockChart.jsx
import React, { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';

const StockChart = ({ data }) => {
  // Create a ref for the chart container div element
  const chartContainerRef = useRef(null);
  
  // This useEffect hook handles the creation and destruction of the chart
  useEffect(() => {
    // Exit if the container isn't mounted yet
    if (!chartContainerRef.current) return;

    // Create the chart instance
    const chart = createChart(chartContainerRef.current, {
      layout: {
        background: { color: '#1f2937' },
        textColor: '#d1d5db',
      },
      grid: {
        vertLines: { color: '#374151' },
        horzLines: { color: '#374151' },
      },
      width: chartContainerRef.current.clientWidth,
      height: 400,
    });

    // Add a candlestick series to the chart
    const candlestickSeries = chart.addCandlestickSeries({
      upColor: '#10B981',
      downColor: '#EF4444',
      borderDownColor: '#EF4444',
      borderUpColor: '#10B981',
      wickDownColor: '#EF4444',
      wickUpColor: '#10B981',
    });

    // Set the data for the series
    candlestickSeries.setData(data);

    // Adjust the visible range to fit the data
    chart.timeScale().fitContent();

    // Resize chart on window resize
    const handleResize = () => {
      chart.applyOptions({ width: chartContainerRef.current.clientWidth });
    };
    window.addEventListener('resize', handleResize);

    // This is the cleanup function that runs when the component is unmounted
    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
    };
  }, [data]); // Re-run this effect if the data prop changes

  return <div ref={chartContainerRef} style={{ position: 'relative', width: '100%', height: '400px' }} />;
};

export default StockChart;