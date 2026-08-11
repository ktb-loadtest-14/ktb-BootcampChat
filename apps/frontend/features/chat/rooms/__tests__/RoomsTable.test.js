import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import RoomsTable from '../RoomsTable';

const createRooms = (count) =>
  Array.from({ length: count }, (_, index) => ({
    _id: `room-${index}`,
    name: `방 ${index}`,
    participantsCount: index,
    recentMessageCount: 0,
    createdAt: '2026-08-11T10:00:00Z',
  }));

describe('RoomsTable', () => {
  it('renders large room lists in bounded batches', () => {
    render(
      <RoomsTable
        rooms={createRooms(100)}
        joiningRoomId={null}
        onJoinRoom={vi.fn()}
      />
    );

    expect(screen.getAllByTestId('join-chat-room-button')).toHaveLength(40);

    fireEvent.click(screen.getByText('방 더 보기 (60)'));

    expect(screen.getAllByTestId('join-chat-room-button')).toHaveLength(80);
    expect(screen.getByText('방 더 보기 (20)')).toBeTruthy();
  });

  it('only disables the room whose join request is in flight', () => {
    render(
      <RoomsTable
        rooms={createRooms(2)}
        joiningRoomId="room-1"
        onJoinRoom={vi.fn()}
      />
    );

    const buttons = screen.getAllByTestId('join-chat-room-button');
    expect(buttons[0]).not.toBeDisabled();
    expect(buttons[1]).toBeDisabled();
  });
});
