package com.inkwell.media.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilTest {

    @Test
    void generateUniqueFilename_ShouldKeepExtension() {
        String filename = FileUtil.generateUniqueFilename("photo.JPG");

        assertTrue(filename.endsWith(".jpg"));
    }

    @Test
    void generateUniqueFilename_WhenNoExtension_ShouldStillReturnValue() {
        String filename = FileUtil.generateUniqueFilename("photo");

        assertFalse(filename.isBlank());
        assertFalse(filename.contains("."));
    }

    @Test
    void isValidFileType_ShouldHandleAllowedAndInvalidValues() {
        assertTrue(FileUtil.isValidFileType("image/jpeg"));
        assertTrue(FileUtil.isValidFileType("IMAGE/PNG"));
        assertFalse(FileUtil.isValidFileType("text/plain"));
        assertFalse(FileUtil.isValidFileType(null));
    }

    @Test
    void isValidFileSize_ShouldValidateBounds() {
        assertFalse(FileUtil.isValidFileSize(0));
        assertTrue(FileUtil.isValidFileSize(1024));
        assertFalse(FileUtil.isValidFileSize(11L * 1024 * 1024));
    }

    @Test
    void getAllowedTypesDescription_ShouldReturnReadableText() {
        assertEquals("JPEG, PNG, GIF, WebP, PDF", FileUtil.getAllowedTypesDescription());
    }
}
