import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

// Vitest runs without `globals: true`, so React Testing Library cannot
// register its automatic afterEach cleanup; do it explicitly.
afterEach(() => {
  cleanup();
});
