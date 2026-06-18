package com.fandrops.user.application.exception;

public class InvalidImageUrlException extends IllegalArgumentException {
    public InvalidImageUrlException(String imageUrl) {
        super("허용되지 않는 이미지 URL입니다: " + imageUrl);
    }
}
