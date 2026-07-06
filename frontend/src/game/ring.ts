// Mirrors amiss-core's Board.ringDistance: the walking distance in ring steps
// between two stops on the 13-stop loop, the smaller of going clockwise or
// anticlockwise. Preview-only for the confirm panel's cost estimate — the
// server's MoveResponse stays authoritative.
export function ringSteps(a: number, b: number, size: number): number {
  const direct = Math.abs(a - b);
  return Math.min(direct, size - direct);
}
