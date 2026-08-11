package com.ktb.chatapp.websocket.socketio;

import java.util.Optional;
import java.util.Set;

/**
 * Data store interface for chat-related data storage.
 * Provides key-value storage operations for chat user and room data.
 */
public interface ChatDataStore {
    
    /**
     * Retrieve a value by key
     *
     * @param key the storage key
     * @param type the type of value to retrieve
     * @param <T> the type parameter
     * @return Optional containing the value if found, empty otherwise
     */
    <T> Optional<T> get(String key, Class<T> type);
    
    /**
     * Store a value with the given key
     *
     * @param key the storage key
     * @param value the value to store
     */
    void set(String key, Object value);
    
    /**
     * Delete a value by key
     *
     * @param key the storage key
     */
    void delete(String key);

    /** Retrieve a defensive snapshot of a string set. */
    Set<String> getSet(String key);

    /** Add one member without replacing concurrent additions. */
    void addToSet(String key, String value);

    /** Remove one member without replacing concurrent changes. */
    void removeFromSet(String key, String value);
    
    int size();
}
