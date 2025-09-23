// src/components/StockChart.jsx
import React, { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';

const StockChart = ({ data }) => {
  const chartContainerRef = useRef();
  const chartRef = useRef();
  const seriesRef = useRef();

  // This effect creates the chart and series ONCE when the component mounts
  useEffect(() => {
    chartRef.current = createChart(chartContainerRef.current, {
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

    seriesRef.current = chartRef.current.addCandlestickSeries({
      upColor: '#10B981',
      downColor: '#EF4444',
      borderDownColor: '#EF4444',
      borderUpColor: '#10B981',
      wickDownColor: '#EF4444',
      wickUpColor: '#10B981',
    });

    const handleResize = () => {
      chartRef.current.applyOptions({ width: chartContainerRef.current.clientWidth });
    };
    window.addEventListener('resize', handleResize);

    // Cleanup function to remove the chart and event listener
    return () => {
      window.removeEventListener('resize', handleResize);
      chartRef.current.remove();
    };
  }, []); // The empty array [] ensures this runs only once

  // This effect UPDATES the data on the series whenever the 'data' prop changes
  useEffect(() => {
    if (seriesRef.current) {
      seriesRef.current.setData(data);
    }
  }, [data]);

  return <div ref={chartContainerRef} style={{ position: 'relative' }} />;
};

export default StockChart;