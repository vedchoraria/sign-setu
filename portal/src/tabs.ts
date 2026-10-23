/** WAI-ARIA tabs pattern: arrow keys wrap, Home and End jump to the first and last tab. */
export function nextTabIndex(key: string, index: number, count: number): number | null {
  if (count < 1) return null;
  switch (key) {
    case "ArrowRight":
      return (index + 1) % count;
    case "ArrowLeft":
      return (index - 1 + count) % count;
    case "Home":
      return 0;
    case "End":
      return count - 1;
    default:
      return null;
  }
}
