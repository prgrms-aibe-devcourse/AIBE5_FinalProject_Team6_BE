package com.fandrops.community.application.exception;

public class ScheduleNotFoundException extends RuntimeException {

    public ScheduleNotFoundException(String message) {
        super(message);
    }
}