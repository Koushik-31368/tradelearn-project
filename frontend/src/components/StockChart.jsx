// src/components/StockChart.jsx
import React, { useEffect, useRef } from 'react';
import { createChart } from 'lightweight-charts';

/**
 * Candlestick chart driven entirely by server candle data.
 *
 * Props:
 *   data  – array of { time, open, high, low, close }
 *           `time` must be a date-string ("2024-01-15") or UNIX timestamp.
 *           New candles are appended in real-time via `.update()`.
 */
const StockChart = ({ data }) => {
  const containerRef = useRef(null);
  const chartRef     = useRef(null);
  const seriesRef    = useRef(null);
  const prevLenRef   = useRef(0);

  // Create chart once on mount
  useEffect(() => {
    if (!containerRef.current) return;

    const chart = createChart(containerRef.current, {
      layout: {
        background: { color: '#1f2937' },
        textColor: '#d1d5db',
      },
      grid: {
        vertLines: { color: '#374151' },
        horzLines: { color: '#374151' },
      },
      width: containerRef.current.clientWidth,
      height: 350,
      crosshair: { mode: 0 },
      timeScale: { timeVisible: false, borderColor: '#374151' },
      rightPriceScale: { borderColor: '#374151' },
    });

    const series = chart.addCandlestickSeries({
      upColor:         '#10B981',
      downColor:       '#EF4444',
      borderDownColor: '#EF4444',
      borderUpColor:   '#10B981',
      wickDownColor:   '#EF4444',
      wickUpColor:     '#10B981',
    });

    chartRef.current  = chart;
    seriesRef.current = series;

    const handleResize = () => {
      if (containerRef.current) {
        chart.applyOptions({ width: containerRef.current.clientWidth });
      }
    };
    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
      chart.remove();
      chartRef.current  = null;
      seriesRef.current = null;
      prevLenRef.current = 0;
    };
  }, []); // mount-only

  // React to data changes — append new candles incrementally
  useEffect(() => {
    if (!seriesRef.current || !data || data.length === 0) return;

    if (prevLenRef.current === 0) {
      // First load — set full dataset
      seriesRef.current.setData(data);
      chartRef.current?.timeScale().fitContent();
    } else if (data.length > prevLenRef.current) {
      // Append only the newest candle(s)
      for (let i = prevLenRef.current; i < data.length; i++) {
        seriesRef.current.update(data[i]);
      }
      chartRef.current?.timeScale().scrollToRealTime();
    }

    prevLenRef.current = data.length;
  }, [data]);

  return (
    <div
      ref={containerRef}
      style={{ position: 'relative', width: '100%', height: '350px' }}
    />
  );
};

export default StockChart;