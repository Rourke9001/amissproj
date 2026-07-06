import { useQuery } from '@tanstack/react-query';
import { fetchHighscores } from '../api/highscores';

export function HomePage() {
  const { data, isPending, error } = useQuery({
    queryKey: ['highscores'],
    queryFn: fetchHighscores,
  });

  return (
    <section>
      <h1>Highscores</h1>
      {isPending && <p>Loading highscores…</p>}
      {error !== null && (
        <p role="alert">
          {error.message} — check that the Spring API is running on http://localhost:8080.
        </p>
      )}
      {data !== undefined &&
        (data.length === 0 ? (
          <p>No scores yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Rank</th>
                <th>Player</th>
                <th>Round</th>
              </tr>
            </thead>
            <tbody>
              {data.map((entry) => (
                <tr key={entry.rank}>
                  <td>{entry.rank}</td>
                  <td>{entry.username}</td>
                  <td>{entry.round}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}
    </section>
  );
}
