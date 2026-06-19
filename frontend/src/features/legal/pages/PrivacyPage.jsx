// src/pages/PrivacyPage.jsx
import React from 'react';
import './LegalPages.css';

const PrivacyPage = () => (
  <div className="legal-page">
    <div className="legal-inner">
      <h1 className="legal-title">Privacy Policy</h1>
      <p className="legal-updated">Last Updated: February 28, 2026</p>

      <div className="legal-callout">
        <p>
          Your privacy matters. This policy explains what data we collect, how we
          use it, and what rights you have as a TradeLearn user.
        </p>
      </div>

      <section className="legal-section">
        <h2 className="legal-section-title">1. Information We Collect</h2>
        <p>When you use TradeLearn, we may collect the following information:</p>
        <ul>
          <li>Email address and username provided during registration</li>
          <li>Trading activity and performance data within the simulator</li>
          <li>Match history, scores, and ranking information</li>
          <li>Device and browser information for platform optimization</li>
        </ul>
        <p>
          We do not collect real financial data, bank details, or payment
          information of any kind.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">2. How We Use Information</h2>
        <p>The information collected is used to:</p>
        <ul>
          <li>Provide and maintain the TradeLearn platform</li>
          <li>Calculate ELO ratings and leaderboard rankings</li>
          <li>Improve educational content and platform features</li>
          <li>Communicate important updates about the platform</li>
          <li>Ensure platform security and prevent abuse</li>
        </ul>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">3. Data Storage &amp; Security</h2>
        <p>
          Your data is stored on secure servers with industry-standard encryption.
          We implement appropriate technical and organizational measures to protect
          your personal information against unauthorized access, alteration,
          disclosure, or destruction.
        </p>
        <p>
          While we strive to protect your data, no method of transmission over the
          internet is completely secure. We cannot guarantee absolute security.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">4. Third-Party Services</h2>
        <p>
          TradeLearn may use third-party services for hosting, analytics, and
          platform infrastructure. These services may have access to limited data
          necessary to perform their functions but are contractually obligated to
          protect it.
        </p>
        <p>We do not sell your personal information to any third party.</p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">5. Cookies</h2>
        <p>
          TradeLearn uses essential cookies and local storage to maintain your
          authentication session and user preferences. We do not use tracking
          cookies for advertising purposes.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">6. User Rights</h2>
        <p>You have the right to:</p>
        <ul>
          <li>Access the personal data we hold about you</li>
          <li>Request correction of inaccurate information</li>
          <li>Request deletion of your account and associated data</li>
          <li>Withdraw consent for data processing at any time</li>
        </ul>
        <p>
          To exercise any of these rights, please contact us using the information
          provided below.
        </p>
      </section>

      <section className="legal-section">
        <h2 className="legal-section-title">7. Contact Information</h2>
        <p>
          For privacy-related inquiries or requests, contact us at
          privacy@tradelearn.app. We aim to respond to all requests within 30
          business days.
        </p>
      </section>
    </div>
  </div>
);

export default PrivacyPage;
