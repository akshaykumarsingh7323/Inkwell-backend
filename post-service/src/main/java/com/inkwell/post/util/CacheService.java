package com.inkwell.post.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class CacheService {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        Object value;
        long expiryTime;

        CacheEntry(Object value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    public void put(String key, Object value, long timeout, TimeUnit unit) {
        long expiryTime = System.currentTimeMillis() + unit.toMillis(timeout);
        cache.put(key, new CacheEntry(value, expiryTime));
    }

    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() > entry.expiryTime) {
                cache.remove(key);
                return null;
            }
            return entry.value;
        }
        return null;
    }

    public boolean hasKey(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            if (System.currentTimeMillis() > entry.expiryTime) {
                cache.remove(key);
                return false;
            }
            return true;
        }
        return false;
    }

    public void delete(String key) {
        cache.remove(key);
    }
}
