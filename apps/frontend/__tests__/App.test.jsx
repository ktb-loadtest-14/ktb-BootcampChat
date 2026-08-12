import React from 'react';
import { renderToString } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import MyApp from '../pages/_app';

const mocks = vi.hoisted(() => ({
  pathname: '/chat/new',
}));

vi.mock('next/router', () => ({
  useRouter: () => ({ pathname: mocks.pathname }),
}));

vi.mock('@vapor-ui/core', () => ({
  ThemeProvider: ({ children }) => children,
}));

vi.mock('@/contexts/AuthContext', () => ({
  AuthProvider: ({ children }) => children,
}));

vi.mock('@/components/AuthenticatedPagesShell', () => ({
  default: ({ children }) => (
    <div data-testid="authenticated-pages-shell">{children}</div>
  ),
}));

const Page = () => <main data-testid="page">page</main>;

describe('MyApp authenticated page rendering', () => {
  beforeEach(() => {
    mocks.pathname = '/chat/new';
  });

  it('server-renders exactly one authenticated shell and page', () => {
    const html = renderToString(<MyApp Component={Page} pageProps={{}} />);
    const document = new DOMParser().parseFromString(html, 'text/html');

    expect(document.querySelectorAll('[data-testid="authenticated-pages-shell"]')).toHaveLength(1);
    expect(document.querySelectorAll('[data-testid="page"]')).toHaveLength(1);
  });

  it('does not render the authenticated shell on public pages', () => {
    mocks.pathname = '/register';

    const html = renderToString(<MyApp Component={Page} pageProps={{}} />);
    const document = new DOMParser().parseFromString(html, 'text/html');

    expect(document.querySelectorAll('[data-testid="authenticated-pages-shell"]')).toHaveLength(0);
    expect(document.querySelectorAll('[data-testid="page"]')).toHaveLength(1);
  });
});
