import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { deleteSave, listSaves } from '../api/saves';
import { errorMessage } from '../api/http';
import { NewGameSetup } from '../game/NewGameSetup';
import './saves.css';

export function SavesPage() {
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const savesQuery = useQuery({ queryKey: ['saves'], queryFn: listSaves });
  const deleteMutation = useMutation({
    mutationFn: (id: number) => deleteSave(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saves'] }),
  });

  if (creating) {
    return (
      <NewGameSetup
        onCreated={(saveId) => navigate(`/play/${saveId}`)}
        onCancel={() => setCreating(false)}
      />
    );
  }

  return (
    <section className="saves-page">
      <h1>Your Saves</h1>
      {savesQuery.isPending && <p>Loading saves...</p>}
      {savesQuery.isError && <p role="alert">{errorMessage(savesQuery.error)}</p>}
      {savesQuery.data && savesQuery.data.length === 0 && <p>No saves yet, start a new game.</p>}
      {savesQuery.data && savesQuery.data.length > 0 && (
        <ul className="saves-list">
          {savesQuery.data.map((save) => (
            <li key={save.id} className="save-row">
              <span className="save-label">{save.label}</span>
              <span className="save-meta">
                Round {save.round} - R{save.cash}
                {save.won ? ' - Won!' : ''}
              </span>
              <button type="button" onClick={() => navigate(`/play/${save.id}`)}>
                Continue
              </button>
              <button
                type="button"
                className="danger"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  if (window.confirm(`Delete "${save.label}"? This cannot be undone.`)) {
                    deleteMutation.mutate(save.id);
                  }
                }}
              >
                Delete
              </button>
            </li>
          ))}
        </ul>
      )}
      {deleteMutation.isError && <p role="alert">{errorMessage(deleteMutation.error)}</p>}
      <button type="button" onClick={() => setCreating(true)}>
        New Game
      </button>
    </section>
  );
}
