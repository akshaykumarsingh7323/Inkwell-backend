package com.inkwell.category.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SlugUtilTest {

    @Test
    void toSlug_ShouldNormalizeInput() {
        assertEquals("hello-world", SlugUtil.toSlug("Hello World"));
        assertEquals("cafe-au-lait", SlugUtil.toSlug("Cafe au lait"));
    }

    @Test
    void toSlug_WhenInputNull_ShouldReturnEmptyString() {
        assertEquals("", SlugUtil.toSlug(null));
    }
}
