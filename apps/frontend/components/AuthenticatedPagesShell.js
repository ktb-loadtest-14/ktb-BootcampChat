import React from 'react';
import ChatHeader from '@/components/ChatHeader';
import ToastContainer from '@/components/Toast';
import { useAuth } from '@/contexts/AuthContext';
import { SocketProvider } from '@/lib/socket/SocketProvider';

const AuthenticatedSocketProvider = ({ children }) => {
  const { user } = useAuth();

  return <SocketProvider session={user}>{children}</SocketProvider>;
};

const AuthenticatedPagesShell = ({ children }) => (
  <AuthenticatedSocketProvider>
    <ChatHeader />
    {children}
    <ToastContainer />
  </AuthenticatedSocketProvider>
);

export default AuthenticatedPagesShell;
