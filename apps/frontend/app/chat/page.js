'use client';

import { useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import ChatHeader from '@/components/ChatHeader';
import { useAuth } from '@/contexts/AuthContext';
import ChatRoomsView from '@/features/chat/rooms/ChatRoomsView';

const LoadingState = () => (
  <div
    style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      height: '100%',
      backgroundColor: 'var(--vapor-color-background)',
      color: 'var(--vapor-color-text-primary)',
    }}
  >
    <div>Loading...</div>
  </div>
);

export default function ChatPage() {
  const router = useRouter();
  const pathname = usePathname();
  const { isAuthenticated, isLoading } = useAuth();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace(`/?redirect=${pathname}`);
    }
  }, [isAuthenticated, isLoading, pathname, router]);

  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        height: '100dvh',
        overflow: 'hidden',
      }}
    >
      {(isLoading || isAuthenticated) && <ChatHeader />}
      <main style={{ flex: '1 1 auto', minHeight: 0 }}>
        {isLoading || !isAuthenticated ? (
          <LoadingState />
        ) : (
          <ChatRoomsView router={router} />
        )}
      </main>
    </div>
  );
}
