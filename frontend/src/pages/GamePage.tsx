import { BoardScreen } from '../game/BoardScreen';
import { useAuth } from '../auth/AuthContext';

export function GamePage() {
  const { username } = useAuth();

  if (username === null) {
    // RequireAuth already guards this route; this is just a defensive no-op.
    return null;
  }

  return <BoardScreen username={username} />;
}
