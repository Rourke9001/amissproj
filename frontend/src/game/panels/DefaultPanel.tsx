import type { PanelProps } from './types';

export function DefaultPanel({ player }: PanelProps) {
  return <p className="panel-muted">{player.location.name} opens with the KAN-5 economy epic.</p>;
}
