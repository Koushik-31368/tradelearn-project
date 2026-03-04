// src/pages/RiskDisclosurePage.jsx
import React from 'react';
import './LegalPages.css';

const RiskDisclosurePage = () => (
  <div className="legal-page">
    <div className="legal-inner">
      <h1 className="legal-title">Risk Disclosure</h1>
      <p className="legal-updated">Last Updated: February 28, 2026</p>

      <div className="legal-callout">
        <p>
          Trading financial instruments involves significant risk. This disclosure
          is intended to inform you of the risks associated with trading, even in a
          simulated environment.
        </p>
      </div>

      <section className="legal-section">
        <h2 className="legal-section-title">1. Trading Risk Warning</h2>
        <p>
          Trading in financial markets carries a high level of risk and may not be
          suitable for all individuals. The value of investments can go down as well
          as up, and you may lose more than your initial capital in real market
          conditions.
        </p>
        <p>
          TradeLearn provides a simulated environment to practice trading
          strategies. However, simulated results do not represent actual market
          conditions and should not be used as a basis for real trading decisions.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">2. Market Volatility</h2>
        <p>
          Financial markets are subject to rapid and unpredictable price movements.
          Volatility can result in significant gains or losses within short time
          periods. While TradeLearn uses simulated data, users should understand
          that real markets carry substantially greater risk.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">3. No Guarantee of Profit</h2>
        <p>
          There is no guarantee that any trading strategy, method, or system will
          generate profits. Success in the TradeLearn simulator does not indicate
          or predict success in live trading environments.
        </p>
        <p>
          Historical performance, whether real or simulated, is not indicative of
          future results.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">4. Educational Use Only</h2>
        <p>
          TradeLearn is strictly an educational platform. All trading activity
          occurs with virtual funds in a simulated environment. No real money is at
          risk. The platform is designed to help users develop analytical skills and
          understand market mechanics.
        </p>
        <p>
          Users are strongly advised to seek professional financial advice before
          engaging in any real trading activity.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">5. Responsibility Disclaimer</h2>
        <p>
          TradeLearn, its creators, and contributors are not responsible for any
          financial losses incurred as a result of applying strategies or knowledge
          gained on this platform to real markets.
        </p>
        <p>
          By using this platform, you acknowledge that you understand the risks
          associated with trading and accept full responsibility for any trading
          decisions you make outside of the TradeLearn environment.
        </p>
      </section>
    </div>
  </div>
);

export default RiskDisclosurePage;
