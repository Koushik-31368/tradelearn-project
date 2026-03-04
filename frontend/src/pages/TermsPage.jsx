// src/pages/TermsPage.jsx
import React from 'react';
import './LegalPages.css';

const TermsPage = () => (
  <div className="legal-page">
    <div className="legal-inner">
      <h1 className="legal-title">Terms of Service</h1>
      <p className="legal-updated">Last Updated: February 28, 2026</p>

      <div className="legal-callout">
        <p>
          TradeLearn is a simulated trading education platform. No real money is
          involved. We do not provide financial advice, brokerage services, or
          investment recommendations of any kind.
        </p>
      </div>

      <section className="legal-section">
        <h2 className="legal-section-title">1. Acceptance of Terms</h2>
        <p>
          By accessing or using the TradeLearn platform, you agree to be bound by
          these Terms of Service. If you do not agree to all of these terms, you
          must not use the platform.
        </p>
        <p>
          We reserve the right to modify these terms at any time. Continued use of
          the platform after changes constitutes acceptance of the revised terms.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">2. Educational Purpose Disclaimer</h2>
        <p>
          TradeLearn is designed exclusively for educational and skill-development
          purposes. All trading activity on the platform is simulated using virtual
          funds and does not involve real financial instruments.
        </p>
        <p>
          Past performance in simulated environments does not guarantee or indicate
          future results in live markets.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">3. No Financial Advice</h2>
        <p>
          Nothing on this platform constitutes financial advice, investment
          recommendation, or solicitation to buy or sell any financial instrument.
          All content, strategies, and educational material are provided for
          informational purposes only.
        </p>
        <p>
          You should consult a qualified financial advisor before making any real
          trading or investment decisions.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">4. User Responsibilities</h2>
        <p>As a user of TradeLearn, you agree to:</p>
        <ul>
          <li>Provide accurate registration information</li>
          <li>Maintain the confidentiality of your account credentials</li>
          <li>Use the platform in compliance with all applicable laws</li>
          <li>Not attempt to manipulate rankings or exploit platform mechanics</li>
          <li>Not use the platform for any unlawful or unauthorized purpose</li>
        </ul>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">5. Account Security</h2>
        <p>
          You are responsible for all activity that occurs under your account. You
          must notify us immediately of any unauthorized use or security breach.
          TradeLearn is not liable for losses arising from unauthorized access to
          your account.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">6. Intellectual Property</h2>
        <p>
          All content, design, code, and educational material on TradeLearn are the
          intellectual property of TradeLearn and its contributors. You may not
          reproduce, distribute, or create derivative works without express written
          permission.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">7. Limitation of Liability</h2>
        <p>
          TradeLearn is provided on an &ldquo;as is&rdquo; and &ldquo;as
          available&rdquo; basis. We make no warranties, express or implied,
          regarding the accuracy, reliability, or availability of the platform.
        </p>
        <p>
          In no event shall TradeLearn be liable for any indirect, incidental,
          special, or consequential damages arising from your use of the platform.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">8. Termination of Account</h2>
        <p>
          We reserve the right to suspend or terminate your account at any time,
          with or without notice, for conduct that we believe violates these Terms
          of Service or is harmful to other users or the platform.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">9. Governing Law</h2>
        <p>
          These Terms of Service are governed by and construed in accordance with
          applicable laws. Any disputes arising from these terms or your use of the
          platform shall be resolved through appropriate legal channels.
        </p>
      </section>
    </div>
  </div>
);

export default TermsPage;
