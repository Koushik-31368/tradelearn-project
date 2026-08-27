// src/components/simulator/TransactionHistory.jsx
import React, { useState, useMemo, useCallback } from 'react';
import './TransactionHistory.css';

const TransactionHistory = ({ trades = [] }) => {
  const [sortBy, setSortBy] = useState('date');
  const [sortDir, setSortDir] = useState('desc');

  const handleSort = (field) => {
    if (sortBy === field) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortBy(field);
      setSortDir('desc');
    }
  };

  const sorted = useMemo(() => {
    const list = [...trades];
    list.sort((a, b) => {
      let va = a[sortBy];
      let vb = b[sortBy];
      if (sortBy === 'date') {
        va = new Date(va).getTime();
        vb = new Date(vb).getTime();
      }
      if (typeof va === 'string') {
        va = va.toLowerCase();
        vb = vb.toLowerCase();
      }
      if (va < vb) return sortDir === 'asc' ? -1 : 1;
      if (va > vb) return sortDir === 'asc' ? 1 : -1;
      return 0;
    });
    return list;
  }, [trades, sortBy, sortDir]);

  const SortIcon = ({ field }) => {
    if (sortBy !== field) return <span className="th-sort-icon">⇅</span>;
    return <span className="th-sort-icon active">{sortDir === 'asc' ? '↑' : '↓'}</span>;
  };

  const exportCSV = useCallback(() => {
    const headers = ['Date', 'Symbol', 'Type', 'Quantity', 'Price (INR)', 'Total (INR)'];
    const rows = sorted.map((t) => [
      new Date(t.date).toLocaleDateString('en-IN'),
      t.symbol,
      t.type,
      t.quantity,
      t.price.toFixed(2),
      t.total.toFixed(2),
    ]);
    const csvContent = [headers, ...rows].map((r) => r.join(',')).join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `tradelearn_trades_${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }, [sorted]);

  return (
    <div className="transaction-history">
      <div className="transaction-history__header">
        <h3 className="transaction-history__title">Transaction History</h3>
        <div className="transaction-history__header-right">
          <span className="transaction-history__count">{trades.length} trades</span>
          {trades.length > 0 && (
            <button
              className="th-export-btn"
              onClick={exportCSV}
              title="Download trade history as CSV"
              aria-label="Export trades as CSV"
            >
              ⬇ Export CSV
            </button>
          )}
        </div>
      </div>

      {trades.length === 0 ? (
        <div className="transaction-history__empty">
          No transactions yet. Start trading to see your history.
        </div>
      ) : (
        <div className="transaction-history__scroll">
          <table className="transaction-history__table">
            <thead>
              <tr>
                <th onClick={() => handleSort('date')}>
                  Date <SortIcon field="date" />
                </th>
                <th onClick={() => handleSort('symbol')}>
                  Symbol <SortIcon field="symbol" />
                </th>
                <th onClick={() => handleSort('type')}>
                  Type <SortIcon field="type" />
                </th>
                <th onClick={() => handleSort('quantity')}>
                  Qty <SortIcon field="quantity" />
                </th>
                <th onClick={() => handleSort('price')}>
                  Price <SortIcon field="price" />
                </th>
                <th onClick={() => handleSort('total')}>
                  Total <SortIcon field="total" />
                </th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((t) => (
                <tr key={t.id} className="transaction-history__row">
                  <td className="td-date">{new Date(t.date).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: '2-digit' })}</td>
                  <td className="td-symbol">{t.symbol}</td>
                  <td>
                    <span className={`trade-badge trade-badge--${t.type.toLowerCase()}`}>
                      {t.type}
                    </span>
                  </td>
                  <td className="td-num">{t.quantity}</td>
                  <td className="td-num">₹{t.price.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                  <td className="td-num">₹{t.total.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default TransactionHistory;
