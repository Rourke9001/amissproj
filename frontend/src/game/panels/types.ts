import type { PlayerStateDto } from '../../api/types';

export interface PanelProps {
  username: string;
  player: PlayerStateDto;
  onNotify: (message: string) => void;
}
