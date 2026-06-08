package com.fandrops.user.application.exception;

public class AgencyApplicationNotFoundException extends RuntimeException {
    public AgencyApplicationNotFoundException(String message) {
        super(message);
    }
}
