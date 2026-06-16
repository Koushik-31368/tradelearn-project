// src/utils/skillTier.js
// Centralised skill-tier logic — derived from ELO rating.
// Every component must import from here; never inline tier mappings.

const TIERS = [
  { min: 2500, name: 'Grandmaster' },
  { min: 2000, name: 'Master' },
  { min: 1500, name: 'Diamond' },
  { min: 1100, name: 'Platinum' },
  { min: 800,  name: 'Gold' },
  { min: 500,  name: 'Silver' },
  { min: 0,    name: 'Bronze' },
];

/**
 * Derive the skill tier name from a numeric ELO rating.
 */
export function getSkillTier(rating) {
  const r = Number(rating) || 0;
  for (const t of TIERS) {
    if (r >= t.min) return t.name;
  }
  return 'Bronze';
}

/** Border colour for a given tier (used on the pill badge). */
export function getTierBorderColor(tier) {
  switch (tier) {
    case 'Grandmaster': return '#ff0055'; // neon pink/red
    case 'Master':      return '#cc00ff'; // purple
    case 'Diamond':     return '#00e5ff'; // cyan
    case 'Platinum':    return '#00ff88'; // green accent
    case 'Gold':        return '#ffd700'; // gold
    case 'Silver':      return '#c0c0c0'; // silver
    case 'Bronze':      return '#cd7f32'; // bronze
    default:            return '#484f58'; // muted gray
  }
}

/** Text colour for a given tier. */
export function getTierColor(tier) {
  switch (tier) {
    case 'Grandmaster': return '#ff0055';
    case 'Master':      return '#cc00ff';
    case 'Diamond':     return '#00e5ff';
    case 'Platinum':    return '#00ff88';
    case 'Gold':        return '#ffd700';
    case 'Silver':      return '#c0c0c0';
    case 'Bronze':      return '#cd7f32';
    default:            return '#8b949e';
  }
}
