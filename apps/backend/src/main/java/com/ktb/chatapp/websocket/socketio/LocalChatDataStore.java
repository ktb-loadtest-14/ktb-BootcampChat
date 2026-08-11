package com.ktb.chatapp.websocket.socketio;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local in-memory implementation of ChatDataStore using ConcurrentHashMap.
 * Thread-safe storage for chat-related data without external dependencies.
 */
public class LocalChatDataStore implements ChatDataStore {
    
    private final ConcurrentHashMap<String, Object> storage = new ConcurrentHashMap<>();
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        Object value = storage.get(key);
        if (value == null) {
            return Optional.empty();
        }
        
        try {
            return Optional.of(type.cast(value));
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }
    
    @Override
    public void set(String key, Object value) {
        storage.put(key, value);
    }
    
    @Override
    public void delete(String key) {
        storage.remove(key);
    }

    @Override
    public Set<String> getSet(String key) {
        return copyStringSet(storage.get(key));
    }

    @Override
    public void addToSet(String key, String value) {
        storage.compute(key, (ignored, current) -> {
            Set<String> values = new HashSet<>(copyStringSet(current));
            values.add(value);
            return Set.copyOf(values);
        });
    }

    @Override
    public void removeFromSet(String key, String value) {
        storage.computeIfPresent(key, (ignored, current) -> {
            Set<String> values = new HashSet<>(copyStringSet(current));
            values.remove(value);
            return values.isEmpty() ? null : Set.copyOf(values);
        });
    }
    
    @Override
    public int size() {
        return storage.size();
    }

    private Set<String> copyStringSet(Object value) {
        if (!(value instanceof Set<?> values)) {
            return Set.of();
        }

        Set<String> copy = new HashSet<>();
        for (Object item : values) {
            if (item instanceof String stringValue) {
                copy.add(stringValue);
            }
        }
        return Set.copyOf(copy);
    }
}
