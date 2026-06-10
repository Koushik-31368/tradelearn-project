import React, { useState, useRef, useEffect } from 'react';
import './LearnSection.css';

const LearnSection = ({ id, number, title, icon, children, defaultOpen = false }) => {
  const [isOpen, setIsOpen] = useState(defaultOpen);
  const contentRef = useRef(null);
  const [height, setHeight] = useState(defaultOpen ? 'auto' : '0px');

  useEffect(() => {
    if (isOpen) {
      setHeight(`${contentRef.current.scrollHeight}px`);
      const timer = setTimeout(() => setHeight('auto'), 350);
      return () => clearTimeout(timer);
    } else {
      if (height === 'auto') {
        setHeight(`${contentRef.current.scrollHeight}px`);
        requestAnimationFrame(() => {
          requestAnimationFrame(() => setHeight('0px'));
        });
      } else {
        setHeight('0px');
      }
    }
  }, [isOpen]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <section className="learn-section" id={id}>
      <button
        className={`learn-section__header${isOpen ? ' learn-section__header--open' : ''}`}
        onClick={() => setIsOpen(prev => !prev)}
        aria-expanded={isOpen}
      >
        <div className="learn-section__header-left">
          <span className="learn-section__number">{String(number).padStart(2, '0')}</span>
          <span className="learn-section__icon">{icon}</span>
          <h2 className="learn-section__title">{title}</h2>
        </div>
        <span className={`learn-section__chevron${isOpen ? ' learn-section__chevron--open' : ''}`}>
          <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
            <path d="M5 7.5L10 12.5L15 7.5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
          </svg>
        </span>
      </button>

      <div
        className="learn-section__body"
        ref={contentRef}
        style={{ maxHeight: height }}
      >
        <div className="learn-section__content">
          {children}
        </div>
      </div>
    </section>
  );
};

export default LearnSection;
