import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { HomePage } from './HomePage';

const mockUseAuth = vi.fn();
vi.mock('../auth/AuthContext', () => ({ useAuth: () => mockUseAuth() }));

describe('HomePage', () => {
  it('links to /login when signed out', () => {
    mockUseAuth.mockReturnValue({ username: null });
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Log In to Play' })).toHaveAttribute('href', '/login');
  });

  it('links to /saves when signed in', () => {
    mockUseAuth.mockReturnValue({ username: 'alice' });
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Play' })).toHaveAttribute('href', '/saves');
  });
});
