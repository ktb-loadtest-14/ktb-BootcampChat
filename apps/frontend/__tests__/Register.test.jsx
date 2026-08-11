import React from 'react';
import { hydrateRoot } from 'react-dom/client';
import { renderToString } from 'react-dom/server';
import { screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Register from '../pages/register';

const mocks = vi.hoisted(() => ({
  register: vi.fn(),
  push: vi.fn(),
}));

vi.mock('next/router', () => ({
  useRouter: () => ({
    isReady: true,
    push: mocks.push,
  }),
}));

vi.mock('@/contexts/AuthContext', () => ({
  useAuth: () => ({ register: mocks.register }),
  withoutAuth: (Component) => Component,
}));

describe('Register hydration guard', () => {
  beforeEach(() => {
    mocks.register.mockReset();
    mocks.push.mockReset();
  });

  it('disables the complete registration form in server-rendered HTML', () => {
    const html = renderToString(<Register />);
    const document = new DOMParser().parseFromString(html, 'text/html');

    expect(document.querySelector('[data-testid="register-page"]')?.getAttribute('data-hydrated'))
      .toBe('false');
    expect(document.querySelector('[data-testid="register-name-input"]')?.disabled).toBe(true);
    expect(document.querySelector('[data-testid="register-email-input"]')?.disabled).toBe(true);
    expect(document.querySelector('[data-testid="register-password-input"]')?.disabled).toBe(true);
    expect(document.querySelector('[data-testid="register-password-confirm-input"]')?.disabled)
      .toBe(true);
    expect(document.querySelector('[data-testid="register-submit-button"]')?.disabled).toBe(true);
  });

  it('enables the server-rendered registration form only after hydration', async () => {
    const container = document.createElement('div');
    container.innerHTML = renderToString(<Register />);
    document.body.appendChild(container);

    const root = hydrateRoot(container, <Register />);

    await waitFor(() => {
      expect(screen.getByTestId('register-page')).toHaveAttribute('data-hydrated', 'true');
    });

    expect(screen.getByTestId('register-name-input')).toBeEnabled();
    expect(screen.getByTestId('register-email-input')).toBeEnabled();
    expect(screen.getByTestId('register-password-input')).toBeEnabled();
    expect(screen.getByTestId('register-password-confirm-input')).toBeEnabled();
    expect(screen.getByTestId('register-submit-button')).toBeEnabled();

    root.unmount();
    container.remove();
  });
});
