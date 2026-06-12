package com.fandrops.user.application.exception;

public class DuplicateAgencyAccountException extends RuntimeException {
    public DuplicateAgencyAccountException(String message) {
        super(message);
    }
}
