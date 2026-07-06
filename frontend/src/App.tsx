import { Link, Route, Routes } from 'react-router';
import { useAuth } from './auth/AuthContext';
import { RequireAuth } from './routes/RequireAuth';
import { GamePage } from './pages/GamePage';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';

function App() {
  const { username, logout } = useAuth();

  return (
    <>
      <header>
        <strong>AmissProj</strong>
        <nav>
          <Link to="/">Home</Link>
          <Link to="/game">Game</Link>
        </nav>
        <div className="session">
          {username !== null ? (
            <>
              <span>{username}</span>
              <button type="button" onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <Link to="/login">Log in</Link>
          )}
        </div>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/game"
            element={
              <RequireAuth>
                <GamePage />
              </RequireAuth>
            }
          />
          <Route path="*" element={<p>Page not found.</p>} />
        </Routes>
      </main>
    </>
  );
}

export default App;
