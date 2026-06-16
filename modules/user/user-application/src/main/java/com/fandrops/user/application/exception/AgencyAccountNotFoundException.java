package com.fandrops.user.application.exception;

public class AgencyAccountNotFoundException extends RuntimeException {
    public AgencyAccountNotFoundException(String message) {
        super(message);
    }
}
