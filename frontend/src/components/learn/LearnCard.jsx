import React from 'react';
import './LearnCard.css';

const LearnCard = ({ icon, title, description, highlight }) => {
  return (
    <div className={`learn-card${highlight ? ' learn-card--highlight' : ''}`}>
      <div className="learn-card__icon">{icon}</div>
      <h4 className="learn-card__title">{title}</h4>
      <p className="learn-card__desc">{description}</p>
    </div>
  );
};

export default LearnCard;
