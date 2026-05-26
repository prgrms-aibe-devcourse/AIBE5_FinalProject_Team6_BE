package com.fandrops.user.application.exception;

public class FanNotFoundException extends RuntimeException {
    public FanNotFoundException(String message) {
        super(message);
    }
}