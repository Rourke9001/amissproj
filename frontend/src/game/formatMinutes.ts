// Mirrors amiss-core's TimeService.format: "38h 30m", or just "72h" when the
// minutes part is zero, or just "45m" under an hour ("0h" for 0).
export function formatMinutes(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  if (m === 0) {
    return `${h}h`;
  }
  if (h === 0) {
    return `${m}m`;
  }
  return `${h}h ${m}m`;
}
