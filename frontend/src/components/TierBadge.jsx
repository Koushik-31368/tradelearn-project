// src/components/TierBadge.jsx
import React from 'react';
import { getSkillTier, getTierColor, getTierBorderColor } from '../utils/skillTier';
import './TierBadge.css';

/**
 * Minimal pill badge that displays the user's skill tier.
 * Accepts `rating` (number) — tier is derived automatically.
 * Optional `className` for layout overrides.
 */
const TierBadge = ({ rating, className = '' }) => {
  const tier = getSkillTier(rating);
  const color = getTierColor(tier);
  const borderColor = getTierBorderColor(tier);
  const isElite = tier === 'Elite';

  return (
    <span
      className={`tier-badge ${isElite ? 'tier-badge--elite' : ''} ${className}`.trim()}
      style={{ color, borderColor }}
    >
      {tier}
    </span>
  );
};

export default TierBadge;
