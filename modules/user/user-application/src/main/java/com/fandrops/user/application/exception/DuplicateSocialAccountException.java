package com.fandrops.user.application.exception;

public class DuplicateSocialAccountException extends RuntimeException {
    public DuplicateSocialAccountException(String message) {
        super(message);
    }
}
