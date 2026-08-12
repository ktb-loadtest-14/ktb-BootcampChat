import React from 'react';
import ChatHeader from '@/components/ChatHeader';
import ToastContainer from '@/components/Toast';

const AuthenticatedPagesShell = ({ children }) => (
  <>
    <ChatHeader />
    {children}
    <ToastContainer />
  </>
);

export default AuthenticatedPagesShell;
