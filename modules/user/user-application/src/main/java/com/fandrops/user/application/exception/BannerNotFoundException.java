package com.fandrops.user.application.exception;

public class BannerNotFoundException extends RuntimeException {
    public BannerNotFoundException(String message) {
        super(message);
    }
}