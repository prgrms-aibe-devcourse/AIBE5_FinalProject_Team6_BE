package com.fandrops.community.application.exception;

public class NotFanMemberException extends RuntimeException {
    public NotFanMemberException(String message) {
        super(message);
    }
}