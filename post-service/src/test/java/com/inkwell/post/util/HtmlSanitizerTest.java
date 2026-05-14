package com.inkwell.post.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HtmlSanitizerTest {

    @Test
    void sanitizeShouldRemoveMaliciousScripts() {
        String input = "<p>Hello</p><script>alert('xss')</script>";
        String expected = "<p>Hello</p>";
        assertEquals(expected, HtmlSanitizer.sanitize(input));
    }

    @Test
    void sanitizeShouldAllowImagesAndHeaders() {
        String input = "<h1>Title</h1>";
        String output = HtmlSanitizer.sanitize(input);
        assertTrue(output.contains("<h1>Title</h1>"));
    }

    @Test
    void sanitizeShouldReturnNullForNullInput() {
        assertNull(HtmlSanitizer.sanitize(null));
    }

    @Test
    void stripAllShouldRemoveAllTags() {
        String input = "<div><span>Text</span> <p>Paragraph</p></div>";
        String expected = "Text Paragraph";
        assertEquals(expected, HtmlSanitizer.stripAll(input).trim());
    }

    @Test
    void stripAllShouldReturnNullForNullInput() {
        assertNull(HtmlSanitizer.stripAll(null));
    }
}
