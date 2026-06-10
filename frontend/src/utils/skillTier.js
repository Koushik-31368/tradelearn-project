// src/utils/skillTier.js
// Centralised skill-tier logic — derived from ELO rating.
// Every component must import from here; never inline tier mappings.

const TIERS = [
  { min: 1500, name: 'Elite' },
  { min: 1300, name: 'Advanced' },
  { min: 1100, name: 'Intermediate' },
  { min: 0,    name: 'Beginner' },
];

/**
 * Derive the skill tier name from a numeric ELO rating.
 * @param {number} rating
 * @returns {'Beginner'|'Intermediate'|'Advanced'|'Elite'}
 */
export function getSkillTier(rating) {
  const r = Number(rating) || 0;
  for (const t of TIERS) {
    if (r >= t.min) return t.name;
  }
  return 'Beginner';
}

/** Border colour for a given tier (used on the pill badge). */
export function getTierBorderColor(tier) {
  switch (tier) {
    case 'Elite':        return '#cfaa3c';   // gold
    case 'Advanced':     return '#00ff88';   // green accent
    case 'Intermediate': return '#58a6ff';   // blue
    default:             return '#484f58';   // muted gray
  }
}

/** Text colour for a given tier. */
export function getTierColor(tier) {
  switch (tier) {
    case 'Elite':        return '#cfaa3c';
    case 'Advanced':     return '#00ff88';
    case 'Intermediate': return '#58a6ff';
    default:             return '#8b949e';
  }
}
