import React, { useState, useRef, useEffect } from 'react';
import {
  GripVertical,
  ChevronUp,
  ChevronDown,
  Minimize2,
  Maximize2,
  Move,
  Lock,
  Unlock,
  RotateCcw,
  Sparkles,
  Layers
} from 'lucide-react';
import { MouseSkin, MOUSE_SKINS } from '../types';

interface FloatingMiniMouseProps {
  onLeftClick: () => void;
  onRightClick: () => void;
  onMiddleClick: () => void;
  onScrollUp: () => void;
  onScrollDown: () => void;
  onDoubleClick: () => void;
  onDragToggle: () => void;
  isDraggingActive: boolean;
  activeSkin: MouseSkin;
  onSkinSelect: (skinId: string) => void;
  onResetPosition?: () => void;
}

export const FloatingMiniMouse: React.FC<FloatingMiniMouseProps> = ({
  onLeftClick,
  onRightClick,
  onMiddleClick,
  onScrollUp,
  onScrollDown,
  onDoubleClick,
  onDragToggle,
  isDraggingActive,
  activeSkin,
  onSkinSelect,
  onResetPosition
}) => {
  const [position, setPosition] = useState({ x: 20, y: 120 });
  const [isExpanded, setIsExpanded] = useState(true);
  const [showSkinPicker, setShowSkinPicker] = useState(false);
  const isDraggingRef = useRef(false);
  const dragStartRef = useRef({ x: 0, y: 0 });

  // Handle widget dragging across the viewport
  const handleWidgetPointerDown = (e: React.PointerEvent | React.TouchEvent) => {
    isDraggingRef.current = true;
    const clientX = 'touches' in e ? e.touches[0].clientX : (e as React.PointerEvent).clientX;
    const clientY = 'touches' in e ? e.touches[0].clientY : (e as React.PointerEvent).clientY;
    
    dragStartRef.current = {
      x: clientX - position.x,
      y: clientY - position.y
    };
  };

  useEffect(() => {
    const handlePointerMove = (e: MouseEvent | TouchEvent) => {
      if (!isDraggingRef.current) return;
      const clientX = 'touches' in e ? (e as TouchEvent).touches[0].clientX : (e as MouseEvent).clientX;
      const clientY = 'touches' in e ? (e as TouchEvent).touches[0].clientY : (e as MouseEvent).clientY;

      const newX = Math.max(10, Math.min(window.innerWidth - 110, clientX - dragStartRef.current.x));
      const newY = Math.max(10, Math.min(window.innerHeight - 200, clientY - dragStartRef.current.y));

      setPosition({ x: newX, y: newY });
    };

    const handlePointerUp = () => {
      isDraggingRef.current = false;
    };

    window.addEventListener('mousemove', handlePointerMove);
    window.addEventListener('mouseup', handlePointerUp);
    window.addEventListener('touchmove', handlePointerMove);
    window.addEventListener('touchend', handlePointerUp);

    return () => {
      window.removeEventListener('mousemove', handlePointerMove);
      window.removeEventListener('mouseup', handlePointerUp);
      window.removeEventListener('touchmove', handlePointerMove);
      window.removeEventListener('touchend', handlePointerUp);
    };
  }, [position]);

  const currentSkinData = MOUSE_SKINS.find(s => s.id === activeSkin) || MOUSE_SKINS[0];

  return (
    <div
      style={{
        transform: `translate3d(${position.x}px, ${position.y}px, 0)`,
        position: 'fixed',
        top: 0,
        left: 0,
        zIndex: 9999
      }}
      className="touch-none select-none transition-transform duration-75 ease-out"
    >
      {/* Skin Picker Overlay */}
      {showSkinPicker && (
        <div className="absolute -top-36 left-0 right-0 bg-slate-900/95 border border-cyan-500/50 rounded-2xl p-2 shadow-2xl backdrop-blur-md flex flex-col gap-1 z-50 animate-in fade-in zoom-in-95 duration-150 w-48">
          <div className="flex items-center justify-between text-[10px] text-cyan-300 font-bold px-1 pb-1 border-b border-slate-800">
            <span className="flex items-center gap-1">
              <Sparkles size={11} /> پوسته موس
            </span>
            <button
              onClick={() => setShowSkinPicker(false)}
              className="text-slate-400 hover:text-white text-xs px-1"
            >
              ✕
            </button>
          </div>
          <div className="grid grid-cols-2 gap-1 max-h-28 overflow-y-auto p-0.5">
            {MOUSE_SKINS.map((skin) => (
              <button
                key={skin.id}
                onClick={() => {
                  onSkinSelect(skin.id);
                  setShowSkinPicker(false);
                }}
                className={`p-1.5 rounded-lg text-[9px] font-medium flex items-center gap-1.5 transition-all ${
                  activeSkin === skin.id
                    ? 'bg-cyan-500/20 text-cyan-200 border border-cyan-400'
                    : 'bg-slate-800/80 text-slate-300 hover:bg-slate-700/80 border border-transparent'
                }`}
              >
                <span className="text-xs">{skin.icon}</span>
                <span className="truncate">{skin.name}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Mini Mode (Collapsed Pill) */}
      {!isExpanded ? (
        <div className="flex items-center gap-1 bg-slate-950/90 border border-cyan-500/60 rounded-full p-1 shadow-xl backdrop-blur-md animate-in fade-in zoom-in-90 duration-150">
          <button
            onPointerDown={handleWidgetPointerDown}
            onTouchStart={handleWidgetPointerDown}
            className="p-1 text-cyan-400 hover:text-cyan-200 cursor-grab active:cursor-grabbing"
            title="جابه‌جایی"
          >
            <GripVertical size={12} />
          </button>
          <button
            onClick={onLeftClick}
            className="px-2 py-1 bg-cyan-600/30 hover:bg-cyan-500/50 text-cyan-200 text-[10px] font-bold rounded-full border border-cyan-400/40 active:scale-95 transition-transform"
          >
            چپ
          </button>
          <button
            onClick={onRightClick}
            className="px-2 py-1 bg-rose-600/30 hover:bg-rose-500/50 text-rose-200 text-[10px] font-bold rounded-full border border-rose-400/40 active:scale-95 transition-transform"
          >
            راست
          </button>
          <button
            onClick={() => setIsExpanded(true)}
            className="p-1 bg-slate-800 hover:bg-slate-700 text-cyan-300 rounded-full border border-slate-700 active:scale-95 transition-transform"
            title="بزرگ‌سازی (ماکسیمم)"
          >
            <Maximize2 size={11} />
          </button>
        </div>
      ) : (
        /* Physical Ergonomic Mouse Body Structure */
        <div className="w-24 bg-slate-950/95 border-2 border-cyan-400/80 rounded-[22px] shadow-[0_15px_35px_rgba(0,0,0,0.85)] backdrop-blur-xl overflow-hidden flex flex-col animate-in fade-in zoom-in-95 duration-150">
          
          {/* Mouse Top Section: Left Click | Center (Single Minimize/Maximize Toggle + Drag Lock) | Right Click */}
          <div className="p-1 bg-slate-900/90 flex items-stretch justify-between gap-1 border-b border-slate-800 relative">
            
            {/* Left Click Button (Clean, no text/emoji) */}
            <button
              onClick={onLeftClick}
              className="flex-1 py-3 px-0.5 bg-gradient-to-b from-cyan-950/90 to-slate-900 border border-cyan-500/60 hover:border-cyan-400 active:bg-cyan-500/30 rounded-xl text-center active:scale-95 transition-all shadow-inner flex items-center justify-center shrink-0 min-h-[32px]"
              title="کلیک چپ"
            >
              <div className="w-1.5 h-1.5 rounded-full bg-cyan-400/80 shadow-[0_0_6px_rgba(6,182,212,0.8)]" />
            </button>

            {/* Center Column: [Single Minimize/Maximize Toggle] & [Drag Lock] */}
            <div className="flex flex-col gap-1 justify-between items-center px-0.5 shrink-0">
              {/* Single Minimize/Maximize Toggle Button */}
              <button
                onClick={() => setIsExpanded(!isExpanded)}
                onPointerDown={handleWidgetPointerDown}
                onTouchStart={handleWidgetPointerDown}
                className="w-6 h-5 p-0.5 rounded-md bg-slate-800 hover:bg-slate-700 active:bg-cyan-600 text-slate-300 hover:text-white border border-slate-700 transition-colors flex items-center justify-center shadow-sm"
                title={isExpanded ? "کوچک‌سازی (مینیمم)" : "بزرگ‌سازی (ماکسیمم)"}
              >
                {isExpanded ? <Minimize2 size={10} /> : <Maximize2 size={10} />}
              </button>

              {/* Tiny Drag Lock Switch */}
              <button
                onClick={onDragToggle}
                className={`p-1 rounded transition-all ${
                  isDraggingActive
                    ? 'bg-amber-500 text-slate-950 font-bold shadow-[0_0_8px_rgba(245,158,11,0.6)] animate-pulse'
                    : 'text-slate-400 hover:text-amber-300 hover:bg-slate-800'
                }`}
                title={isDraggingActive ? 'قفل کشیدن فعال است (رهاسازی)' : 'قفل کشیدن (درگ نگه‌داشتن)'}
              >
                {isDraggingActive ? <Lock size={10} /> : <Unlock size={10} />}
              </button>
            </div>

            {/* Right Click Button (Clean, no text/emoji) */}
            <button
              onClick={onRightClick}
              className="flex-1 py-3 px-0.5 bg-gradient-to-b from-rose-950/90 to-slate-900 border border-rose-500/60 hover:border-rose-400 active:bg-rose-500/30 rounded-xl text-center active:scale-95 transition-all shadow-inner flex items-center justify-center shrink-0 min-h-[32px]"
              title="کلیک راست"
            >
              <div className="w-1.5 h-1.5 rounded-full bg-rose-400/80 shadow-[0_0_6px_rgba(244,63,94,0.8)]" />
            </button>
          </div>

          {/* Center Scroll Wheel & Quick Utility Bar */}
          <div className="px-1 py-1 bg-slate-950 flex items-center justify-between border-b border-slate-800/80">
            {/* Scroll Up */}
            <button
              onClick={onScrollUp}
              className="p-1 rounded bg-slate-900 hover:bg-cyan-950/80 border border-slate-800 hover:border-cyan-500/50 text-cyan-300 active:scale-90 transition-all"
              title="اسکرول به بالا"
            >
              <ChevronUp size={11} />
            </button>

            {/* Physical Scroll Wheel Button (Middle Click) */}
            <button
              onClick={onMiddleClick}
              className="px-1.5 py-0.5 bg-gradient-to-b from-slate-800 to-slate-900 border border-slate-700 hover:border-cyan-400 rounded text-[8px] font-bold text-slate-300 hover:text-cyan-300 active:scale-90 transition-all flex items-center gap-0.5 shadow-sm"
              title="کلیک وسط (Wheel)"
            >
              <span className="w-1.5 h-3 bg-cyan-400/60 rounded-full inline-block animate-pulse" />
            </button>

            {/* Scroll Down */}
            <button
              onClick={onScrollDown}
              className="p-1 rounded bg-slate-900 hover:bg-cyan-950/80 border border-slate-800 hover:border-cyan-500/50 text-cyan-300 active:scale-90 transition-all"
              title="اسکرول به پایین"
            >
              <ChevronDown size={11} />
            </button>
          </div>

          {/* Mouse Touchpad Body & Micro Controls */}
          <div className="p-1 bg-gradient-to-b from-slate-900 to-slate-950 flex flex-col items-center justify-center relative min-h-[50px] group">
            {/* Decorative Touchpad Grid Lines */}
            <div className="absolute inset-0 opacity-20 bg-[radial-gradient(#06b6d4_1px,transparent_1px)] [background-size:8px_8px] pointer-events-none" />

            <div className="w-4 h-4 rounded-full border border-cyan-500/40 bg-cyan-500/10 flex items-center justify-center text-cyan-400 pointer-events-none group-hover:scale-110 transition-transform opacity-60">
              <Move size={10} />
            </div>

            {/* Bottom-Left Tiny Scroll Up Button */}
            <button
              onClick={onDoubleClick}
              className="absolute bottom-1 left-1 p-1 bg-slate-900/90 hover:bg-slate-800 border border-slate-700/80 rounded text-[7.5px] font-bold text-slate-300 hover:text-cyan-300 active:scale-90 transition-all"
              title="دوبار کلیک"
            >
              2x
            </button>

            {/* Bottom-Right Skin Switcher Button */}
            <button
              onClick={() => setShowSkinPicker(!showSkinPicker)}
              className="absolute bottom-1 right-1 p-1 bg-slate-900/90 hover:bg-cyan-950/90 border border-cyan-500/40 rounded text-cyan-300 hover:text-white active:scale-90 transition-all flex items-center gap-0.5"
              title="تغییر پوسته موس"
            >
              <Layers size={9} />
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
