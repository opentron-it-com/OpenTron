import { useState, useEffect } from 'react';
import './AvatarOrb.css';

export type AvatarState = 'idle' | 'listening' | 'thinking' | 'speaking';

interface AvatarOrbProps {
  state: AvatarState;
  onClick?: () => void;
}

function LightningBolt({ className, color = '#7df9ff', delay = 0, rotate = 0 }: {
  className: string;
  color?: string;
  delay?: number;
  rotate?: number;
}) {
  return (
    <svg
      className={`bolt ${className}`}
      viewBox="0 0 28 80"
      xmlns="http://www.w3.org/2000/svg"
      style={{ animationDelay: `${delay}s`, transform: `rotate(${rotate}deg)` }}
    >
      <defs>
        <filter id={`glow-${className}`} x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur stdDeviation="3" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      {/* Main bolt shape — jagged zigzag */}
      <polygon
        points="18,0 9,34 16,34 5,80 22,30 14,30 24,0"
        fill={color}
        opacity="0.95"
        filter={`url(#glow-${className})`}
      />
      {/* Bright core */}
      <polygon
        points="16,4 11,32 15,32 8,72 19,32 13,32 20,4"
        fill="white"
        opacity="0.5"
        filter={`url(#glow-${className})`}
      />
    </svg>
  );
}

export function AvatarOrb({ state, onClick }: AvatarOrbProps) {
  const [isAnimating, setIsAnimating] = useState(false);

  useEffect(() => {
    setIsAnimating(state === 'thinking' || state === 'speaking');
  }, [state]);

  return (
    <div
      className={`avatar-orb avatar-${state}`}
      onClick={onClick}
      role="button"
      tabIndex={0}
      aria-label={`Avatar - ${state}`}
    >
      {/* Lightning bolts — only visible while animating */}
      {isAnimating && (
        <div className="orb-lightning active">
          <LightningBolt className="bolt-1" color="#7df9ff" delay={0}    rotate={0}   />
          <LightningBolt className="bolt-2" color="#4fc3f7" delay={0.15} rotate={-8}  />
          <LightningBolt className="bolt-3" color="#7df9ff" delay={0.30} rotate={5}   />
          <LightningBolt className="bolt-4" color="#29b6f6" delay={0.45} rotate={-4}  />
          <LightningBolt className="bolt-5" color="#7df9ff" delay={0.60} rotate={8}   />
        </div>
      )}

      {/* Main orb - TRON image */}
      <div className={`orb-inner ${isAnimating ? 'pulse' : ''}`}>
        <img src="/ermis.png" alt="Ermis Avatar" className="tron-image" />
      </div>

      {/* Outer rotating ring */}
      <div className={`orb-ring ${isAnimating ? 'spin' : ''}`} />

      {/* Listening dots */}
      {state === 'listening' && (
        <div className="listening-dots">
          <span className="dot dot-1" />
          <span className="dot dot-2" />
          <span className="dot dot-3" />
        </div>
      )}

      {/* Speaking waveform */}
      {state === 'speaking' && (
        <div className="waveform">
          <div className="wave wave-1" />
          <div className="wave wave-2" />
          <div className="wave wave-3" />
        </div>
      )}

      {/* Glow effect */}
      <div className={`orb-glow ${isAnimating ? 'active' : ''}`} />
    </div>
  );
}
