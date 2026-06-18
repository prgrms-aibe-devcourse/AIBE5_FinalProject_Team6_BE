package com.fandrops.user.application.constant;

import com.fandrops.user.application.exception.InvalidContentTypeException;

public enum AllowedImageContentType {
    JPEG("image/jpeg", ".jpg"),
    PNG("image/png",   ".png"),
    WEBP("image/webp", ".webp");

    private final String mimeType;
    private final String extension;

    AllowedImageContentType(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    public static boolean isAllowed(String mimeType) {
        for (AllowedImageContentType t : values()) {
            if (t.mimeType.equals(mimeType)) return true;
        }
        return false;
    }

    public static String extensionFor(String mimeType) {
        for (AllowedImageContentType t : values()) {
            if (t.mimeType.equals(mimeType)) return t.extension;
        }
        throw new InvalidContentTypeException(mimeType);
    }
}
