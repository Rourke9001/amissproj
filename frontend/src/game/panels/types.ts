import type { SaveStateDto } from '../../api/types';

export interface PanelProps {
  saveId: number;
  player: SaveStateDto;
  onNotify: (message: string) => void;
}
