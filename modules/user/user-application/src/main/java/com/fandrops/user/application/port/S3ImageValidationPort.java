package com.fandrops.user.application.port;

public interface S3ImageValidationPort {
    boolean imageExists(String imageUrl);
    boolean isOwnedUrl(String imageUrl);
}