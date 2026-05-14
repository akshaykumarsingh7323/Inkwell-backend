package com.inkwell.post.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CacheServiceTest {

    private CacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new CacheService();
    }

    @Test
    void putAndGetShouldWork() {
        cacheService.put("key1", "value1", 1, TimeUnit.MINUTES);
        assertEquals("value1", cacheService.get("key1"));
        assertTrue(cacheService.hasKey("key1"));
    }

    @Test
    void getShouldReturnNullAfterExpiry() throws InterruptedException {
        cacheService.put("key1", "value1", 10, TimeUnit.MILLISECONDS);
        Thread.sleep(20);
        assertNull(cacheService.get("key1"));
        assertFalse(cacheService.hasKey("key1"));
    }

    @Test
    void hasKeyShouldReturnFalseAfterExpiry() throws InterruptedException {
        cacheService.put("key2", "value2", 10, TimeUnit.MILLISECONDS);
        Thread.sleep(20);

        assertFalse(cacheService.hasKey("key2"));
    }

    @Test
    void deleteShouldRemoveKey() {
        cacheService.put("key1", "value1", 1, TimeUnit.MINUTES);
        cacheService.delete("key1");
        assertNull(cacheService.get("key1"));
        assertFalse(cacheService.hasKey("key1"));
    }

    @Test
    void hasKeyShouldReturnFalseForMissingKey() {
        assertFalse(cacheService.hasKey("nonexistent"));
    }

    @Test
    void getShouldReturnNullForMissingKey() {
        assertNull(cacheService.get("nonexistent"));
    }
}
