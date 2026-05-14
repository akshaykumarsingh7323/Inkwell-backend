package com.inkwell.newsletter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokenUtilTest {

    @Test
    void generateToken_ShouldReturnUniqueNonBlankTokens() {
        String first = TokenUtil.generateToken();
        String second = TokenUtil.generateToken();

        assertFalse(first.isBlank());
        assertNotEquals(first, second);
    }
}
