import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getBoard } from '../api/board';
import { endWeek, getPlayerState, move } from '../api/player';
import { ApiError } from '../api/http';
import type { EndWeekResponse, LocationDto, MoveResponse, TravelDto } from '../api/types';
import { BOARD_IMAGE } from '../assets/manifest';
import { formatMinutes } from './formatMinutes';
import { ringSteps } from './ring';
import { Hud } from './Hud';
import { EndWeekModal } from './EndWeekModal';
import { resolvePanel } from './panels/registry';
import './board.css';

interface BoardScreenProps {
  username: string;
}

const MAX_NOTIFICATIONS = 6;

function errorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    return err.problem.detail ?? err.problem.title ?? err.message;
  }
  return 'Could not reach the server.';
}

function hotspotTooltip(
  stop: LocationDto,
  currentStop: LocationDto,
  travel: TravelDto,
  totalStops: number,
): string {
  const steps = ringSteps(stop.ringIndex, currentStop.ringIndex, totalStops);
  if (steps === 0) {
    return `Enter — ${formatMinutes(travel.enterBuildingMinutes)}`;
  }
  const cost = formatMinutes(steps * travel.minutesPerStep + travel.enterBuildingMinutes);
  return `${steps} stop${steps === 1 ? '' : 's'} — ${cost}`;
}

export function BoardScreen({ username }: BoardScreenProps) {
  const queryClient = useQueryClient();
  const [notifications, setNotifications] = useState<string[]>([]);
  const [endWeekResult, setEndWeekResult] = useState<EndWeekResponse | null>(null);

  const pushNotification = (message: string) => {
    setNotifications((prev) => [message, ...prev].slice(0, MAX_NOTIFICATIONS));
  };

  const boardQuery = useQuery({ queryKey: ['board'], queryFn: getBoard, staleTime: Infinity });
  const playerQuery = useQuery({
    queryKey: ['player', username],
    queryFn: () => getPlayerState(username),
  });

  const moveMutation = useMutation({
    mutationFn: (target: string) => move(username, target),
    onSuccess: (res: MoveResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      const label = boardQuery.data?.stops.find((s) => s.id === res.target)?.name ?? res.target;
      pushNotification(
        res.steps === 0
          ? `Entered ${label} (${formatMinutes(res.minutesCharged)})`
          : `Moved to ${label} (${formatMinutes(res.minutesCharged)})`,
      );
    },
    onError: (err: unknown) => {
      pushNotification(errorMessage(err));
      // A rejected move can still have charged the clock: a move landing exactly on
      // 0 minutes is persisted server-side but answered with 409 week-over (Swing
      // parity), so refetch rather than trust the cached state.
      void queryClient.invalidateQueries({ queryKey: ['player', username] });
    },
  });

  const endWeekMutation = useMutation({
    mutationFn: () => endWeek(username),
    onSuccess: (res: EndWeekResponse) => {
      queryClient.setQueryData(['player', username], res.state);
      setEndWeekResult(res);
    },
    onError: (err: unknown) => {
      pushNotification(errorMessage(err));
    },
  });

  if (boardQuery.isPending || playerQuery.isPending) {
    return <p>Loading board…</p>;
  }
  if (boardQuery.error !== null) {
    return <p role="alert">{boardQuery.error.message}</p>;
  }
  if (playerQuery.error !== null) {
    return <p role="alert">{playerQuery.error.message}</p>;
  }

  const board = boardQuery.data;
  const player = playerQuery.data;
  if (board === undefined || player === undefined) {
    return null;
  }

  const currentStop = player.location;
  const tokenLeft = currentStop.col * 20 + 10;
  const tokenTop = currentStop.row * 25 + 12.5;

  const handleHotspotClick = (stop: LocationDto) => {
    if (moveMutation.isPending) {
      return;
    }
    moveMutation.mutate(stop.id);
  };

  const StopPanel = resolvePanel(currentStop.id);

  return (
    <section className="board-screen">
      <h1>Game</h1>
      <div className="board-frame">
        <img src={BOARD_IMAGE} alt="Jones in the Fast Lane board" className="board-image" />
        {board.stops.map((stop) => (
          <button
            key={stop.id}
            type="button"
            className={
              stop.id === currentStop.id ? 'stop-hotspot stop-hotspot--current' : 'stop-hotspot'
            }
            style={{
              left: `${stop.col * 20}%`,
              top: `${stop.row * 25}%`,
              width: '20%',
              height: '25%',
            }}
            aria-label={stop.name}
            onClick={() => handleHotspotClick(stop)}
          >
            <span className="hotspot-tooltip">
              {hotspotTooltip(stop, currentStop, board.travel, board.stops.length)}
            </span>
          </button>
        ))}
        <svg
          className="player-token"
          style={{ left: `${tokenLeft}%`, top: `${tokenTop}%` }}
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <circle cx="12" cy="12" r="10" />
        </svg>
        <div className="board-centre">
          <Hud
            player={player}
            onEndWeek={() => endWeekMutation.mutate()}
            endWeekPending={endWeekMutation.isPending}
          />
          <StopPanel username={username} player={player} onNotify={pushNotification} />
        </div>
        <div className="board-clock" style={{ left: '40%', top: '75%' }}>
          <p className="board-clock-time">{player.timeDisplay}</p>
          <p className="board-clock-round">Round {player.round}</p>
        </div>
      </div>
      <ul role="log" aria-label="Notifications" className="board-notifications">
        {notifications.map((message, index) => (
          <li key={`${index}-${message}`}>{message}</li>
        ))}
      </ul>
      {endWeekResult !== null && (
        <EndWeekModal result={endWeekResult} onClose={() => setEndWeekResult(null)} />
      )}
    </section>
  );
}
