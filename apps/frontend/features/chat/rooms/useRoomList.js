import { useState, useCallback, useRef } from 'react';
import axiosInstance from '@/services/axios';
import { CONNECTION_STATUS } from './useServerConnection';

const ROOM_LIST_CACHE_VERSION = 1;
const ROOM_LIST_CACHE_MAX_AGE_MS = 5 * 60 * 1000;
const ROOM_LIST_REQUEST_TIMEOUT_MS = 5000;

const getRoomListCacheKey = (currentUser) => {
  const userKey =
    currentUser?.id ||
    currentUser?._id ||
    currentUser?.email ||
    currentUser?.sessionId;

  return userKey ? `chat-room-list:v${ROOM_LIST_CACHE_VERSION}:${userKey}` : null;
};

const toCachedRoom = (room) => ({
  _id: room?._id,
  name: room?.name,
  hasPassword: Boolean(room?.hasPassword),
  participantsCount:
    room?.participantsCount ?? room?.participants?.length ?? 0,
  recentMessageCount: room?.recentMessageCount ?? 0,
  createdAt: room?.createdAt,
  isCreator: Boolean(room?.isCreator),
});

const readRoomListCache = (cacheKey) => {
  if (!cacheKey || typeof window === 'undefined') return null;

  try {
    const cached = JSON.parse(window.sessionStorage.getItem(cacheKey));
    const isFresh =
      cached?.savedAt &&
      Date.now() - cached.savedAt <= ROOM_LIST_CACHE_MAX_AGE_MS;

    if (!isFresh || !Array.isArray(cached.rooms)) {
      window.sessionStorage.removeItem(cacheKey);
      return null;
    }

    return cached.rooms;
  } catch {
    window.sessionStorage.removeItem(cacheKey);
    return null;
  }
};

const writeRoomListCache = (cacheKey, rooms) => {
  if (!cacheKey || typeof window === 'undefined') return;

  try {
    window.sessionStorage.setItem(
      cacheKey,
      JSON.stringify({
        savedAt: Date.now(),
        rooms: rooms.map(toCachedRoom),
      })
    );
  } catch {
    // 저장 공간이 부족해도 네트워크에서 받은 목록은 그대로 사용한다.
  }
};

export const useRoomList = ({
  currentUser,
  router,
  setConnectionStatus,
  isRetrying,
}) => {
  const cacheKey = getRoomListCacheKey(currentUser);
  const [initialRooms] = useState(() => readRoomListCache(cacheKey));
  const [rooms, setRoomsState] = useState(initialRooms || []);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(initialRooms === null);
  const [refreshing, setRefreshing] = useState(false);
  const [joiningRoomId, setJoiningRoomId] = useState(null);

  const isLoadingRef = useRef(false);
  const hasLoadedRoomsRef = useRef(initialRooms !== null);

  const setRooms = useCallback((roomsOrUpdater) => {
    setRoomsState((previousRooms) => {
      const nextRooms =
        typeof roomsOrUpdater === 'function'
          ? roomsOrUpdater(previousRooms)
          : roomsOrUpdater;

      return nextRooms;
    });
  }, []);

  const handleFetchError = useCallback((error) => {
    let errorMessage = '채팅방 목록을 불러오는데 실패했습니다.';
    let errorType = 'danger';
    let showRetry = !isRetrying;

    const isAuthExpired =
      error?.code === 'AUTH_EXPIRED' || error?.status === 401;
    const isServerUnreachable =
      error?.isNetworkError ||
      error?.code === 'NETWORK_ERROR' ||
      error?.message === 'SERVER_UNREACHABLE';

    if (isAuthExpired) {
      errorMessage = '인증이 만료되었습니다. 다시 로그인해주세요.';
      errorType = 'danger';
      showRetry = false;

      setError({
        title: '인증 만료',
        message: errorMessage,
        type: errorType,
        showRetry,
      });

      setConnectionStatus(CONNECTION_STATUS.ERROR);
      return;
    }

    if (isServerUnreachable) {
      errorMessage = '서버와 연결할 수 없습니다. 다시 시도해주세요.';
      errorType = 'warning';
      showRetry = true;
      setConnectionStatus(CONNECTION_STATUS.ERROR);
    }

    setError({
      title: '채팅방 목록 로드 실패',
      message: errorMessage,
      type: errorType,
      showRetry,
    });
  }, [isRetrying, setConnectionStatus]);

  const loadRooms = useCallback(async () => {
    // 화면 단의 짧은 재시도와 공통 axios 재시도가 겹치면 장애 중 요청이
    // 최대 9배로 불어난다. 목록은 이 화면에서만 총 3회로 제한한다.
    const response = await axiosInstance.get('/api/rooms', {
      timeout: ROOM_LIST_REQUEST_TIMEOUT_MS,
      maxRetries: 0,
    });
    const nextRooms = response?.data?.data;

    if (!Array.isArray(nextRooms)) {
      throw new Error('INVALID_RESPONSE');
    }

    hasLoadedRoomsRef.current = true;
    setRooms(nextRooms);
    writeRoomListCache(cacheKey, nextRooms);
    return nextRooms;
  }, [cacheKey, setRooms]);

  const fetchRooms = useCallback(async () => {
    if (!currentUser?.token || isLoadingRef.current) {
      return null;
    }

    const hasLoadedRooms = hasLoadedRoomsRef.current;

    try {
      isLoadingRef.current = true;

      setLoading(!hasLoadedRooms);
      setRefreshing(hasLoadedRooms);
      setError(null);

      await loadRooms();
      return true;
    } catch (error) {
      handleFetchError(error);
      return false;
    } finally {
      setLoading(false);
      setRefreshing(false);
      isLoadingRef.current = false;
    }
  }, [currentUser?.token, loadRooms, handleFetchError]);

  /**
   * 이미 그려진 목록을 유지한 채 다시 조회한다.
   * 자동 갱신(silent)은 실패해도 화면을 흔들지 않고 다음 주기를 기다린다.
   */
  const refreshRooms = useCallback(async ({ silent = false } = {}) => {
    if (!currentUser?.token || isLoadingRef.current) {
      return false;
    }

    try {
      isLoadingRef.current = true;
      setRefreshing(true);

      await loadRooms();
      setError(null);

      return true;
    } catch (error) {
      if (!silent) {
        setError({
          title: '채팅방 목록 갱신 실패',
          message: '목록을 갱신하지 못했습니다. 잠시 후 다시 시도해주세요.',
          type: 'warning',
          showRetry: false,
        });
      }

      return false;
    } finally {
      setRefreshing(false);
      isLoadingRef.current = false;
    }
  }, [currentUser, loadRooms]);

  const handleJoinRoom = useCallback(async (roomId) => {
    setJoiningRoomId(roomId);
    setError(null);

    try {
      const response = await axiosInstance.post(`/api/rooms/${roomId}/join`, {});

      if (response.data.success) {
        router.push(`/chat/${roomId}`);
        return true;
      }

      throw new Error('INVALID_RESPONSE');
    } catch (error) {
      const status = error.response?.status ?? error.status;
      let errorMessage = '입장에 실패했습니다.';
      if (status === 404) {
        errorMessage = '채팅방을 찾을 수 없습니다.';
      } else if (status === 403) {
        errorMessage = '채팅방 입장 권한이 없습니다.';
      }

      setError({
        title: '채팅방 입장 실패',
        message:
          error.response?.data?.message || error.data?.message || errorMessage,
        type: 'danger',
      });
      return false;
    } finally {
      setJoiningRoomId(null);
    }
  }, [router]);

  return {
    rooms,
    setRooms,
    error,
    setError,
    loading,
    refreshing,
    joiningRoom: joiningRoomId !== null,
    joiningRoomId,
    fetchRooms,
    refreshRooms,
    handleJoinRoom,
  };
};

export default useRoomList;
