export function Watermark() {
  return (
    <div aria-hidden
      className="pointer-events-none fixed inset-0 z-0 overflow-hidden select-none"
      style={{
        backgroundImage:
          "repeating-linear-gradient(-30deg, transparent 0 220px, oklch(0.58 0.115 45 / 0.05) 220px 222px)",
      }}>
      <div className="absolute -right-10 bottom-10 font-script text-[18vw] leading-none text-primary/[0.045] rotate-[-6deg] whitespace-nowrap">
        Mini&rsquo;s n More
      </div>
    </div>
  );
}
