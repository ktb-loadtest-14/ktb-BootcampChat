import React from 'react';
import dynamic from 'next/dynamic';
import { useRouter } from 'next/router';
import { ThemeProvider } from '@vapor-ui/core';
import '@vapor-ui/core/styles.css';
import '../styles/globals.css';
import { AuthProvider } from '@/contexts/AuthContext';

const AuthenticatedPagesShell = dynamic(
  () => import('@/components/AuthenticatedPagesShell')
);

const PUBLIC_PAGES = new Set(['/', '/register']);

function MyApp({ Component, pageProps }) {
  const router = useRouter();

  const isErrorPage = router.pathname === '/_error';
  if (isErrorPage) {
    return <Component {...pageProps} />;
  }

  const isPublicPage = PUBLIC_PAGES.has(router.pathname);

  return (
    <ThemeProvider defaultTheme="dark">
      <AuthProvider>
        {isPublicPage ? (
          <Component {...pageProps} />
        ) : (
          <AuthenticatedPagesShell>
            <Component {...pageProps} />
          </AuthenticatedPagesShell>
        )}
      </AuthProvider>
    </ThemeProvider>
  );
}

export default MyApp;
