package com.inkwell.media.util;

import java.util.Set;
import java.util.UUID;

public class FileUtil {

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf"
    );

    public static String generateUniqueFilename(String originalFilename) {
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        return UUID.randomUUID().toString() + extension;
    }

    public static boolean isValidFileType(String mimeType) {
        if (mimeType == null) return false;
        return ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase());
    }

    public static boolean isValidFileSize(long sizeBytes) {
        return sizeBytes > 0 && sizeBytes <= MAX_FILE_SIZE_BYTES;
    }

    public static String getAllowedTypesDescription() {
        return "JPEG, PNG, GIF, WebP, PDF";
    }
}
