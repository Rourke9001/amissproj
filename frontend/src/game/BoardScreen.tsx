import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { getBoard } from '../api/board';
import { getPlayerState, move } from '../api/player';
import { ApiError } from '../api/http';
import type { LocationDto, MoveResponse } from '../api/types';
import { BOARD_IMAGE, storefrontImage } from '../assets/manifest';
import { formatMinutes } from './formatMinutes';
import { ringSteps } from './ring';
import './board.css';

interface BoardScreenProps {
  username: string;
}

export function BoardScreen({ username }: BoardScreenProps) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<LocationDto | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

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
      setNotice(
        res.steps === 0
          ? `Entered ${label} (${formatMinutes(res.minutesCharged)})`
          : `Moved to ${label} (${formatMinutes(res.minutesCharged)})`,
      );
      setSelected(null);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setNotice(err.problem.detail ?? err.problem.title ?? err.message);
      } else {
        setNotice('Could not reach the server.');
      }
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
    setNotice(null);
    setSelected(stop);
  };

  const steps =
    selected !== null
      ? ringSteps(selected.ringIndex, currentStop.ringIndex, board.stops.length)
      : 0;
  const cost =
    selected !== null
      ? formatMinutes(steps * board.travel.minutesPerStep + board.travel.enterBuildingMinutes)
      : '';

  return (
    <section className="board-screen">
      <h1>Game</h1>
      <p className="board-status">
        Round {player.round} — {player.timeDisplay} remaining — R{player.cash}
      </p>
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
          />
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
          {selected !== null ? (
            <div className="board-confirm">
              <p>
                {steps === 0
                  ? `Enter ${selected.name}: costs ${cost}`
                  : `Travel to ${selected.name}: ${steps} stops, costs ${cost}`}
              </p>
              <div className="board-confirm-actions">
                <button
                  type="button"
                  onClick={() => moveMutation.mutate(selected.id)}
                  disabled={moveMutation.isPending}
                >
                  Confirm
                </button>
                <button
                  type="button"
                  onClick={() => setSelected(null)}
                  disabled={moveMutation.isPending}
                >
                  Cancel
                </button>
              </div>
            </div>
          ) : (
            <>
              <img
                src={storefrontImage(currentStop.id)}
                alt={currentStop.name}
                className="board-storefront"
              />
              <p className="board-location-name">{currentStop.name}</p>
              <p className="board-muted">Stats &amp; objectives arrive with KAN-41.</p>
            </>
          )}
        </div>
      </div>
      <p role="status" className="board-notice">
        {notice}
      </p>
    </section>
  );
}
