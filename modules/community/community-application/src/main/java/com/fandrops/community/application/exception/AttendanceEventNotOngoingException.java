package com.fandrops.community.application.exception;

public class AttendanceEventNotOngoingException extends RuntimeException {
    public AttendanceEventNotOngoingException(String message) {
        super(message);
    }
}