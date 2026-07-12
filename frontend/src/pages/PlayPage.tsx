import { useParams } from 'react-router';
import { BoardScreen } from '../game/BoardScreen';

export function PlayPage() {
  const { saveId } = useParams<{ saveId: string }>();
  const id = Number(saveId);
  if (!saveId || Number.isNaN(id)) {
    return <p role="alert">Unknown save.</p>;
  }
  return <BoardScreen saveId={id} />;
}
