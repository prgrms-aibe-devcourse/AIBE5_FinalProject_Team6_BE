package com.fandrops.user.application.exception;

public class S3ImageNotFoundException extends RuntimeException {
    public S3ImageNotFoundException(String imageUrl) {
        super("이미지를 먼저 업로드해주세요. S3에서 찾을 수 없음: " + imageUrl);
    }
}