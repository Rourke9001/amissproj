import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';

export function HomePage() {
  const { username } = useAuth();

  return (
    <section className="home-page">
      <h1>Amiss</h1>
      <p>A browser port of the 1990 economic sim Jones in the Fast Lane.</p>
      <Link to={username === null ? '/login' : '/saves'} className="cta-button">
        {username === null ? 'Log In to Play' : 'Play'}
      </Link>
    </section>
  );
}
