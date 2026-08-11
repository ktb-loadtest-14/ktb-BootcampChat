import React, { memo, useCallback, useEffect, useState } from 'react';
import { LockIcon, GroupIcon } from '@vapor-ui/icons';
import { Button, Text, VStack, HStack } from '@vapor-ui/core';
import * as Table from '@/components/Table';

const INITIAL_VISIBLE_ROOM_COUNT = 40;
const ROOM_RENDER_BATCH_SIZE = 40;
const LOAD_MORE_THRESHOLD_PX = 160;

const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

const getCreatedAt = (createdAt) => {
  const date = new Date(createdAt);
  if (Number.isNaN(date.getTime())) {
    return { dateTime: undefined, label: '-' };
  }

  return {
    dateTime: date.toISOString(),
    label: dateFormatter.format(date),
  };
};

const RoomRow = memo(({ room, isJoining, onJoinRoom }) => {
  const createdAt = getCreatedAt(room.createdAt);
  const participantsCount =
    room.participantsCount ?? room.participants?.length ?? 0;

  return (
    <Table.Row>
      <Table.Cell>
        <VStack $css={{ gap: '$050', alignItems: 'flex-start' }}>
          <Text style={{ fontWeight: 500 }}>{room.name}</Text>
          {room.hasPassword && (
            <HStack
              $css={{
                gap: '$050',
                alignItems: 'center',
                color: '$warning-100',
              }}
            >
              <LockIcon size={16} />
              <Text typography="body3" foreground="warning-100">
                비밀번호 필요
              </Text>
            </HStack>
          )}
        </VStack>
      </Table.Cell>
      <Table.Cell>
        <HStack $css={{ gap: '$050', alignItems: 'center' }}>
          <GroupIcon />
          <Text typography="body2">{participantsCount}</Text>
        </HStack>
      </Table.Cell>
      <Table.Cell>
        {room.recentMessageCount > 0 ? room.recentMessageCount : '-'}
      </Table.Cell>
      <Table.Cell>
        <time dateTime={createdAt.dateTime}>{createdAt.label}</time>
      </Table.Cell>
      <Table.Cell>
        <Button
          colorPalette="primary"
          size="md"
          onClick={() => onJoinRoom(room._id)}
          disabled={isJoining}
          data-testid="join-chat-room-button"
        >
          {isJoining ? '입장 중' : '입장'}
        </Button>
      </Table.Cell>
    </Table.Row>
  );
});
RoomRow.displayName = 'RoomRow';

const RoomsTable = memo(({ rooms, joiningRoomId, onJoinRoom }) => {
  const [visibleCount, setVisibleCount] = useState(() =>
    Math.min(rooms?.length || 0, INITIAL_VISIBLE_ROOM_COUNT)
  );

  useEffect(() => {
    setVisibleCount((currentCount) =>
      Math.min(
        rooms.length,
        Math.max(currentCount, INITIAL_VISIBLE_ROOM_COUNT)
      )
    );
  }, [rooms.length]);

  const loadMoreRooms = useCallback(() => {
    setVisibleCount((currentCount) =>
      Math.min(rooms.length, currentCount + ROOM_RENDER_BATCH_SIZE)
    );
  }, [rooms.length]);

  const handleScroll = useCallback(
    (event) => {
      const container = event.currentTarget;
      const remainingScroll =
        container.scrollHeight - container.scrollTop - container.clientHeight;

      if (remainingScroll <= LOAD_MORE_THRESHOLD_PX) {
        loadMoreRooms();
      }
    },
    [loadMoreRooms]
  );

  if (!rooms || rooms.length === 0) return null;

  const visibleRooms = rooms.slice(0, visibleCount);

  return (
    <div
      className="chat-rooms-table"
      style={{
        width: '100%',
        height: '100%',
        overflowY: 'auto',
        position: 'relative',
        borderRadius: '0.5rem',
        backgroundColor: 'var(--background-normal)',
        border: '1px solid var(--border-color)',
        WebkitOverflowScrolling: 'touch',
      }}
      onScroll={handleScroll}
    >
      <Table.Root style={{ width: '100%' }}>
        <Table.ColumnGroup>
          <Table.Column style={{ width: '40%' }} />
          <Table.Column style={{ width: '12%' }} />
          <Table.Column style={{ width: '12%' }} />
          <Table.Column style={{ width: '21%' }} />
          <Table.Column style={{ width: '15%' }} />
        </Table.ColumnGroup>

        <Table.Header>
          <Table.Row>
            <Table.Heading>채팅방</Table.Heading>
            <Table.Heading>참여자</Table.Heading>
            <Table.Heading>최근 메시지</Table.Heading>
            <Table.Heading>생성일</Table.Heading>
            <Table.Heading>액션</Table.Heading>
          </Table.Row>
        </Table.Header>

        <Table.Body>
          {visibleRooms.map((room) => (
            <RoomRow
              key={room._id}
              room={room}
              isJoining={joiningRoomId === room._id}
              onJoinRoom={onJoinRoom}
            />
          ))}
        </Table.Body>
      </Table.Root>
      {visibleCount < rooms.length && (
        <div
          style={{ display: 'flex', justifyContent: 'center', padding: '12px' }}
        >
          <Button variant="outline" size="sm" onClick={loadMoreRooms}>
            방 더 보기 ({rooms.length - visibleCount})
          </Button>
        </div>
      )}
    </div>
  );
});
RoomsTable.displayName = 'RoomsTable';

export default RoomsTable;
