package com.fandrops.user.application.exception;

public class InvalidContentTypeException extends IllegalArgumentException {
    public InvalidContentTypeException(String contentType) {
        super("허용되지 않는 파일 형식입니다: " + contentType + " (허용: image/jpeg, image/png, image/webp)");
    }
}