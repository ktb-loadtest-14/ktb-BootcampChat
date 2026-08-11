import React from 'react';
import { hydrateRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Login from '../pages/index';

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  push: vi.fn(),
}));

vi.mock('next/router', () => ({
  useRouter: () => ({
    isReady: true,
    query: {},
    push: mocks.push,
  }),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ login: mocks.login }),
  withoutAuth: (Component) => Component,
}));

describe('Login hydration guard', () => {
  beforeEach(() => {
    mocks.login.mockReset();
    mocks.push.mockReset();
  });

  it('disables every form control in the server-rendered HTML', () => {
    const html = renderToString(<Login />);
    const document = new DOMParser().parseFromString(html, 'text/html');

    expect(document.querySelector('[data-testid="login-page"]')?.getAttribute('data-hydrated'))
      .toBe('false');
    expect(document.querySelector('[data-testid="login-email-input"]')?.disabled).toBe(true);
    expect(document.querySelector('[data-testid="login-password-input"]')?.disabled).toBe(true);
    expect(document.querySelector('[data-testid="login-submit-button"]')?.disabled).toBe(true);
  });

  it('enables the server-rendered login form only after client hydration', async () => {
    const container = document.createElement('div');
    container.innerHTML = renderToString(<Login />);
    document.body.appendChild(container);

    const root = hydrateRoot(container, <Login />);

    await waitFor(() => {
      expect(screen.getByTestId('login-page')).toHaveAttribute('data-hydrated', 'true');
    });

    expect(screen.getByTestId('login-email-input')).toBeEnabled();
    expect(screen.getByTestId('login-password-input')).toBeEnabled();
    expect(screen.getByTestId('login-submit-button')).toBeEnabled();

    root.unmount();
    container.remove();
  });
});
