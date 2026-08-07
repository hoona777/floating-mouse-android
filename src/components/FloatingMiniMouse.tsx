import React, { useState, useRef } from 'react';
import { Palette, Move, Lock, ChevronUp, ChevronDown, Maximize2, Minimize2 } from 'lucide-react';

interface FloatingMiniMouseProps {
  onLeftClick?: () => void;
  onRightClick?: () => void;
  onMovePointer?: (deltaX: number, deltaY: number) => void;
  onScrollUp?: () => void;
  onScrollDown?: () => void;
  isDragLockActive?: boolean;
  onToggleDragLock?: () => void;
  currentSkin?: string;
  onSwitchSkin?: () => void;
}

export const FloatingMiniMouse: React.FC<FloatingMiniMouseProps> = ({
  onLeftClick,
  onRightClick,
  onMovePointer,
  onScrollUp,
  onScrollDown,
  isDragLockActive = false,
  onToggleDragLock,
  onSwitchSkin,
}) => {
  const [position, setPosition] = useState<{ x: number; y: number }>({ x: 20, y: 120 });
  const [isDraggingMouse, setIsDraggingMouse] = useState(false);
  const [isMinimized, setIsMinimized] = useState(false);
  
  const dragStartRef = useRef<{ startX: number; startY: number; mouseX: number; mouseY: number }>({
    startX: 0,
    startY: 0,
    mouseX: 0,
    mouseY: 0,
  });

  const touchpadRef = useRef<HTMLDivElement>(null);
  const touchpadDragRef = useRef<{ lastX: number; lastY: number }>({ lastX: 0, lastY: 0 });

  const handleMouseDownHeader = (e: React.MouseEvent) => {
    setIsDraggingMouse(true);
    dragStartRef.current = {
      startX: e.clientX,
      startY: e.clientY,
      mouseX: position.x,
      mouseY: position.y,
    };

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const deltaX = dragStartRef.current.startX - moveEvent.clientX;
      const deltaY = dragStartRef.current.startY - moveEvent.clientY;
      setPosition({
        x: Math.max(10, dragStartRef.current.mouseX + deltaX),
        y: Math.max(10, dragStartRef.current.mouseY + deltaY),
      });
    };

    const handleMouseUp = () => {
      setIsDraggingMouse(false);
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  };

  const handleTouchpadStart = (e: React.TouchEvent) => {
    if (e.touches.length > 0) {
      touchpadDragRef.current = {
        lastX: e.touches[0].clientX,
        lastY: e.touches[0].clientY,
      };
    }
  };

  const handleTouchpadMove = (e: React.TouchEvent) => {
    if (e.touches.length > 0 && onMovePointer) {
      const currentX = e.touches[0].clientX;
      const currentY = e.touches[0].clientY;

      const deltaX = (currentX - touchpadDragRef.current.lastX) * 1.5;
      const deltaY = (currentY - touchpadDragRef.current.lastY) * 1.5;

      onMovePointer(deltaX, deltaY);

      touchpadDragRef.current = {
        lastX: currentX,
        lastY: currentY,
      };
    }
  };

  return (
    <div
      style={{ right: `${position.x}px`, bottom: `${position.y}px` }}
      className={`fixed z-[999999] select-none transition-shadow ${
        isDraggingMouse ? 'cursor-grabbing opacity-90' : 'cursor-grab'
      }`}
    >
      <div className="relative group">
        {/* Outer Ergonomic Physical Mouse Outer Shell */}
        <div className="w-[110px] bg-slate-950/95 border-2 border-cyan-500/80 rounded-[28px] p-1.5 shadow-[0_0_25px_rgba(6,182,212,0.4)] backdrop-blur-md flex flex-col items-stretch overflow-hidden transition-all duration-300">
          
          {/* Header Drag Handle */}
          <div
            onMouseDown={handleMouseDownHeader}
            className="w-full py-1 bg-gradient-to-r from-cyan-950 via-slate-900 to-cyan-950 flex items-center justify-between px-2 cursor-grab rounded-t-2xl border-b border-cyan-500/30"
          >
            <div className="flex items-center gap-1">
              <div className="w-1.5 h-1.5 rounded-full bg-cyan-400 animate-pulse" />
              <span className="text-[8px] font-black tracking-tighter text-cyan-300 uppercase">
                MINI MOUSE
              </span>
            </div>

            <button
              onClick={() => setIsMinimized(!isMinimized)}
              className="text-cyan-400 hover:text-white transition-colors p-0.5"
            >
              {isMinimized ? <Maximize2 size={10} /> : <Minimize2 size={10} />}
            </button>
          </div>

          {!isMinimized && (
            <>
              {/* Mouse Top Buttons */}
              <div className="p-1 bg-slate-900/90 flex items-stretch justify-between gap-1 border-b border-slate-800 relative">
                
                {/* Left Click Button */}
                <button
                  onClick={onLeftClick}
                  className="flex-1 py-3 px-0.5 bg-gradient-to-b from-cyan-950/90 to-slate-900 border border-cyan-500/60 hover:border-cyan-400 active:bg-cyan-500/30 rounded-xl text-center active:scale-95 transition-all shadow-inner flex items-center justify-center shrink-0 min-h-[32px]"
                  title="کلیک چپ"
                >
                  <div className="w-1.5 h-1.5 rounded-full bg-cyan-400/80 shadow-[0_0_6px_rgba(6,182,212,0.8)]" />
                </button>

                {/* Center Column */}
                <div className="flex flex-col items-center justify-between gap-1 shrink-0 w-6">
                  <button
                    onClick={onSwitchSkin}
                    className="w-full py-0.5 bg-slate-800 border border-cyan-500/40 rounded-lg text-cyan-300 flex items-center justify-center hover:bg-slate-700 active:scale-90 transition-transform"
                    title="تغییر پوسته"
                  >
                    <Palette size={10} />
                  </button>

                  <button
                    onClick={onToggleDragLock}
                    className={`w-full py-0.5 rounded-lg border flex items-center justify-center transition-all ${
                      isDragLockActive
                        ? 'bg-amber-500/20 border-amber-400 text-amber-300 shadow-[0_0_8px_rgba(245,158,11,0.5)]'
                        : 'bg-slate-800 border-slate-700 text-slate-400 hover:text-slate-200'
                    }`}
                    title="قفل کشیدن"
                  >
                    <Lock size={9} />
                  </button>
                </div>

                {/* Right Click Button */}
                <button
                  onClick={onRightClick}
                  className="flex-1 py-3 px-0.5 bg-gradient-to-b from-rose-950/90 to-slate-900 border border-rose-500/60 hover:border-rose-400 active:bg-rose-500/30 rounded-xl text-center active:scale-95 transition-all shadow-inner flex items-center justify-center shrink-0 min-h-[32px]"
                  title="کلیک راست"
                >
                  <div className="w-1.5 h-1.5 rounded-full bg-rose-400/80 shadow-[0_0_6px_rgba(244,63,94,0.8)]" />
                </button>
              </div>

              {/* Physical Touchpad Container */}
              <div
                ref={touchpadRef}
                onTouchStart={handleTouchpadStart}
                onTouchMove={handleTouchpadMove}
                className="relative h-16 bg-slate-950/90 rounded-b-2xl border-t border-slate-800/80 overflow-hidden flex flex-col items-center justify-center cursor-crosshair group/pad"
              >
                <div className="absolute inset-0 opacity-20 bg-[radial-gradient(#06b6d4_1px,transparent_1px)] [background-size:8px_8px] pointer-events-none" />

                <div className="w-4 h-4 rounded-full border border-cyan-500/40 bg-cyan-500/10 flex items-center justify-center text-cyan-400 pointer-events-none group-hover/pad:scale-110 transition-transform opacity-60">
                  <Move size={10} />
                </div>

                {/* Scroll Up Button */}
                <button
                  onClick={onScrollUp}
                  className="absolute bottom-1 left-1 p-1 bg-slate-900/90 border border-cyan-500/40 hover:border-cyan-400 text-cyan-300 rounded-lg active:scale-90 transition-transform shadow-md"
                  title="اسکرول به بالا"
                >
                  <ChevronUp size={10} />
                </button>

                {/* Scroll Down Button */}
                <button
                  onClick={onScrollDown}
                  className="absolute bottom-1 right-1 p-1 bg-slate-900/90 border border-cyan-500/40 hover:border-cyan-400 text-cyan-300 rounded-lg active:scale-90 transition-transform shadow-md"
                  title="اسکرول به پایین"
                >
                  <ChevronDown size={10} />
                </button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};
