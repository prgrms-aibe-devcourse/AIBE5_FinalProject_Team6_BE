package com.fandrops.community.application.exception;

public class AttendanceEventNotFoundException extends RuntimeException {
    public AttendanceEventNotFoundException(String message) {
        super(message);
    }
}