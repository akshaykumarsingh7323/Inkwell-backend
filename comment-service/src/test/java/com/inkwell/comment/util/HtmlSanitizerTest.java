package com.inkwell.comment.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerTest {

    @Test
    void sanitize_WithNull_ShouldReturnNull() {
        assertNull(HtmlSanitizer.sanitize(null));
    }

    @Test
    void sanitize_WithHtml_ShouldSanitize() {
        String input = "<script>alert('xss')</script><p>Hello</p>";
        String result = HtmlSanitizer.sanitize(input);
        assertFalse(result.contains("script"));
        assertTrue(result.contains("<p>Hello</p>"));
    }

    @Test
    void stripAll_WithNull_ShouldReturnNull() {
        assertNull(HtmlSanitizer.stripAll(null));
    }

    @Test
    void stripAll_WithHtml_ShouldStripAllTags() {
        String input = "<b>Bold</b> <i>Italic</i>";
        String result = HtmlSanitizer.stripAll(input);
        assertEquals("Bold Italic", result.trim());
    }
}
